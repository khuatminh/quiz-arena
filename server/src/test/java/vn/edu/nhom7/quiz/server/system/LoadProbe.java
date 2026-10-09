package vn.edu.nhom7.quiz.server.system;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import vn.edu.nhom7.quiz.common.protocol.*;
import vn.edu.nhom7.quiz.server.support.SocketHarness;

/** Real TCP latency utility. Requires pre-created demo1..demoN accounts and a running server. */
public final class LoadProbe {
  private static final ProtocolCodec CODEC = new ProtocolCodec();
  private static final Duration WAIT = Duration.ofSeconds(15);
  private final String host;
  private final int port, connections, matches, samples;
  private final Path csv;
  private final List<Row> rows = Collections.synchronizedList(new ArrayList<>());
  private final List<SocketHarness> clients = new ArrayList<>();
  private final List<Long> users = new ArrayList<>();

  private record Row(
      long event, String type, double latencyMs, int connections, int matches, double rttMs) {}

  private LoadProbe(Map<String, String> args) {
    host = args.getOrDefault("host", "localhost");
    port = Integer.parseInt(args.getOrDefault("port", "5555"));
    connections = Integer.parseInt(args.getOrDefault("connections", "4"));
    matches = Integer.parseInt(args.getOrDefault("matches", "2"));
    samples = Integer.parseInt(args.getOrDefault("samples", "100"));
    csv = Path.of(args.getOrDefault("csv", "latency.csv"));
    if (connections < 1
        || connections > 32
        || matches < 0
        || matches > 16
        || matches * 2 > connections
        || samples < 1)
      throw new IllegalArgumentException(
          "Require connections 1..32, matches 0..16 <= connections/2, samples >=1");
  }

  public static void main(String[] arguments) throws Exception {
    Map<String, String> args = new HashMap<>();
    for (String arg : arguments) {
      if (!arg.startsWith("--") || !arg.contains("="))
        throw new IllegalArgumentException("Expected --name=value");
      String[] pair = arg.substring(2).split("=", 2);
      if (!Set.of("host", "port", "connections", "matches", "samples", "csv").contains(pair[0]))
        throw new IllegalArgumentException("Unknown option " + pair[0]);
      args.put(pair[0], pair[1]);
    }
    new LoadProbe(args).run();
  }

  private Envelope command(MessageType type, UUID match, UUID round, Object payload) {
    return CODEC.envelope(type, UUID.randomUUID(), match, round, null, payload);
  }

  private void measure(String type, long started, double rtt) {
    rows.add(
        new Row(
            System.nanoTime(),
            type,
            (System.nanoTime() - started) / 1_000_000.0,
            connections,
            matches,
            rtt));
  }

  private SocketHarness login(int index, String password) throws Exception {
    SocketHarness c = SocketHarness.connect(host, port);
    try {
      c.send(command(MessageType.HELLO, null, null, new Payloads.Hello("2.0", "1")));
      c.await(MessageType.HELLO_ACK, WAIT);
      Envelope login =
          command(MessageType.LOGIN, null, null, new Payloads.Login("demo" + index, password));
      long start = System.nanoTime();
      c.send(login);
      Envelope result = c.await(MessageType.LOGIN_RESULT, login.requestId(), WAIT);
      measure("LOGIN_RESULT", start, Double.NaN);
      if (clients.size() < connections) {
        clients.add(c);
        users.add(result.payload().path("profile").path("userId").asLong());
      }
      return c;
    } catch (Exception e) {
      c.close();
      throw e;
    }
  }

  private void run() throws Exception {
    String password = System.getenv("QUIZ_DEMO_PASSWORD");
    if (password == null || password.isBlank())
      throw new IllegalArgumentException("Set QUIZ_DEMO_PASSWORD for existing demo accounts");
    ScheduledExecutorService pings = Executors.newSingleThreadScheduledExecutor();
    ExecutorService workers = Executors.newFixedThreadPool(Math.max(1, matches));
    try {
      // Repeated complete login batches provide the requested number of login measurements.
      int measured = 0;
      while (true) {
        clients.clear();
        users.clear();
        for (int i = 1; i <= connections; i++) {
          login(i, password);
          measured++;
        }
        if (measured >= samples) break;
        for (var c : clients) {
          Envelope logout = command(MessageType.LOGOUT, null, null, new Payloads.Logout());
          c.send(logout);
          c.await(MessageType.LOGOUT_ACK, logout.requestId(), WAIT);
          c.close();
        }
      }
      pings.scheduleAtFixedRate(
          () -> {
            for (var c : clients)
              try {
                c.send(
                    command(
                        MessageType.PING,
                        null,
                        null,
                        new Payloads.Ping(System.currentTimeMillis())));
              } catch (IOException ignored) {
              }
          },
          10,
          10,
          TimeUnit.SECONDS);
      double rtt = ping(clients.getFirst());
      int onlineSamples = 0;
      long[] nextQuery = new long[clients.size()];
      while (onlineSamples < samples) {
        int slot = onlineSamples % clients.size();
        long remaining = nextQuery[slot] - System.nanoTime();
        if (remaining > 0) java.util.concurrent.locks.LockSupport.parkNanos(remaining);
        nextQuery[slot] = System.nanoTime() + 110_000_000L;
        var c = clients.get(onlineSamples % clients.size());
        Envelope request =
            command(MessageType.ONLINE_LIST_REQUEST, null, null, new Payloads.OnlineListRequest());
        long start = System.nanoTime();
        c.send(request);
        c.await(MessageType.ONLINE_LIST, request.requestId(), WAIT);
        measure("ONLINE_LIST", start, rtt);
        onlineSamples++;
      }
      List<Future<?>> jobs = new ArrayList<>();
      for (int pair = 0; pair < matches; pair++) {
        int number = pair;
        jobs.add(
            workers.submit(
                () -> {
                  try {
                    playPair(number, rtt);
                  } catch (Exception e) {
                    throw new CompletionException(e);
                  }
                }));
      }
      for (var job : jobs) job.get();
    } finally {
      pings.shutdownNow();
      workers.shutdownNow();
      for (var c : clients)
        try {
          c.close();
        } catch (IOException ignored) {
        }
      write();
    }
  }

  private double ping(SocketHarness c) throws IOException {
    Envelope request =
        command(MessageType.PING, null, null, new Payloads.Ping(System.currentTimeMillis()));
    long start = System.nanoTime();
    c.send(request);
    c.await(MessageType.PONG, request.requestId(), WAIT);
    return (System.nanoTime() - start) / 1_000_000.0;
  }

  private void playPair(int pair, double rtt) throws Exception {
    SocketHarness one = clients.get(pair * 2), two = clients.get(pair * 2 + 1);
    Envelope challenge =
        command(
            MessageType.CHALLENGE, null, null, new Payloads.Challenge(users.get(pair * 2 + 1), 1));
    one.send(challenge);
    Envelope invite = two.await(MessageType.CHALLENGE_RECEIVED, WAIT);
    UUID invitation = UUID.fromString(invite.payload().path("challengeId").asText());
    two.send(
        command(
            MessageType.CHALLENGE_ACCEPT, null, null, new Payloads.ChallengeAccept(invitation)));
    int roundsNeeded = (int) Math.ceil(samples / (2.0 * matches));
    int played = 0;
    while (played < roundsNeeded) {
      Envelope start = one.await(MessageType.MATCH_START, WAIT);
      two.await(MessageType.MATCH_START, WAIT);
      UUID match = start.matchId();
      one.send(command(MessageType.MATCH_READY, match, null, new Payloads.MatchReady()));
      two.send(command(MessageType.MATCH_READY, match, null, new Payloads.MatchReady()));
      for (int round = 1; round <= 10 && played < roundsNeeded; round++, played++) {
        Envelope question = one.await(MessageType.QUESTION, WAIT);
        two.await(MessageType.QUESTION, WAIT);
        long id = question.payload().path("question").path("questionId").asLong();
        for (var c : List.of(one, two))
          c.send(
              command(
                  MessageType.QUESTION_READY,
                  match,
                  question.roundId(),
                  new Payloads.QuestionReady(id)));
        one.await(MessageType.QUESTION_OPEN, WAIT);
        two.await(MessageType.QUESTION_OPEN, WAIT);
        for (var c : List.of(one, two)) {
          Envelope chat =
              command(MessageType.CHAT, match, null, new Payloads.Chat("Latency probe"));
          long sent = System.nanoTime();
          c.send(chat);
          c.await(MessageType.CHAT_MESSAGE, chat.requestId(), WAIT);
          measure("CHAT_MESSAGE", sent, rtt);
          var q = question.payload().path("question");
          var mapper = CODEC.mapper();
          com.fasterxml.jackson.databind.JsonNode answer =
              switch (q.path("questionType").asText()) {
                case "SINGLE_CHOICE" ->
                    mapper.valueToTree(q.path("options").get(0).path("id").asText());
                case "MULTIPLE_CHOICE" ->
                    mapper.valueToTree(
                        List.of(
                            q.path("options").get(0).path("id").asText(),
                            q.path("options").get(1).path("id").asText()));
                case "TRUE_FALSE" -> mapper.valueToTree(true);
                case "SHORT_ANSWER" -> mapper.valueToTree("Hà Nội");
                default -> throw new IllegalStateException("Unknown question type");
              };
          Envelope submitted =
              command(
                  MessageType.ANSWER, match, question.roundId(), new Payloads.Answer(id, answer));
          sent = System.nanoTime();
          c.send(submitted);
          c.await(MessageType.ANSWER_ACK, submitted.requestId(), WAIT);
          measure("ANSWER_ACK", sent, rtt);
        }
        one.await(MessageType.QUESTION_RESULT, WAIT);
        two.await(MessageType.QUESTION_RESULT, WAIT);
        one.await(MessageType.ROUND_LEADERBOARD, WAIT);
        two.await(MessageType.ROUND_LEADERBOARD, WAIT);
      }
      if (played < roundsNeeded) {
        one.await(MessageType.MATCH_RESULT, WAIT);
        two.await(MessageType.MATCH_RESULT, WAIT);
        for (var c : List.of(one, two))
          c.send(command(MessageType.REMATCH_REQUEST, match, null, new Payloads.RematchRequest()));
      }
    }
  }

  private void write() throws IOException {
    Path parent = csv.toAbsolutePath().getParent();
    if (parent != null) Files.createDirectories(parent);
    List<Row> snapshot;
    synchronized (rows) {
      snapshot = List.copyOf(rows);
    }
    try (BufferedWriter writer = Files.newBufferedWriter(csv)) {
      writer.write("event,type,latencyMs,connections,matches,rttMs\n");
      for (var r : snapshot)
        writer.write(
            String.format(
                Locale.ROOT,
                "%d,%s,%.6f,%d,%d,%s%n",
                r.event,
                r.type,
                r.latencyMs,
                r.connections,
                r.matches,
                Double.isNaN(r.rttMs) ? "" : String.format(Locale.ROOT, "%.6f", r.rttMs)));
    }
    for (String type : List.of("LOGIN_RESULT", "ONLINE_LIST", "ANSWER_ACK", "CHAT_MESSAGE")) {
      double[] values =
          snapshot.stream()
              .filter(r -> r.type.equals(type))
              .mapToDouble(Row::latencyMs)
              .sorted()
              .toArray();
      System.out.printf(
          Locale.ROOT,
          "%s samples=%d p95Ms=%s%n",
          type,
          values.length,
          values.length == 0
              ? "N/A"
              : String.format(
                  Locale.ROOT, "%.3f", values[(int) Math.ceil(values.length * .95) - 1]));
    }
    System.out.println(
        "CSV: "
            + csv.toAbsolutePath()
            + "; timer dispatch is measured separately by server telemetry.");
  }
}

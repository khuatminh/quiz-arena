package vn.edu.nhom7.quiz.server.match;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.edu.nhom7.quiz.common.protocol.Envelope;
import vn.edu.nhom7.quiz.server.quiz.QuizRepository;
import vn.edu.nhom7.quiz.server.session.*;

/** Loads fresh questions outside aggregate/registry locks and guards stale completions. */
public final class RematchCoordinator {
  private final MatchManager manager;
  private final SessionRegistry registry;
  private final QuizRepository repository;
  private final Executor executor;

  public RematchCoordinator(
      MatchManager manager,
      SessionRegistry registry,
      QuizRepository repository,
      Executor executor) {
    this.manager = manager;
    this.registry = registry;
    this.repository = repository;
    this.executor = executor;
  }

  void commandLocked(Match m, SessionContext caller, Envelope command) {
    if (m.phase != MatchPhase.RESULT
        || m.attached.size() != 2
        || m.sessions.stream().anyMatch(s -> !manager.alive.test(s.connectionId())))
      throw new vn.edu.nhom7.quiz.common.protocol.ProtocolException(
          "INVALID_STATE", "INVALID_STATE");
    if (!manager.sink.canCreateMatch())
      throw new vn.edu.nhom7.quiz.common.protocol.ProtocolException("SERVER_BUSY", "SERVER_BUSY");
    if (command.type().name().equals("REMATCH_RESPONSE")
        && !command.payload().path("accept").asBoolean()) {
      if (m.rematchRequester == null || m.rematchRequester == caller.userId())
        throw new vn.edu.nhom7.quiz.common.protocol.ProtocolException(
            "INVALID_STATE", "INVALID_STATE");
      status(m, "REJECTED", null);
      m.rematchRequester = null;
      m.rematchGeneration++;
      return;
    }
    if (m.rematchRequester == null && command.type().name().equals("REMATCH_RESPONSE"))
      throw new vn.edu.nhom7.quiz.common.protocol.ProtocolException(
          "INVALID_STATE", "No pending rematch");
    if (m.rematchRequester == null) {
      m.rematchRequester = caller.userId();
      m.rematchExpiry = Math.min(m.resultDeadline, manager.clock.nanoTime() + 20_000_000_000L);
      long generation = ++m.rematchGeneration;
      status(m, "PENDING", null);
      manager.scheduler.schedule(
          Duration.ofNanos(Math.max(0, m.rematchExpiry - manager.clock.nanoTime())),
          () ->
              manager.locked(
                  m,
                  () -> {
                    if (generation == m.rematchGeneration
                        && m.rematchRequester != null
                        && !m.rematchLoading) {
                      status(m, "EXPIRED", null);
                      m.rematchRequester = null;
                      m.rematchGeneration++;
                    }
                  }));
      return;
    }
    if (m.rematchRequester == caller.userId() || m.rematchLoading) {
      status(m, "PENDING", null);
      return;
    }
    if (manager.clock.nanoTime() >= m.rematchExpiry)
      throw new vn.edu.nhom7.quiz.common.protocol.ProtocolException(
          "INVALID_STATE", "INVALID_STATE");
    m.rematchLoading = true;
    long generation = ++m.rematchGeneration;
    manager.after(() -> load(m, generation));
  }

  private void load(Match old, long generation) {
    var assignment = registry.assignment(old.id);
    if (assignment.isEmpty()) {
      fail(old, generation);
      return;
    }
    AtomicBoolean completed = new AtomicBoolean();
    var timeout =
        manager.scheduler.schedule(
            Duration.ofSeconds(5),
            () -> {
              if (completed.compareAndSet(false, true)) fail(old, generation);
            });
    executor.execute(
        () -> {
          try {
            var questions =
                repository.loadMatchQuestions(
                    old.quiz.quizId(), java.util.random.RandomGenerator.getDefault());
            if (!completed.compareAndSet(false, true)) return;
            timeout.cancel();
            UUID nextId = UUID.randomUUID();
            Match next = manager.prepare(nextId, old.quiz, old.sessions, questions);
            if (!manager.sink.reserveMatch(nextId)) {
              fail(old, generation);
              return;
            }
            manager.registerPrepared(next);
            boolean[] valid = {false};
            manager.locked(
                old,
                () -> {
                  valid[0] =
                      old.phase == MatchPhase.RESULT
                          && old.rematchGeneration == generation
                          && old.attached.size() == 2
                          && manager.clock.nanoTime() < old.resultDeadline
                          && old.sessions.stream()
                              .allMatch(s -> manager.alive.test(s.connectionId()));
                  if (valid[0]) {
                    old.timer.cancel();
                    old.rematchGeneration++;
                    old.rematchLoading = false;
                    old.phase = MatchPhase.CLOSED;
                    old.version++;
                  }
                });
            if (!valid[0]) {
              manager.discardPrepared(next);
              manager.sink.cancelReservation(nextId);
              return;
            }
            if (!registry.transferPair(old.id, assignment.get().generation(), nextId)) {
              manager.discardPrepared(next);
              manager.sink.cancelReservation(nextId);
              manager.locked(old, () -> manager.closeResult(old, "EXPIRED"));
              return;
            }
            manager.locked(
                old,
                () -> {
                  status(old, "ACCEPTED", nextId);
                  for (var s : old.sessions)
                    manager.emit(
                        old,
                        "RESULT_SESSION_CLOSED",
                        null,
                        s.connectionId(),
                        MatchManager.obj("reason", "REMATCH"));
                  old.attached.clear();
                  manager.after(() -> manager.matches.remove(old.id, old));
                });
            manager.activate(next);
          } catch (RuntimeException e) {
            if (completed.compareAndSet(false, true)) timeout.cancel();
            fail(old, generation);
          }
        });
  }

  private void fail(Match m, long generation) {
    manager.locked(
        m,
        () -> {
          if (m.rematchGeneration == generation && m.phase == MatchPhase.RESULT) {
            status(m, "INVALIDATED", null);
            m.rematchRequester = null;
            m.rematchLoading = false;
            m.rematchGeneration++;
          }
        });
  }

  private void status(Match m, String status, UUID next) {
    manager.emit(
        m,
        "REMATCH_STATUS",
        null,
        null,
        MatchManager.obj(
            "status",
            status,
            "requesterUserId",
            m.rematchRequester,
            "expiresAtMs",
            status.equals("PENDING")
                ? manager.now()
                    + Math.max(0, (m.rematchExpiry - manager.clock.nanoTime()) / 1_000_000L)
                : null,
            "newMatchId",
            next));
  }
}

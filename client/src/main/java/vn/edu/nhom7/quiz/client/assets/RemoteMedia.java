package vn.edu.nhom7.quiz.client.assets;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import vn.edu.nhom7.quiz.client.ui.UiCommandSink;
import vn.edu.nhom7.quiz.common.protocol.*;

/** Immutable media cache and bounded stop-and-wait TCP transfers. Never reads files on FX. */
public final class RemoteMedia {
  private static final int LIMIT = 5 * 1024 * 1024, CHUNK = 24576;
  private static final ScheduledExecutorService IO =
      Executors.newScheduledThreadPool(
          2,
          r -> {
            Thread t = new Thread(r, "quiz-media");
            t.setDaemon(true);
            return t;
          });
  private static UiCommandSink sink;
  private static final long CACHE_LIMIT = 64L * 1024 * 1024;
  private static final Map<String, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true);
  private static long cachedBytes;
  private static final Map<String, Download> downloads = new HashMap<>();
  private static final Map<UUID, Upload> uploads = new HashMap<>();
  private static final Map<UUID, Object> requests = new HashMap<>();
  private static final Set<Upload> pendingUploads = new HashSet<>();

  private RemoteMedia() {}

  public static synchronized void configure(UiCommandSink commands) {
    sink = commands;
  }

  public static synchronized void clear() {
    var error = new IllegalStateException("Phiên truyền ảnh đã kết thúc");
    new ArrayList<>(downloads.values()).forEach(d -> d.future.completeExceptionally(error));
    new ArrayList<>(pendingUploads).forEach(u -> u.future.completeExceptionally(error));
    new ArrayList<>(uploads.values()).forEach(u -> u.future.completeExceptionally(error));
    downloads.clear();
    uploads.clear();
    pendingUploads.clear();
    requests.clear();
    cache.clear();
    cachedBytes = 0;
  }

  public static synchronized Optional<byte[]> cached(String id) {
    byte[] value = cache.get(id);
    return value == null ? Optional.empty() : Optional.of(value.clone());
  }

  public static synchronized CompletableFuture<byte[]> fetch(String id) {
    if (cache.containsKey(id)) return CompletableFuture.completedFuture(cache.get(id).clone());
    if (downloads.containsKey(id)) return downloads.get(id).future;
    Download d = new Download(id);
    downloads.put(id, d);
    d.future.whenComplete(
        (v, e) -> {
          synchronized (RemoteMedia.class) {
            downloads.remove(id, d);
            requests.values().removeIf(x -> x == d);
          }
        });
    timeout(d.future);
    send(MessageType.MEDIA_REQUEST, new MediaPayloads.MediaRequest(id, 0), d);
    return d.future;
  }

  public static CompletableFuture<String> upload(Path path) {
    Upload u = new Upload();
    synchronized (RemoteMedia.class) {
      pendingUploads.add(u);
    }
    u.future.whenComplete(
        (v, e) -> {
          synchronized (RemoteMedia.class) {
            pendingUploads.remove(u);
            uploads.values().removeIf(x -> x == u);
            requests.values().removeIf(x -> x == u);
          }
        });
    timeout(u.future);
    IO.execute(
        () -> {
          try {
            long size = Files.size(path);
            if (size < 1 || size > LIMIT)
              throw new IllegalArgumentException("Ảnh phải nhỏ hơn hoặc bằng 5 MiB");
            u.bytes = Files.readAllBytes(path);
            if (u.bytes.length > LIMIT) throw new IllegalArgumentException("Ảnh quá lớn");
            String mime = png(u.bytes) ? "image/png" : jpeg(u.bytes) ? "image/jpeg" : null;
            if (mime == null) throw new IllegalArgumentException("Chỉ nhận nội dung PNG hoặc JPEG");
            synchronized (RemoteMedia.class) {
              if (!u.future.isDone())
                send(
                    MessageType.MEDIA_UPLOAD_START,
                    new MediaPayloads.UploadStart(mime, u.bytes.length, hash(u.bytes)),
                    u);
            }
          } catch (Exception e) {
            u.future.completeExceptionally(e);
          }
        });
    return u.future;
  }

  public static synchronized void onEvent(Envelope event) {
    Object transfer = event.requestId() == null ? null : requests.remove(event.requestId());
    try {
      var p = event.payload();
      if (event.type() == MessageType.ERROR && transfer != null) {
        fail(transfer, new IllegalStateException(p.path("message").asText("Không thể truyền ảnh")));
        return;
      }
      if (event.type() == MessageType.MEDIA_UPLOAD_STARTED && transfer instanceof Upload u) {
        u.id = UUID.fromString(p.path("transferId").asText());
        uploads.put(u.id, u);
        uploadChunk(u, 0);
      } else if (event.type() == MessageType.MEDIA_UPLOAD_ACK) {
        if (!(transfer instanceof Upload u)
            || uploads.get(UUID.fromString(p.path("transferId").asText())) != u) return;
        int offset = p.path("nextOffset").asInt(-1);
        if (offset != u.expected) throw new IllegalStateException("Sai thứ tự khối ảnh");
        if (p.hasNonNull("mediaId")) {
          if (offset != u.bytes.length) throw new IllegalStateException("Ảnh chưa hoàn tất");
          String id = p.path("mediaId").asText();
          cachePut(id, u.bytes);
          u.future.complete(id);
        } else uploadChunk(u, offset);
      } else if (event.type() == MessageType.MEDIA_CHUNK) {
        if (!(transfer instanceof Download d) || downloads.get(d.id) != d) return;
        if (!d.id.equals(p.path("mediaId").asText()))
          throw new IllegalStateException("Khối ảnh không khớp yêu cầu");
        int size = p.path("totalSize").asInt(-1), offset = p.path("offset").asInt(-1);
        if (size < 1 || size > LIMIT || offset != d.offset)
          throw new IllegalStateException("Khối ảnh không hợp lệ");
        if (d.bytes == null) {
          d.bytes = new byte[size];
          d.hash = p.path("sha256").asText();
        }
        if (size != d.bytes.length || !d.hash.equals(p.path("sha256").asText()))
          throw new IllegalStateException("Ảnh thay đổi trong khi tải");
        byte[] chunk = Base64.getDecoder().decode(p.path("data").asText());
        if (chunk.length < 1 || chunk.length > CHUNK || offset + chunk.length > size)
          throw new IllegalStateException("Kích thước khối ảnh không hợp lệ");
        System.arraycopy(chunk, 0, d.bytes, offset, chunk.length);
        d.offset += chunk.length;
        if (d.offset == size) {
          if (!hash(d.bytes).equals(d.hash))
            throw new IllegalStateException("Kiểm tra toàn vẹn ảnh thất bại");
          cachePut(d.id, d.bytes);
          d.future.complete(d.bytes.clone());
        } else send(MessageType.MEDIA_REQUEST, new MediaPayloads.MediaRequest(d.id, d.offset), d);
      }
    } catch (Exception e) {
      if (transfer != null) fail(transfer, e);
    }
  }

  /** Called with the class monitor held. Eviction never cancels an active transfer. */
  private static void cachePut(String id, byte[] bytes) {
    byte[] previous = cache.put(id, bytes);
    cachedBytes += bytes.length - (previous == null ? 0 : previous.length);
    var entries = cache.entrySet().iterator();
    while (cachedBytes > CACHE_LIMIT && entries.hasNext()) {
      cachedBytes -= entries.next().getValue().length;
      entries.remove();
    }
  }

  private static void uploadChunk(Upload u, int offset) {
    if (offset < 0 || offset >= u.bytes.length) {
      u.future.completeExceptionally(
          new IllegalStateException("Server chưa xác nhận ảnh hoàn tất"));
      return;
    }
    int end = Math.min(offset + CHUNK, u.bytes.length);
    u.expected = end;
    send(
        MessageType.MEDIA_UPLOAD_CHUNK,
        new MediaPayloads.UploadChunk(
            u.id,
            offset,
            Base64.getEncoder().encodeToString(Arrays.copyOfRange(u.bytes, offset, end))),
        u);
  }

  private static void send(MessageType type, Object payload, Object transfer) {
    try {
      if (sink == null) throw new IllegalStateException("Chưa kết nối media");
      UUID id = sink.send(type, null, null, payload);
      if (id == null) throw new IllegalStateException("Không thể gửi ảnh");
      requests.put(id, transfer);
    } catch (Exception e) {
      fail(transfer, e);
    }
  }

  private static void fail(Object transfer, Throwable e) {
    if (transfer instanceof Download d) d.future.completeExceptionally(e);
    if (transfer instanceof Upload u) u.future.completeExceptionally(e);
  }

  private static void timeout(CompletableFuture<?> future) {
    IO.schedule(
        () ->
            future.completeExceptionally(
                new TimeoutException("Tải ảnh hết thời gian chờ; hãy thử lại")),
        30,
        TimeUnit.SECONDS);
  }

  private static boolean png(byte[] b) {
    return b.length >= 8
        && b[0] == (byte) 137
        && b[1] == 80
        && b[2] == 78
        && b[3] == 71
        && b[4] == 13
        && b[5] == 10
        && b[6] == 26
        && b[7] == 10;
  }

  private static boolean jpeg(byte[] b) {
    return b.length >= 3 && b[0] == (byte) 255 && b[1] == (byte) 216 && b[2] == (byte) 255;
  }

  private static String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static final class Download {
    final String id;
    final CompletableFuture<byte[]> future = new CompletableFuture<>();
    byte[] bytes;
    int offset;
    String hash;

    Download(String id) {
      this.id = id;
    }
  }

  private static final class Upload {
    final CompletableFuture<String> future = new CompletableFuture<>();
    UUID id;
    byte[] bytes;
    int expected;
  }
}

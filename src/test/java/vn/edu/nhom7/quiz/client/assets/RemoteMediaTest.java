package vn.edu.nhom7.quiz.client.assets;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import vn.edu.nhom7.quiz.common.protocol.*;

class RemoteMediaTest {
  record Sent(MessageType type, UUID id, Object payload) {}

  final BlockingQueue<Sent> sent = new LinkedBlockingQueue<>();
  final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void setup() {
    RemoteMedia.clear();
    RemoteMedia.configure(
        (type, m, r, p) -> {
          UUID id = UUID.randomUUID();
          sent.add(new Sent(type, id, p));
          return id;
        });
  }

  @AfterEach
  void cleanup() {
    RemoteMedia.clear();
  }

  void event(MessageType type, UUID id, Object payload) {
    RemoteMedia.onEvent(new Envelope(2, type, id, null, null, null, json.valueToTree(payload)));
  }

  String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  @Test
  void downloadVerifiesHashAndCachesWithoutNetwork() throws Exception {
    byte[] bytes = {1, 2, 3};
    var result = RemoteMedia.fetch("media-test");
    Sent request = sent.take();
    event(
        MessageType.MEDIA_CHUNK,
        request.id(),
        new MediaPayloads.MediaChunk(
            "media-test", 0, Base64.getEncoder().encodeToString(bytes), 3, hash(bytes)));
    assertArrayEquals(bytes, result.get(1, TimeUnit.SECONDS));
    byte[] cached = RemoteMedia.cached("media-test").orElseThrow();
    cached[0] = 9;
    assertArrayEquals(bytes, RemoteMedia.fetch("media-test").get());
    assertTrue(sent.isEmpty());
  }

  @Test
  void rejectsTamperedDownloadAndAllowsRetry() throws Exception {
    var result = RemoteMedia.fetch("media-test");
    Sent request = sent.take();
    event(
        MessageType.MEDIA_CHUNK,
        request.id(),
        new MediaPayloads.MediaChunk("media-test", 0, "AQID", 3, "0".repeat(64)));
    assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS));
    assertTrue(RemoteMedia.cached("media-test").isEmpty());
    assertFalse(RemoteMedia.fetch("media-test").isDone());
    assertEquals(1, sent.size());
  }

  @Test
  void sessionClearFailsAllActiveDownloads() {
    var a = RemoteMedia.fetch("a");
    var b = RemoteMedia.fetch("b");
    assertDoesNotThrow(RemoteMedia::clear);
    assertTrue(a.isCompletedExceptionally());
    assertTrue(b.isCompletedExceptionally());
  }

  @Test
  void lateChunkFromExpiredRequestCannotAffectRetry() throws Exception {
    var expired = RemoteMedia.fetch("media-retry");
    Sent oldRequest = sent.take();
    expired.completeExceptionally(new TimeoutException("Expired"));
    var retry = RemoteMedia.fetch("media-retry");
    Sent freshRequest = sent.take();
    event(
        MessageType.MEDIA_CHUNK,
        oldRequest.id(),
        new MediaPayloads.MediaChunk("media-retry", 1, "AQID", 3, "0".repeat(64)));
    event(
        MessageType.MEDIA_CHUNK,
        UUID.randomUUID(),
        new MediaPayloads.MediaChunk("media-retry", 0, "AQID", 3, "0".repeat(64)));
    assertFalse(retry.isDone());
    byte[] bytes = {1, 2, 3};
    event(
        MessageType.MEDIA_CHUNK,
        freshRequest.id(),
        new MediaPayloads.MediaChunk("media-retry", 0, "AQID", 3, hash(bytes)));
    assertArrayEquals(bytes, retry.get(1, TimeUnit.SECONDS));
  }

  @Test
  void cacheEvictsLeastRecentlyUsedWithoutInvalidatingFetchFutures() throws Exception {
    byte[] bytes = new byte[4 * 1024 * 1024];
    Arrays.fill(bytes, (byte) 7);
    var oldest = download("media-cache-0", bytes);
    var active = RemoteMedia.fetch("media-active");
    Sent activeRequest = sent.take();
    for (int i = 1; i <= 16; i++) download("media-cache-" + i, bytes);
    assertTrue(RemoteMedia.cached("media-cache-0").isEmpty());
    assertArrayEquals(bytes, oldest.get());
    assertFalse(active.isDone());
    assertTrue(RemoteMedia.cached("media-cache-1").isPresent()); // Refresh LRU order.
    download("media-cache-17", bytes);
    assertTrue(RemoteMedia.cached("media-cache-2").isEmpty());
    assertTrue(RemoteMedia.cached("media-cache-1").isPresent());
    byte[] last = {1, 2, 3};
    event(
        MessageType.MEDIA_CHUNK,
        activeRequest.id(),
        new MediaPayloads.MediaChunk("media-active", 0, "AQID", 3, hash(last)));
    assertArrayEquals(last, active.get(1, TimeUnit.SECONDS));
  }

  private CompletableFuture<byte[]> download(String id, byte[] bytes) throws Exception {
    var result = RemoteMedia.fetch(id);
    String digest = hash(bytes);
    for (int offset = 0; offset < bytes.length; offset += MediaPayloads.CHUNK_BYTES) {
      Sent request = sent.poll(2, TimeUnit.SECONDS);
      assertNotNull(request);
      int end = Math.min(bytes.length, offset + MediaPayloads.CHUNK_BYTES);
      event(
          MessageType.MEDIA_CHUNK,
          request.id(),
          new MediaPayloads.MediaChunk(
              id,
              offset,
              Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes, offset, end)),
              bytes.length,
              digest));
    }
    assertTrue(result.isDone());
    assertFalse(result.isCompletedExceptionally());
    return result;
  }

  @Test
  void uploadUsesBoundedStopAndWaitChunks() throws Exception {
    byte[] bytes = new byte[30000];
    new Random(4).nextBytes(bytes);
    byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    System.arraycopy(signature, 0, bytes, 0, 8);
    var path = Files.createTempFile("quiz-test-", ".png");
    try {
      Files.write(path, bytes);
      var result = RemoteMedia.upload(path);
      Sent start = sent.poll(2, TimeUnit.SECONDS);
      assertNotNull(start);
      assertEquals(MessageType.MEDIA_UPLOAD_START, start.type());
      UUID transfer = UUID.randomUUID();
      event(
          MessageType.MEDIA_UPLOAD_STARTED, start.id(), new MediaPayloads.UploadStarted(transfer));
      Sent first = sent.take();
      var chunk = (MediaPayloads.UploadChunk) first.payload();
      assertEquals(24576, Base64.getDecoder().decode(chunk.data()).length);
      assertTrue(sent.isEmpty());
      event(
          MessageType.MEDIA_UPLOAD_ACK,
          first.id(),
          new MediaPayloads.UploadAck(transfer, 24576, null));
      Sent last = sent.take();
      assertEquals(24576, ((MediaPayloads.UploadChunk) last.payload()).offset());
      event(
          MessageType.MEDIA_UPLOAD_ACK,
          last.id(),
          new MediaPayloads.UploadAck(transfer, bytes.length, "media-created"));
      assertEquals("media-created", result.get(1, TimeUnit.SECONDS));
    } finally {
      Files.deleteIfExists(path);
    }
  }
}

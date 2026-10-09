package vn.edu.nhom7.quiz.server.media;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.nhom7.quiz.common.protocol.*;

class MediaServiceTest {
  @TempDir Path directory;
  Map<String, MediaService.Metadata> metadata;
  MediaService service;

  @BeforeEach
  void setup() {
    metadata = new HashMap<>();
    service =
        new MediaService(
            directory,
            new MediaService.MetadataStore() {
              public void save(MediaService.Metadata m) {
                metadata.put(m.id(), m);
              }

              public Optional<MediaService.Metadata> find(String id) {
                return Optional.ofNullable(metadata.get(id));
              }
            },
            (user, id) -> user == 3);
  }

  byte[] png() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  @Test
  void storesVerifiedImageAndGatesEachDownload() throws Exception {
    byte[] bytes = png();
    var start =
        service.start(1, new MediaPayloads.UploadStart("image/png", bytes.length, hash(bytes)));
    var ack =
        service.chunk(
            1,
            new MediaPayloads.UploadChunk(
                start.transferId(), 0, Base64.getEncoder().encodeToString(bytes)));
    assertNotNull(ack.mediaId());
    var owner = service.read(1, new MediaPayloads.MediaRequest(ack.mediaId(), 0));
    assertArrayEquals(bytes, Base64.getDecoder().decode(owner.data()));
    assertThrows(
        ProtocolException.class,
        () -> service.read(2, new MediaPayloads.MediaRequest(ack.mediaId(), 0)));
    assertEquals(owner, service.read(3, new MediaPayloads.MediaRequest(ack.mediaId(), 0)));
  }

  @Test
  void rejectsWrongOwnerOffsetDigestAndFakeImage() throws Exception {
    byte[] bytes = png();
    var start =
        service.start(1, new MediaPayloads.UploadStart("image/png", bytes.length, hash(bytes)));
    assertThrows(
        ProtocolException.class,
        () -> service.chunk(2, new MediaPayloads.UploadChunk(start.transferId(), 0, "AA==")));
    assertThrows(
        ProtocolException.class,
        () -> service.chunk(1, new MediaPayloads.UploadChunk(start.transferId(), 1, "AA==")));
    var bad =
        service.start(2, new MediaPayloads.UploadStart("image/png", bytes.length, "0".repeat(64)));
    assertThrows(
        ProtocolException.class,
        () ->
            service.chunk(
                2,
                new MediaPayloads.UploadChunk(
                    bad.transferId(), 0, Base64.getEncoder().encodeToString(bytes))));
    byte[] fake = "not an image".getBytes();
    var fakeStart =
        service.start(2, new MediaPayloads.UploadStart("image/png", fake.length, hash(fake)));
    assertThrows(
        ProtocolException.class,
        () ->
            service.chunk(
                2,
                new MediaPayloads.UploadChunk(
                    fakeStart.transferId(), 0, Base64.getEncoder().encodeToString(fake))));
    assertTrue(metadata.isEmpty());
  }

  @Test
  void limitsUploadSizeTypeAndSessions() {
    assertThrows(
        ProtocolException.class,
        () ->
            service.start(
                1,
                new MediaPayloads.UploadStart("image/png", 5 * 1024 * 1024 + 1, "0".repeat(64))));
    assertThrows(
        ProtocolException.class,
        () -> service.start(1, new MediaPayloads.UploadStart("image/svg+xml", 10, "0".repeat(64))));
    service.start(1, new MediaPayloads.UploadStart("image/png", 10, "0".repeat(64)));
    service.start(1, new MediaPayloads.UploadStart("image/png", 10, "0".repeat(64)));
    assertThrows(
        ProtocolException.class,
        () -> service.start(1, new MediaPayloads.UploadStart("image/png", 10, "0".repeat(64))));
  }

  @Test
  void rejectsMimeMismatchAndOversizedPixels() throws Exception {
    byte[] bytes = png();
    var start =
        service.start(1, new MediaPayloads.UploadStart("image/jpeg", bytes.length, hash(bytes)));
    assertThrows(
        ProtocolException.class,
        () ->
            service.chunk(
                1,
                new MediaPayloads.UploadChunk(
                    start.transferId(), 0, Base64.getEncoder().encodeToString(bytes))));
    byte[] giant = png();
    // PNG IHDR dimensions: reject before allocating image pixel memory.
    java.nio.ByteBuffer.wrap(giant).putInt(16, 100000).putInt(20, 100000);
    var large =
        service.start(1, new MediaPayloads.UploadStart("image/png", giant.length, hash(giant)));
    assertThrows(
        ProtocolException.class,
        () ->
            service.chunk(
                1,
                new MediaPayloads.UploadChunk(
                    large.transferId(), 0, Base64.getEncoder().encodeToString(giant))));
  }
}

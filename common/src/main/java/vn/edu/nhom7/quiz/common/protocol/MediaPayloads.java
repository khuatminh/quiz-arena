package vn.edu.nhom7.quiz.common.protocol;

import java.util.UUID;

/** Bounded stop-and-wait transfers; no media bytes in question or history records. */
public final class MediaPayloads {
  private MediaPayloads() {}

  public static final int CHUNK_BYTES = 24 * 1024;
  public static final int MAX_BYTES = 5 * 1024 * 1024;

  public record UploadStart(String mimeType, int byteSize, String sha256) {}

  public record UploadStarted(UUID transferId) {}

  public record UploadChunk(UUID transferId, int offset, String data) {}

  public record UploadAck(UUID transferId, int nextOffset, String mediaId) {}

  public record MediaRequest(String mediaId, int offset) {}

  public record MediaChunk(String mediaId, int offset, String data, int totalSize, String sha256) {}
}

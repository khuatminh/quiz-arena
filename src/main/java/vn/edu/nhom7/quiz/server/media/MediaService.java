package vn.edu.nhom7.quiz.server.media;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import javax.imageio.ImageIO;
import vn.edu.nhom7.quiz.common.protocol.*;

/** Validated disk media with bounded, expiring upload sessions and per-request access checks. */
public final class MediaService implements AutoCloseable {
  public record Metadata(String id, long ownerId, String mimeType, int byteSize, String sha256) {}

  public interface MetadataStore {
    void save(Metadata metadata);

    Optional<Metadata> find(String id);
  }

  private static final long IDLE_NANOS = 120_000_000_000L;
  private final Path root;
  private final MetadataStore metadata;
  private final BiPredicate<Long, String> allowed;
  private final Map<UUID, Upload> uploads = new ConcurrentHashMap<>();

  private static final class Upload {
    final long owner;
    final MediaPayloads.UploadStart spec;
    final Path path;
    int offset;
    volatile long touched = System.nanoTime();

    Upload(long owner, MediaPayloads.UploadStart spec, Path path) {
      this.owner = owner;
      this.spec = spec;
      this.path = path;
    }
  }

  public MediaService(Path root, MetadataStore metadata, BiPredicate<Long, String> allowed) {
    this.root = root.toAbsolutePath().normalize();
    this.metadata = metadata;
    this.allowed = allowed;
    try {
      Files.createDirectories(this.root);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot initialize media storage", e);
    }
  }

  public synchronized MediaPayloads.UploadStarted start(long user, MediaPayloads.UploadStart spec) {
    cleanup();
    if (user <= 0
        || !Set.of("image/png", "image/jpeg").contains(spec.mimeType())
        || spec.byteSize() < 1
        || spec.byteSize() > MediaPayloads.MAX_BYTES
        || spec.sha256() == null
        || !spec.sha256().matches("[a-f0-9]{64}"))
      throw invalid("Ảnh phải là PNG/JPEG, tối đa 5 MB và có SHA-256 hợp lệ.");
    if (uploads.size() >= 32 || uploads.values().stream().filter(u -> u.owner == user).count() >= 2)
      throw new ProtocolException(
          "MEDIA_BUSY", "Đang có quá nhiều ảnh tải lên. Hãy đợi hoặc thử lại.");
    UUID id = UUID.randomUUID();
    try {
      Path path = root.resolve(id + ".part");
      Files.createFile(path);
      uploads.put(id, new Upload(user, spec, path));
      return new MediaPayloads.UploadStarted(id);
    } catch (IOException e) {
      throw unavailable();
    }
  }

  public MediaPayloads.UploadAck chunk(long user, MediaPayloads.UploadChunk part) {
    Upload u = uploads.get(part.transferId());
    if (u == null || u.owner != user) throw denied();
    synchronized (u) {
      if (uploads.get(part.transferId()) != u || System.nanoTime() - u.touched > IDLE_NANOS) {
        discard(part.transferId(), u);
        throw invalid("Phiên tải ảnh đã hết hạn.");
      }
      if (part.offset() != u.offset || part.data() == null || part.data().length() > 32768)
        throw invalid("Khối ảnh sai thứ tự hoặc quá lớn.");
      byte[] bytes;
      try {
        bytes = Base64.getDecoder().decode(part.data());
      } catch (IllegalArgumentException e) {
        throw invalid("Khối ảnh không hợp lệ.");
      }
      if (bytes.length < 1
          || bytes.length > MediaPayloads.CHUNK_BYTES
          || u.offset + bytes.length > u.spec.byteSize())
        throw invalid("Kích thước khối ảnh không hợp lệ.");
      try {
        Files.write(u.path, bytes, StandardOpenOption.APPEND);
        u.offset += bytes.length;
        u.touched = System.nanoTime();
        if (u.offset < u.spec.byteSize())
          return new MediaPayloads.UploadAck(part.transferId(), u.offset, null);
        String digest = sha256(Files.readAllBytes(u.path));
        if (!digest.equals(u.spec.sha256())) throw invalid("Ảnh tải lên không khớp dữ liệu gốc.");
        validateImage(u.path, u.spec.mimeType());
        String id = "media-" + UUID.randomUUID();
        Path target = root.resolve(id);
        Files.move(u.path, target, StandardCopyOption.ATOMIC_MOVE);
        try {
          metadata.save(new Metadata(id, user, u.spec.mimeType(), u.offset, digest));
        } catch (RuntimeException e) {
          Files.deleteIfExists(target);
          throw e;
        }
        uploads.remove(part.transferId(), u);
        return new MediaPayloads.UploadAck(part.transferId(), u.offset, id);
      } catch (ProtocolException e) {
        discard(part.transferId(), u);
        throw e;
      } catch (IOException e) {
        discard(part.transferId(), u);
        throw unavailable();
      } catch (RuntimeException e) {
        discard(part.transferId(), u);
        throw e;
      }
    }
  }

  public MediaPayloads.MediaChunk read(long user, MediaPayloads.MediaRequest request) {
    String id = request.mediaId();
    if (id == null || !id.matches("media-[0-9a-f-]{36}")) throw denied();
    var m = metadata.find(id).orElseThrow(MediaService::denied);
    if (m.ownerId() != user && !allowed.test(user, id)) throw denied();
    if (request.offset() < 0 || request.offset() >= m.byteSize())
      throw invalid("Vị trí đọc ảnh không hợp lệ.");
    try (var file = new RandomAccessFile(root.resolve(id).toFile(), "r")) {
      if (file.length() != m.byteSize()) throw unavailable();
      file.seek(request.offset());
      byte[] bytes = new byte[Math.min(MediaPayloads.CHUNK_BYTES, m.byteSize() - request.offset())];
      file.readFully(bytes);
      return new MediaPayloads.MediaChunk(
          id,
          request.offset(),
          Base64.getEncoder().encodeToString(bytes),
          m.byteSize(),
          m.sha256());
    } catch (IOException e) {
      throw unavailable();
    }
  }

  private static void validateImage(Path path, String mime) throws IOException {
    try (var input = ImageIO.createImageInputStream(path.toFile())) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid("File ảnh không đọc được.");
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!(mime.equals("image/png") && format.equals("png"))
            && !(mime.equals("image/jpeg") && (format.equals("jpeg") || format.equals("jpg"))))
          throw invalid("Nội dung file không khớp định dạng ảnh.");
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || (long) width * height > 16_000_000L)
          throw invalid("Ảnh vượt giới hạn 16 triệu điểm ảnh.");
        if (reader.read(0) == null) throw invalid("File ảnh bị hỏng.");
      } catch (javax.imageio.IIOException e) {
        throw invalid("File ảnh bị hỏng.");
      } finally {
        reader.dispose();
      }
    }
  }

  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public void cleanup() {
    long now = System.nanoTime();
    uploads.forEach(
        (id, u) -> {
          synchronized (u) {
            if (now - u.touched > IDLE_NANOS) discard(id, u);
          }
        });
    // Remove partial files left by an earlier process, without touching active transfers.
    try (var paths = Files.list(root)) {
      paths
          .filter(p -> p.getFileName().toString().endsWith(".part"))
          .forEach(
              p -> {
                try {
                  if (System.currentTimeMillis() - Files.getLastModifiedTime(p).toMillis() > 120000
                      && uploads.values().stream().noneMatch(u -> u.path.equals(p)))
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
              });
    } catch (IOException ignored) {
    }
  }

  private void discard(UUID id, Upload u) {
    uploads.remove(id, u);
    try {
      Files.deleteIfExists(u.path);
    } catch (IOException ignored) {
    }
  }

  public void close() {
    uploads.forEach(
        (id, u) -> {
          synchronized (u) {
            discard(id, u);
          }
        });
  }

  private static ProtocolException invalid(String message) {
    return new ProtocolException("INVALID_MEDIA", message);
  }

  private static ProtocolException denied() {
    return new ProtocolException("FORBIDDEN", "Không có quyền truy cập ảnh.");
  }

  private static ProtocolException unavailable() {
    return new ProtocolException("MEDIA_UNAVAILABLE", "Không thể lưu hoặc đọc ảnh. Hãy thử lại.");
  }
}

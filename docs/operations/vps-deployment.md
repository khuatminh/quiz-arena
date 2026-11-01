# VPS Quiz Arena

## Cập nhật 02/11/2026 (giờ Việt Nam)

- Server local và VPS đã nâng thời gian REVEAL lên 6 giây; bảng điểm vẫn 3 giây.
- Quiz mới **Đi một vòng thế giới — 20 điều thú vị**: 20 câu, 9 ảnh,
  ID 15 trên local và ID 16 trên VPS. Đây là quiz cộng đồng đã xuất bản,
  xuất hiện trong sảnh; các quiz hệ thống hiện có được giữ nguyên.
- JAR đang chạy có SHA-256 `3828c00385f609237367f97b1e66a1fd80ea545853e0343fee51d160c962b466`.
- Release `/opt/quiz-arena/releases/curiosity-quiz-20261101T172119Z/`.
  JAR cũ, database và media được sao lưu trong thư mục cùng tên dưới
  `/opt/quiz-arena/backups/` và `data/backups/` local (Git bỏ qua).
- Desktop **3.0.2** cho Mac ARM64 và Windows x64 dùng nhạc Thinking Music
  của Kevin MacLeod, kèm ghi công CC BY 4.0. Cần mở bản desktop mới để nghe
  nhạc mới; client cũ kết nối server cập nhật vẫn nhận REVEAL 6 giây.
- Không thay schema 5 hoặc protocol/client version 3.0.

## Đợt cập nhật trước

Đã cập nhật ngày **01/11/2026** (giờ Việt Nam):

- Host client: `180.93.38.185`, TCP port `5555`.
- Server yêu cầu client `3.0`, protocol `1`, asset pack `1`.
- Database schema `5`; đã nâng lần lượt từ `3` qua migration `004` và `005`.
- Thư mục triển khai `/opt/quiz-arena`, Docker Compose project `quiz-arena`.
- Database và media hiện có được giữ nguyên. MySQL chỉ nằm trong mạng Docker; cổng client giữ nguyên cấu hình đang dùng.
- Đã chuyển toàn bộ 9 quiz cộng đồng hiện có sang SYSTEM: 6 bộ đã xuất bản và 3 bản nháp ẩn. Cả 10 bộ đang hiển thị đều chơi đủ số câu (4 bộ 20 câu, 6 bộ 14 câu), có tính hạng. System hỗ trợ 1–50 câu và mọi loại câu hỏi, không ép cơ cấu 4/2/2/2.

JAR triển khai có SHA-256:

```text
22710624e1423b9ac1e3de9761b32dbbeae406dbae1b4b249040ed7d80e86e29
```

Release staging: `/opt/quiz-arena/releases/system-quiz-20261101T164044Z/` (tên theo UTC).

Sao lưu ngay trước chuyển đổi: `/opt/quiz-arena/backups/system-quiz-20261101T164044Z/`,
bản ngoài VPS tại `data/backups/system-quiz-20261101T164044Z/`. Đã kiểm checksum database,
ảnh và gói cấu hình/JAR cũ trước khi khôi phục dữ liệu về local. Trước/sau chuyển đổi
giữ nguyên 6 tài khoản, 3 trận, 13 quiz, 164 câu hỏi, 7 phiên bản xuất bản,
12 ảnh và tổng 102 điểm người dùng. Các trận cũ giữ nguyên trạng thái tính hạng.

## Bản local đồng bộ

Đã đồng bộ snapshot VPS và ảnh về database `quiz_arena_demo` tại `127.0.0.1:13306`,
áp dụng cùng thao tác chuyển đổi; server local chạy ở `127.0.0.1:5555`. Cấu hình
đăng nhập database cũ trong `config/server.local.properties` được giữ nguyên.
Dữ liệu MySQL mới nằm trong `data/local-mysql/`, được Git bỏ qua, không dùng thư mục tạm.
Để khởi động lại sau khi tắt máy:

```sh
scripts/start-local-mysql.sh
java -Dquiz.mediaDir=data/media -jar target/quiz-arena-server.jar --config=config/server.local.properties
```

Chờ MySQL sẵn sàng trước khi chạy server. Tài khoản và mật khẩu chơi local giống snapshot VPS.
Kết quả kiểm chứng tại [system-quiz-verification.md](../testing/system-quiz-verification.md).

## Kết nối từ app

Mở app Mac hoặc Windows đã build, mở **Kết nối máy chủ**:

```text
Host: 180.93.38.185
Port: 5555
```

Nhấn **Kết nối**, rồi đăng nhập hoặc đăng ký. Địa chỉ mặc định `localhost` chỉ dùng khi server chạy cùng máy. Client 2.0 được server từ chối ngay khi HELLO để tránh lỗi dữ liệu câu hỏi mới.

## Sao lưu trước nâng cấp

Đã dừng server trước khi sao lưu database, media, compose và JAR cũ; kiểm tra checksum cả bản trên VPS và bản ngoài VPS:

- VPS: `/opt/quiz-arena/backups/20261030T175145Z/`.
- Máy phát triển: `data/backups/vps-20261030T175145Z/` (đã được Git bỏ qua).

Thư mục sao lưu có quyền 700, các file dữ liệu sao lưu có quyền 600. Không đưa các bản sao lưu hoặc cấu hình có mật khẩu vào Git. Nâng schema và khôi phục JAR cũ là hai việc khác nhau; muốn quay lại server/schema 3 phải khôi phục database từ cùng bản sao lưu, không chỉ thay JAR.

## Vận hành

```sh
ssh root@180.93.38.185
cd /opt/quiz-arena
docker compose ps
docker compose logs --tail=100 server
docker compose restart server
```

Healthcheck database hiện yêu cầu schema 5. Hai container giữ chính sách `restart: unless-stopped`. File `.env`, cấu hình database và thư mục dữ liệu hiện có không bị thay mật khẩu hoặc khởi tạo lại.

## Kiểm tra sau triển khai

Đã kiểm qua địa chỉ public: HELLO 3.0, từ chối HELLO 2.0, đăng ký/đăng nhập tiếng Việt, danh sách quiz, tải ảnh, lưu/đọc/xuất bản/gỡ xuất bản câu REORDER, RANGE, PINPOINT. Tài khoản, quiz và ảnh tạm do kiểm thử tạo đã được dọn; số tài khoản, trận và quiz có sẵn khớp số trước nâng cấp.

Gói app là client, kết nối server này qua TCP. Kết nối Internet hiện vẫn dùng cơ chế TCP sẵn có của dự án; đợt cập nhật này không thay lớp truyền tải.

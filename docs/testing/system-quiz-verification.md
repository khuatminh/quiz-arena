# Kiểm chứng System quiz có số câu theo bộ — 01/11/2026

## Hành vi

System quiz chơi đủ 1–50 câu của bộ, giữ mọi dạng câu hỏi, không cố định 10 câu
hoặc tỷ lệ 4/2/2/2. Bộ gốc xáo trộn câu; bộ có phiên bản xuất bản dùng đúng phiên bản
và tùy chọn thứ tự đã lưu. Trận System vẫn tính hạng; không tính lại lịch sử cũ.
Schema giữ phiên bản 5, client protocol giữ phiên bản 3.0. Gói desktop mới là 3.0.1.

## Kiểm thử tự động

- `mvn -q -Pmysql-it verify`: exit 0. Unit: 313 test, 0 lỗi/thất bại, 1 bỏ qua;
  integration: 37 test, 0 lỗi/thất bại/bỏ qua. Integration dùng riêng schema
  `quiz_system_test`, không dùng dữ liệu local chơi thật hoặc VPS.
- Repository chơi đủ 20 câu từ seed; chuyển bộ Community 1/14/50 câu sang System
  vẫn giữ nguyên câu hỏi và thứ tự. Chủ cũ không thể gỡ xuất bản System qua thao tác Community.
- Engine hoàn thành System 1/14/50 câu với snapshot tính hạng. System và Community
  đều hỗ trợ 3 dạng tương tác, cửa sổ 30 giây và giữ kín đáp án trước khi công bố.
- TCP integration chơi đủ 20 vòng, lưu 40 lượt trả lời, trả kết quả tính hạng,
  phân trang đủ phần xem lại và tạo tái đấu với trạng thái mới.
- Log unit có ngoại lệ bất đồng bộ JavaFX ở `AppShell.openSettings` khi đóng bảng
  cài đặt trong kiểm thử; bộ kiểm thử trả exit 0. Không sửa luồng cài đặt trong thay đổi này.

## Kiểm chứng dữ liệu thật

Đã chuyển 9 quiz Community sang System trên VPS. 3 bản nháp vẫn ẩn; 10 quiz công khai
đều SYSTEM/AVAILABLE: ID 1, 2, 3, 6 có 20 câu; ID 9–14 có 14 câu.
Không xóa bản nháp, phiên bản xuất bản, ảnh hoặc nội dung câu hỏi.

Trước/sau chuyển đổi giữ nguyên 6 tài khoản, 3 trận, 13 quiz, 164 câu hỏi,
7 phiên bản xuất bản, 12 ảnh và tổng 102 điểm người dùng.

Local được khôi phục từ snapshot VPS sang `quiz_arena_demo`, áp dụng cùng script
chuyển đổi và sao chép ảnh. Qua TCP trên cả VPS và localhost:

- HELLO 3.0 và đăng nhập bằng tài khoản hiện có thành công.
- Danh sách và chi tiết 10 bộ khớp nhau; số vòng bằng tổng câu trong chi tiết.
- 12 ảnh tải được, byte và SHA-256 khớp giữa hai server.
- Dùng repository/JAR đang chạy chuẩn bị thành công trận từ từng bộ trong cả
  hai database; không ghi thêm trận hoặc thay đổi điểm để kiểm tra.

Hai ZIP desktop 3.0.1 cho macOS ARM64 và Windows x64 đã build thành công,
kiểm tra CRC toàn bộ archive và ghi SHA-256 trong `dist/SHA256SUMS.txt`.
Không chạy Windows GUI trên macOS trong lần kiểm chứng này.

Sao lưu trước chuyển đổi: `data/backups/system-quiz-20261101T164044Z/` và cùng tên
trong `/opt/quiz-arena/backups/` trên VPS. Dữ liệu/cấu hình có thông tin đăng nhập
được lưu riêng trong đường dẫn Git bỏ qua, thư mục 700 và file 600.

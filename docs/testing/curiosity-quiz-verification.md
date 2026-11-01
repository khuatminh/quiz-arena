# Quiz tổng hợp, công bố đáp án 6 giây và nhạc — 02/11/2026

## Kết quả

- Bộ **Đi một vòng thế giới — 20 điều thú vị**: 20 câu tiếng Việt,
  12 câu một đáp án, 2 câu nhiều đáp án, 2 đúng/sai, 2 sắp xếp,
  1 chọn giá trị số và 1 trả lời ngắn. Các đáp án đúng ở câu một lựa chọn
  phân bố đều: mỗi vị trí A/B/C/D xuất hiện 3 lần.
- Hai ảnh câu hỏi (nhật thực, quốc kỳ) và 7 ảnh giải thích. Hình tự dựng bằng
  Pillow; sơ đồ thiên văn và Suez ghi rõ không theo tỷ lệ/bản đồ địa lý.
  Câu hỏi chỉ nhận ảnh câu hỏi; ảnh giải thích được gửi khi công bố đáp án.
- Nguồn tra cứu theo từng câu trong `data/generated-quizzes/curiosity-v1/sources.json`.
- Đã xuất bản, đọc lại đủ 20 câu, so sánh byte của 9 ảnh tải xuống và kiểm tra
  sảnh local `127.0.0.1:5555` (quiz 15) và VPS `180.93.38.185:5555` (quiz 16).
  Chạy lại importer xác minh cùng ID, không tạo thêm quiz.

## Thời gian và kiểm thử

- Server vào REVEAL với thời hạn 6 giây và công bố `durationMs=6000`.
  Deadline của kết quả và thời gian còn lại trong snapshot khớp nhau.
- Kiểm thử mới xác nhận tại 5999 ms vẫn đang REVEAL, giữ nguyên đáp án và
  lời giải; chỉ tại 6000 ms mới phát bảng điểm cho cả hai người chơi.
- Các kiểm thử hoàn thành trận và rời trận được cập nhật theo thời gian mới.
  File phát lại và ví dụ giao thức cũng sử dụng 6 giây.
- `mvn -q -Pmysql-it verify`: exit 0; 314 unit tests (1 bỏ qua),
  38 integration tests, 0 lỗi/thất bại. Integration dùng riêng
  `quiz_system_test`, không dùng database chơi thật hoặc VPS.
- `InterfaceEvidenceTest` chạy riêng: exit 0. Kiểm tra ảnh cài đặt ở kích thước
  1024×720 và cỡ chữ rất lớn: tên nhạc/giấy phép hiển thị, phần cài đặt có cuộn.
  Ảnh ở `target/curiosity-ui-evidence/`.
- Log kiểm thử vẫn có ngoại lệ JavaFX bất đồng bộ tại `AppShell.openSettings`
  và cảnh báo CSS đã có từ trước; các assertion đều qua. Xem ghi nhận tương tự
  trong `system-quiz-verification.md` của đợt trước.

## Nhạc và bản desktop

- Thinking Music — Kevin MacLeod (incompetech.com), ISRC USUAN1600060,
  CC BY 4.0. Bản gốc lấy từ website tác giả; chuyển sang WAV PCM 16 bit,
  mono, 22050 Hz; giữ toàn bộ 195,84 giây. MediaPlayer lặp lại khi phát.
- Ghi công trong Cài đặt, `MUSIC-LICENSE.txt` trong JAR và hướng dẫn desktop.
- Đã build `QuizArena-3.0.2-macos-arm64.zip` và `QuizArena-3.0.2-windows-x64.zip`,
  kiểm CRC toàn archive và ghi SHA-256 trong `dist/SHA256SUMS.txt`.
- Windows được đóng gói trên macOS; chưa chạy GUI Windows trong lần kiểm tra này.

## Triển khai

Server local và VPS đã khởi động bằng JAR SHA-256
`3828c00385f609237367f97b1e66a1fd80ea545853e0343fee51d160c962b466`.
HELLO 3.0 thành công sau khởi động. Đã kiểm tra không có người chơi khác
kết nối trước khi khởi động lại. Schema và định dạng giao thức giữ nguyên.
JAR trước nâng cấp, database và media được sao lưu trên VPS và local trong
`curiosity-quiz-20261101T172119Z`; dữ liệu có tài khoản được Git bỏ qua.

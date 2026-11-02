# Xem lại chi tiết lịch sử trận đấu

Kiểm chứng ngày 02/11/2026, client desktop 3.0.5.

## Hành vi

- Bấm **Xem chi tiết** chuyển ngay sang trang riêng, có tên quiz, thời gian, tỷ số và kết quả trận.
- Tự tải đủ các trang câu hỏi và ghép theo thứ tự trong cùng một màn hình cuộn. Mỗi câu mở sẵn nội dung, lựa chọn, đáp án, giải thích, ảnh và kết quả của hai người khi server cho phép công bố.
- Có nút quay lại danh sách lịch sử, giữ trang danh sách đang xem, và nút mở/thu gọn tất cả câu hỏi.
- Khi tải lỗi hoặc quá 5 giây, hiện nút thử lại. Thử lại tiếp tục từ trang bị lỗi, giữ các câu đã tải.
- Rời trang dừng tải tiếp; phản hồi trễ của một trận hoặc yêu cầu cũ không được chèn vào trận khác.
- Trận kết thúc sớm chỉ hiển thị những câu đã được lưu; câu chưa chấm không hiển thị đáp án hoặc lời giải chưa được công bố.

## Giao thức

Giữ nguyên `MATCH_DETAIL_REQUEST {historyMatchId, page}` và `MATCH_DETAIL {summary, review, page, totalItems}`. Server vẫn trả tối đa 2 câu mỗi phản hồi; client gửi tuần tự, cách nhau ít nhất 200 ms, để tránh giới hạn 10 truy vấn/giây. Không thay đổi schema hoặc quyền đọc lịch sử/ảnh.

## Kiểm chứng

Chạy bằng JDK 21:

```sh
mvn test -Dquiz.uiEvidence=target/history-detail-ui
```

Kết quả: **319 tests, 0 failures, 0 errors, 0 skipped**. Năm bài trong `HistoryNavigationTest` kiểm tra chuyển trang qua nút thực, quay lại đúng trang danh sách, phản hồi trễ, tải đủ 50 câu và câu trả lời hai người, timeout/thử lại, tiếp tục sau lỗi giữa chừng, trận trống và câu chưa chấm.

Ảnh JavaFX trong `target/history-detail-ui/` kiểm tra giao diện sáng/tối và cửa sổ 1280 × 800, 1024 × 720. Trang chi tiết nằm trong ảnh `19-history-review.png` của từng biến thể.

Kiểm tra đọc trực tiếp VPS `180.93.38.185:5555` bằng tài khoản kiểm thử riêng: lấy lịch sử, tải đủ một trận **14 câu qua 7 trang**, gồm cả bảy loại câu hỏi. Đây là kiểm chứng dữ liệu/giao thức thật; các ca nhấn nút và giao diện chạy cục bộ với phản hồi kiểm thử. Không ghi thông tin đăng nhập hoặc dữ liệu trận thật vào Git.

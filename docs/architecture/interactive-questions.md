# Ba dạng câu hỏi tương tác

Quiz cộng đồng hỗ trợ thêm `REORDER`, `RANGE`, `PINPOINT`. Quiz hệ thống giữ nguyên cơ cấu 4/2/2/2 để kết quả tính hạng cũ và mới nhất quán. Các dạng mới đi qua cùng luồng lưu nháp, xuất bản phiên bản cố định, tải câu, chấm trên server, công bố đáp án và lịch sử.

## Soạn và chơi

| Dạng | Soạn câu | Người chơi | Luật chấm |
| --- | --- | --- | --- |
| Sắp xếp thứ tự | Nhập 3–6 mục theo thứ tự đúng; dùng ↑/↓ để đổi thứ tự | Kéo thả hoặc dùng ↑/↓, rồi khóa thứ tự | Đúng toàn bộ hoán vị; thiếu, trùng hoặc có mã lạ bị từ chối |
| Ước lượng số | Đặt min, max, bước nhảy, đơn vị và khoảng được chấp nhận | Kéo thanh trượt hoặc nhập số, rồi khóa giá trị | Số thuộc thang đo, đúng bước nhảy và nằm trong khoảng đúng, kể cả hai đầu |
| Ghim trên ảnh | Tải ảnh PNG/JPEG; kéo khoanh một vùng chữ nhật hoặc nhập tọa độ/kích thước theo % | Bấm ảnh hoặc dùng phím mũi tên, rồi khóa vị trí | Điểm nằm trong vùng chữ nhật, kể cả biên |

Ba dạng mới có 30 giây trả lời. Đúng trong 10 giây đầu: 3 điểm; sau 10 đến 20 giây: 2; sau 20 đến 30 giây: 1. Sai/hết giờ: 0. Câu cũ giữ 15 giây và mốc 5/10 giây. Không có điểm một phần. x2 vẫn hoạt động; bỏ đáp án và 50/50 chỉ dành cho câu một lựa chọn với ít nhất ba phương án.

Thanh số không tự gửi giá trị mặc định: người chơi phải thao tác hoặc nhập số trước khi khóa. Ghim trên ảnh không thể gửi khi ảnh chưa tải/giải mã xong. Tọa độ chỉ tính trên phần ảnh thật, không tính khoảng trống quanh ảnh; vùng đúng và các ghim được vẽ lại khi đổi kích thước cửa sổ. Trình xem lại thể hiện thứ tự đúng, khoảng số đúng hoặc vùng đúng cùng các ghim người chơi.

## Dữ liệu và giao thức

Không cần cột dữ liệu mới. `options_json` và `answer_key_json` lưu cấu trúc từng dạng; `question_asset_id` bắt buộc cho PINPOINT. Bản nháp có thể chưa hoàn chỉnh; xuất bản chặn dữ liệu không hợp lệ.

REORDER dùng options `{id,text}` và answerKey là mảng ID theo thứ tự đúng. Trình soạn cấp UUID cho mỗi mục, lưu danh sách options theo thứ tự độc lập với key; client xáo trộn các mục khi chơi.

RANGE dùng answerKey:

```json
{"min":0,"max":100,"step":1,"unit":"ms","acceptedMin":45,"acceptedMax":55}
```

`PublicQuestion.config` chỉ có `min`, `max`, `step`, `unit`; khoảng đúng không được gửi trước REVEAL. `ANSWER.answer` là một số JSON, không nhận chuỗi số. Min/max phải phân biệt, bước dương và chia hết thang đo, tối đa một triệu bước. Khoảng đúng phải chứa ít nhất một giá trị đạt được trên thang. Số có tối đa chín chữ số thập phân, tối đa 15 chữ số có nghĩa, trị tuyệt đối không quá 10^12; đơn vị tối đa 32 ký tự. Phép so sánh và bước nhảy dùng BigDecimal, không dùng dung sai số thực. Thang đo bị từ chối nếu các giá trị trung gian có thể vượt 15 chữ số; giới hạn này giữ số ổn định khi MySQL lưu JSON. Dữ liệu quá chính xác bị từ chối ngay khi lưu nháp, trước khi MySQL có thể làm tròn.

PINPOINT dùng answerKey:

```json
{"x":0.2,"y":0.3,"width":0.4,"height":0.2}
```

Vùng phải nằm trong hình vuông tọa độ [0,1] × [0,1] và có diện tích dương. Câu công khai chỉ có ID ảnh, không có tọa độ vùng đúng. `ANSWER.answer` là `{"x":0.4,"y":0.4}`; server từ chối tọa độ ngoài ảnh và trường dư. Trình soạn và chuột làm tròn đến sáu chữ số thập phân để ổn định tại biên.

`PublicQuestion.config` tùy chọn cho fixture cũ, null cho các dạng ngoài RANGE. Snapshot và lịch sử dùng cùng phép chiếu công khai; key chỉ xuất hiện sau khi câu đã được công bố. Media vẫn truyền thành các khối riêng, giữ giới hạn frame 64 KiB. Bộ kiểm tra kích thước xét cả đáp án mới và cấu hình thang số.

## Nâng cấp

1. Dừng server và sao lưu database + media như quy trình hiện tại.
2. Với schema 4, chạy `database/005_interactive_questions.sql` một lần để lên schema 5. Cài mới chạy lần lượt 001, 002, 003, 004, 005.
3. Build và cập nhật cả server lẫn client. HELLO yêu cầu client **3.0**, protocol 1, asset pack 1; client 2.0 bị từ chối ngay với thông báo cập nhật.
4. Trong **Quiz của tôi**, chọn một trong ba loại mới, lưu câu, xem trước, rồi xuất bản.

Migration chỉ mở rộng CHECK loại câu hỏi và tăng schema version, không xóa dữ liệu, thay điểm hay sửa bản xuất bản cũ.

## Kiểm thử

`./scripts/verify.sh -Pmysql-it` với cấu hình `QUIZ_TEST_DB_*` trỏ tới schema riêng kết thúc `_test`, đã áp dụng đủ năm script. Không dùng dữ liệu runtime cho kiểm thử.

Kiểm thử bổ sung bao phủ hoán vị, biên số thập phân/bước nhảy, vùng ảnh và dữ liệu sai kiểu; deadline 30 giây và điểm; loại bỏ key khỏi câu công khai/snapshot; thao tác trình soạn, khóa đáp án, bàn phím và co giãn ảnh; hai client TCP tạo/tải ảnh/xuất bản/chơi cả ba dạng/lưu và đọc lịch sử sau gỡ xuất bản.

# Thiết kế quiz cộng đồng và ảnh do người dùng tải lên

Ngày: 2026-10-10. Trạng thái: chức năng đã thống nhất; tài liệu chờ người dùng xem lại trước khi lập kế hoạch triển khai.

## Mục tiêu và phạm vi

Thêm trình soạn quiz cơ bản vào ứng dụng JavaFX hiện tại. Người dùng đã đăng nhập tạo bộ quiz, soạn câu, tải ảnh từ máy, lưu nháp, xem trước và xuất bản công khai để mọi người chọn thách đấu. Server TCP/MySQL vẫn quyết định quyền truy cập, thời gian và điểm.

Không bao gồm nhập hàng loạt, ngân hàng câu hỏi riêng, liên kết ảnh bên ngoài, đánh giá, kiểm duyệt, chế độ riêng tư hay thời gian tùy chỉnh.

## Các quyết định đã thống nhất

- Quiz cộng đồng công khai sau khi xuất bản; bản nháp chỉ chủ sở hữu thấy.
- Một bộ có 1–50 câu. Người tạo tự chọn loại câu, không bắt buộc tỷ lệ các loại.
- Hỗ trợ SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER như hiện tại.
- Mỗi trận chơi toàn bộ câu trong bộ. Mặc định theo thứ tự soạn; chủ sở hữu có thể bật xáo trộn. Hai người luôn nhận cùng thứ tự đã chốt cho trận.
- Mỗi câu có 15 giây trả lời, sử dụng cách chấm điểm hiện tại.
- Mỗi câu có tối đa một ảnh câu hỏi và một ảnh giải thích, độc lập và không bắt buộc.
- Chọn ảnh PNG/JPEG từ máy, tối đa 5 MiB (5 × 1024 × 1024 byte) mỗi ảnh.
- Ảnh câu hỏi xuất hiện cùng câu; ảnh giải thích chỉ hiện khi cả hai đã trả lời hoặc hết giờ, cùng đáp án.
- Quiz cộng đồng có điểm, kết quả thắng/thua và lịch sử trận nhưng không thay đổi điểm, số trận, thắng/thua/hòa của thống kê xếp hạng chung. Quiz hệ thống giữ nguyên hành vi tính hạng.

## Luồng sử dụng

Sảnh có mục “Quiz của tôi” và thao tác “Tạo quiz”. Trình soạn gồm tên, mô tả, danh mục hiện có, tùy chọn xáo trộn và danh sách câu có thể thêm, sửa, xóa, sắp xếp.

Biểu mẫu câu có loại câu, nội dung, lựa chọn theo loại, đáp án đúng, giải thích, ảnh câu hỏi và ảnh giải thích. Ảnh có xem trước, thay thế và bỏ ảnh. Xem trước mô phỏng riêng trạng thái đang trả lời và công bố đáp án, không tạo trận và không ghi thống kê.

“Lưu nháp” cho phép nội dung chưa đủ điều kiện xuất bản; vẫn kiểm tra giới hạn kích thước và định dạng dữ liệu. Nội dung trên màn hình được giữ nếu lưu/tải ảnh lỗi để người dùng thử lại. Không cam kết phục hồi thay đổi chưa lưu khi đóng ứng dụng.

“Xuất bản” kiểm tra toàn bộ bộ quiz và chỉ thành công khi nội dung hợp lệ. Quiz xuất hiện trong danh sách công khai với tên tác giả và nhãn “Cộng đồng · Không tính hạng”. Chỉ chủ sở hữu có thể sửa hoặc gỡ khỏi sảnh; quiz hệ thống không được sửa qua trình soạn này.

Sửa quiz đã xuất bản mở bản nháp mới; phiên bản đang công khai vẫn dùng được cho đến khi bản sửa được xuất bản. Gỡ khỏi sảnh ngăn tạo trận mới và vô hiệu hóa lời mời chưa trở thành trận. Trận đã tạo tiếp tục dùng phiên bản đã chốt. Phiên bản cũ được giữ khi còn được trận hoặc lịch sử tham chiếu.

## Quy tắc dữ liệu và kiểm tra

Tên bắt buộc, tối đa 128 ký tự; mô tả tối đa 1000 ký tự, có thể để trống. Danh mục phải tồn tại và đang hoạt động. Nội dung câu không được rỗng. Giới hạn văn bản/lựa chọn hiện có của giao thức được giữ và công bố trong biểu mẫu; mọi kiểm tra client phải có kiểm tra tương ứng ở server.

Một đáp án cần các lựa chọn không rỗng và đúng một lựa chọn đúng. Nhiều đáp án cần các lựa chọn không rỗng và ít nhất một lựa chọn đúng. Đúng/sai dùng đáp án boolean. Trả lời ngắn có ít nhất một đáp án được chấp nhận, giữ quy tắc chuẩn hóa/chấm hiện tại. ID lựa chọn phải duy nhất trong câu, đáp án phải tham chiếu lựa chọn có thật.

Server kiểm tra nội dung ảnh thực tế, không chỉ tên file; từ chối file hỏng, sai định dạng, quá 5 MiB hoặc quá 16 triệu pixel sau giải mã. Giới hạn pixel là quyết định kỹ thuật để giới hạn bộ nhớ. Lỗi được trả về theo câu/trường để người tạo sửa, không xuất bản một phần.

## Kiến trúc và dữ liệu

Tách ba trách nhiệm: quản lý quiz/phiên bản và quyền sở hữu; lưu/truyền ảnh; sử dụng phiên bản quiz trong trận. Client có màn danh sách cá nhân, trình soạn, xem trước và bộ tải/cache ảnh. Dùng lại các renderer bốn loại câu và trạng thái công bố đáp án hiện tại.

Bổ sung loại quiz SYSTEM/COMMUNITY, chủ sở hữu và trạng thái công khai. Lưu nội dung phiên bản gồm thông tin quiz, thứ tự câu, lựa chọn, đáp án, giải thích và tham chiếu ảnh. Bản nháp có thể sửa; bản xuất bản được chốt bất biến. Server kiểm tra quyền trên mọi thao tác bằng phiên đăng nhập, không tin ownerId do client gửi.

Trận lưu nguồn quiz, phiên bản, số vòng và cờ tính hạng tại thời điểm tạo. Cờ tính hạng được server suy ra từ loại quiz. Giữ snapshot câu và kết quả để xem lịch sử sau khi tác giả sửa/gỡ quiz. Thao tác lưu kết quả cộng đồng vẫn nguyên tử và chống lưu trùng như trận hệ thống, nhưng bỏ qua cập nhật thống kê xếp hạng.

Migration phải giữ nguyên quiz hệ thống, ID, ảnh đóng gói và lịch sử cũ; dữ liệu cũ được phân loại SYSTEM và tính hạng. Thay các ràng buộc cố định 10 vòng/30 điểm bằng ràng buộc phù hợp tối đa 50 vòng/150 điểm cho cộng đồng, trong khi luật quiz hệ thống vẫn là 10 câu theo tỷ lệ cũ. Không chỉnh sửa script seed để thay cho migration cơ sở dữ liệu đang dùng.

## Lưu và truyền ảnh

Lưu file trong thư mục media cấu hình trên server, metadata trong MySQL; client chỉ dùng ID mờ do server cấp, không gửi đường dẫn máy cá nhân hoặc tự chọn đường dẫn server. Không nhúng ảnh vào bản ghi câu hay kết quả JSON.

Giữ kiến trúc TCP, thêm truyền ảnh theo từng khối có giới hạn dưới kích thước frame 64 KiB hiện tại. Dùng ID phiên truyền, offset, kích thước và kiểm tra toàn vẹn; giới hạn tổng dung lượng, số phiên đồng thời và thời gian chờ. Truyền file không được chặn luồng điều khiển trận hoặc làm trễ timer; dùng xử lý I/O riêng và ưu tiên thông điệp trận. Client/server cần cùng phiên bản giao thức mới; bắt tay phải từ chối phiên bản không tương thích với thông báo rõ ràng.

Chỉ ảnh tải hoàn tất mới có thể gắn vào nội dung lưu. Upload dang dở hết hạn được dọn. Ảnh đã được bản nháp, phiên bản hoặc lịch sử tham chiếu không bị xóa. Client cache theo ID bất biến, không đổi nội dung ảnh dưới cùng ID.

Không đưa đáp án hay ID ảnh giải thích vào payload câu công khai trước khi công bố. Quyền tải ảnh giải thích được server mở theo trạng thái trận, không chỉ dựa vào việc client có biết ID; chủ sở hữu vẫn có quyền xem trước nội dung của mình. Sau trận, chỉ người được phép xem lịch sử trận đó tải được ảnh từ lịch sử. Ảnh trong trình soạn của tác giả và ảnh trong trận là hai ngữ cảnh quyền riêng.

## Đồng bộ trận và lỗi

Sau khi nhận câu, client tải/giải mã ảnh câu hỏi rồi mới báo QUESTION_READY. Server chỉ bắt đầu 15 giây khi cả hai sẵn sàng. Dùng thời gian chờ tải tối đa 30 giây mỗi câu; không sẵn sàng thì hủy trận với thông báo tải câu thất bại, không tính thống kê. Thời gian chờ này là quyết định kỹ thuật tách khỏi 15 giây làm bài.

Khi cả hai trả lời hoặc hết giờ, server công bố đáp án và cấp quyền tải ảnh giải thích. Nếu ảnh giải thích lỗi, vẫn hiện đáp án, giải thích chữ và kết quả; báo ảnh không tải được và cho thử lại. Không kéo dài hay chấm lại câu vì lỗi ảnh giải thích.

Thông báo lỗi lưu/tải không chứa đường dẫn nội bộ. Server kiểm tra lại quiz còn công khai khi chấp nhận lời mời và tạo trận. Việc tạo trận và chọn phiên bản phải nhất quán với xuất bản/gỡ quiz; trận không trộn câu của nhiều phiên bản.

Kết quả và lịch sử tối đa 50 câu có thể vượt frame 64 KiB. Phân trang phần xem lại câu hỏi, giữ thông điệp kết quả chính nhỏ; không tăng giới hạn frame để chứa toàn bộ ảnh hoặc toàn bộ lịch sử. Hiển thị số vòng, điểm tối đa và tiến độ theo dữ liệu trận thay vì số 10 viết cố định.

## Kiểm chứng bắt buộc khi triển khai

- Quyền: người khác không đọc bản nháp, sửa, xuất bản, gỡ hoặc lấy ảnh riêng của chủ sở hữu.
- Soạn: bốn loại câu, thêm/xóa/sắp xếp, xem trước, lưu nháp chưa hoàn chỉnh; xuất bản chặn câu thiếu đáp án và bộ ngoài 1–50 câu.
- Ảnh: PNG/JPEG hợp lệ, sai nội dung/đuôi file, quá dung lượng/pixel, mất kết nối giữa upload, thử lại và toàn vẹn dữ liệu. Hai client trên hai máy tải được cùng ảnh.
- Thời điểm: không bắt đầu timer trước khi hai ảnh câu hỏi sẵn sàng; không lấy được ảnh giải thích trước reveal, kể cả đoán/biết ID. Hết thời gian tải thì hủy rõ ràng.
- Gameplay: 1, 10 và 50 câu, bộ chỉ một loại câu, thứ tự/xáo trộn đồng nhất; 15 giây/câu; chấm điểm và kết thúc trận đúng số vòng.
- Phiên bản: sửa/gỡ trong lúc mời hoặc đang đấu, kiểm tra snapshot và lịch sử cũ không đổi.
- Lưu kết quả: cộng đồng có lịch sử và thắng/thua nhưng mọi trường xếp hạng chung không đổi; hệ thống tiếp tục cập nhật đúng; lưu lại kết quả không bị nhân đôi.
- Giao thức: truyền ảnh không vượt frame hoặc làm trễ điều khiển trận; xem lại 50 câu bằng phân trang; lỗi phiên bản client rõ ràng.
- Migration: nâng cấp dữ liệu hiện có không mất quiz, ảnh, lịch sử, ràng buộc hoặc thống kê cũ.

## Tiêu chí hoàn thành

Một người dùng tạo bộ quiz từ máy A, lưu nháp, đính kèm ảnh, xuất bản; người dùng trên máy B thấy và chọn thách đấu. Hai bên chơi đủ câu, cùng thứ tự, ảnh xuất hiện đúng giai đoạn. Kết quả được lưu nhưng bảng xếp hạng chung không đổi. Tác giả sửa/gỡ quiz mà trận đã tạo và lịch sử vẫn giữ nội dung gốc.

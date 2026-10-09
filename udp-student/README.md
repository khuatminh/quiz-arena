# Bài UDP.Student — cổng 2209

Yêu cầu Java 8 trở lên. Chạy các lệnh sau trong thư mục `udp-student`:

```sh
mkdir -p out
javac -encoding UTF-8 -d out UDP/Student.java UDP/StudentClient.java
java -cp out UDP.StudentClient <IP_SERVER> <MA_SINH_VIEN> <MA_CAU_HOI>
```

Thay ba tham số bằng thông tin của bài thi. Ví dụ khi server chạy trên máy này:

```sh
java -cp out UDP.StudentClient 127.0.0.1 B15DCCN001 EE29C059
```

Client gửi `;studentCode;qCode`, nhận 8 byte requestId rồi đọc `Student` bằng
`ObjectInputStream`. Tên được xóa khoảng trắng thừa và viết hoa chữ đầu mỗi từ.
Email gồm tên cuối cùng và các chữ cái đầu của họ, tên đệm, viết thường.
Nếu tên có dấu tiếng Việt, phần email được bỏ dấu.

Ví dụ: `nguyen van tuan nam` → `Nguyen Van Tuan Nam` → `namnvt@ptit.edu.vn`.

Client gửi lại đúng 8 byte requestId ban đầu kèm đối tượng đã cập nhật `name`,
`email`; giữ nguyên `id`, `code`. Socket tự đóng sau khi gửi hoặc khi có lỗi.
Thời gian chờ nhận là 10 giây; client không chờ thêm thông điệp xác nhận.

`Student.java` phải có `package UDP`, triển khai `Serializable`, đúng bốn thuộc
tính kiểu `String` và `serialVersionUID = 20171107L` để đọc được đối tượng từ server.

## Kiểm tra với server cục bộ

Không chạy kiểm tra này đồng thời với một server khác đang dùng cổng UDP 2209:

```sh
javac -encoding UTF-8 -d out UDP/Student.java UDP/StudentClient.java tests/StudentClientTest.java
java -cp out StudentClientTest
```

Bài kiểm tra chạy server cục bộ để xác nhận thông điệp mở đầu, requestId,
serialization, tên, email và việc giữ nguyên id/code. Server bài thi chưa được
kiểm tra vì chưa có IP và mã sinh viên/mã câu hỏi thực tế.

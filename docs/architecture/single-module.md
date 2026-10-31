# Cấu trúc Maven một module

Dự án dùng một `pom.xml` gốc, packaging `jar`. Toàn bộ mã nguồn nằm dưới `src/main/java/vn/edu/nhom7/quiz/`, giữ nguyên các package `common`, `server`, `client` và các package con. Các package phân chia trách nhiệm; chúng không còn là ba module Maven riêng.

```text
src/
├── main/
│   ├── java/vn/edu/nhom7/quiz/
│   │   ├── common/
│   │   ├── server/
│   │   └── client/
│   └── resources/                 # assets, fonts, protocol, ui
└── test/
    ├── java/vn/edu/nhom7/quiz/     # Giữ nguyên các package kiểm thử
    └── resources/
```

## Build và chạy

Chọn JDK 21 cho Maven và chạy các lệnh từ thư mục gốc:

```sh
mvn clean verify
java -jar target/quiz-arena-server.jar --config=config/server.local.properties
```

Mở terminal khác để chạy client:

```sh
mvn javafx:run
# Demo UI bằng fixture, không cần server
mvn javafx:run -Djavafx.args="--fixture --participant=101"
```

Client và server vẫn là các tiến trình độc lập với hai điểm chạy `ClientMain` và `ServerMain`. Chúng giao tiếp qua TCP như trước; một module Maven chỉ thay cách quản lý source và build. Không cần `mvn -f client/pom.xml`, `-pl client`, `-pl server` hoặc cài artifact `quiz-arena-common` riêng.

## Các JAR tạo ra

| Tệp | Nội dung và cách dùng |
| --- | --- |
| `target/quiz-arena-1.0.0-SNAPSHOT.jar` | Artifact mặc định chứa toàn bộ lớp của dự án, chưa gộp thư viện |
| `target/quiz-arena-server.jar` | Server và common, kèm Jackson và JDBC; chạy bằng `java -jar`; không chứa client hoặc JavaFX |
| `target/quiz-arena-1.0.0-SNAPSHOT-client.jar` | Client và common cùng tài nguyên giao diện; chưa gộp thư viện; không chứa code server |

Script `scripts/package-desktop.py` lấy JAR client riêng và chỉ chép các thư viện client cần dùng. JDBC, protobuf và cấu hình database không được đưa vào bản desktop. Script vẫn tạo tên JAR nội bộ và launcher tương thích với cách đóng gói trước đó.

## Kiểm thử và đối chiếu

`./scripts/verify.sh` tự chọn JDK 21 trên macOS khi cần rồi chạy Maven verify. Với kiểm thử tích hợp, dùng database riêng có tên kết thúc `_test`, thiết lập `QUIZ_TEST_DB_URL`, `QUIZ_TEST_DB_USER`, `QUIZ_TEST_DB_PASSWORD`, rồi chạy:

```sh
./scripts/verify.sh -Pmysql-it
```

Kiểm thử nằm chung trong `src/test/java`, kết quả nằm tại `target/surefire-reports` và `target/failsafe-reports`. Các file Java và tài nguyên được chuyển nguyên byte, không thay đổi package, import, luật chơi, giao thức, schema hay dữ liệu. Báo cáo đối chiếu và kết quả kiểm thử của lần chuyển cấu trúc nằm trong `single-module-verification.json` cùng thư mục.

Nạp lại dự án trong IDE từ `pom.xml` gốc. Các thư mục `common/target`, `server/target`, `client/target` còn trên máy nếu có chỉ là sản phẩm build cũ đã được Git ignore; có thể xóa khi không cần.

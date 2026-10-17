# Tổng quan dự án Quiz Arena

> Nội dung tổng hợp từ README, cấu hình Maven, schema SQL và mã nguồn trong repository. Đây là phân tích tĩnh; các kết quả kiểm thử được dẫn từ tài liệu có sẵn, không phải kết quả chạy kiểm thử mới.

## 1. Chủ đề và mục tiêu

**Quiz Arena** là ứng dụng desktop thi đấu quiz **1 đối 1 theo thời gian thực**, được xây dựng bằng Java.

Theo tài liệu trong repository, đây là **bài tập lớn môn Lập trình mạng của Nhóm 7**. Trọng tâm của dự án là kết nối nhiều người chơi, đồng bộ trận đấu, xử lý đa luồng và lưu dữ liệu.

Người chơi đăng nhập, chọn bộ câu hỏi, mời một người đang online và cùng thi đấu. **Server đóng vai trò trọng tài**, quyết định thời gian, đáp án, điểm số và kết quả.

## 2. Công nghệ và framework

| Thành phần | Công nghệ | Vai trò |
| --- | --- | --- |
| Ngôn ngữ | Java 21 | Dùng cho cả client và server |
| Framework giao diện | JavaFX 21.0.6 | Xây dựng ứng dụng desktop |
| Styling | JavaFX CSS | Định dạng giao diện, màu sắc và thành phần |
| Giao tiếp mạng | TCP Socket, `Socket` / `ServerSocket` | Kết nối hai chiều lâu dài |
| Định dạng dữ liệu | JSON + Jackson 2.18.3 | Mã hóa và giải mã thông điệp |
| Database | MySQL 8.4 | Lưu tài khoản, quiz và lịch sử trận |
| Truy cập database | JDBC + MySQL Connector/J 8.4.0 | Truy vấn SQL và transaction |
| Xử lý đồng thời | Java Concurrency API | Thread pool, scheduler và cấu trúc dữ liệu đồng thời |
| Build | Maven multi-module | Quản lý dependency, build và đóng gói |
| Kiểm thử | JUnit 5.11.4 | Unit test và integration test |
| Băm mật khẩu | PBKDF2-HMAC-SHA256 | Salt riêng cho từng tài khoản, 600.000 vòng lặp |

**Framework chính là JavaFX ở phía giao diện. Backend được viết bằng Java thuần và JDBC trực tiếp**, không sử dụng Spring Boot hay Hibernate.

Nguồn cấu hình:

- [Maven cha](pom.xml)
- [Maven client](client/pom.xml)
- [Maven server](server/pom.xml)

## 3. Kiến trúc nền tảng

### 3.1. Kiến trúc tổng thể: Client–Server tập trung

```text
┌───────────────────┐
│ Client A — JavaFX │ ────┐
└───────────────────┘     │
                         │ TCP + JSON
                         ▼
                  ┌────────────────────┐
                  │ Java Quiz Server   │
                  │                    │
                  │ Tài khoản / phiên  │
                  │ Thách đấu / trận   │
                  │ Chấm điểm / chat   │
                  │ Quiz / lịch sử     │
                  └──────┬───────┬─────┘
                         │       │
                       JDBC    File I/O
                         │       │
                         ▼       ▼
                     MySQL   data/media

┌───────────────────┐
│ Client B — JavaFX │ ───── TCP + JSON ─────► Java Quiz Server
└───────────────────┘
```

Có thể mô tả hệ thống theo ba tầng trách nhiệm:

1. **Presentation:** client JavaFX hiển thị và nhận thao tác.
2. **Application/Business:** server xử lý nghiệp vụ và điều phối trận.
3. **Persistence:** MySQL lưu dữ liệu; filesystem lưu ảnh.

Client chỉ giao tiếp với server; server mới truy cập database.

### 3.2. Server-authoritative

Đây là đặc điểm quan trọng nhất của kiến trúc:

- Client gửi lựa chọn của người chơi.
- Server kiểm tra người gửi, trận, câu hỏi, thời hạn và việc đã trả lời hay chưa.
- Server tự đo thời gian nhận đáp án và tính điểm.
- Đáp án đúng chỉ được công bố khi kết thúc lượt trả lời.

Nhờ đó, logic quyết định thắng/thua không phụ thuộc vào dữ liệu điểm hoặc thời gian do client tự khai báo.

### 3.3. Tổ chức backend

Backend là **một ứng dụng server duy nhất, chia thành các package nghiệp vụ**. Cách tổ chức này gần với **modular monolith kết hợp service/repository**.

Các thành phần được khởi tạo và nối với nhau trong [ServerRuntime.java](server/src/main/java/vn/edu/nhom7/quiz/server/ServerRuntime.java):

| Thành phần | Trách nhiệm |
| --- | --- |
| `AuthService` | Đăng ký, đăng nhập |
| `SessionRegistry` | Quản lý phiên người dùng |
| `ChallengeManager` | Quản lý lời thách đấu |
| `MatchManager` | Quản lý trận đấu |
| `CommunityQuizService` | Tạo và xuất bản quiz |
| `MediaService` | Xử lý ảnh |
| `PersistenceService` | Lưu kết quả và thử lại |
| Các `Jdbc...Repository` | Truy cập database |

### 3.4. Tổ chức client

Client tách riêng **UI, network và state**, với luồng cập nhật chính:

```text
Sự kiện từ server
    → NetworkClient
    → ClientStore
    → ClientStateReducer
    → ClientState mới
    → JavaFX render
```

Đây là cách quản lý trạng thái theo **store/reducer**, gần với tư duy luồng dữ liệu một chiều. State được xử lý tuần tự trên executor riêng; cập nhật giao diện chuyển sang JavaFX thread bằng `Platform.runLater`.

Giao diện theo phong cách "console arena": màu và cỡ chữ là các token CSS trên `.root` (`quiz-arena.css` cho giao diện tối, `quiz-arena-light.css` chỉ ghi đè token cho giao diện sáng); `Theme` đổi stylesheet và cỡ chữ lúc chạy. Hiệu ứng (rung, chớp, hạt, sóng xung kích) chạy trên một lớp phủ không nhận chuột (`Effects.Layer`) và được tắt khi người chơi bật giảm chuyển động.

## 4. Cấu trúc mã nguồn

Dự án gồm **3 Maven module**:

| Module/thư mục | Nội dung |
| --- | --- |
| `common/` | Hợp đồng giao thức, DTO/payload, loại message, đóng khung TCP và tài nguyên dùng chung |
| `server/` | Networking, tài khoản, phiên, thách đấu, trận, quiz, ảnh, JDBC và truy vấn |
| `client/` | Ứng dụng JavaFX, các màn hình, quản lý state, networking và tải tài nguyên |
| `database/` | Schema, dữ liệu mẫu và migration |
| `config/` | Cấu hình server mẫu |
| `docs/` | Thiết kế, giao thức, vận hành, kế hoạch và bằng chứng kiểm thử |

Quan hệ dependency:

```text
client ──► common ◄── server
```

Các package chính:

```text
common/
  protocol/       Hợp đồng thông điệp và payload
  net/            Mã hóa, giải mã khung TCP
  assets/         Danh mục tài nguyên dùng chung

server/
  config/         Cấu hình server
  network/        Kết nối, handshake, định tuyến thông điệp
  auth/           Tài khoản và băm mật khẩu
  session/        Phiên và trạng thái online
  challenge/      Lời mời thi đấu
  match/          Vòng đời trận, timer, điểm và chat
  quiz/           Ngân hàng câu hỏi và quiz cộng đồng
  media/          Tải lên, tải xuống và kiểm tra ảnh
  persistence/    Lưu kết quả và retry
  query/          Xếp hạng và lịch sử
  db/             JDBC và kiểm tra database
  domain/         Mô hình nghiệp vụ
  tools/          Công cụ hỗ trợ

client/
  ui/             Màn hình và thành phần JavaFX
  state/          Store, reducer và trạng thái client
  network/        Giao tiếp với server
  assets/         Nạp, tải và lưu đệm tài nguyên
  support/        Tiện ích và fixture demo
```

Hai điểm khởi động chính:

- [ClientMain.java](client/src/main/java/vn/edu/nhom7/quiz/client/ClientMain.java)
- [ServerMain.java](server/src/main/java/vn/edu/nhom7/quiz/server/ServerMain.java)

## 5. Chức năng và nghiệp vụ chính

### 5.1. Chức năng người dùng

- Đăng ký, đăng nhập, tên hiển thị và avatar.
- Xem người chơi online và bộ quiz.
- Gửi, nhận, từ chối hoặc hủy lời thách đấu.
- Thi đấu 1v1, chat trong trận và tái đấu.
- Xem lịch sử, chi tiết trận và bảng xếp hạng.
- Tạo quiz, chỉnh sửa bản nháp, sắp xếp câu hỏi, xem trước và xuất bản.
- Tải ảnh cho câu hỏi và phần giải thích.
- Ba quyền trợ giúp mỗi trận: x2 điểm, Loại 1 đáp án, 50-50 (phím X, L, N).
- Chơi được hoàn toàn bằng bàn phím; menu Esc chỉnh âm lượng, cỡ chữ, giao diện sáng/tối và giảm chuyển động.

### 5.2. Các loại câu hỏi

| Loại | Cách trả lời | Cách xét đúng |
| --- | --- | --- |
| `SINGLE_CHOICE` | Chọn một phương án | Trùng phương án đúng |
| `MULTIPLE_CHOICE` | Chọn nhiều phương án | Tập lựa chọn khớp hoàn toàn |
| `TRUE_FALSE` | Chọn đúng hoặc sai | Trùng giá trị đúng/sai |
| `SHORT_ANSWER` | Nhập câu trả lời ngắn | Khớp đáp án được chấp nhận sau chuẩn hóa |

### 5.3. Hai nguồn quiz

| Tiêu chí | Quiz hệ thống | Quiz cộng đồng |
| --- | --- | --- |
| Nguồn | Dữ liệu có sẵn | Người dùng tự tạo |
| Số câu/trận | 10 câu | 1–50 câu |
| Thứ tự | Chọn ngẫu nhiên theo cơ cấu loại câu | Theo tác giả hoặc xáo trộn |
| Điểm tối đa | 33 (30 + 3 nhờ x2) | 153 |
| Xếp hạng toàn cục | Có với trận đủ điều kiện | Không cập nhật thống kê xếp hạng |

Dữ liệu mẫu có **3 bộ quiz và 60 câu hỏi**.

### 5.4. Thời gian và tính điểm

Mỗi câu có tối đa **15 giây** trả lời:

| Kết quả | Thời gian server ghi nhận | Điểm |
| --- | --- | ---: |
| Đúng | Đến 5 giây | 3 |
| Đúng | Trên 5 đến 10 giây | 2 |
| Đúng | Trên 10 giây, còn trong thời hạn trả lời | 1 |
| Sai hoặc hết giờ | — | 0 |

Trận đấu vận hành theo các pha rõ ràng:

```text
Sẵn sàng → Đếm ngược → Chuẩn bị câu/ảnh → Trả lời
         → Công bố đáp án → Bảng điểm → Câu tiếp theo/Kết quả
```

Server kết thúc lượt trả lời khi cả hai đã trả lời hoặc hết giờ. Việc chuẩn bị ảnh diễn ra trước khi tính thời gian làm bài.

## 6. Giao thức mạng và xử lý đồng thời

### 6.1. Giao thức TCP riêng

Thông điệp có dạng:

```text
[4 byte độ dài big-endian] + [JSON UTF-8]
```

JSON tối đa **65.536 byte**. Envelope chứa:

```text
protocolVersion, type, requestId,
matchId, roundId, eventSeq, payload
```

Các định danh giúp liên kết phản hồi với yêu cầu và đúng trận/câu hỏi; `eventSeq` hỗ trợ kiểm soát thứ tự sự kiện.

Sau khi kết nối, client gửi `HELLO` để kiểm tra phiên bản. Theo README, phiên bản hiện tại dùng **client `2.0`, protocol `1`, asset pack `1`**.

### 6.2. Đồng thời và giới hạn kết nối

- Server hiện giới hạn **32 kết nối**.
- Mỗi kết nối có reader, writer và hàng đợi gửi riêng.
- Mỗi trận có cơ chế đồng bộ và định thời riêng.
- Tác vụ database và ảnh dùng các thread pool có giới hạn.
- Heartbeat kiểm tra kết nối định kỳ 10 giây; kết nối không hoạt động hết hạn sau 30 giây.
- Việc lưu kết quả không giữ khóa xử lý trận.

Tham khảo: [Kiến trúc mạng](docs/architecture/network-explanation.md), [Giao thức v1](docs/protocol/protocol-v1.md).

## 7. Database và lưu trữ ảnh

### 7.1. Các nhóm bảng

| Nhóm | Bảng |
| --- | --- |
| Phiên bản schema | `SCHEMA_METADATA` |
| Tài khoản | `USERS` |
| Ngân hàng câu hỏi | `CATEGORIES`, `QUIZZES`, `QUESTIONS` |
| Soạn thảo | `QUIZ_DRAFTS`, `QUIZ_DRAFT_QUESTIONS` |
| Xuất bản | `QUIZ_VERSIONS`, `QUIZ_VERSION_QUESTIONS` |
| Thi đấu | `MATCHES`, `MATCH_QUESTIONS`, `MATCH_ANSWERS` |
| Ảnh | `MEDIA_ASSETS` |

Hai quyết định thiết kế đáng chú ý:

- **Phiên bản quiz đã xuất bản cố định**, để sửa bản nháp không làm đổi nội dung trận đang chơi.
- **Lịch sử lưu snapshot câu hỏi**, để phản ánh đúng nội dung ở thời điểm thi đấu.

Schema hiện được nâng cấp lên **phiên bản 4** qua các script:

1. [001_schema.sql](database/001_schema.sql)
2. [002_seed.sql](database/002_seed.sql)
3. [003_community_quizzes.sql](database/003_community_quizzes.sql)
4. [004_lifelines.sql](database/004_lifelines.sql)

### 7.2. Lưu kết quả nhất quán

Kết quả trận, câu trả lời và thống kê được lưu trong **một transaction**. Cơ chế lưu có retry và chống cộng thống kê hai lần khi lưu lại cùng trận.

Các trạng thái lưu gồm:

- `PENDING`: đang chờ lưu.
- `SAVED`: đã lưu thành công.
- `FAILED`: lưu gặp lỗi; server còn giữ kết quả và tiếp tục thử lại khi đang chạy.

### 7.3. Lưu trữ ảnh

Ảnh nằm trong `data/media`; MySQL lưu metadata và quan hệ sở hữu.

- Hỗ trợ PNG/JPEG.
- Tối đa 5 MiB/file và 16 triệu pixel.
- Kiểm tra nội dung ảnh và SHA-256.
- Truyền ảnh theo từng phần qua TCP.
- Ảnh giải thích chỉ được mở theo trạng thái công bố đáp án.

## 8. Triển khai

Môi trường được hướng dẫn sử dụng là **desktop và mạng LAN**:

- Máy server cần JDK 21 và MySQL.
- Máy người chơi chạy JavaFX client.
- Cổng TCP mặc định: **5555**.
- Server lắng nghe trên `0.0.0.0`.
- Server được đóng gói thành JAR kèm dependency.
- Có chế độ `--fixture` để xem demo giao diện mà không cần server/database.

Các lệnh chính, chạy từ thư mục gốc sau khi chuẩn bị database và cấu hình:

```sh
# Build và cài các module vào kho Maven cục bộ
mvn clean install

# Chạy server
java -jar server/target/quiz-arena-server.jar --config=config/server.local.properties

# Chạy client
mvn -f client/pom.xml javafx:run

# Xem demo giao diện ngoại tuyến
mvn -f client/pom.xml javafx:run -Djavafx.args="--fixture --participant=101"

# Chạy kiểm thử thông thường
mvn clean verify
```

Hướng dẫn đầy đủ: [README.md](README.md).

## 9. Tình trạng triển khai và giới hạn

Repository đã có mã nguồn triển khai, unit test, integration test và tài liệu vận hành.

[Báo cáo nghiệm thu](docs/testing/acceptance-report.md) ngày **09/10/2026** ghi nhận **243 test pass**, nhưng đó là kết quả được lưu trong tài liệu, không phải lần chạy kiểm thử mới trong quá trình tổng hợp này. Tài liệu cũng ghi rõ còn một số kiểm tra tương tác thực tế, LAN hai máy và high-DPI chưa hoàn tất.

Các giới hạn hiện được mô tả gồm:

- Chưa có TLS; môi trường hiện tại phù hợp mạng cục bộ tin cậy.
- Chưa tiếp tục trận cũ khi kết nối lại.
- Kết quả chưa lưu còn nằm trong RAM và có thể mất nếu server crash hoặc bị tắt cưỡng bức.
- Chưa có cơ chế khôi phục trận đang chơi hoặc nhật ký bền vững cho hàng đợi lưu.

## 10. Kết luận

**Quiz Arena là một dự án Java desktop full-stack**, dùng **JavaFX + TCP Socket + MySQL**, với kiến trúc **client–server tập trung, server-authoritative**, backend chia module nghiệp vụ và client cập nhật theo sự kiện.

Giá trị kỹ thuật nổi bật nằm ở:

- Lập trình mạng bằng TCP Socket.
- Đồng bộ trạng thái trận đấu theo thời gian thực.
- Xử lý đa luồng và tranh chấp giữa sự kiện trả lời với timeout.
- Thiết kế giao thức dùng chung giữa client và server.
- Lưu dữ liệu nhất quán bằng transaction và retry.
- Quản lý phiên bản quiz và snapshot lịch sử.

## Tài liệu tham khảo trong repository

- [README](README.md)
- [Tài liệu kiến trúc, thiết kế và phân công Nhóm 7](BTL_Quiz_Doi_Khang_Nhom7_KienTruc_ThietKe_PhanCong.md)
- [Kiến trúc mạng](docs/architecture/network-explanation.md)
- [Giao thức](docs/protocol/protocol-v1.md)
- [Thiết lập database](docs/operations/database-setup.md)
- [Vận hành lưu kết quả](docs/operations/persistence-runbook.md)
- [Tình trạng triển khai](docs/testing/implementation-status.md)
- [Báo cáo nghiệm thu](docs/testing/acceptance-report.md)

# Kiến trúc sau refactor và kết quả kiểm chứng

Đợt refactor ngày 29/10/2026 thực hiện trên nhánh `codex/refactor-project`, từ
commit `06d2a42`. Đã rà soát cả `common`, `server`, `client`, cấu hình build,
kiểm thử và hướng dẫn database. Các thay đổi local có trước đợt này được giữ lại.

## Phân chia trách nhiệm

| Khu vực | Thành phần và trách nhiệm |
| --- | --- |
| Giao thức | `ProtocolCodec` mã hóa/giải mã envelope; `PayloadValidator` kiểm tra schema record, kiểu dữ liệu, UUID và trường nullable. |
| TCP framing | `FrameCodec` sở hữu giới hạn và kích thước prefix; `FrameDecoder` dùng cùng hằng số để ghép khung dữ liệu. |
| Định tuyến server | `MessageRouter` quản lý handshake, cache request, xác thực, giới hạn tốc độ và executor; `QueryDispatcher` chuyển truy vấn đến dịch vụ tương ứng. |
| Điều phối trận | `MatchManager` sở hữu danh sách trận, khóa, timer và thứ tự phát sự kiện. |
| Các vòng đấu | `MatchRounds` xử lý thời hạn, đáp án, chấm điểm và bảng điểm; `Match.requireCurrentRound` kiểm tra round trước khi thay đổi dữ liệu. |
| Kết thúc trận | `MatchResults` xử lý kết quả, xử thua, rời trận và đóng phiên kết quả. `RematchCoordinator` tiếp tục quản lý tái đấu. |
| Chat và trợ giúp | `MatchChat` quản lý nội dung, request trùng và giới hạn chat; `MatchLifelines` kiểm tra và sử dụng trợ giúp. |
| Dữ liệu gửi client | `MatchPayloads` dựng dữ liệu công khai; `MatchSnapshotFactory` dựng snapshot theo người xem và giai đoạn. |
| Quiz cộng đồng | `CommunityQuizService` điều phối giao dịch; `JdbcCommunityQuizRepository` lưu bản nháp; `DraftValidator` kiểm tra dữ liệu; `CommunityQuizPublisher` kiểm tra và xuất bản phiên bản bất biến. |
| Lưu kết quả | `MatchSummaryValidator` kiểm tra tính nhất quán; `MatchSummaryProjection` chuẩn hóa để so sánh lần lưu lặp; `JdbcMatchRepository` chia rõ các bước lưu trận, vòng, đáp án và thống kê. |
| Trạng thái client | `ClientStore` thực hiện mọi chuyển trạng thái qua một executor; `ClientStateReducer` mô tả chuyển trạng thái; `MatchEventData` tích lũy dữ liệu đáp án, trợ giúp và streak. |
| Timeout client | `RequestTimeoutMonitor` hủy request của kết nối cũ và báo mỗi timeout chưa giải quyết một lần. |
| Khung ứng dụng | `AppShell` điều phối màn hình; `NavigationBar`, `ToastOverlay`, `QuestionPreparation` lần lượt quản lý điều hướng, thông báo và tải ảnh trước khi xác nhận sẵn sàng. |
| Đăng nhập | `AuthView` ghép bố cục; `AuthForm` quản lý trường nhập, chế độ đăng nhập/đăng ký và ý định gửi lệnh. |
| Thi đấu | `GameView` điều phối HUD, thời gian và hiệu ứng; `QuestionContent` trình bày câu hỏi/đáp án; `LifelineDock` quản lý trợ giúp và popover. |
| Soạn quiz | `CommunityQuizView` quản lý phiên chỉnh sửa; `ChoiceAnswerEditor` quản lý lựa chọn; `DraftQuestionMapper` chuyển dữ liệu nhập thành payload; `QuestionPreview` hiển thị xem trước. |
| Renderer | `QuestionRenderer.forType` là nơi duy nhất chọn renderer cho cả trận thật và xem trước. |

Các màn hình sảnh, kết quả và lịch sử chia rõ các bước dựng từng phần. Luồng lưu
và xuất bản dùng `SaveAction` cho bước tiếp theo, thay vì chuỗi tự do. Các lớp
đã có trách nhiệm hẹp như xác thực, đánh giá đáp án, ảnh, scheduler, session và
truy vấn lịch sử được giữ trong package nghiệp vụ tương ứng.

## Các nguyên tắc cần giữ khi sửa tiếp

- UI gửi ý định qua `UiCommandSink`; server vẫn quyết định thời gian, điểm và kết quả.
- Các thành phần `MatchRounds`, `MatchResults`, `MatchChat`, `MatchLifelines` chạy
  bên trong khóa của cùng aggregate `Match`. Tác vụ lưu và detach tiếp tục được
  thực hiện sau khi nhả khóa; thứ tự `eventSeq` vẫn do `MatchManager` cấp.
- Dữ liệu đáp án của đối thủ và giải thích chưa công bố không được đưa vào snapshot.
- Giao dịch xuất bản quiz và giao dịch lưu kết quả giữ nguyên ranh giới commit/rollback.
- Không dùng kết quả phản hồi cũ để ghi đè phiên chỉnh sửa mới; lỗi và timeout giữ
  nội dung người dùng đã nhập, đồng thời dừng chuỗi lưu/xuất bản.
- Các lớp helper mới có phạm vi package khi không cần sử dụng từ bên ngoài.
- Maven POM và Java được định dạng nhất quán; không thêm framework hoặc thay phiên bản dependency.

## Một số lớp điều phối trước và sau

Số dòng là chỉ số hỗ trợ tìm điểm tập trung trách nhiệm, không phải tiêu chí duy nhất.

| Lớp | Trước | Sau |
| --- | ---: | ---: |
| `MatchManager` | 1.029 | 403 |
| `CommunityQuizService` | 517 | 83 |
| `GameView` | 1.022 | 668 |
| `AppShell` | 724 | 619 |
| `AuthView` | 226 | 62 |

## Chạy kiểm chứng

Project yêu cầu Java 21. Máy thực hiện đợt này mặc định dùng Java 25, nên lần chạy
đầu dừng ở `BuildSmokeTest`. Script mới tự chọn JDK 21 có sẵn trên macOS khi cần;
trên môi trường khác, cấu hình `JAVA_HOME` tới JDK 21.

```sh
./scripts/verify.sh
```

Để chạy đầy đủ kiểm thử database và giao diện, cấu hình `QUIZ_TEST_DB_URL`,
`QUIZ_TEST_DB_USER`, `QUIZ_TEST_DB_PASSWORD` cho schema riêng kết thúc bằng `_test`.
Áp dụng bốn script SQL theo thứ tự trong hướng dẫn database, rồi chạy:

```sh
./scripts/verify.sh -Pmysql-it -Dquiz.uiEvidence=/duong/dan/tuyet/doi/ui-evidence
```

Windows có thể dùng cùng các tham số với `mvn verify` trên JDK 21.

## Kết quả ngày 29/10/2026

Lần chạy cuối hoàn thành `BUILD SUCCESS`, tổng cộng **331 test**, không có test
lỗi hoặc bị bỏ qua:

| Bộ kiểm thử | Số test | Phạm vi |
| --- | ---: | --- |
| common | 155 | Catalogue và contract giao thức, payload cộng đồng, framing, tài nguyên, Java runtime. |
| server unit | 88 | Xác thực, session, challenge, vòng đời trận, đáp án/điểm, trợ giúp, chat, tái đấu, persistence, media và mạng. |
| server MySQL IT | 34 | Schema/startup, repository, ranking/history, xuất bản quiz, luồng TCP, sảnh, trận 10 vòng, trận đồng thời và database lỗi. |
| client | 54 | Reducer/resync, pending answer, network/timeouts, renderer, màn hình, soạn quiz và bằng chứng giao diện. |

MySQL 8.4.7 được khởi tạo riêng dưới `/tmp/ltm-refactor-mysql`, cổng `13307`, schema
`quiz_refactor_test`. Không sử dụng database vận hành để chạy test.

Năm test hành vi mới kiểm tra:

1. Timeout được báo đúng một lần; phản hồi đã nhận không tạo timeout.
2. Kết nối mới không nhận timeout của kết nối cũ.
3. Đăng nhập/đăng ký kiểm tra đầu vào và xóa mật khẩu sau khi gửi.
4. Thêm/xóa lựa chọn giữ mã chứa dấu phân cách, nội dung và đáp án đang chọn; giới hạn 2–6 lựa chọn.
5. Xuất bản lưu câu trước, rồi metadata, rồi mới xuất bản; lỗi lưu giữ bản nháp và dừng chuỗi thao tác.

Test local về lời mời được chấp nhận đã được cập nhật từ cách tìm `getBottom()` cũ
sang toast và lời mời thật. Test vẫn xác nhận màn hình trận đấu được giữ nguyên.

Bộ giao diện tạo **324 ảnh** ở 1280×800, 1024×720, giao diện sáng và cỡ chữ lớn.
So sánh với mã trước refactor có cùng các thay đổi local: lần chạy cuối có 284 ảnh
trùng hoàn toàn; 40 ảnh còn lại khác trong các vùng hover, đồng hồ và hiệu ứng theo
thời gian. Đã kiểm tra các vùng khác biệt và không thấy thay đổi bố cục. Bộ kiểm thử
cũng kiểm tra nút điều hướng không vượt chiều rộng cửa sổ.

Đã mở client qua `ClientMain` với fixture trong 8 giây: tiến trình kết thúc mã 0,
không có exception runtime; tạo 10 ảnh từ đăng nhập, sảnh, chờ sẵn sàng đến mở câu hỏi.
Đã kiểm tra định dạng Java và `git diff --check`.

## Bằng chứng có thể kiểm tra lại

Các báo cáo JSON và manifest được lưu cùng mã nguồn. Log và ảnh là artifact local
trên máy kiểm chứng; có thể tạo lại bằng lệnh kiểm thử giao diện ở trên.

- [Tổng hợp từng test suite](../../outputs/refactor-2026-10-29/verification-summary.json)
- [Log Maven cuối](../../outputs/refactor-2026-10-29/verification.log)
- [So sánh từng ảnh](../../outputs/refactor-2026-10-29/ui-comparison.json)
- [SHA-256 mã nguồn đã kiểm thử](../../outputs/refactor-2026-10-29/source-manifest.sha256)
- [Ảnh client chạy thật với fixture](../../outputs/refactor-2026-10-29/fixture-smoke/)

Kiểm chứng bao phủ các luồng tự động và giao diện trên macOS với Java 21. Không thay
thế việc kiểm tra riêng gói phát hành trên Windows hoặc kết nối tới VPS đang vận hành.

# Giải thích mã nguồn Quiz Arena theo từng thành viên

Tài liệu học code và luyện trả lời khi bảo vệ bài tập lớn môn Lập trình mạng của Nhóm 7

Ngày đối chiếu mã nguồn 31 tháng 10 năm 2026

Quiz Arena cho hai người thi đấu câu hỏi qua mạng. Để trình bày được dự án, mỗi người cần hiểu dữ liệu đi vào phần mình từ đâu, các bước xử lý chính, kết quả gửi cho ai và cách xử lý khi có lỗi. Tài liệu đi theo đúng bốn câu hỏi đó, sau đó đưa ra tình huống minh họa và câu trả lời mẫu.

Đọc phần tổng quan trước, rồi tập trung vào chương mang tên mình. Khi ôn, hãy mở các tệp được chỉ dẫn, lần theo một thao tác hoàn chỉnh và tự trả lời câu hỏi trước khi đọc đáp án mẫu. Những câu trả lời viết ở ngôi “em” là mẫu diễn đạt để luyện nói; khi trình bày cần nói đúng công việc mình thực sự đã thực hiện.

## 1 Bức tranh chung để cả nhóm cùng hiểu

### 1.1 Bốn phần ghép thành một ứng dụng

| Thành viên | Trách nhiệm chính theo phân công | Kết quả phần việc |
| --- | --- | --- |
| Khuất Quang Minh | Giao thức chung và mạng phía server | Nhận đúng thông điệp, xác định đúng người gửi, quản lý online và lời mời |
| Đỗ Quang Tuấn | Logic thi đấu phía server | Quản lý lượt chơi, nhận đáp án, chấm điểm, kết thúc và tái đấu |
| Nguyễn Hữu Việt | Client JavaFX, mạng phía client và trạng thái giao diện | Hiển thị màn hình, nhận thao tác, gửi yêu cầu và cập nhật theo server |
| Trần Lê Hoàng Phúc | Database, tài khoản, câu hỏi, lưu kết quả và truy vấn | Lưu dữ liệu nhất quán, xác thực, cung cấp câu hỏi, ranking và lịch sử |

Hãy tưởng tượng một cuộc thi: Việt làm bàn điều khiển cho người chơi; Minh làm đường liên lạc và bàn tiếp nhận; Tuấn làm trọng tài; Phúc quản lý sổ tài khoản, ngân hàng đề và biên bản kết quả. Tất cả cùng tham gia một thao tác, nhưng mỗi phần có trách nhiệm riêng.

Phân công mô tả trách nhiệm module. Các lớp phục vụ chức năng mở rộng như quiz cộng đồng và ảnh được giải thích tại chương của module tương ứng, kèm điểm phối hợp. Có thể cùng sửa một tệp khi tích hợp; không nên suy ra tác giả từng dòng chỉ từ nơi đặt tệp.

### 1.2 Cấu trúc dự án và các công nghệ xuất hiện trong code

Dự án dùng Java 21, JavaFX 21.0.6 cho giao diện, Jackson 2.18.3 để chuyển JSON, JDBC với MySQL Connector/J 8.4.0 để truy vấn MySQL, JUnit 5.11.4 cho kiểm thử và Maven để build. Đây là các phiên bản khai báo trong pom.xml của dự án.

Dự án dùng một module Maven với một pom.xml ở thư mục gốc. Toàn bộ Java nằm dưới src/main/java/vn/edu/nhom7/quiz/, chia thành ba package: common chứa hợp đồng dùng chung; server chứa nghiệp vụ và dữ liệu; client chứa JavaFX. common không còn là artifact Maven riêng. Client và server vẫn chạy trong hai tiến trình riêng, giao tiếp qua TCP; client không kết nối trực tiếp MySQL.

```text
Client A và Client B
       | TCP mang các thông điệp JSON
       v
Server mạng và điều phối
       | gọi dịch vụ tài khoản hoặc logic trận
       v
Repository qua JDBC -> MySQL
MediaService -> thư mục ảnh trên server
```

Các đường dẫn trong tài liệu tính từ thư mục gốc LTM. Với Java, ký hiệu C/ là src/main/java/vn/edu/nhom7/quiz/common/; S/ là src/main/java/vn/edu/nhom7/quiz/server/; V/ là src/main/java/vn/edu/nhom7/quiz/client/. Ví dụ S/match/MatchManager.java là lớp quản lý trận ở server. Số dòng đi kèm giúp tìm đúng vị trí trong bản code được đối chiếu.

### 1.3 Luồng từ đăng nhập đến xem lịch sử

1. NetworkClient kết nối TCP, gửi HELLO. Server kiểm tra phiên bản và trả HELLO_ACK.
2. AuthView gửi LOGIN. MessageRouter gọi AuthService trên luồng dịch vụ; AuthService kiểm tra mật khẩu với dữ liệu tài khoản.
3. SessionRegistry gắn userId vào connectionId. Client nhận LOGIN_RESULT và chuyển sang Lobby.
4. Người chơi gửi CHALLENGE. ChallengeManager giữ chỗ hai người; khi đối thủ chấp nhận, server lấy câu hỏi và tạo Match.
5. Hai người gửi MATCH_READY. Mỗi câu lại có QUESTION_READY để báo đã chuẩn bị xong, kể cả ảnh.
6. Server mở thời gian trả lời. Client gửi ANSWER; server kiểm tra, chấm và xác nhận đã nhận.
7. Khi cả hai trả lời hoặc hết giờ, server công bố đáp án, điểm câu và bảng điểm.
8. Hết trận, MatchResults tạo MatchSummary. PersistenceService lưu qua JdbcMatchRepository; client có thể xem kết quả trước khi việc lưu hoàn tất.
9. Khi lưu thành công, dữ liệu history có thể truy vấn; thống kê ranking được cập nhật nếu đây là trận tính hạng hợp lệ.

### 1.4 Các quy tắc phải nhớ đúng

Quiz hệ thống có 10 câu với cơ cấu 4 SINGLE_CHOICE, 2 MULTIPLE_CHOICE, 2 TRUE_FALSE và 2 SHORT_ANSWER. Quiz cộng đồng có từ 1 đến 50 câu, hỗ trợ thêm REORDER, RANGE và PINPOINT. Vì vậy “dự án có 7 dạng câu” đúng, nhưng “mọi trận dùng đủ 7 dạng” không đúng.

Bốn dạng cũ có 15 giây trả lời; ba dạng tương tác có 30 giây. Server quyết định đúng sai, deadline và điểm. Client chỉ gửi lựa chọn và trình bày dữ liệu nhận được. Quiz cộng đồng được lưu lịch sử nhưng không cộng thống kê xếp hạng toàn cục.

ACK của đáp án có nghĩa “server đã nhận hợp lệ”, chưa có nghĩa “đáp án đúng”. Đáp án chuẩn chỉ được công bố khi lượt chuyển sang REVEAL. Snapshot trận đang chạy hỗ trợ đồng bộ lại trạng thái; không đồng nghĩa với khôi phục trận sau khi socket bị mất.

## 2 Khuất Quang Minh phụ trách giao thức và mạng server

### 2.1 Mục tiêu của phần Minh

Phần này biến một luồng byte từ mạng thành lệnh có người gửi xác định, rồi chuyển lệnh đến đúng dịch vụ. Đầu vào là socket và byte; đầu ra là Envelope đã kiểm tra, SessionContext của người gửi và các phản hồi được xếp hàng về đúng kết nối.

Nếu phần mạng sai, hai thông điệp có thể bị đọc dính nhau, một người có thể bị gắn nhầm phiên hoặc thao tác từ hai luồng có thể làm người chơi vào hai trận. Vì vậy điểm cần giải thích sâu là đóng khung TCP, chia luồng, xác thực phiên và thay đổi trạng thái có đồng bộ.

### 2.2 Bản đồ các tệp cần mở

| Nhóm tệp | Vai trò dễ hiểu |
| --- | --- |
| C/net/FrameCodec.java và FrameDecoder.java | Ghi độ dài trước JSON và gom đủ byte để đọc một thông điệp |
| C/protocol/Envelope.java và MessageType.java | Quy định vỏ thông điệp và tên các loại lệnh hoặc sự kiện |
| C/protocol/ProtocolCodec.java, PayloadRegistry.java, PayloadValidator.java | Chuyển JSON và kiểm tra loại, trường, kiểu dữ liệu, chiều gửi |
| S/network/SocketServer.java | Mở cổng và tiếp nhận các kết nối |
| S/network/Connection.java và ConnectionManager.java | Giữ socket, hàng đợi, trạng thái sống và dọn kết nối |
| S/network/ConnectionReader.java và ConnectionWriter.java | Một phía chỉ đọc và một phía chỉ ghi trên mỗi kết nối |
| S/network/MessageRouter.java và HandshakeValidator.java | Bắt tay, lấy phiên và điều hướng đến dịch vụ |
| S/network/HeartbeatService.java, RateLimiter.java, RequestCache.java | Phát hiện kết nối im lặng, giới hạn yêu cầu và xử lý gửi lặp |
| S/session/SessionRegistry.java và OnlinePresenceService.java | Ánh xạ tài khoản với kết nối, trạng thái và danh sách online |
| S/challenge/Challenge.java và ChallengeManager.java | Vòng đời lời mời và giữ chỗ hai người |
| S/ServerRuntime.java | Nối các dịch vụ và callback dọn kết nối với nhau |

### 2.3 TCP là luồng byte nên phải tự chia thông điệp

TCP đưa byte đến theo thứ tự, nhưng một lần read không nhất thiết ứng với một lần send. Giả sử client gửi hai JSON liên tiếp: server có thể đọc được nửa JSON đầu ở lần thứ nhất và phần còn lại cộng JSON thứ hai ở lần thứ hai. Chỉ gọi read rồi parse ngay sẽ dễ báo JSON lỗi.

FrameCodec.encode đặt 4 byte độ dài trước phần JSON UTF 8. ByteBuffer mặc định dùng big endian, tức byte quan trọng nhất của số độ dài đứng trước. Giới hạn phần JSON là từ 1 đến 65.536 byte; bốn byte đầu không tính vào giới hạn đó. Độ dài là số byte UTF 8, không phải số ký tự tiếng Việt.

Mã thực tế trong FrameCodec.encode

```java
return ByteBuffer.allocate(PREFIX_BYTES + jsonUtf8.length)
    .putInt(jsonUtf8.length)
    .put(jsonUtf8)
    .array();
```

allocate cấp chỗ cho cả độ dài và nội dung; putInt ghi độ dài; put ghi nội dung; array lấy mảng byte để gửi. Nếu JSON có 100 byte, khung truyền đi có 104 byte.

FrameDecoder.accept giữ prefixRead và bodyRead qua nhiều lần gọi. Khi đủ 4 byte đầu, nó biết cần đọc bao nhiêu byte thân. Chỉ khi thân đủ độ dài, nó mới trả ra một frame hoàn chỉnh. Nếu một lần read chứa nhiều frame, kết quả là danh sách nhiều phần thân JSON. Khung chưa hoàn chỉnh quá 10 giây hoặc EOF nằm giữa khung là lỗi.

Đối chiếu [C/net/FrameCodec.java dòng 12](../src/main/java/vn/edu/nhom7/quiz/common/net/FrameCodec.java#L12).
Đối chiếu [C/net/FrameDecoder.java dòng 13](../src/main/java/vn/edu/nhom7/quiz/common/net/FrameDecoder.java#L13).

### 2.4 Envelope và kiểm tra giao thức

Envelope có protocolVersion, type, requestId, matchId, roundId, eventSeq và payload. Có thể ví như phong bì: type là loại việc, requestId là mã hồ sơ, matchId là phòng thi, roundId là lượt thi, payload là nội dung.

requestId do client tạo để ghép yêu cầu với phản hồi. matchId xác định trận; roundId chống đáp án từ câu cũ đi vào câu mới. eventSeq do server tăng cho các sự kiện của trận. Client không được tự gửi eventSeq hay tự khai một trường userId ở vỏ để nhận mình là người khác.

ProtocolCodec.decode kiểm tra UTF 8, JSON phải là object, không có trường lạ, đúng protocolVersion và MessageType, UUID đúng định dạng, payload đúng lớp khai báo. Jackson được cấu hình không tự ép kiểu vô tội vạ. Ví dụ boolean true khác chuỗi "true". PayloadRegistry cho biết loại thông điệp đi từ client hay từ server; router chặn client gửi sự kiện vốn chỉ server được phát.

HandshakeValidator yêu cầu HELLO là thông điệp đầu, đến trong 5 giây và khớp protocol 1, asset pack "1", client version "3.0". Phiên bản client là điều kiện tương thích của bản code này, không phải phiên bản Java.

Đối chiếu [C/protocol/Envelope.java dòng 6](../src/main/java/vn/edu/nhom7/quiz/common/protocol/Envelope.java#L6).
Đối chiếu [C/protocol/ProtocolCodec.java dòng 37](../src/main/java/vn/edu/nhom7/quiz/common/protocol/ProtocolCodec.java#L37).
Đối chiếu [S/network/HandshakeValidator.java dòng 6](../src/main/java/vn/edu/nhom7/quiz/server/network/HandshakeValidator.java#L6).

### 2.5 Tại sao phải tách reader và writer

SocketServer.start bind địa chỉ và cổng, tạo luồng accept, rồi lập lịch bảo trì mỗi 250 ms. Mỗi kết nối được giao một tác vụ ConnectionReader và một tác vụ ConnectionWriter. Code có pool 32 reader, pool 32 writer và chặn nhận quá 32 kết nối đang quản lý.

Reader đọc byte, giải frame, decode JSON và chuyển Envelope cho router. Writer là nơi duy nhất ghi OutputStream. Các dịch vụ khác chỉ gọi tryEnqueue. Nhờ vậy hai luồng nghiệp vụ không cùng ghi xen kẽ byte vào socket.

Mỗi kết nối có hàng đợi điều khiển 256 frame và hàng đợi ảnh 8 frame. takeOutbound lấy thông điệp điều khiển trước, rồi mới lấy ảnh. Semaphore báo có dữ liệu cần gửi. tryEnqueue dùng offer nên không chờ hàng đợi có chỗ trống. Nếu queue đầy, kết nối bị đánh dấu slow để bảo trì đóng và giải phóng; một client đọc chậm không giữ luồng trận khác chờ ghi mạng.

Tách pool dịch vụ cũng quan trọng: kiểm tra mật khẩu PBKDF2 và truy vấn JDBC có thể lâu. MessageRouter.submit chuyển chúng sang executor dịch vụ. ServerRuntime cấu hình 4 worker dịch vụ với queue 128; tác vụ ảnh có 2 worker riêng và queue 64. Khi không nhận thêm tác vụ được, server trả lỗi bận thay vì tạo thêm vô hạn.

Đối chiếu [S/network/SocketServer.java dòng 30](../src/main/java/vn/edu/nhom7/quiz/server/network/SocketServer.java#L30).
Đối chiếu [S/network/Connection.java dòng 16](../src/main/java/vn/edu/nhom7/quiz/server/network/Connection.java#L16).
Đối chiếu [S/network/ConnectionManager.java dòng 39](../src/main/java/vn/edu/nhom7/quiz/server/network/ConnectionManager.java#L39).
Đối chiếu [S/ServerRuntime.java dòng 26](../src/main/java/vn/edu/nhom7/quiz/server/ServerRuntime.java#L26).

### 2.6 Router lấy danh tính ở đâu

Sau bắt tay, route xử lý PING, REGISTER và LOGIN theo nhánh riêng. Những lệnh cần đăng nhập phải có sessions.authenticated(connection.id). SessionContext chứa connectionId cùng userId đã xác thực. Router đưa đối tượng này cho ChallengeManager, MatchManager hoặc nhóm query.

Ví dụ ANSWER được đưa vào matches.handle(caller, request). Minh đảm bảo caller là người đã đăng nhập; Tuấn tiếp tục kiểm tra caller có thuộc trận và còn gắn với trận không. AuthService của Phúc xác minh mật khẩu, còn SessionRegistry của Minh quản lý việc tài khoản có đang online hay không.

Khi tác vụ bất đồng bộ hoàn tất, router kiểm tra kết nối còn sống và phiên vẫn đúng trước khi trả query. Nhờ đó kết quả truy vấn cũ không tự động được áp vào phiên khác. Với đăng nhập, code cũng kiểm tra lại trạng thái kết nối quanh bước gắn phiên.

Đối chiếu [S/network/MessageRouter.java dòng 63](../src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java#L63).
Đối chiếu [S/network/MessageRouter.java dòng 115](../src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java#L115).

### 2.7 Session và chống tranh chấp lời mời

SessionRegistry giữ các map kết nối, tài khoản, lời mời đang giữ chỗ và trận. status trả BUSY khi đã có trận, CHALLENGING khi đang giữ chỗ và FREE khi không có hai loại ràng buộc trên. Tài khoản ở màn Ranking hay History vẫn có thể FREE.

reservePair là thao tác synchronized kiểm tra và giữ chỗ cả hai người cùng lúc. Nếu A mời B đồng thời C cũng mời B, chỉ lần giữ chỗ hợp lệ đầu tiên thành công. Không tách “kiểm tra rảnh” và “đánh dấu bận” thành hai thao tác độc lập vì có thể cả hai bên đều thấy B rảnh.

ChallengeManager tạo lời mời có hạn 20 giây. Người gửi được hủy; người nhận được chấp nhận hoặc từ chối. Khi nhận accept, trạng thái chuyển sang CREATING_MATCH và bắt đầu tải đề trên executor. Giai đoạn tạo có thời hạn 5 giây. Việc lấy dữ liệu không nằm trong đoạn khóa quản lý lời mời dài hạn.

Sau khi có đề, code đăng ký Match đã chuẩn bị, kiểm tra lại hai phiên, chuyển giữ chỗ thành gắn trận bằng attachReservedPair rồi activate. Nếu lời mời đã bị hủy, hết hạn hoặc một người mất kết nối, kết quả tải về muộn không được làm sống lại trận không còn hợp lệ.

Đối chiếu [S/session/SessionRegistry.java dòng 56](../src/main/java/vn/edu/nhom7/quiz/server/session/SessionRegistry.java#L56).
Đối chiếu [S/challenge/ChallengeManager.java dòng 163](../src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java#L163).

### 2.8 Heartbeat và yêu cầu bị gửi lại

Client gửi PING định kỳ 10 giây; server trả PONG. HeartbeatService đóng kết nối nếu không có frame hợp lệ trong 30 giây, chưa bắt tay sau 5 giây hoặc đã bị đánh dấu slow. Heartbeat giúp phát hiện trường hợp mất mạng mà socket chưa báo EOF ngay.

ConnectionManager.close dùng compareAndSet để việc dọn chỉ xảy ra một lần dù reader, writer và heartbeat cùng phát hiện lỗi. Nó đóng socket, hủy tác vụ, xóa cache và gọi callback của ServerRuntime để dọn phiên, lời mời, trận và cập nhật online.

RequestCache nhớ tối đa 100 request trong khoảng 60 giây cho mỗi kết nối. Cùng requestId và cùng nội dung thì có thể trả phản hồi đã nhớ hoặc không chạy lại khi còn xử lý. Nếu dùng lại requestId nhưng đổi nội dung, server báo REQUEST_ID_REUSED. Nội dung được nhận diện bằng HMAC SHA 256. Đây là kiểm soát gửi lặp, không phải mã hóa toàn bộ đường truyền TCP.

Đối chiếu [S/network/HeartbeatService.java dòng 10](../src/main/java/vn/edu/nhom7/quiz/server/network/HeartbeatService.java#L10).
Đối chiếu [S/network/ConnectionManager.java dòng 68](../src/main/java/vn/edu/nhom7/quiz/server/network/ConnectionManager.java#L68).
Đối chiếu [S/network/RequestCache.java dòng 16](../src/main/java/vn/edu/nhom7/quiz/server/network/RequestCache.java#L16).

### 2.9 Một tình huống để lần code

A đăng nhập rồi mời B. Lần theo NetworkClient gửi CHALLENGE, ConnectionReader nhận frame, ProtocolCodec giải mã, MessageRouter lấy phiên A, ChallengeManager.invite kiểm tra quiz và giữ chỗ. A nhận CHALLENGE_ACK; B nhận CHALLENGE_RECEIVED. Khi B chấp nhận, ChallengeManager.create gọi repository lấy câu và MatchManager chuẩn bị trận.

Để minh họa xử lý tranh chấp, thử mời cùng B từ một người C trong khi lời mời A đang chờ. Kết quả mong đợi là C nhận lỗi bận; B không đồng thời được giữ chỗ cho hai lời mời. Test liên quan nằm ở FrameDecoderTest, ProtocolContractTest, SocketServerTest, RequestCacheTest, HeartbeatTest, OnlinePresenceTest và ChallengeManagerTest.

### 2.10 Câu hỏi và câu trả lời mẫu cho Minh

**M01 Vì sao dùng TCP mà vẫn phải có FrameCodec?**

Em dùng TCP để có kết nối hai chiều và byte đến đúng thứ tự. TCP không giữ ranh giới giữa các lần gửi nên em đặt 4 byte độ dài trước JSON. Decoder tích lũy đến khi đủ thân thông điệp rồi mới parse; vì vậy đọc thiếu hay đọc nhiều thông điệp một lần đều xử lý được.

**M02 Nếu read nhận nửa JSON thì sao?**

FrameDecoder giữ số byte đã đọc trong prefixRead và bodyRead, chờ read sau bổ sung. Nó chưa gọi parser với JSON thiếu. Nếu không hoàn thành trong 10 giây hoặc gặp EOF giữa khung, kết nối bị xử lý lỗi.

**M03 Vì sao không cho mọi service ghi thẳng vào socket?**

Nhiều luồng ghi cùng OutputStream có thể làm byte bị xen kẽ. Em để mỗi kết nối có một writer duy nhất; các service chỉ enqueue. Ngoài ra enqueue không chờ ghi mạng nên một người chơi đọc chậm không kéo theo toàn bộ xử lý trận.

**M04 Một client gửi userId của người khác có giả mạo được không?**

Router không lấy danh tính người gửi từ payload. Em tra SessionRegistry bằng connectionId đã đăng nhập để tạo caller. Ví dụ targetUserId trong lời mời chỉ là người nhận, còn người gửi luôn là caller.userId. MatchManager còn kiểm tra membership trước khi nhận lệnh trận.

**M05 Hai người cùng mời một đối thủ thì xử lý thế nào?**

reservePair vừa kiểm tra vừa giữ chỗ hai tài khoản trong một phương thức synchronized. Lần đến sau thấy đối thủ đã CHALLENGING nên bị từ chối. Cách này tránh hai luồng cùng đọc FREE rồi cùng tạo lời mời thành công.

**M06 Khi query MySQL lâu thì có khóa reader không?**

Những việc như login, hash mật khẩu và query được đưa sang executor dịch vụ. Reader tiếp tục nhận và phân tích thông điệp. Executor có queue giới hạn; nếu đầy thì trả SERVER_BUSY. Khi trả kết quả, code kiểm tra phiên và kết nối còn đúng.

**M07 requestId và eventSeq khác nhau thế nào?**

requestId nhận diện một yêu cầu và phản hồi tương ứng. eventSeq là thứ tự sự kiện server phát trong một trận. ACK gửi riêng vẫn dùng bộ đếm đó nên người còn lại có thể thấy số nhảy; không được tự kết luận cứ thiếu một số là mất gói.

**M08 Nếu client gửi lại LOGIN hoặc CHALLENGE cùng requestId?**

RequestCache nhận diện nội dung đã chuẩn hóa. Nếu cùng requestId và cùng nội dung thì không thực hiện lại tùy trạng thái và có thể trả phản hồi cũ. Nếu nội dung khác thì trả REQUEST_ID_REUSED. Với đáp án còn có tầng chống gửi lặp ở logic trận.

**M09 Queue gửi đầy thì chuyện gì xảy ra?**

offer trả false và ConnectionManager đánh dấu slow. HeartbeatService sẽ dọn kết nối; đường phát sự kiện trận còn có xử lý khi enqueue thất bại. Em không cho queue tăng vô hạn vì một socket đọc chậm có thể làm hết bộ nhớ server.

**M10 Heartbeat có đảm bảo khôi phục trận khi mạng trở lại không?**

Không. Heartbeat giúp phát hiện kết nối không còn phản hồi. Bản code này dọn phiên khi disconnect và xử lý rời trận theo logic gameplay. Người dùng có thể đăng nhập lại, nhưng không có cam kết nối lại vào đúng trận đang chạy trước đó.

**M11 Mật khẩu đã hash trong database thì TCP đã được mã hóa chưa?**

Chưa. Hash bảo vệ cách lưu mật khẩu; HMAC trong request cache phục vụ nhận diện yêu cầu lặp. SocketServer hiện dùng ServerSocket và Socket thông thường, nên không thể gọi đây là TLS. Nếu cần mã hóa đường truyền phải triển khai thêm lớp TLS hoặc kênh bảo vệ tương ứng.

**M12 Em trình bày phần mình trong khoảng một phút thế nào?**

Em phụ trách giao thức và mạng server. Dữ liệu đi theo chuỗi TCP byte, frame độ dài, Envelope JSON, kiểm tra phiên rồi router chuyển đến service. Mỗi kết nối có reader và writer riêng, hàng đợi giới hạn, heartbeat và cơ chế dọn một lần. Em dùng giữ chỗ nguyên tử cho hai người khi thách đấu, còn chấm điểm và lưu dữ liệu được gọi qua module của Tuấn và Phúc.

## 3 Đỗ Quang Tuấn phụ trách logic trận đấu

### 3.1 Mục tiêu của phần Tuấn

Tuấn biến các lệnh đã xác thực thành diễn biến thi đấu đúng luật. Đầu vào là hai người chơi, bộ câu hỏi riêng tư của server và các lệnh ready, answer, lifeline, chat, exit hoặc rematch. Đầu ra là các sự kiện diễn biến trận, điểm và một MatchSummary để lưu.

Điểm trung tâm là server làm trọng tài. Client không gửi số điểm để server tin theo, không tự quyết định đáp án đúng và không tự chuyển câu chỉ vì đồng hồ giao diện đã về 0.

### 3.2 Bản đồ các tệp cần mở

| Tệp trong S/match | Vai trò |
| --- | --- |
| Match.java và MatchPhase.java | Dữ liệu của một trận và tập các trạng thái có thể có |
| MatchManager.java và MatchGateway.java | Tiếp nhận lệnh, kiểm tra thành viên, khóa trận và phát sự kiện |
| MatchRounds.java | Chuẩn bị câu, nhận đáp án, kết thúc câu và bảng điểm |
| AnswerEvaluator.java và ShortAnswerNormalizer.java | Kiểm tra kiểu đáp án và chấm đúng sai |
| ScoreCalculator.java và MatchRanker.java | Tính điểm theo thời gian và thứ hạng trong trận |
| MatchResults.java | Hoàn tất, bỏ cuộc, hủy và phiên xem kết quả |
| RematchCoordinator.java | Đề nghị tái đấu và chuyển hai người sang trận mới |
| MatchLifelines.java | Quyền x2, bỏ một đáp án và 50 phần trăm |
| MatchChat.java và ChatService.java | Logic chat đang được gọi từ MatchManager nằm ở MatchChat; đọc thêm lớp tiện ích khi cần |
| GameClock.java, SystemGameClock.java, GameScheduler.java, ExecutorGameScheduler.java, PhaseToken.java | Đo thời gian và lập lịch chuyển trạng thái |
| MatchPayloads.java và MatchSnapshotFactory.java | Tạo dữ liệu công khai và snapshot theo trạng thái |
| MatchWireBudget.java | Kiểm tra dữ liệu trận có thể nằm trong giới hạn frame |

S/domain/QuestionSnapshot.java, RoundSummary.java, StoredAnswerOutcome.java và MatchSummary.java là các mô hình đầu vào hoặc đầu ra dùng chung giữa gameplay và lưu trữ. Việc tạo dữ liệu kết quả thuộc gameplay; việc ghi chúng xuống MySQL thuộc persistence.

### 3.3 Một Match giữ những gì

Match có id, quiz, danh sách hai phiên, câu hỏi và profile tại thời điểm tạo; các tập ready, questionReady và attached; map accepted; điểm, số câu đúng và danh sách kết quả các lượt. Nó còn giữ phase, roundId, deadline, version, sequence và terminal summary.

accepted được xóa khi sang câu mới nên mỗi câu có vùng nhận đáp án riêng. attached cho biết ai còn gắn với phiên trận hoặc kết quả; việc tài khoản vẫn online không tự động có nghĩa còn được chat trong trận cũ.

Constructor kiểm tra hai người khác nhau, số câu đúng yêu cầu, questionId không trùng. Quiz hệ thống còn phải đúng cơ cấu 4/2/2/2. MatchManager.prepare kiểm tra dữ liệu câu, snapshot profile và giới hạn kích thước thông điệp trước khi tạo trận.

Mỗi Match có ReentrantLock riêng. Hai lệnh cùng tác động một trận phải nối tiếp trong khóa đó; hai trận khác nhau có thể xử lý đồng thời. Không dùng một khóa toàn cục cho toàn bộ trận đấu.

Đối chiếu [S/match/Match.java dòng 50](../src/main/java/vn/edu/nhom7/quiz/server/match/Match.java#L50).
Đối chiếu [S/match/MatchManager.java dòng 53](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L53).

### 3.4 Máy trạng thái của trận

Máy trạng thái là cách biểu diễn “đang ở bước nào thì được làm việc gì”. Chẳng hạn ANSWER chỉ có hiệu lực ở ANSWERING; gửi đáp án trong thời gian đang tải câu không thể được tính điểm.

| Trạng thái server | Ý nghĩa | Thời lượng hoặc điều kiện đi tiếp |
| --- | --- | --- |
| WAITING_READY | Chờ cả hai sẵn sàng vào trận | Tối đa 30 giây |
| MATCH_COUNTDOWN | Đếm ngược mở trận | 3 giây |
| QUESTION_PREPARING | Gửi câu và chờ client chuẩn bị tài nguyên | Tối đa 30 giây |
| ROUND_COUNTDOWN | Đếm ngược mở quyền trả lời | 2 giây |
| ANSWERING | Nhận đáp án của câu hiện tại | 15 hoặc 30 giây; có thể kết thúc sớm khi cả hai đã trả lời |
| REVEAL | Công bố đáp án và kết quả câu | 2 giây |
| LEADERBOARD | Hiển thị điểm sau lượt | 3 giây |
| RESULT | Kết quả cuối, còn có thể chat hoặc tái đấu theo điều kiện | Tối đa 60 giây |
| CANCELLED hoặc CLOSED | Trận bị hủy trước lúc bắt đầu hoặc phiên đã đóng | Không tiếp nhận lệnh chơi mới |

Sau MATCH_READY của cả hai, trận vào MATCH_COUNTDOWN. Hết đếm ngược thì nextQuestion đặt roundId mới, xóa đáp án cũ và gửi QUESTION công khai. Cả hai QUESTION_READY mới mở ROUND_COUNTDOWN. Hết đếm ngược này server phát QUESTION_OPEN và bắt đầu cửa sổ chấm thời gian.

Nếu client không ready đúng hạn ở WAITING_READY hoặc QUESTION_PREPARING, server xử lý theo nhánh CLIENT_NOT_READY. Kết thúc trận trước startedAt và kết thúc sau startedAt có hành vi lưu khác nhau, nên không gộp mọi trường hợp thành “mất kết nối là hòa”.

Đối chiếu [S/match/MatchManager.java dòng 145](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L145).
Đối chiếu [S/match/MatchRounds.java dòng 19](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L19).
Đối chiếu [S/match/MatchRounds.java dòng 57](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L57).

### 3.5 Timer cũ không được chuyển nhầm trạng thái mới

Khi chuyển phase, MatchManager.phase hủy timer trước, tăng version, đặt deadline bằng nanoTime và tạo PhaseToken gồm matchId, roundId, phase, version. Callback khi chạy lại lấy khóa và so token với trạng thái hiện tại.

Mã thực tế ở callback của MatchManager.phase

```java
() -> {
  if (match.isCurrent(token)) rounds.timeout(match);
}
```

Ví dụ cả hai đã trả lời ở giây thứ 4, câu chuyển REVEAL nhưng callback hết 15 giây cũ vẫn có thể đang chờ chạy. Token của nó mang ANSWERING và version cũ nên bị bỏ qua. Chỉ gọi cancel là chưa đủ để diễn đạt đầy đủ cơ chế chống callback muộn.

nanoTime dùng để đo khoảng thời gian vì không phụ thuộc việc chỉnh giờ hệ thống. instant hoặc epoch millisecond dùng cho timestamp gửi client và lưu dữ liệu. endMillis chuyển thời gian còn lại theo deadline đơn điệu sang mốc hiển thị cho client.

Đối chiếu [S/match/MatchManager.java dòng 263](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L263).
Đối chiếu [S/match/Match.java dòng 118](../src/main/java/vn/edu/nhom7/quiz/server/match/Match.java#L118).

### 3.6 Nhận một ANSWER theo thứ tự nào

1. MatchManager tìm matchId và lấy khóa trận.
2. member kiểm tra đúng SessionContext trong danh sách ban đầu và user vẫn còn attached.
3. MatchRounds.answer lấy thời điểm xử lý bằng clock.nanoTime và requireCurrentRound kiểm tra roundId cùng questionId.
4. Nếu đã có accepted cho user: cùng requestId thì gửi lại ACK; khác requestId thì báo ALREADY_ANSWERED.
5. Kiểm tra phase là ANSWERING và thời điểm không vượt deadline.
6. AnswerEvaluator xác minh kiểu và chấm; ScoreCalculator tính điểm theo elapsed, có nhân hai nếu quyền x2 đang có hiệu lực.
7. Lưu Accepted, gửi ACK riêng và ANSWER_STATUS cho người trong trận. Chỉ trạng thái đã trả lời được công bố ở bước này.
8. Nếu đã có hai đáp án, closeRound ngay; nếu chưa, chờ người còn lại hoặc timer hết hạn.

Thời gian dùng tính điểm được lấy khi lệnh được xử lý bên trong khóa trận. Không phải timestamp người dùng tự khai và cũng không phải timestamp phần cứng card mạng. Do đó không nên nói hệ thống đã loại bỏ hoàn toàn độ trễ mạng.

Đối chiếu [S/match/MatchManager.java dòng 258](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L258).
Đối chiếu [S/match/Match.java dòng 131](../src/main/java/vn/edu/nhom7/quiz/server/match/Match.java#L131).
Đối chiếu [S/match/MatchRounds.java dòng 80](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L80).

### 3.7 Chấm bảy loại câu hỏi

| Loại | Dữ liệu ANSWER.answer | Luật chấm |
| --- | --- | --- |
| SINGLE_CHOICE | Chuỗi ID như "A" | ID nằm trong options và bằng key |
| MULTIPLE_CHOICE | Mảng ID như ["A","C"] | Tập bằng chính xác tập key; thứ tự không quan trọng |
| TRUE_FALSE | Boolean true hoặc false | Phải là boolean JSON và bằng key |
| SHORT_ANSWER | Chuỗi từ 1 đến 120 code point | Chuẩn hóa rồi khớp một câu trả lời được chấp nhận |
| REORDER | Mảng tất cả ID theo thứ tự | Không thiếu hoặc trùng ID và thứ tự bằng key |
| RANGE | Số JSON như 50 | Đúng thang, đúng bước, nằm trong khoảng được chấp nhận |
| PINPOINT | Object có x và y trong khoảng 0 đến 1 | Điểm nằm trong vùng chữ nhật chuẩn, tính cả biên |

Với MULTIPLE_CHOICE, key A và C thì C,A vẫn đúng; chỉ A hoặc A,C,D đều sai. Phần tử trùng hoặc ID không tồn tại bị xem là dữ liệu không hợp lệ, không chỉ là chọn sai. Phải phân biệt một đáp án đúng kiểu nhưng sai kiến thức với một payload sai cấu trúc.

ShortAnswerNormalizer đưa chữ về Unicode NFC, gom các khoảng trắng thành một khoảng, bỏ khoảng trắng đầu cuối và lowercase bằng Locale.ROOT. Nó giữ dấu tiếng Việt. “  HÀ   NỘI ” có thể khớp “Hà Nội”, còn “Ha Noi” chỉ đúng nếu được khai là biến thể chấp nhận riêng.

RANGE dùng BigDecimal để tránh sai số khi so bước thập phân. Với min 0, max 100, step 5, khoảng đúng 45 đến 55: gửi 50 hợp lệ và đúng; 40 hợp lệ nhưng sai; 52 sai bước nên bị từ chối. PublicQuestion.config chỉ gửi thang và bước, không gửi acceptedMin, acceptedMax trước reveal.

PINPOINT dùng tọa độ chuẩn hóa để cùng một vị trí có ý nghĩa trên các cửa sổ khác kích thước. Nếu vùng đúng x=0,2; y=0,3; width=0,1; height=0,2 thì điểm (0,25; 0,4) đúng. Vùng đúng vẫn là dữ liệu server giữ kín trước reveal. REORDER yêu cầu toàn bộ thứ tự đúng và không có điểm một phần.

Đối chiếu [S/match/AnswerEvaluator.java dòng 13](../src/main/java/vn/edu/nhom7/quiz/server/match/AnswerEvaluator.java#L13).
Đối chiếu [S/match/ShortAnswerNormalizer.java dòng 9](../src/main/java/vn/edu/nhom7/quiz/server/match/ShortAnswerNormalizer.java#L9).
Đối chiếu [C/protocol/InteractiveQuestions.java dòng 12](../src/main/java/vn/edu/nhom7/quiz/common/protocol/InteractiveQuestions.java#L12).

### 3.8 Tính điểm và hoàn tất một lượt

ScoreCalculator chia cửa sổ trả lời thành ba phần. Đúng ở một phần ba đầu được 3 điểm; đúng ở một phần ba tiếp theo được 2; phần cuối được 1. Sai được 0. Việc hết giờ không có đáp án được tạo outcome TIMEOUT trong MatchRounds, không cần giả tạo một đáp án sai.

| Cửa sổ | Được 3 điểm | Được 2 điểm | Được 1 điểm |
| --- | --- | --- | --- |
| 15 giây | 0 đến 5 giây, gồm mốc 5 | Sau 5 đến 10 giây, gồm mốc 10 | Sau 10 đến 15 giây, gồm mốc 15 |
| 30 giây | 0 đến 10 giây, gồm mốc 10 | Sau 10 đến 20 giây, gồm mốc 20 | Sau 20 đến 30 giây, gồm mốc 30 |

Mã thực tế trong ScoreCalculator.calculate

```java
return !correct
    ? 0
    : elapsedNanos <= windowNanos / 3 ? 3 : elapsedNanos <= windowNanos / 3 * 2 ? 2 : 1;
```

Ví dụ câu 15 giây: A đúng ở 4 giây được 3 điểm, B đúng ở 8 giây được 2 điểm. Nếu A dùng DOUBLE_POINTS ở câu đó thì A nhận 6. Quyền x2 chỉ dùng một lần trong trận nên điểm tối đa một trận hệ thống 10 câu là 33, không phải 60.

closeRound chỉ chạy khi còn ANSWERING, tổng hợp outcomes và cộng điểm đúng một lần, rồi lưu RoundSummary. Nó phát QUESTION_RESULT chứa đáp án chuẩn và chuyển REVEAL. Sau đó leaderboard phát scoreBefore, earnedPoints, totalScore, rank và previousRank để client trình bày. Hết bảng điểm cuối mới đi đến kết quả trận.

### 3.9 Quyền trợ giúp và chat

Mỗi người có ba quyền: DOUBLE_POINTS, REMOVE_ONE và FIFTY_FIFTY. Mỗi quyền dùng tối đa một lần mỗi trận; mỗi câu dùng tối đa một quyền và phải dùng trước khi đáp án được chấp nhận. REMOVE_ONE và FIFTY_FIFTY chỉ khả dụng cho SINGLE_CHOICE có ít nhất ba phương án. x2 nhân điểm của câu đó, đáp án sai vẫn 0.

MatchLifelines dùng key ở server để chọn các phương án sai được ẩn. Người dùng không tự gửi danh sách muốn bỏ để server tin theo. LIFELINE_RESULT gửi riêng thông tin cần thiết cho người sử dụng; LIFELINE_STATUS cho biết ai đã dùng quyền.

Chat cũng đi qua kiểm tra membership. MatchChat bỏ khoảng trắng đầu cuối, cho từ 1 đến 300 code point và tối đa 5 tin trong cửa sổ 10 giây cho mỗi người. requestId lặp được xử lý để không tạo tin nhắn mới. Ở RESULT, chat chỉ còn hợp lệ khi cả hai vẫn attached. Gửi chat không đặt lại timer câu hỏi.

Đối chiếu [S/match/MatchLifelines.java dòng 26](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchLifelines.java#L26).
Đối chiếu [S/match/MatchChat.java dòng 18](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchChat.java#L18).

### 3.10 Kết thúc và bàn giao cho Phúc

MatchResults.finish chặn tạo terminal summary lần hai. COMPLETED so tổng điểm để thắng hoặc hòa; FORFEIT xử người rời là thua; ABORTED không có người thắng. Nếu cả hai socket đều không còn sống thì code có nhánh BOTH_DISCONNECTED và kết quả NONE.

Nếu trận chưa có startedAt, phần này hủy trận, giải phóng giữ chỗ lưu và dọn assignment. Nếu đã bắt đầu mà một câu chưa công bố xong, có thể ghi RoundSummary chưa revealed và các outcome ABANDONED với 0 điểm cho lượt chưa hoàn tất; không cộng điểm tạm của lượt đó.

Sau khi đóng gói MatchSummary, code lên lịch RESULT 60 giây và đưa sink.submit ra ngoài khóa qua manager.after. Đây là điểm bàn giao Tuấn sang Phúc. Client nhận MATCH_RESULT với persistenceStatus PENDING; việc MySQL lưu thành công hay thất bại sẽ được báo riêng.

RematchCoordinator chỉ nhận tái đấu khi ở RESULT, cả hai còn attached và còn kết nối. Đề nghị tối đa 20 giây, không quá hạn phiên kết quả. Hai người cùng bấm tái đấu có thể được hiểu là đồng ý cùng một đề nghị. Nó tạo matchId mới, tải bộ câu theo repository và dùng generation để callback cũ không chuyển nhầm assignment. Cùng quiz không có nghĩa bảo đảm mọi câu đều khác trận trước.

Đối chiếu [S/match/MatchResults.java dòng 18](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchResults.java#L18).
Đối chiếu [S/match/MatchManager.java dòng 241](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L241).
Đối chiếu [S/match/RematchCoordinator.java dòng 29](../src/main/java/vn/edu/nhom7/quiz/server/match/RematchCoordinator.java#L29).

### 3.11 Cách lần code và tự kiểm tra

Mở MatchManager.handle rồi đi vào case ANSWER, MatchRounds.answer, AnswerEvaluator.evaluate, ScoreCalculator.calculate, closeRound và cuối cùng MatchResults.finish. Đây là đường đi ngắn để giải thích phần cốt lõi mà không bị cuốn vào toàn bộ giao diện.

Các test hữu ích: ScoreCalculatorTest cho mốc nano giây và chuẩn hóa; AnswerEvaluatorTest cho kiểu đáp án; InteractiveQuestionsTest cho dạng mới; MatchEngineTest cho lifecycle; MatchTerminationTest cho kết thúc; RematchCoordinatorTest cho tái đấu. Khi học timer, đọc cả FakeGameClock, FakeGameScheduler và ManualGameScheduler trong test support để thấy có thể đẩy thời gian mà không đợi 15 giây thật.

### 3.12 Câu hỏi và câu trả lời mẫu cho Tuấn

**T01 Vì sao phải dùng máy trạng thái?**

Em cần biết lệnh nào hợp lệ ở thời điểm nào. Trận đi từ chờ ready, chuẩn bị câu, đếm ngược, trả lời, reveal đến bảng điểm và kết quả. Nếu không kiểm tra phase, một đáp án có thể đến khi câu chưa mở hoặc một callback cũ có thể làm kết thúc câu mới.

**T02 Làm sao không tính hai đáp án của cùng người?**

Em giữ accepted theo userId trong từng lượt dưới khóa trận. Có đáp án trước đó thì cùng requestId được trả ACK cũ, còn yêu cầu mới bị ALREADY_ANSWERED. roundId và questionId còn được kiểm tra để không nhận lệnh của câu trước.

**T03 Nếu hai người gửi đáp án cùng lúc thì có mất điểm không?**

Hai lệnh của cùng Match dùng chung ReentrantLock nên cập nhật tuần tự. Mỗi lệnh ghi một Accepted; khi map có hai người thì closeRound một lần. Các trận khác dùng khóa khác nên vẫn chạy đồng thời.

**T04 Vì sao vừa cancel timer vừa cần PhaseToken?**

cancel không nên được xem là bảo đảm callback cũ chưa thể đang chạy hoặc đang chờ khóa. Token chứa trận, lượt, phase và version; callback chỉ làm việc khi còn khớp. Khi câu kết thúc sớm, version đổi nên timer trả lời cũ không còn tác dụng.

**T05 Tại sao dùng nanoTime thay currentTimeMillis để chấm?**

Em cần khoảng thời gian đã trôi qua, không cần giờ lịch. nanoTime phù hợp với việc đo khoảng và không bị việc chỉnh đồng hồ hệ thống làm lệch như giờ lịch. Thời gian gửi đáp án do server đo lúc xử lý dưới khóa, không nhận elapsed từ client.

**T06 Chọn nhiều đáp án có được điểm một phần không?**

Không. Em chuyển danh sách hợp lệ thành tập rồi so với tập đáp án chuẩn. Đúng đủ và không thừa mới đúng; thứ tự phần tử không quan trọng. Nếu lặp ID hoặc dùng ID lạ thì dữ liệu bị từ chối là INVALID_ANSWER.

**T07 Trả lời ngắn có bỏ dấu tiếng Việt không?**

Không tự bỏ dấu. Code chuẩn hóa Unicode NFC, chữ thường và khoảng trắng. “HÀ NỘI” và “Hà Nội” có thể khớp; “Ha Noi” cần một alias riêng nếu bộ câu muốn chấp nhận.

**T08 Điểm 3 2 1 có áp dụng câu 30 giây không?**

Có, nhưng ngưỡng đổi theo độ dài cửa sổ. Với câu 15 giây là mốc 5 và 10 giây; với câu 30 giây là mốc 10 và 20. Đúng ở mốc đầu vẫn nhận mức điểm cao hơn. Quyền x2 nhân mức điểm thực của câu đó.

**T09 Đã nhận ANSWER_ACK thì có được tô xanh đáp án ngay không?**

Không. ACK xác nhận đáp án được nhận, gồm thời gian và request được chấp nhận. Đúng sai và key chỉ được công bố ở QUESTION_RESULT sau khi đóng lượt. Client có thể khóa vùng trả lời và hiển thị đã gửi trước đó.

**T10 Nếu một người thoát giữa câu thì người đang dẫn chắc chắn thắng theo điểm à?**

Nếu là FORFEIT thì người rời bị xử thua theo luật, không chỉ so điểm đang có. Nếu cả hai disconnect thì có nhánh ABORTED. Một lượt chưa hoàn tất không được cộng như lượt đã reveal; kết quả cần phân biệt lý do kết thúc với tổng điểm.

**T11 Tại sao nhận kết quả rồi mà ranking chưa thay đổi?**

Gameplay phát kết quả trước với trạng thái PENDING. Sau đó persistence commit thành công mới cập nhật profile và phát invalidation cho ranking. Quiz cộng đồng có ranked=false nên lưu lịch sử nhưng không làm thay đổi thống kê hạng.

**T12 Em tóm tắt phần mình thế nào?**

Em phụ trách trọng tài của trận: giữ phase và timer, kiểm tra thành viên cùng lượt, chấm bảy dạng đáp án và tính điểm theo thời gian server. Em dùng khóa riêng từng trận và token cho timer cũ. Khi hết trận em tạo summary bất biến để Phúc lưu; sự kiện theo thứ tự được chuyển qua lớp mạng của Minh đến UI của Việt.

## 4 Nguyễn Hữu Việt phụ trách ứng dụng client JavaFX

### 4.1 Mục tiêu của phần Việt

Phần Việt là ứng dụng người chơi trực tiếp nhìn và bấm. Đầu vào gồm thao tác chuột, bàn phím và sự kiện server; đầu ra là yêu cầu gửi lên mạng và giao diện phản ánh đúng trạng thái được server xác nhận.

Một UI đẹp chưa đủ nếu đọc socket làm cửa sổ đứng, bấm hai lần gửi hai đáp án hoặc kết quả trận cũ chèn lên trận mới. Vì vậy cần hiểu ba lớp network, state và ui cùng cách chuyển việc sang đúng luồng.

### 4.2 Bản đồ các tệp cần mở

| Nhóm tệp trong V/ | Trách nhiệm |
| --- | --- |
| ClientMain.java và DesktopLauncher.java | Khởi động, nối network với store và giao diện; đầu vào cho ứng dụng desktop |
| network/NetworkClient.java | Kết nối TCP, gửi HELLO, reader, writer, heartbeat và báo disconnect |
| network/RequestTracker.java và RequestTimeoutMonitor.java | Theo dõi yêu cầu chưa phản hồi và báo quá hạn |
| network/ServerTimeEstimator.java | Ước lượng chênh lệch đồng hồ để hiển thị countdown |
| state/ClientState.java, ClientStore.java, ClientStateReducer.java, MatchEventData.java | Trạng thái client và các phép chuyển trạng thái |
| ui/AppShell.java, NavigationBar.java, UiCommandSink.java, CommandGateway.java | Điều hướng, khung ứng dụng và lối gửi lệnh thống nhất |
| ui/AuthView.java, AuthForm.java, LobbyView.java, QuizDetailView.java, ChallengeDialog.java | Đăng nhập, sảnh và lời mời |
| ui/WaitingView.java, GameView.java, ResultView.java | Các màn hình trong và sau trận |
| ui/QuestionRenderer.java và các lớp Renderer | Hiển thị từng kiểu câu hỏi và tạo đáp án đúng kiểu |
| ui/QuestionPreparation.java, QuestionContent.java, RevealPanel.java, LeaderboardPanel.java, ReviewCard.java | Chuẩn bị câu, kết quả câu, điểm và xem lại |
| ui/ChatPanel.java, LifelineDock.java, RankingView.java, HistoryView.java | Chat, trợ giúp, ranking và lịch sử |
| ui/CommunityQuizView.java, DraftQuestionMapper.java, ChoiceAnswerEditor.java, InteractiveAnswerEditor.java, QuestionPreview.java | Soạn và xem trước quiz cộng đồng |
| assets/AssetLoader.java, RemoteMedia.java và ui/ImageTarget.java | Tải ảnh và xử lý tương tác trên ảnh |
| ui/Theme.java, Effects.java, Sound.java, SettingsOverlay.java, ToastOverlay.java, PresentationText.java | Chủ đề, hiệu ứng, âm thanh, cài đặt và thông báo |

### 4.3 Ba luồng công việc dễ hình dung

JavaFX Application Thread giống người phụ trách vẽ cửa sổ và nhận thao tác. Nếu bắt người này đứng đợi socket.read hoặc chờ ảnh tải xong, các nút và animation cũng bị đứng. NetworkClient dùng executor cho kết nối, reader và writer; ClientStore có một executor đơn để chuyển state nối tiếp nhau; mọi cập nhật scene graph được chuyển sang JavaFX bằng Platform.runLater.

Luồng dữ liệu chính là server event → NetworkClient → ClientStore → ClientStateReducer → state mới → AppShell.render. Theo hướng ngược lại, nút UI → UiCommandSink hoặc CommandGateway → NetworkClient.send → outbound queue → writer → socket.

Mã thực tế tại điểm đăng ký nghe state trong ClientMain

```java
store.subscribe(
    s ->
        Platform.runLater(
            () -> {
              shell.render(s);
```

Đây là phần đầu của callback, không phải một đoạn chương trình độc lập. s là trạng thái mới; runLater đặt việc render vào hàng đợi của JavaFX. Nó không biến truy vấn mạng thành nhanh hơn, nhưng giúp việc vẽ chạy đúng luồng.

Đối chiếu [V/ClientMain.java dòng 62](../src/main/java/vn/edu/nhom7/quiz/client/ClientMain.java#L62).
Đối chiếu [V/state/ClientStore.java dòng 49](../src/main/java/vn/edu/nhom7/quiz/client/state/ClientStore.java#L49).

### 4.4 Kết nối và gửi dữ liệu không chặn cửa sổ

NetworkClient.connect chạy bất đồng bộ bằng CompletableFuture trên executor io. Nó tạo Socket với timeout kết nối 5 giây, read timeout 1 giây, bật TCP_NODELAY, đưa HELLO với clientVersion "3.0" và assetPackVersion "1" vào queue rồi khởi chạy reader, writer.

connect hoàn tất việc thiết lập socket chưa có nghĩa bắt tay đã xong. isConnected chỉ true khi open và handshake đều true. send chặn gửi các lệnh trước handshake, ngoại trừ HELLO. Reader kiểm tra HELLO_ACK đầu tiên, cập nhật thời điểm frame hợp lệ và đẩy event cho callback.

Writer duy nhất lấy Envelope từ queue 256 phần tử, ProtocolCodec mã hóa thành JSON, FrameCodec đóng khung và ghi OutputStream. send trả boolean cho biết có đưa được vào queue không; nó không bảo đảm server đã nhận. Vì thế cần RequestTracker và phản hồi của server.

Heartbeat mỗi 10 giây gửi PING, PONG cập nhật ước lượng giờ server. Không nhận frame hợp lệ đủ lâu sẽ disconnect. Khi đóng, code hủy heartbeat, đóng socket, hủy writer và xóa queue. Các vòng đọc ghi còn kiểm tra socket hiện tại có phải socket của tác vụ đó không để giảm việc tác vụ của kết nối cũ can thiệp kết nối mới.

Đối chiếu [V/network/NetworkClient.java dòng 45](../src/main/java/vn/edu/nhom7/quiz/client/network/NetworkClient.java#L45).
Đối chiếu [V/network/NetworkClient.java dòng 98](../src/main/java/vn/edu/nhom7/quiz/client/network/NetworkClient.java#L98).

### 4.5 State store và reducer làm gì

ClientState là record giữ profile, activeMatchId, resultMatchId, roundId, phase, dữ liệu event, trạng thái đáp án, chat và trạng thái lưu của các trận. Constructor sao chép các map thành bản không cho thay đổi trực tiếp ở cấu trúc map. Việc chuyển state đi qua ClientStore, không để các view tùy ý sửa map chung.

Reducer nhận state hiện tại cùng event, tạo state kế tiếp. Ví dụ LOGIN_RESULT đưa phase về LOBBY; QUESTION đưa sang QUESTION_PREPARE và xóa cờ pending, accepted; QUESTION_OPEN đổi thành OPEN; ANSWER_ACK đặt accepted=true; QUESTION_RESULT đổi sang REVEAL; MATCH_RESULT đổi sang RESULT.

Server gọi trạng thái trả lời là ANSWERING, client dùng tên phase OPEN. Khi nhận MATCH_SNAPSHOT, reducer chuyển tên server sang tên UI tương ứng. Vì vậy không nên tìm chuỗi OPEN trong MatchPhase rồi cho rằng server thiếu trạng thái.

Reducer bỏ eventSeq không lớn hơn số đã nhận của cùng match. Nó còn bỏ event của match khác đang không hoạt động, ngoại trừ các nhánh cần xử lý riêng như MATCH_START và trạng thái lưu. ONLINE_LIST có revision riêng để bản online cũ không ghi đè bản mới. Số eventSeq có thể nhảy do ACK gửi riêng nên reducer không đòi số liên tiếp tuyệt đối.

Đối chiếu [V/state/ClientStateReducer.java dòng 22](../src/main/java/vn/edu/nhom7/quiz/client/state/ClientStateReducer.java#L22).
Đối chiếu [V/state/ClientState.java dòng 46](../src/main/java/vn/edu/nhom7/quiz/client/state/ClientState.java#L46).

### 4.6 Bấm trả lời và ba trạng thái cần phân biệt

Trước khi gửi, client cần phase OPEN, chưa ownPending và chưa accepted. canAnswer tổng hợp ba điều kiện này. CommandGateway còn giữ pendingRound theo roundId để chặn bấm rất nhanh trước khi state executor kịp cập nhật.

Mã thực tế trong CommandGateway.send

```java
if (!store.state().canAnswer() || java.util.Objects.equals(pendingRound, roundId))
  throw new IllegalStateException("Answer already pending or round closed");
pendingRound = roundId;
store.markAnswerPending();
```

ownPending nghĩa là đã yêu cầu gửi nhưng chưa biết server chấp nhận; accepted nghĩa là đã nhận ACK; đúng hoặc sai chỉ có sau QUESTION_RESULT. Nếu network.send thất bại ngay, gateway hủy pending để giao diện không treo ở trạng thái “đang gửi” của một yêu cầu chưa ra mạng.

Gateway tạo UUID mới cho request, đưa matchId, roundId và payload vào Envelope, rồi đăng ký RequestTracker. ANSWER và LOGOUT có mốc theo dõi 2 giây; phần lớn yêu cầu khác 5 giây. QUESTION_READY không được đăng ký như yêu cầu thông thường vì cơ chế phản hồi của nó khác. TimeoutMonitor báo mỗi timeout chưa giải quyết một lần và xóa tracker khi connectionEpoch đổi.

Không nên tự động gửi lại một đáp án mới chỉ vì chưa thấy ACK. Timeout có thể xảy ra khi đáp án đã đến server. Snapshot cho phép hỏi lại trạng thái hiện tại để biết có acceptedAnswer hay chưa.

Đối chiếu [V/ui/CommandGateway.java dòng 22](../src/main/java/vn/edu/nhom7/quiz/client/ui/CommandGateway.java#L22).
Đối chiếu [V/network/RequestTracker.java dòng 19](../src/main/java/vn/edu/nhom7/quiz/client/network/RequestTracker.java#L19).
Đối chiếu [V/network/RequestTimeoutMonitor.java dòng 21](../src/main/java/vn/edu/nhom7/quiz/client/network/RequestTimeoutMonitor.java#L21).

### 4.7 Hiển thị bảy dạng câu bằng một hợp đồng

QuestionRenderer là interface có view, bind, setControlsEnabled và showResult. forType chọn renderer phù hợp; GameView không cần chứa toàn bộ logic nhập liệu của bảy loại câu.

| Renderer | Cách tạo đáp án | Điều cần giải thích |
| --- | --- | --- |
| SingleChoiceRenderer | TextNode chứa ID | Chỉ gửi ID; nhãn hiển thị có thể thay đổi |
| MultipleChoiceRenderer | ArrayNode chứa các ID | Chọn nhiều rồi khóa; server vẫn kiểm tra tập |
| TrueFalseRenderer | BooleanNode | Gửi boolean thật, không gửi chữ Đúng hoặc Sai |
| ShortAnswerRenderer | TextNode | Kiểm tra nhập liệu cơ bản rồi để server chuẩn hóa và chấm |
| ReorderRenderer | Mảng ID theo thứ tự sau khi sắp xếp | Người chơi kéo thả hoặc dùng nút điều chỉnh rồi khóa |
| RangeRenderer | DecimalNode | Chỉ cho gửi khi người chơi đã tương tác và số hợp lệ |
| PinpointRenderer | Object có tọa độ x và y | Chỉ cho khóa khi ảnh đã sẵn sàng và có điểm chọn |

AnswerIntentFactory tạo typed JSON cho bốn dạng cũ. Các renderer tương tác tạo mảng, số hoặc object phù hợp dạng mới. UI kiểm tra trước để người chơi sửa dễ; server vẫn phải kiểm tra vì client có thể bị sửa hoặc có lỗi.

RangeRenderer có cờ touched để thanh trượt đặt sẵn ở giữa không bị hiểu thành người dùng đã chọn. Cờ syncing tránh vòng lặp khi text cập nhật slider rồi slider lại cập nhật text. Giá trị số dùng BigDecimal và kiểm tra đúng bước trước khi tạo DecimalNode.

ImageTarget tính tọa độ trên vùng ảnh thật, không tính khoảng trống viền do giữ tỷ lệ. Công thức x chuẩn hóa là (vị trí chuột trừ mép trái ảnh) chia chiều rộng ảnh. Khi resize, vùng và ghim được vẽ lại theo tỷ lệ. Cách này giữ ý nghĩa vị trí giữa màn hình nhỏ và lớn.

Đối chiếu [V/ui/QuestionRenderer.java dòng 10](../src/main/java/vn/edu/nhom7/quiz/client/ui/QuestionRenderer.java#L10).
Đối chiếu [V/ui/AnswerIntentFactory.java dòng 10](../src/main/java/vn/edu/nhom7/quiz/client/ui/AnswerIntentFactory.java#L10).
Đối chiếu [V/ui/RangeRenderer.java dòng 17](../src/main/java/vn/edu/nhom7/quiz/client/ui/RangeRenderer.java#L17).
Đối chiếu [V/ui/ImageTarget.java dòng 142](../src/main/java/vn/edu/nhom7/quiz/client/ui/ImageTarget.java#L142).

### 4.8 Tại sao cần báo tải câu xong

Khi nhận QUESTION, QuestionPreparation kiểm tra questionAssetId và tải ảnh nếu có. Sau khi hoàn tất, nó quay lại JavaFX thread và kiểm tra matchId cùng roundId hiện tại vẫn khớp với lần bắt đầu tải. Chỉ khi còn đúng câu mới gửi QUESTION_READY. Nếu tải lỗi, nó báo lỗi và không giả vờ đã sẵn sàng.

Ví dụ ảnh của câu 3 tải chậm, trong lúc đó trận bị đóng hoặc UI đã đổi phiên. Callback muộn phải bị bỏ qua; nếu không, client có thể gửi ready cho một lượt không còn hiệu lực. readyRounds cũng tránh gửi lại ready nhiều lần do render nhiều lần.

AssetLoader cung cấp lối tải tài nguyên. Với ảnh do người dùng tải lên, RemoteMedia xử lý chuỗi request lấy từng chunk, ráp dữ liệu và kiểm tra hash trước khi dùng. Không nên giải mã ảnh lớn hay chờ I/O ngay trên thao tác vẽ.

Đối chiếu [V/ui/QuestionPreparation.java dòng 28](../src/main/java/vn/edu/nhom7/quiz/client/ui/QuestionPreparation.java#L28).
Đối chiếu [V/assets/RemoteMedia.java dòng 109](../src/main/java/vn/edu/nhom7/quiz/client/assets/RemoteMedia.java#L109).

### 4.9 Countdown và hiển thị điểm

ServerTimeEstimator nhận sentMs, receivedMs và serverMs từ PONG, tính RTT là thời gian khứ hồi và offset là chênh lệch giờ server với trung điểm gửi nhận. Nó giữ tối đa 5 mẫu rồi lấy offset từ mẫu RTT nhỏ nhất. Countdown dùng deadline của server trừ giờ client đã hiệu chỉnh, giới hạn trong khoảng 0 đến duration.

Đây là ước lượng để đồng hồ giao diện bám thời gian server hơn, không bảo đảm đồng bộ tuyệt đối. Server vẫn quyết định một đáp án có kịp hay không. Không dùng việc animation chạy chậm để xin thêm thời gian từ server.

GameView render vùng câu, trạng thái đã trả lời, lifeline, reveal và leaderboard theo ClientState. LeaderboardPanel có thể animate scoreBefore đến totalScore nhưng điểm cuối lấy từ event. Effects và Sound tạo phản hồi; Theme và SettingsOverlay cho đổi giao diện hoặc giảm chuyển động. Các hiệu ứng không thay luật chấm.

Đối chiếu [V/network/ServerTimeEstimator.java dòng 16](../src/main/java/vn/edu/nhom7/quiz/client/network/ServerTimeEstimator.java#L16).
Đối chiếu [V/ui/GameView.java dòng 418](../src/main/java/vn/edu/nhom7/quiz/client/ui/GameView.java#L418).

### 4.10 Các màn hình ngoài lượt trả lời

AuthView và AuthForm thu thông tin tài khoản, gửi REGISTER hoặc LOGIN rồi hiển thị phản hồi. LobbyView hiển thị quiz cùng danh sách online; QuizDetailView giúp chọn nội dung trước khi mời; ChallengeDialog nhận thao tác chấp nhận, từ chối hoặc hủy. WaitingView gửi sẵn sàng vào trận.

ResultView hiển thị thắng, thua hoặc hòa cùng trạng thái lưu, rematch và xem lại. RankingView và HistoryView yêu cầu dữ liệu từ server theo trang; ReviewCard dùng dữ liệu câu đã lưu hoặc live review để giải thích đáp án. Không lấy riêng ngân hàng answer key từ database trên client.

CommunityQuizView quản lý danh sách quiz của tôi, sửa metadata, lưu từng câu, preview và publish qua AUTHOR_REQUEST. ChoiceAnswerEditor phụ trách dạng lựa chọn; InteractiveAnswerEditor cùng DraftQuestionMapper chuyển phần nhập liệu thành dữ liệu câu mới. Preview tái sử dụng QuestionRenderer để cách trình bày gần với lúc chơi.

Đối với dữ liệu bản nháp, client có thể đang nhập dở nên trải nghiệm lưu khác publish. Phúc chịu trách nhiệm kiểm tra quyền sở hữu và tính hợp lệ trước khi xuất bản; việc nút publish đang được bật không thay thế cho kiểm tra phía server.

### 4.11 Một kịch bản tự lần code

Chọn một câu SINGLE_CHOICE, bấm đáp án, theo dõi renderer → CommandGateway.send → NetworkClient.send. Ở chiều về, lần ANSWER_ACK → callback ClientMain → store.accept → reducer → AppShell.render. Tiếp tục đợi QUESTION_RESULT để thấy lúc nào mới xuất hiện đúng sai.

Sau đó thử RANGE mà chưa thao tác: nút khóa chưa được phép gửi. Thử PINPOINT khi ảnh chưa sẵn sàng: không có điểm hợp lệ để gửi. Cuối cùng mở History để thấy một view gửi query thay vì tự đọc MySQL.

Test liên quan: NetworkClientTest, RequestTrackerTest, RequestTimeoutMonitorTest, ServerTimeEstimatorTest, AnswerPendingTest, ClientStateReducerTest, ResyncStateTest, AnswerIntentFactoryTest, InteractiveViewsTest và RemoteMediaTest. Các test này là điểm đọc và tự kiểm tra trong repository, không thay thế một lượt demo ứng dụng thật.

### 4.12 Câu hỏi và câu trả lời mẫu cho Việt

**V01 Vì sao đọc socket trên JavaFX thread làm UI treo?**

read có thể đợi mạng trong khi JavaFX thread còn phải xử lý click và vẽ. Em tách reader, writer ra executor nền. Khi có dữ liệu cần cập nhật giao diện, em dùng Platform.runLater để đưa phần vẽ trở lại JavaFX thread.

**V02 NetworkClient.send trả true có nghĩa server nhận rồi không?**

Chưa. Nó cho biết lệnh đã được đưa vào hàng đợi khi kết nối và handshake cho phép. Writer còn phải gửi, mạng còn phải truyền và server còn phải kiểm tra. Em theo dõi requestId và ACK hoặc ERROR để biết kết quả xử lý.

**V03 Vì sao cần store và reducer thay vì sửa từng Label khi nhận event?**

Em muốn trạng thái được chuyển có thứ tự tại một nơi. Reducer tạo state mới từ state cũ và event, sau đó UI render theo state. Cách này dễ kiểm tra phase, chống event cũ và viết test hơn việc mỗi callback sửa rải rác nhiều control.

**V04 Nếu bấm hai lần trước khi ACK về thì sao?**

CommandGateway kiểm tra canAnswer và pendingRound ngay khi gửi, đồng thời đặt ownPending. Vì vậy lần bấm thứ hai bị chặn ngay cả khi state executor chưa kịp cập nhật. Server vẫn có kiểm tra chống đáp án lặp nên hai phía cùng bảo vệ tính đúng.

**V05 ownPending và accepted khác nhau thế nào?**

ownPending là client đang chờ kết quả nhận yêu cầu. accepted là server đã ACK chấp nhận đáp án. Hai cờ này chưa nói đáp án đúng hay sai; chỉ QUESTION_RESULT mới cho dữ liệu chấm để hiển thị kết quả.

**V06 Vì sao không lấy số giây cục bộ đếm lùi từ 15?**

Đồng hồ và thời điểm nhận của mỗi client có thể khác nhau. Em dùng deadline server và offset ước lượng từ PING PONG để hiển thị gần thực tế hơn. Server vẫn dùng thời gian riêng để quyết định chấm, nên countdown ở UI chỉ là phần trình bày.

**V07 Snapshot có giúp quay lại trận sau disconnect không?**

Snapshot giúp đồng bộ trạng thái hiện có khi phiên vẫn hợp lệ, chẳng hạn chưa rõ đáp án đã được nhận chưa. Bản hiện tại không nối lại trận sau disconnect thật. AppShell thông báo cần kết nối và đăng nhập lại để chơi các trận mới.

**V08 Vì sao tách mỗi loại câu thành renderer?**

Các loại câu có cách nhập khác nhau nhưng đều cần bind câu, bật tắt control và showResult. Em đặt chúng sau cùng interface QuestionRenderer. GameView chọn renderer theo loại nên không phải viết bảy màn game hoàn toàn riêng biệt.

**V09 Ghim ảnh trên cửa sổ khác kích thước có lệch đáp án không?**

Em gửi tọa độ theo tỷ lệ 0 đến 1 của vùng ảnh thật. ImageTarget loại trừ phần trống quanh ảnh và vẽ lại ghim theo kích thước mới. Server chấm điểm đó trên vùng chuẩn hóa nên không phụ thuộc số pixel của cửa sổ.

**V10 Tại sao RANGE không tự gửi vị trí giữa slider?**

Vị trí ban đầu do UI đặt chưa phải lựa chọn của người chơi. Em dùng touched, yêu cầu nhập hoặc tương tác rồi mới bật khóa. Em cũng kiểm tra thang và bước bằng BigDecimal để không gửi một số sai cấu hình.

**V11 Nếu ảnh cũ tải xong sau khi đã đổi câu thì sao?**

QuestionPreparation so lại matchId và roundId trước khi gửi ready. Nếu không còn khớp thì bỏ callback đó. Nhờ vậy tác vụ bất đồng bộ của câu cũ không làm trạng thái câu hiện tại bị thay đổi sai.

**V12 Em tóm tắt phần mình thế nào?**

Em phụ trách ứng dụng JavaFX và mạng client. UI gửi ý định qua CommandGateway; NetworkClient gửi nền; event về đi qua store và reducer rồi mới render trên JavaFX thread. Em làm các màn hình, renderer bảy loại, pending ACK, tải ảnh, countdown, chat, kết quả, ranking, history và phần soạn quiz; luật điểm vẫn theo server.

## 5 Trần Lê Hoàng Phúc phụ trách dữ liệu và tài khoản

### 5.1 Mục tiêu của phần Phúc

Phúc quản lý dữ liệu cần tồn tại sau khi chương trình tắt: tài khoản, đề, phiên bản quiz, ảnh và kết quả. Đầu vào là thông tin đăng ký hoặc đăng nhập, yêu cầu lấy đề, summary do gameplay tạo và query của người dùng. Đầu ra là profile, bộ câu hợp lệ, xác nhận lưu hoặc dữ liệu ranking và history.

Ba vấn đề cần nắm nhất là bảo vệ dữ liệu tài khoản, không lưu nửa trận và không cộng thống kê hai lần khi thử lưu lại. Với quiz cộng đồng còn phải tách bản nháp đang sửa khỏi bản đã xuất bản cho người khác chơi.

### 5.2 Bản đồ các tệp cần mở

| Nhóm tệp | Trách nhiệm |
| --- | --- |
| database/001_schema.sql và 002_seed.sql | Khởi tạo bảng cơ sở và dữ liệu mẫu |
| database/003_community_quizzes.sql | Thêm quiz cộng đồng, bản nháp, phiên bản và metadata ảnh |
| database/004_lifelines.sql và 005_interactive_questions.sql | Mở giới hạn điểm và hỗ trợ bảy dạng câu |
| S/db/ConnectionFactory.java, JdbcConnectionFactory.java, SchemaVerifier.java, StartupValidator.java | Mở JDBC và kiểm tra môi trường dữ liệu lúc khởi động |
| S/auth/AccountValidator.java, PasswordHasher.java, AuthService.java, JdbcUserRepository.java | Kiểm tra tài khoản, hash và xác thực |
| S/quiz/QuizRepository.java, JdbcQuizRepository.java, QuestionValidator.java | Lấy đề đúng cấu trúc và kiểm tra câu |
| S/quiz/CommunityQuizService.java, JdbcCommunityQuizRepository.java, CommunityQuizPublisher.java, DraftValidator.java, QuizSql.java | Quản lý quiz của tác giả và xuất bản phiên bản |
| S/persistence/PersistenceService.java, ResultSink.java, DatabaseHealth.java | Nhận yêu cầu lưu, giới hạn số lượng và thử lại khi lỗi |
| S/persistence/JdbcMatchRepository.java, MatchSummaryValidator.java, MatchSummaryProjection.java, SaveResult.java | Ghi transaction và nhận diện kết quả đã lưu |
| S/query/RankingService.java, HistoryService.java, QueryDispatcher.java | Xếp hạng, lịch sử và điều phối truy vấn |
| S/media/MediaService.java và JdbcMediaStore.java | Lưu file ảnh cùng metadata và kiểm tra quyền truy cập |
| S/domain/ | Mô hình dữ liệu trao đổi với gameplay và persistence |

### 5.3 Đọc schema theo mối quan hệ

| Bảng | Một dòng biểu diễn điều gì | Liên kết quan trọng |
| --- | --- | --- |
| USERS | Một tài khoản và thống kê tích lũy | id được các bảng khác tham chiếu |
| CATEGORIES | Một nhóm chủ đề | QUIZZES thuộc một category |
| QUIZZES | Một bộ quiz và nguồn SYSTEM hoặc COMMUNITY | Có owner và public_version_id nếu là quiz cộng đồng |
| QUESTIONS | Một câu đã có cấu trúc và answer key | Thuộc quiz; được phiên bản và lịch sử tham chiếu |
| QUIZ_DRAFTS | Metadata bản đang soạn | Mỗi quiz có thể có bản nháp |
| QUIZ_DRAFT_QUESTIONS | Một câu bản nháp theo vị trí | Khóa gồm quiz_id và position |
| QUIZ_VERSIONS và QUIZ_VERSION_QUESTIONS | Một bản xuất bản và thứ tự các câu của bản đó | Match có thể gắn quiz_version_id |
| MATCHES | Kết quả tổng quát một trận | Hai tài khoản, quiz, điểm, lý do và thời gian |
| MATCH_QUESTIONS | Một lượt của trận cùng snapshot câu | round_id và cặp match_id, round_index |
| MATCH_ANSWERS | Kết quả một người trong một lượt | Unique round_id, user_id |
| MEDIA_ASSETS | Metadata ảnh | owner, MIME, kích thước và SHA 256; file nằm ngoài DB |

Khóa chính nhận diện một dòng. Khóa ngoại giữ liên kết tới dòng tồn tại. Unique(round_id,user_id) là hàng rào dữ liệu ngăn một người có hai kết quả trong cùng lượt. CHECK bảo vệ các giới hạn như điểm, số câu và dạng dữ liệu ở mức bảng.

MATCH_QUESTIONS có question_snapshot_json để giữ nội dung và đáp án tại lúc thi. Nếu chỉ giữ question_id rồi đọc lại ngân hàng, việc người tạo sửa đề có thể làm màn lịch sử không còn đúng với đề đã chơi. Snapshot giữ biên bản của trận; phiên bản xuất bản giữ bộ đề dùng để bắt đầu trận. Đây là hai lớp dữ liệu cho hai mục đích khác nhau.

### 5.4 Vì sao không chỉ đọc 001_schema

001_schema thể hiện nền ban đầu với bốn dạng câu và giới hạn điểm 30. Những migration sau mở rộng cấu trúc. 003 thêm quiz cộng đồng và tổng số lượt 1 đến 50; 004 cho earned_points đến 6 và nới giới hạn tổng điểm; 005 thêm REORDER, RANGE, PINPOINT vào CHECK của question_type và đưa schema lên version 5.

Điểm tối đa theo gameplay có x2 một lần là 3 nhân số câu cộng 3: hệ thống 10 câu tối đa 33; cộng đồng 50 câu tối đa 153. CHECK tổng điểm của bảng được nới đến 153 để chứa phạm vi chung. Không nên diễn giải giới hạn CHECK chung là mọi trận hệ thống đều có thể đạt 153.

Các migration có thay đổi CHECK và ALTER TABLE, không phải script để chạy lặp tùy ý. Khi trình bày cần dùng hướng dẫn thiết lập trong docs/operations/database-setup.md và kiểm tra version thật, thay vì chỉ chạy lại toàn bộ SQL vào database đang có dữ liệu.

Đối chiếu [database/004_lifelines.sql dòng 9](../database/004_lifelines.sql#L9).
Đối chiếu [database/005_interactive_questions.sql dòng 7](../database/005_interactive_questions.sql#L7).

### 5.5 JDBC và PreparedStatement

JdbcConnectionFactory.open dùng DriverManager với dbUrl, user và password từ cấu hình. Nó đặt connectTimeout, socketTimeout 5 giây và múi giờ session UTC. Tài khoản database là cấu hình vận hành, không phải tài khoản người chơi.

Repository dùng try with resources để đóng Connection, PreparedStatement và ResultSet khi ra khỏi khối, kể cả có lỗi. SQL có dấu ? và các lời gọi setString, setLong hoặc setInt truyền tham số. PreparedStatement giữ dữ liệu nhập ở vị trí tham số, tránh cách ghép chuỗi trực tiếp biến nội dung người dùng thành một phần câu lệnh SQL.

ConnectionFactory là interface giúp chỗ sử dụng không phụ thuộc cách lấy kết nối. Code hiện mở kết nối qua DriverManager trong factory này; không nên tự gọi đây là connection pool nếu chưa có lớp pool tương ứng.

Đối chiếu [S/db/JdbcConnectionFactory.java dòng 14](../src/main/java/vn/edu/nhom7/quiz/server/db/JdbcConnectionFactory.java#L14).
Đối chiếu [S/auth/JdbcUserRepository.java dòng 45](../src/main/java/vn/edu/nhom7/quiz/server/auth/JdbcUserRepository.java#L45).

### 5.6 Đăng ký và lưu mật khẩu

AuthService.register gọi AccountValidator trước khi insert. Username được lowercase theo Locale.ROOT và phải khớp 3 đến 24 ký tự ASCII thuộc chữ, số, gạch dưới hoặc chấm. Display name có thể là tiếng Việt, được strip và kiểm tra số code point; mật khẩu dài 8 đến 128 theo điều kiện code; avatar phải có trong danh sách cho phép.

PasswordHasher.hash tạo salt ngẫu nhiên 16 byte bằng SecureRandom và dùng PBKDF2WithHmacSHA256 với 600.000 vòng, khóa đầu ra 256 bit. Chuỗi lưu gồm phiên bản, tên thuật toán, số vòng, salt Base64 và key Base64. Hai tài khoản cùng mật khẩu thường có chuỗi lưu khác nhau vì salt khác.

Mã thực tế ở bước tạo khóa trong PasswordHasher

```java
var spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
try {
  return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
```

Đây là phần đầu khối try; phần finally của hàm gọi spec.clearPassword. PBEKeySpec chứa mật khẩu, salt, số vòng và độ dài khóa. PBKDF2 làm việc đoán mật khẩu tốn công hơn so với một lần hash nhanh. Base64 chỉ là cách biểu diễn byte thành chữ để lưu, không phải thuật toán bảo mật bổ sung.

verify tách chuỗi lưu, kiểm tra định dạng và tham số, tính lại khóa từ mật khẩu nhập cùng salt đã lưu rồi dùng MessageDigest.isEqual so sánh. Không có thao tác giải mã chuỗi hash để lấy mật khẩu cũ.

Đối chiếu [S/auth/AccountValidator.java dòng 8](../src/main/java/vn/edu/nhom7/quiz/server/auth/AccountValidator.java#L8).
Đối chiếu [S/auth/PasswordHasher.java dòng 11](../src/main/java/vn/edu/nhom7/quiz/server/auth/PasswordHasher.java#L11).
Đối chiếu [S/auth/PasswordHasher.java dòng 20](../src/main/java/vn/edu/nhom7/quiz/server/auth/PasswordHasher.java#L20).

### 5.7 Đăng nhập và phân biệt xác thực với online

AuthService.login chuẩn hóa username, kiểm tra đầu vào, tìm tài khoản và verify mật khẩu. Không thấy tài khoản vẫn verify với một dummy hash được tạo sẵn để giảm khác biệt công việc giữa “không có username” và “mật khẩu sai”. Các lỗi thông tin đăng nhập trả chung INVALID_CREDENTIALS. Đây là biện pháp giảm lộ thông tin, không nên nói mọi đường đi có thời gian tuyệt đối bằng nhau.

Nếu đúng, AuthService trả Profile. Sau đó MessageRouter dùng SessionRegistry.tryAuthenticate để kiểm tra tài khoản chưa online ở kết nối khác. Vậy Phúc trả lời “mật khẩu có đúng không”; Minh trả lời “tài khoản được gắn vào kết nối nào và đang online ra sao”.

REGISTER_RESULT không tự biến kết nối thành đã đăng nhập trong router. LOGIN_RESULT mới đi kèm bước gắn session. Khi demo, cần kiểm tra đúng hành vi giao diện thay vì khẳng định cứ đăng ký là đã vào Lobby.

Đối chiếu [S/auth/AuthService.java dòng 34](../src/main/java/vn/edu/nhom7/quiz/server/auth/AuthService.java#L34).
Đối chiếu [S/network/MessageRouter.java dòng 169](../src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java#L169).

### 5.8 Lấy bộ câu hỏi và giữ đáp án riêng tư

JdbcQuizRepository đọc câu đang active, đồng thời kiểm tra quiz và category đang active. Dữ liệu được đổi thành QuestionSnapshot rồi QuestionValidator.validate kiểm tra trước khi đưa vào ngân hàng hợp lệ.

Với quiz hệ thống, REQUIRED quy định cơ cấu 4/2/2/2. Repository gom câu theo loại, shuffle từng nhóm, lấy đủ số cần và shuffle kết quả. Không đủ một nhóm thì trả QUIZ_UNAVAILABLE. Hai người nhận cùng bộ vì Match chứa một danh sách snapshot của trận, không gọi random đề riêng cho mỗi client.

Với quiz cộng đồng, repository đọc các câu của public_version_id, kiểm tra phiên bản vẫn đúng, lấy khóa FOR SHARE trong transaction khi dùng withMatchQuestions để chuẩn bị và kích hoạt. Tùy shuffleQuestions mà giữ hoặc đảo thứ tự. Nó lấy số câu của phiên bản, không ép về 10 câu.

QuestionSnapshot riêng tư chứa answerKeyJson, explanation và ảnh giải thích. MatchPayloads tạo PublicQuestion chỉ gồm dữ liệu cần chơi; InteractiveQuestions.publicConfig chỉ lộ cấu hình công khai. Tách dữ liệu công khai giúp server không gửi nhầm khoảng đúng của RANGE hoặc vùng đúng PINPOINT lúc mở câu.

Đối chiếu [S/quiz/JdbcQuizRepository.java dòng 70](../src/main/java/vn/edu/nhom7/quiz/server/quiz/JdbcQuizRepository.java#L70).
Đối chiếu [S/quiz/JdbcQuizRepository.java dòng 99](../src/main/java/vn/edu/nhom7/quiz/server/quiz/JdbcQuizRepository.java#L99).
Đối chiếu [S/match/MatchPayloads.java dòng 90](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchPayloads.java#L90).

### 5.9 Quiz cộng đồng và phiên bản xuất bản

CommunityQuizService.execute xác định action, mở transaction và điều phối thao tác. Với các action cần quiz cụ thể, repository.owner kiểm tra tác giả trước khi đọc hoặc sửa dữ liệu riêng. LIST chỉ đọc danh sách của người đó; CREATE tạo quiz và nháp; SAVE_META, SAVE_QUESTION, MOVE_QUESTION, DELETE_QUESTION sửa nháp; PUBLISH và UNPUBLISH thay trạng thái công bố.

CommunityQuizPublisher.publish kiểm tra metadata, category, từ 1 đến 50 câu, tính hợp lệ từng câu và wire budget. Nếu hợp lệ, nó tạo QUIZ_VERSIONS, tạo các dòng QUESTIONS mới từ nháp, liên kết qua QUIZ_VERSION_QUESTIONS rồi cập nhật public_version_id. Tất cả nằm trong transaction của service.

Sửa nháp không sửa trực tiếp bản đã công bố. Khi publish lại, người chơi mới có thể dùng bản mới, còn snapshot và liên kết của trận đã có vẫn phục vụ lịch sử đúng nội dung. UNPUBLISH gỡ khả năng chọn công khai, không nên hiểu là xóa mọi lịch sử liên quan.

Đối chiếu [S/quiz/CommunityQuizService.java dòng 23](../src/main/java/vn/edu/nhom7/quiz/server/quiz/CommunityQuizService.java#L23).
Đối chiếu [S/quiz/CommunityQuizPublisher.java dòng 23](../src/main/java/vn/edu/nhom7/quiz/server/quiz/CommunityQuizPublisher.java#L23).

### 5.10 Lưu trận trong một transaction

JdbcMatchRepository.save nhận MatchSummary từ gameplay. Trước khi ghi, MatchSummaryValidator kiểm tra sự nhất quán; repository mở Connection và setAutoCommit(false). Đường ghi chính gồm insertMatch, insertRound cùng snapshot cho mỗi lượt, insertAnswers theo batch và updateRankedProfiles nếu được phép tính hạng.

Mã thực tế của nhóm thao tác ghi

```java
insertMatch(connection, summary);
for (var round : summary.rounds()) {
  insertRound(connection, summary, round);
  faults.at("after_round");
  insertAnswers(connection, round);
  faults.at("after_answers");
}
updateRankedProfiles(connection, summary);
```

faults.at là điểm chèn lỗi phục vụ kiểm thử, không phải thao tác ghi dữ liệu nghiệp vụ. Cả nhóm thao tác dùng cùng connection; save chỉ commit sau khi hoàn thành. Nếu lỗi trước commit, rollback làm trận, đáp án và thống kê không bị lưu một phần.

Ví dụ trận đã insert MATCHES nhưng lỗi sau lượt 3: không thể để history có trận dở trong khi USERS đã cộng điểm. Transaction giữ những thay đổi trong cùng một đơn vị. Khi update profile của hai người, code sắp theo userId rồi SELECT FOR UPDATE để giảm nguy cơ hai transaction khóa hai tài khoản theo thứ tự ngược nhau.

Một trận hệ thống hoàn tất đủ 10 lượt thường có 10 MATCH_QUESTIONS và 20 MATCH_ANSWERS, kể cả người timeout. Trận ngắn hơn, cộng đồng hoặc bị kết thúc giữa chừng có số dòng theo summary thực tế, nên không khẳng định mọi save đều có đúng 20 đáp án.

Đối chiếu [S/persistence/JdbcMatchRepository.java dòng 34](../src/main/java/vn/edu/nhom7/quiz/server/persistence/JdbcMatchRepository.java#L34).
Đối chiếu [S/persistence/JdbcMatchRepository.java dòng 77](../src/main/java/vn/edu/nhom7/quiz/server/persistence/JdbcMatchRepository.java#L77).
Đối chiếu [S/persistence/JdbcMatchRepository.java dòng 171](../src/main/java/vn/edu/nhom7/quiz/server/persistence/JdbcMatchRepository.java#L171).

### 5.11 Thử lưu lại mà không cộng hai lần

Tình huống khó là MySQL đã commit nhưng kết nối lỗi trước khi ứng dụng nhận biết thành công. Nếu cứ insert và cộng USERS lại thì số trận hoặc tổng điểm bị tăng hai lần. Repository dùng matchId làm khóa nhận diện và load kết quả đã tồn tại trước khi ghi.

Nếu bản đã lưu có MatchSummaryProjection bằng summary đang gửi, save trả ALREADY_SAVED mà không update counter lần nữa. Nếu cùng matchId nhưng nội dung xung đột, nó báo lỗi thay vì coi là cùng kết quả. Nhánh xử lý khóa trùng còn tra lại kết quả để xử lý hai lần lưu cạnh tranh.

PersistenceService giữ pending và reservations với tổng sức chứa 100. Trận phải giữ trước một chỗ lưu khi tạo. Nếu database không khỏe hoặc đủ sức chứa, hệ thống ngừng nhận thêm trận mới thay vì hứa lưu vô hạn.

Khi save lỗi, service đánh dấu database unavailable và thử lại sau 1, 2, 4 giây cho các lần đầu; từ lần lỗi thứ tư trở đi theo chu kỳ 30 giây. FAILED có retryable=true không có nghĩa đã ngừng thử vĩnh viễn. Khi thành công, nó xóa pending và báo SAVED. Listener hỏng không được đảo ngược một commit đã xong.

Pending nằm trong RAM. Cơ chế retry xử lý lỗi tạm thời trong lúc tiến trình còn chạy; không phải nhật ký bền vững để tự khôi phục mọi kết quả chưa lưu sau khi server bị tắt đột ngột.

Đối chiếu [S/persistence/JdbcMatchRepository.java dòng 65](../src/main/java/vn/edu/nhom7/quiz/server/persistence/JdbcMatchRepository.java#L65).
Đối chiếu [S/persistence/PersistenceService.java dòng 63](../src/main/java/vn/edu/nhom7/quiz/server/persistence/PersistenceService.java#L63).
Đối chiếu [S/persistence/PersistenceService.java dòng 134](../src/main/java/vn/edu/nhom7/quiz/server/persistence/PersistenceService.java#L134).

### 5.12 Ranking và history khác nhau ra sao

RankingService đọc thống kê tích lũy trong USERS. SQL dùng RANK trên total_score giảm dần rồi wins giảm dần để cho hạng bằng nhau khi hai giá trị này bằng nhau. ROW_NUMBER thêm id tăng dần để thứ tự phân trang ổn định. Vì thế cùng hạng vẫn có thể đứng ở hai vị trí dòng khác nhau; id không phá hòa của rank.

Ranking có giới hạn pageSize đến 50 và trả cả myEntry nếu người yêu cầu không nằm trong trang đang xem. Tổng điểm ở đây là điểm tích lũy đã commit của trận tính hạng, không phải điểm đang animate trên màn game.

HistoryService chỉ liệt kê trận có requester là player1 hoặc player2. detail kiểm tra quyền tham gia trước khi load review. Review trả tối đa 2 câu mỗi trang theo REVIEW_PAGE_SIZE để không vượt frame khi nội dung dài. Câu chưa revealed không được công bố key và giải thích trong review như một câu đã mở đáp án.

ServerRuntime nhận thông báo SAVED rồi mới làm mới profile và phát RANKING_INVALIDATED cho trận ranked. Client dùng thông báo đó để biết cần query lại; không cộng hạng trực tiếp từ animation.

Đối chiếu [S/query/RankingService.java dòng 16](../src/main/java/vn/edu/nhom7/quiz/server/query/RankingService.java#L16).
Đối chiếu [S/query/HistoryService.java dòng 63](../src/main/java/vn/edu/nhom7/quiz/server/query/HistoryService.java#L63).
Đối chiếu [S/ServerRuntime.java dòng 165](../src/main/java/vn/edu/nhom7/quiz/server/ServerRuntime.java#L165).

### 5.13 Lưu ảnh và kiểm tra quyền

Ảnh có file thực trong thư mục data/media theo cấu hình, còn MEDIA_ASSETS lưu metadata. MediaService cho PNG hoặc JPEG, kích thước tối đa 5 MiB theo hằng số code và không quá 16 triệu pixel. Nó kiểm tra SHA 256, nội dung ảnh thật và MIME thay vì chỉ tin phần mở rộng tên file.

Upload chia chunk tối đa 24 KiB dữ liệu thô rồi Base64 để đưa vào JSON. Mỗi lần phải đúng offset; đợi ACK trước chunk sau giúp ảnh không chiếm toàn bộ đường gửi. Một tài khoản tối đa 2 upload đang chạy; toàn hệ thống tối đa 32; bỏ dở quá 2 phút bị dọn.

Mỗi request tải ảnh đều kiểm tra owner hoặc quyền được cấp bởi trận và lịch sử. Biết mediaId không tự cho phép đọc ảnh bất kỳ. Ảnh giải thích được cấp cho người tham gia theo trạng thái reveal. File và metadata còn cần được sao lưu cùng nhau vì chỉ sao lưu MySQL không giữ được nội dung ảnh.

Đối chiếu [S/media/MediaService.java dòng 53](../src/main/java/vn/edu/nhom7/quiz/server/media/MediaService.java#L53).
Đối chiếu [S/media/MediaService.java dòng 129](../src/main/java/vn/edu/nhom7/quiz/server/media/MediaService.java#L129).

### 5.14 Cách tự lần code của Phúc

Luồng tài khoản: AuthService.register → AccountValidator → PasswordHasher.hash → JdbcUserRepository.insert; rồi AuthService.login → users.find → hasher.verify. Luồng kết quả: MatchResults.finish → ResultSink.submit → PersistenceService → JdbcMatchRepository.save → các insert và update → commit → thông báo SAVED → profile và ranking.

Đọc PasswordHasherTest, UserRepositoryIT, QuizRepositoryIT, CommunityQuizServiceIT, MatchPersistenceIT, PersistenceServiceTest và RankingHistoryIT. Các lớp IT là kiểm thử tích hợp có thể cần MySQL theo cấu hình. Để minh họa transaction, xem điểm faults.at và tình huống lỗi trước hoặc sau commit trong test, thay vì cố tình phá dữ liệu đang dùng demo.

### 5.15 Câu hỏi và câu trả lời mẫu cho Phúc

**P01 Vì sao không lưu mật khẩu gốc?**

Em lưu kết quả PBKDF2 với salt ngẫu nhiên riêng, không lưu mật khẩu để đọc lại. Khi login, em tính lại từ mật khẩu nhập và salt rồi so với key đã lưu. Nếu database bị lộ, người đọc không có ngay mật khẩu gốc, dù vẫn cần bảo vệ mật khẩu mạnh và hệ thống vận hành.

**P02 Salt và 600 nghìn vòng có tác dụng gì?**

Salt làm hai tài khoản cùng mật khẩu thường có hash khác nhau, giảm lợi ích của bảng tính sẵn dùng chung. Nhiều vòng PBKDF2 làm mỗi lần đoán tốn công hơn. Base64 trong chuỗi lưu chỉ giúp biểu diễn byte thành chữ, không phải mã hóa bảo mật.

**P03 Tại sao dùng PreparedStatement?**

Em viết SQL với dấu hỏi và truyền giá trị bằng setter. Như vậy dữ liệu người dùng được xử lý là tham số thay vì ghép thành cú pháp câu lệnh. Em còn dùng try with resources để đóng statement, result và connection khi xong hoặc có lỗi.

**P04 Transaction bảo vệ tình huống nào?**

Một kết quả trận kéo theo dòng MATCHES, các lượt, đáp án và thống kê USERS. Em ghi cùng transaction rồi commit. Nếu lỗi trước commit, rollback cả nhóm nên không có tình huống đã cộng điểm tài khoản mà thiếu biên bản trận.

**P05 Commit thành công rồi ứng dụng tưởng lỗi thì có cộng lại không?**

Em dùng matchId và kiểm tra nội dung summary đã lưu. Nếu cùng kết quả thì trả ALREADY_SAVED, không cộng counter lại. Nếu trùng ID nhưng khác nội dung thì báo xung đột. Đây là tính chất lưu lặp không làm thay đổi kết quả sau lần thành công đầu.

**P06 Vì sao có cả QUESTIONS và MATCH_QUESTIONS?**

QUESTIONS là câu trong ngân hàng hoặc bản xuất bản. MATCH_QUESTIONS lưu câu cụ thể của lượt thi cùng snapshot. Nhờ snapshot, sau này sửa đề hoặc tạo bản mới không làm lịch sử kể sai nội dung và đáp án đã dùng trong trận cũ.

**P07 Hai người có lấy ngẫu nhiên hai đề khác nhau không?**

Không. Repository lấy một danh sách snapshot cho Match, rồi gameplay gửi dữ liệu công khai của cùng từng câu cho cả hai. Hệ thống 10 câu lấy theo cơ cấu 4/2/2/2; cộng đồng lấy các câu trong phiên bản được chọn.

**P08 Quiz cộng đồng có làm tăng ranking không?**

Không. Summary của quiz cộng đồng có ranked=false. Repository vẫn ghi history, nhưng updateRankedProfiles chỉ cộng thống kê khi ranked và không ABORTED. Vì vậy chơi nhiều quiz tự tạo không tự tăng hạng chung.

**P09 Hai người bằng điểm thì ranking xếp sao?**

SQL xét total_score giảm dần rồi wins giảm dần. Nếu cả hai giá trị bằng nhau, RANK cho cùng hạng. ROW_NUMBER thêm id để phân trang ổn định, nên cần phân biệt hạng hiển thị với vị trí một dòng trong danh sách.

**P10 Server tắt khi còn pending thì tự khôi phục hết được không?**

Không có bảo đảm đó trong bản hiện tại. Pending nằm trong RAM, retry có tác dụng khi tiến trình còn hoạt động. Muốn tự khôi phục qua crash cần thêm hàng đợi hoặc journal bền vững; đó là hướng phát triển, không phải khả năng đã có.

**P11 Vì sao bản nháp tách khỏi bản xuất bản?**

Tác giả có thể đang nhập một câu chưa hoàn chỉnh. Em cho thao tác nháp riêng, còn publish kiểm tra toàn bộ và tạo phiên bản cố định trong transaction. Người chơi dùng bản đã công bố, nên việc tác giả đang sửa không làm đề thi thay đổi giữa trận.

**P12 Em tóm tắt phần mình thế nào?**

Em phụ trách schema, tài khoản, ngân hàng câu hỏi, quiz cộng đồng, lưu kết quả và các query. Em dùng PBKDF2 cho mật khẩu, repository JDBC cho dữ liệu, snapshot cho lịch sử và transaction cùng matchId để lưu an toàn khi thử lại. Ranking chỉ đổi sau commit của trận tính hạng; dữ liệu ảnh có metadata trong MySQL và file trên server.

## 6 Ghép bốn phần để trả lời câu hỏi liên hệ

### 6.1 Ví dụ một câu hỏi đi qua cả bốn người

Giả sử A và B chơi câu SINGLE_CHOICE có đáp án chuẩn A. A chọn A ở giây thứ 4, B chọn C ở giây thứ 7.

1. Phúc cung cấp QuestionSnapshot có key riêng tư; Tuấn giữ nó trong Match và tạo PublicQuestion không có key.
2. Minh truyền QUESTION qua cùng cơ chế frame và queue đến hai kết nối. Việt hiển thị câu, tải ảnh nếu có và gửi QUESTION_READY khi xong.
3. Tuấn đợi cả hai ready, đếm ngược rồi phát QUESTION_OPEN. Việt bật vùng nhập và hiển thị countdown.
4. A bấm đáp án. Việt tạo answer kiểu chuỗi, gắn matchId, roundId, questionId và requestId; khóa gửi lặp trong lúc chờ.
5. Minh giải mã, tra session của A và chuyển tới MatchManager. Tuấn kiểm tra đúng lượt, chấm A đúng và tính 3 điểm, nhưng chỉ gửi ACK và trạng thái đã trả lời ngay lúc đó.
6. B gửi C, được nhận hợp lệ nhưng chấm sai và 0 điểm. Vì cả hai đã trả lời, Tuấn đóng lượt và phát QUESTION_RESULT cùng đáp án A.
7. Việt mới hiển thị đúng sai, sau đó animate điểm từ dữ liệu ROUND_LEADERBOARD do server gửi.
8. Hết trận, Tuấn chuyển summary sang Phúc. Phúc commit lịch sử và thống kê nếu ranked; Minh truyền thông báo lưu và làm mới dữ liệu cho Việt hiển thị.

Ví dụ này giúp mỗi người trình bày điểm bắt đầu và điểm kết thúc phần mình, đồng thời giải thích được vì sao một chức năng cần nhiều thành viên phối hợp.

### 6.2 Tám câu hỏi chung có thể gặp

**C01 Dự án thuộc mô hình gì?**

Dự án build bằng một module Maven nhưng vẫn là client server tập trung với hai điểm chạy ClientMain và ServerMain. Hai ứng dụng JavaFX gửi lệnh qua TCP đến một server Java; server quyết định luật và truy cập MySQL qua JDBC. Trong server có các package dịch vụ và repository để chia trách nhiệm, không phải mỗi package là một dịch vụ mạng độc lập.

**C02 Vì sao không chấm ngay trên client cho nhanh?**

Client là chương trình trên máy người dùng nên có thể bị thay đổi. Nếu tin điểm hoặc key ở client thì khó giữ luật thống nhất. Client chỉ gửi ý định; server kiểm tra thời hạn và chấm từ dữ liệu riêng tư. Cách này vẫn chịu độ trễ mạng, không có nghĩa mọi người đều có độ trễ như nhau.

**C03 Vì sao đã kiểm tra ở UI mà server còn phải kiểm tra?**

Kiểm tra UI giúp sửa lỗi nhập dễ và tránh gửi vô ích. Kiểm tra server là hàng rào quyết định vì client có thể bị sửa, dùng phiên bản cũ hoặc gửi dữ liệu lỗi. Database có thêm khóa và CHECK để bảo vệ tính nhất quán khi ghi.

**C04 Nếu không thấy điểm trên ranking ngay thì phải kiểm tra đâu?**

Kiểm tra trận có ranked không, MATCH_SAVE_STATUS đã SAVED chưa, transaction có commit không và client đã query lại sau RANKING_INVALIDATED chưa. Đừng chỉ kiểm tra điểm hiển thị ở GameView vì đó là điểm trong trận.

**C05 Khi mất kết nối, mỗi người xử lý phần nào?**

Minh phát hiện và dọn connection cùng session; Tuấn áp dụng luật rời trận và tạo kết quả phù hợp nếu trận đã bắt đầu; Phúc nhận summary và lưu theo cơ chế persistence; Việt báo mất kết nối và đưa người dùng vào luồng kết nối lại. Không nên chỉ sửa một Label rồi coi toàn bộ xử lý mất mạng đã xong.

**C06 Lịch sử có làm lộ đáp án cho người không tham gia không?**

HistoryService kiểm tra requester thuộc trận trước khi trả detail. Review và quyền ảnh còn xét trạng thái revealed. Biết matchId hoặc mediaId không tự mang quyền xem. Khi xuất bản hoặc hiển thị quiz công khai cũng phải dùng payload không chứa private key.

**C07 Gửi ảnh lớn có làm chậm điều khiển trận không?**

Code giảm ảnh hưởng bằng chunk nhỏ, stop and wait, executor ảnh riêng và queue ảnh được ưu tiên sau control. Tuy nhiên cùng một TCP vẫn có giới hạn băng thông và thứ tự byte, nên không thể nói hoàn toàn không có ảnh hưởng. Đây là cách hạn chế tải và duy trì khả năng phản hồi.

**C08 Nếu được phát triển tiếp thì cải thiện gì?**

Có thể thêm TLS cho đường truyền, journal bền vững cho kết quả chờ lưu và cơ chế nối lại trận có quy định phiên rõ ràng. Đây là đề xuất phát triển. Khi bảo vệ, nên nêu ranh giới của bản hiện tại trước rồi mới giải thích vì sao cải tiến đó có ích.

### 6.3 Trình tự demo để mỗi người có điểm nói

| Bước demo | Người dẫn chính | Điều nên mở hoặc giải thích |
| --- | --- | --- |
| Đăng ký và đăng nhập hai client | Phúc rồi Minh | Hash, truy vấn tài khoản, session và online |
| Mời đấu rồi từ chối hoặc chấp nhận | Minh | CHALLENGING, thời hạn và giữ chỗ hai người |
| Vào trận và sẵn sàng | Tuấn rồi Việt | Máy trạng thái, tải tài nguyên và QUESTION_READY |
| Trả lời câu và chờ reveal | Việt rồi Tuấn | Typed answer, pending ACK, chấm và điểm theo thời gian |
| Dùng trợ giúp và gửi chat | Tuấn rồi Việt | Giới hạn quyền, membership và phản hồi giao diện |
| Kết thúc và xem lịch sử | Phúc | PENDING sang SAVED, transaction và snapshot |
| Mở ranking | Phúc rồi Việt | Chỉ trận ranked đã lưu, phân trang và hiển thị |
| Nếu cần minh họa chức năng mở rộng | Việt rồi Phúc | Soạn quiz 7 dạng, publish phiên bản và trận không tính hạng |

Trước khi demo, chọn sẵn một quiz hệ thống và một quiz cộng đồng hợp lệ, dùng dữ liệu tài khoản phục vụ demo, xác nhận server và MySQL chạy đúng cấu hình. Phần này là kịch bản thực hành; không đồng nghĩa các bước đã được thực thi trong lần biên soạn tài liệu.

## 7 Thuật ngữ cơ bản và cách ôn nhanh

### 7.1 Từ điển đọc code

| Thuật ngữ | Hiểu đơn giản trong dự án |
| --- | --- |
| Socket | Đầu nối để client và server trao đổi byte |
| Frame | Một thông điệp có ranh giới do ứng dụng tự đặt trên TCP |
| JSON | Dạng chữ có cấu trúc để mô tả dữ liệu |
| Envelope | Vỏ thông điệp có loại, các ID và payload |
| Payload hoặc DTO | Dữ liệu cụ thể của một yêu cầu hay phản hồi |
| Thread và executor | Luồng làm việc và nơi quản lý việc chạy tác vụ |
| Queue | Hàng đợi, ví dụ chờ writer gửi dữ liệu |
| Lock hoặc synchronized | Cho phép một nhóm thao tác trên dữ liệu chung chạy an toàn theo lượt |
| Race condition | Kết quả sai do nhiều tác vụ xen kẽ lúc đọc sửa cùng trạng thái |
| Session | Mối gắn tài khoản đã xác thực với một kết nối |
| State và reducer | Trạng thái hiện tại và hàm tạo trạng thái tiếp theo từ event |
| ACK | Thông báo xác nhận đã nhận hoặc chấp nhận xử lý theo loại yêu cầu |
| Deadline | Mốc hết hạn; timer là tác vụ được hẹn chạy tại thời điểm đó |
| Snapshot | Bản dữ liệu chụp tại một thời điểm để giữ nội dung ổn định |
| Repository | Lớp truy cập và chuyển đổi dữ liệu lưu trữ |
| Transaction | Nhóm thao tác dữ liệu cùng commit hoặc cùng rollback |
| Idempotent | Lặp thao tác hợp lệ không tạo thêm hiệu ứng sau lần thành công đầu |
| Salt và password hash | Dữ liệu ngẫu nhiên riêng và kết quả dẫn xuất dùng kiểm tra mật khẩu |
| Ranked | Trận có thể tham gia tính thống kê xếp hạng theo điều kiện lưu |
| Migration | Script thay đổi schema từ phiên bản cũ sang phiên bản mới |

### 7.2 Mỗi người cần tự nói được năm điều

1. Phần của mình nhận đầu vào gì, từ lớp của ai.
2. Ba đến năm lớp quan trọng nhất và đường gọi giữa chúng.
3. Một ví dụ dữ liệu đi từ đầu đến cuối, kể cả khi có lỗi.
4. Cách tránh một lỗi đặc trưng: Minh tránh đọc sai frame và giữ chỗ trùng; Tuấn tránh timer cũ và cộng điểm lặp; Việt tránh block UI và gửi lặp; Phúc tránh lưu nửa trận và cộng counter hai lần.
5. Một giới hạn thực tế của code và một hướng cải thiện được phân biệt rõ với chức năng đang có.

### 7.3 Thứ tự mở code khi thuyết trình

Minh: FrameCodec → FrameDecoder → SocketServer → MessageRouter → SessionRegistry → ChallengeManager → ConnectionManager. Tuấn: MatchManager → Match → MatchRounds → AnswerEvaluator → ScoreCalculator → MatchResults → RematchCoordinator. Việt: ClientMain → NetworkClient → ClientStore → ClientStateReducer → CommandGateway → QuestionRenderer → GameView. Phúc: schema và migration → AuthService → PasswordHasher → JdbcQuizRepository → PersistenceService → JdbcMatchRepository → RankingService và HistoryService.

Không cần đọc từng dòng import. Hãy giải thích trường dữ liệu nào quan trọng, điều kiện bảo vệ nào đứng trước thay đổi trạng thái và kết quả được bàn giao ở đâu. Nếu bị hỏi sâu một hàm, chia câu trả lời thành đầu vào, kiểm tra, thay đổi dữ liệu và đầu ra.

## 8 Chỉ dẫn đối chiếu mã nguồn

### 8.1 Nguồn phân công và kiến trúc

Phân công bốn người được đối chiếu từ BTL_Quiz_Doi_Khang_Nhom7_KienTruc_ThietKe_PhanCong.md mục 3.8 và các phần cá nhân, cùng docs/superpowers/specs/2026-10-05-quiz-arena-design.md mục 14.1. Bảng công việc Phan_cong_Nhom_7.xlsx phân rã theo bốn trách nhiệm này. Các chức năng mới được giải thích theo package đang triển khai và các điểm tích hợp giữa module.

Để đọc tổng quan, dùng README.md và THONG_TIN_DU_AN.md. Để xem cơ chế luồng và giới hạn đang mô tả, dùng docs/architecture/network-explanation.md. Để xem ba dạng tương tác, dùng docs/architecture/interactive-questions.md. Chi tiết version thư viện và cấu hình chạy nằm ở pom.xml duy nhất. Kiểm thử nằm chung trong src/test/java; tài nguyên nằm trong src/main/resources. Chạy client bằng mvn javafx:run và server bằng java -jar target/quiz-arena-server.jar sau khi build. Các chỉ dẫn dưới đây trỏ vào bản mã nguồn được đối chiếu ngày 31 tháng 10 năm 2026; số dòng có thể thay đổi khi sửa code.

### 8.2 Danh mục vị trí code được giải thích

- [1] [C/net/FrameCodec.java dòng 12](../src/main/java/vn/edu/nhom7/quiz/common/net/FrameCodec.java#L12)
- [2] [C/net/FrameDecoder.java dòng 13](../src/main/java/vn/edu/nhom7/quiz/common/net/FrameDecoder.java#L13)
- [3] [C/protocol/Envelope.java dòng 6](../src/main/java/vn/edu/nhom7/quiz/common/protocol/Envelope.java#L6)
- [4] [C/protocol/ProtocolCodec.java dòng 37](../src/main/java/vn/edu/nhom7/quiz/common/protocol/ProtocolCodec.java#L37)
- [5] [S/network/HandshakeValidator.java dòng 6](../src/main/java/vn/edu/nhom7/quiz/server/network/HandshakeValidator.java#L6)
- [6] [S/network/SocketServer.java dòng 30](../src/main/java/vn/edu/nhom7/quiz/server/network/SocketServer.java#L30)
- [7] [S/network/Connection.java dòng 16](../src/main/java/vn/edu/nhom7/quiz/server/network/Connection.java#L16)
- [8] [S/network/ConnectionManager.java dòng 39](../src/main/java/vn/edu/nhom7/quiz/server/network/ConnectionManager.java#L39)
- [9] [S/ServerRuntime.java dòng 26](../src/main/java/vn/edu/nhom7/quiz/server/ServerRuntime.java#L26)
- [10] [S/network/MessageRouter.java dòng 63](../src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java#L63)
- [11] [S/network/MessageRouter.java dòng 115](../src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java#L115)
- [12] [S/session/SessionRegistry.java dòng 56](../src/main/java/vn/edu/nhom7/quiz/server/session/SessionRegistry.java#L56)
- [13] [S/challenge/ChallengeManager.java dòng 163](../src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java#L163)
- [14] [S/network/HeartbeatService.java dòng 10](../src/main/java/vn/edu/nhom7/quiz/server/network/HeartbeatService.java#L10)
- [15] [S/network/ConnectionManager.java dòng 68](../src/main/java/vn/edu/nhom7/quiz/server/network/ConnectionManager.java#L68)
- [16] [S/network/RequestCache.java dòng 16](../src/main/java/vn/edu/nhom7/quiz/server/network/RequestCache.java#L16)
- [17] [S/match/Match.java dòng 50](../src/main/java/vn/edu/nhom7/quiz/server/match/Match.java#L50)
- [18] [S/match/MatchManager.java dòng 53](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L53)
- [19] [S/match/MatchManager.java dòng 145](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L145)
- [20] [S/match/MatchRounds.java dòng 19](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L19)
- [21] [S/match/MatchRounds.java dòng 57](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L57)
- [22] [S/match/MatchManager.java dòng 263](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L263)
- [23] [S/match/Match.java dòng 118](../src/main/java/vn/edu/nhom7/quiz/server/match/Match.java#L118)
- [24] [S/match/MatchManager.java dòng 258](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L258)
- [25] [S/match/Match.java dòng 131](../src/main/java/vn/edu/nhom7/quiz/server/match/Match.java#L131)
- [26] [S/match/MatchRounds.java dòng 80](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L80)
- [27] [S/match/AnswerEvaluator.java dòng 13](../src/main/java/vn/edu/nhom7/quiz/server/match/AnswerEvaluator.java#L13)
- [28] [S/match/ShortAnswerNormalizer.java dòng 9](../src/main/java/vn/edu/nhom7/quiz/server/match/ShortAnswerNormalizer.java#L9)
- [29] [C/protocol/InteractiveQuestions.java dòng 12](../src/main/java/vn/edu/nhom7/quiz/common/protocol/InteractiveQuestions.java#L12)
- [30] [S/match/MatchLifelines.java dòng 26](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchLifelines.java#L26)
- [31] [S/match/MatchChat.java dòng 18](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchChat.java#L18)
- [32] [S/match/MatchResults.java dòng 18](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchResults.java#L18)
- [33] [S/match/MatchManager.java dòng 241](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L241)
- [34] [S/match/RematchCoordinator.java dòng 29](../src/main/java/vn/edu/nhom7/quiz/server/match/RematchCoordinator.java#L29)
- [35] [V/ClientMain.java dòng 62](../src/main/java/vn/edu/nhom7/quiz/client/ClientMain.java#L62)
- [36] [V/state/ClientStore.java dòng 49](../src/main/java/vn/edu/nhom7/quiz/client/state/ClientStore.java#L49)
- [37] [V/network/NetworkClient.java dòng 45](../src/main/java/vn/edu/nhom7/quiz/client/network/NetworkClient.java#L45)
- [38] [V/network/NetworkClient.java dòng 98](../src/main/java/vn/edu/nhom7/quiz/client/network/NetworkClient.java#L98)
- [39] [V/state/ClientStateReducer.java dòng 22](../src/main/java/vn/edu/nhom7/quiz/client/state/ClientStateReducer.java#L22)
- [40] [V/state/ClientState.java dòng 46](../src/main/java/vn/edu/nhom7/quiz/client/state/ClientState.java#L46)
- [41] [V/ui/CommandGateway.java dòng 22](../src/main/java/vn/edu/nhom7/quiz/client/ui/CommandGateway.java#L22)
- [42] [V/network/RequestTracker.java dòng 19](../src/main/java/vn/edu/nhom7/quiz/client/network/RequestTracker.java#L19)
- [43] [V/network/RequestTimeoutMonitor.java dòng 21](../src/main/java/vn/edu/nhom7/quiz/client/network/RequestTimeoutMonitor.java#L21)
- [44] [V/ui/QuestionRenderer.java dòng 10](../src/main/java/vn/edu/nhom7/quiz/client/ui/QuestionRenderer.java#L10)
- [45] [V/ui/AnswerIntentFactory.java dòng 10](../src/main/java/vn/edu/nhom7/quiz/client/ui/AnswerIntentFactory.java#L10)
- [46] [V/ui/RangeRenderer.java dòng 17](../src/main/java/vn/edu/nhom7/quiz/client/ui/RangeRenderer.java#L17)
- [47] [V/ui/ImageTarget.java dòng 142](../src/main/java/vn/edu/nhom7/quiz/client/ui/ImageTarget.java#L142)
- [48] [V/ui/QuestionPreparation.java dòng 28](../src/main/java/vn/edu/nhom7/quiz/client/ui/QuestionPreparation.java#L28)
- [49] [V/assets/RemoteMedia.java dòng 109](../src/main/java/vn/edu/nhom7/quiz/client/assets/RemoteMedia.java#L109)
- [50] [V/network/ServerTimeEstimator.java dòng 16](../src/main/java/vn/edu/nhom7/quiz/client/network/ServerTimeEstimator.java#L16)
- [51] [V/ui/GameView.java dòng 418](../src/main/java/vn/edu/nhom7/quiz/client/ui/GameView.java#L418)
- [52] [database/004_lifelines.sql dòng 9](../database/004_lifelines.sql#L9)
- [53] [database/005_interactive_questions.sql dòng 7](../database/005_interactive_questions.sql#L7)
- [54] [S/db/JdbcConnectionFactory.java dòng 14](../src/main/java/vn/edu/nhom7/quiz/server/db/JdbcConnectionFactory.java#L14)
- [55] [S/auth/JdbcUserRepository.java dòng 45](../src/main/java/vn/edu/nhom7/quiz/server/auth/JdbcUserRepository.java#L45)
- [56] [S/auth/AccountValidator.java dòng 8](../src/main/java/vn/edu/nhom7/quiz/server/auth/AccountValidator.java#L8)
- [57] [S/auth/PasswordHasher.java dòng 11](../src/main/java/vn/edu/nhom7/quiz/server/auth/PasswordHasher.java#L11)
- [58] [S/auth/PasswordHasher.java dòng 20](../src/main/java/vn/edu/nhom7/quiz/server/auth/PasswordHasher.java#L20)
- [59] [S/auth/AuthService.java dòng 34](../src/main/java/vn/edu/nhom7/quiz/server/auth/AuthService.java#L34)
- [60] [S/network/MessageRouter.java dòng 169](../src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java#L169)
- [61] [S/quiz/JdbcQuizRepository.java dòng 70](../src/main/java/vn/edu/nhom7/quiz/server/quiz/JdbcQuizRepository.java#L70)
- [62] [S/quiz/JdbcQuizRepository.java dòng 99](../src/main/java/vn/edu/nhom7/quiz/server/quiz/JdbcQuizRepository.java#L99)
- [63] [S/match/MatchPayloads.java dòng 90](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchPayloads.java#L90)
- [64] [S/quiz/CommunityQuizService.java dòng 23](../src/main/java/vn/edu/nhom7/quiz/server/quiz/CommunityQuizService.java#L23)
- [65] [S/quiz/CommunityQuizPublisher.java dòng 23](../src/main/java/vn/edu/nhom7/quiz/server/quiz/CommunityQuizPublisher.java#L23)
- [66] [S/persistence/JdbcMatchRepository.java dòng 34](../src/main/java/vn/edu/nhom7/quiz/server/persistence/JdbcMatchRepository.java#L34)
- [67] [S/persistence/JdbcMatchRepository.java dòng 77](../src/main/java/vn/edu/nhom7/quiz/server/persistence/JdbcMatchRepository.java#L77)
- [68] [S/persistence/JdbcMatchRepository.java dòng 171](../src/main/java/vn/edu/nhom7/quiz/server/persistence/JdbcMatchRepository.java#L171)
- [69] [S/persistence/JdbcMatchRepository.java dòng 65](../src/main/java/vn/edu/nhom7/quiz/server/persistence/JdbcMatchRepository.java#L65)
- [70] [S/persistence/PersistenceService.java dòng 63](../src/main/java/vn/edu/nhom7/quiz/server/persistence/PersistenceService.java#L63)
- [71] [S/persistence/PersistenceService.java dòng 134](../src/main/java/vn/edu/nhom7/quiz/server/persistence/PersistenceService.java#L134)
- [72] [S/query/RankingService.java dòng 16](../src/main/java/vn/edu/nhom7/quiz/server/query/RankingService.java#L16)
- [73] [S/query/HistoryService.java dòng 63](../src/main/java/vn/edu/nhom7/quiz/server/query/HistoryService.java#L63)
- [74] [S/ServerRuntime.java dòng 165](../src/main/java/vn/edu/nhom7/quiz/server/ServerRuntime.java#L165)
- [75] [S/media/MediaService.java dòng 53](../src/main/java/vn/edu/nhom7/quiz/server/media/MediaService.java#L53)
- [76] [S/media/MediaService.java dòng 129](../src/main/java/vn/edu/nhom7/quiz/server/media/MediaService.java#L129)

# Giải thích luồng xử lý Quiz Arena theo từng thành viên

Tài liệu đọc code và luyện trả lời bảo vệ bài tập lớn Nhóm 7

Đối chiếu mã nguồn ngày 31 tháng 10 năm 2026

Tài liệu giúp mỗi thành viên giải thích được phần mình bằng ngôn ngữ dễ hiểu: người dùng làm gì, chương trình nhận yêu cầu ở đâu, xử lý qua những bước nào và trả kết quả ra sao. Mỗi phần có hướng dẫn mở file, ví dụ cụ thể và 12 câu hỏi mẫu. Cuối tài liệu có thêm 8 câu hỏi chung để cả nhóm luyện trình bày cùng một luồng.

Khi ôn, hãy đọc luồng trước, mở file được chỉ dẫn rồi thử kể lại bằng lời của mình. Các câu trả lời dùng từ “em” chỉ là mẫu diễn đạt; mỗi người cần điều chỉnh cho đúng phần việc thực tế của mình. Phân chia trong tài liệu dựa trên trách nhiệm đã giao cho bốn thành viên, không khẳng định tác giả của từng dòng code.

## 1 Tổng quan để cả nhóm cùng hiểu

### 1.1 Mỗi người phụ trách đoạn nào

Quiz Arena là ứng dụng cho hai người thi đấu câu hỏi qua mạng. Cùng một thao tác thường đi qua phần của nhiều người. Ví dụ, khi bấm gửi đáp án, giao diện của Việt nhận thao tác, phần mạng của Minh chuyển yêu cầu, phần thi đấu của Tuấn chấm điểm và phần dữ liệu của Phúc lưu kết quả sau trận.

| Thành viên | Phần phụ trách chính | Có thể giải thích ngắn gọn là |
| --- | --- | --- |
| Khuất Quang Minh | Giao thức chung, mạng server, phiên đăng nhập, online và thách đấu | Nhận yêu cầu và chuyển đến đúng nơi xử lý |
| Đỗ Quang Tuấn | Trận đấu, lượt câu hỏi, chấm điểm, trợ giúp, chat và tái đấu | Điều hành cuộc thi và quyết định kết quả |
| Nguyễn Hữu Việt | Giao diện, kết nối phía client và trạng thái màn hình | Nhận thao tác và hiển thị những gì đang xảy ra |
| Trần Lê Hoàng Phúc | Tài khoản, database, ngân hàng câu hỏi, lưu trận, xếp hạng và lịch sử | Cung cấp và lưu giữ dữ liệu của ứng dụng |

Các chức năng mở rộng như quiz cộng đồng và ảnh có cả giao diện lẫn xử lý server. Tài liệu đặt phần hiển thị ở chương của Việt, phần kiểm tra và lưu dữ liệu ở chương của Phúc; Minh và Tuấn tiếp tục phụ trách đường truyền và trận đấu có sử dụng những dữ liệu đó.

### 1.2 Dự án đã gom thành một module

Dự án có một file pom.xml ở thư mục gốc. Toàn bộ code Java nằm trong src/main/java/vn/edu/nhom7/quiz/. Bên trong vẫn chia thành ba package để dễ tìm: common là các quy định dùng chung; server là phần chạy trên máy chủ; client là phần chạy trên máy người chơi.

Một module là cách gom dự án để build. Nó không có nghĩa tất cả chương trình phải chạy trong một cửa sổ. Client và server vẫn được khởi động riêng, trao đổi qua kết nối TCP. Chỉ server làm việc với MySQL; client gửi yêu cầu để server đọc hoặc ghi dữ liệu giúp mình.

Từ đây, các đường dẫn Java đều tính từ src/main/java/vn/edu/nhom7/quiz/. Ví dụ server/match/MatchRounds.java nghĩa là file MatchRounds.java trong phần server. Khi tài liệu ghi “tìm hàm answer”, hãy mở file rồi tìm tên hàm đó. Dòng “Đối chiếu” giúp tìm vị trí trong bản code hiện tại; số dòng có thể thay đổi khi sửa code về sau.

### 1.3 Một lượt sử dụng hoàn chỉnh

1. Người chơi mở ứng dụng. Client kết nối đến server và xác nhận hai bên có thể trao đổi với nhau.
2. Người chơi đăng nhập. Server kiểm tra tài khoản rồi đưa người đó vào danh sách đang online.
3. Ở sảnh chờ, người chơi chọn bộ câu hỏi và gửi lời mời cho một đối thủ đang rảnh.
4. Đối thủ đồng ý. Server lấy câu hỏi, tạo trận và đưa cả hai đến màn hình chuẩn bị.
5. Cả hai bấm sẵn sàng. Khi chuyển đến từng câu, client chuẩn bị nội dung và ảnh rồi báo đã tải xong.
6. Server mở thời gian trả lời. Người chơi gửi đáp án; server ghi nhận và chấm theo quy tắc của dạng câu đó.
7. Khi cả hai đã trả lời hoặc hết giờ, server công bố đáp án và điểm. Sau đó ứng dụng chuyển sang câu tiếp theo.
8. Hết trận, server xác định thắng, thua hoặc hòa và gửi màn hình kết quả. Công việc lưu dữ liệu được thực hiện tiếp ở phía server.
9. Sau khi lưu thành công, người chơi xem được lịch sử. Nếu là trận tính hạng hợp lệ, thống kê xếp hạng cũng được cập nhật.

Có thể nhớ đường đi chính như sau: **Thao tác trên giao diện → gửi yêu cầu → server chọn nơi xử lý → xử lý nghiệp vụ hoặc database → gửi kết quả → cập nhật màn hình.**

### 1.4 Những điểm cần nói đúng khi bảo vệ

Server quyết định thời gian, đúng sai và điểm số. Đồng hồ trên client giúp người chơi quan sát; việc sửa đồng hồ hoặc màn hình client không làm server đổi kết quả.

Thông báo “đã nhận đáp án” chỉ xác nhận lượt gửi được chấp nhận, chưa công bố đúng sai. Đáp án chuẩn được công bố sau khi lượt trả lời kết thúc.

Quiz hệ thống có 10 câu, gồm 4 câu chọn một đáp án, 2 câu chọn nhiều đáp án, 2 câu đúng sai và 2 câu trả lời ngắn. Quiz cộng đồng có từ 1 đến 50 câu và có thể dùng thêm sắp xếp, chọn giá trị trên khoảng và chọn vị trí trên ảnh. Bốn dạng đầu có 15 giây trả lời; ba dạng tương tác có 30 giây.

Quiz cộng đồng được lưu lịch sử nhưng không cộng thống kê xếp hạng toàn cục. Mất kết nối không có cơ chế tự quay lại trận cũ. Chức năng lấy lại trạng thái trận chỉ hỗ trợ đồng bộ trong phiên còn hợp lệ.

## 2 Khuất Quang Minh phụ trách mạng và thách đấu

### 2.1 Minh cần trình bày được điều gì

Phần Minh giúp server biết ai đang gửi yêu cầu và cần chuyển yêu cầu đó cho ai xử lý. Ngoài việc nhận và gửi thông tin, phần này còn nhớ người nào online, người nào đang được mời, người nào đang trong trận. Nhờ vậy server không tùy tiện đưa một người vào nhiều trận cùng lúc.

Cách giới thiệu mẫu: “Phần của em là đường liên lạc và điều phối phía server. Em tiếp nhận kết nối, kiểm tra người gửi đã đăng nhập chưa, chuyển lệnh đến đúng chức năng và quản lý danh sách online cùng quá trình thách đấu.”

| Mở file | Để xem chức năng |
| --- | --- |
| common/protocol/Envelope.java và MessageType.java | Cách đặt tên yêu cầu và thông tin đi kèm |
| common/protocol/ProtocolCodec.java | Đọc và kiểm tra thông điệp giữa hai phía |
| common/net/FrameCodec.java và FrameDecoder.java | Giúp nhận đủ từng thông điệp qua mạng |
| server/network/SocketServer.java và ConnectionReader.java | Nhận kết nối và đọc yêu cầu từ client |
| server/network/MessageRouter.java | Chọn chức năng cần xử lý yêu cầu |
| server/session/SessionRegistry.java | Nhớ tài khoản, kết nối và tình trạng của người chơi |
| server/session/OnlinePresenceService.java | Tạo và gửi danh sách online |
| server/challenge/ChallengeManager.java | Gửi, nhận, chấp nhận và đóng lời mời |
| server/network/ConnectionManager.java và server/ServerRuntime.java | Dọn trạng thái khi kết nối đóng |

### 2.2 Từ lúc mở ứng dụng đến lúc server nhận được yêu cầu

1. Phía server chạy SocketServer.start để mở nơi tiếp nhận kết nối. Phía client kết nối đến địa chỉ và cổng đã cấu hình.
2. Hai bên thực hiện bước chào ban đầu, gọi là HELLO. Khi hợp lệ, server trả HELLO_ACK để client biết có thể tiếp tục.
3. Client gửi yêu cầu, chẳng hạn LOGIN hoặc CHALLENGE. Thông điệp có tên yêu cầu và dữ liệu cần xử lý.
4. ConnectionReader đọc thông tin nhận được. Các lớp FrameDecoder và ProtocolCodec giúp tách đúng thông điệp và hiểu dữ liệu bên trong.
5. MessageRouter.route xem tên yêu cầu để chuyển đến phần tài khoản, thách đấu, trận đấu hoặc truy vấn dữ liệu.
6. Kết quả được gửi về đúng kết nối của người chơi. Client nhận kết quả rồi thay đổi màn hình.

Ví dụ dễ hiểu: MessageRouter giống bàn tiếp nhận. Yêu cầu đăng nhập được chuyển đến AuthService; yêu cầu trả lời câu hỏi được chuyển đến MatchManager. Router không tự chấm đáp án và cũng không tự viết câu lệnh lưu trận.

Tìm SocketServer.start, ConnectionReader.run và MessageRouter.route để theo dõi đường đi này. Phần tách thông điệp chỉ cần giải thích là “mạng có thể đưa dữ liệu đến thành nhiều đoạn nên chương trình phải nhận đủ rồi mới đọc một yêu cầu”.

Đối chiếu [server/network/SocketServer.java dòng 30](../src/main/java/vn/edu/nhom7/quiz/server/network/SocketServer.java#L30).
Đối chiếu [server/network/ConnectionReader.java dòng 25](../src/main/java/vn/edu/nhom7/quiz/server/network/ConnectionReader.java#L25).
Đối chiếu [server/network/MessageRouter.java dòng 63](../src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java#L63).

### 2.3 Khi đăng nhập thành công thì ai xuất hiện trong danh sách online

1. MessageRouter nhận LOGIN và gọi AuthService để kiểm tra tài khoản. Việc kiểm tra mật khẩu thuộc phần Phúc.
2. Nếu đúng, SessionRegistry.tryAuthenticate gắn tài khoản đó với kết nối hiện tại. Từ đây server biết lệnh trên kết nối này do ai gửi.
3. Server trả LOGIN_RESULT. Client chuyển vào sảnh và xin danh sách bộ đề, danh sách online.
4. OnlinePresenceService.snapshot lấy các phiên đã đăng nhập, kèm tên, điểm tổng và tình trạng của từng người.
5. OnlinePresenceService.broadcast gửi danh sách mới khi trạng thái online thay đổi để các client cập nhật.

Trạng thái FREE nghĩa là có thể nhận lời mời; CHALLENGING nghĩa là đang trong quá trình mời hoặc được mời; BUSY nghĩa là đang gắn với một trận. SessionRegistry dựa trên tình trạng thật mà server đang giữ để xác định những trạng thái này.

Nếu mật khẩu sai, quy trình dừng ở bước xác thực, chưa tạo phiên đăng nhập và chưa thêm người đó vào danh sách online. Đăng ký thành công cũng chưa tự đưa người dùng vào sảnh; người dùng cần đăng nhập tiếp.

Đối chiếu [server/network/MessageRouter.java dòng 169](../src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java#L169).
Đối chiếu [server/session/SessionRegistry.java dòng 14](../src/main/java/vn/edu/nhom7/quiz/server/session/SessionRegistry.java#L14).
Đối chiếu [server/session/OnlinePresenceService.java dòng 29](../src/main/java/vn/edu/nhom7/quiz/server/session/OnlinePresenceService.java#L29).

### 2.4 Khi A mời B thi đấu

1. A chọn bộ đề rồi bấm mời B ở sảnh. Client gửi CHALLENGE chứa người được mời và bộ đề đã chọn.
2. MessageRouter chuyển yêu cầu cho ChallengeManager. Hàm invite kiểm tra A có đang mời chính mình không, bộ đề có dùng được không và hai người có còn online, còn rảnh không.
3. Nếu hợp lệ, SessionRegistry.reservePair giữ chỗ cho cặp A và B. Mục đích là tránh người khác chen thêm một lời mời vào cùng lúc.
4. A nhận xác nhận đã gửi; B nhận thông báo có lời mời. Cả hai chuyển sang tình trạng đang thách đấu.
5. B có thể đồng ý hoặc từ chối. A có thể hủy. Nếu không có phản hồi trong 20 giây, lời mời hết hạn.
6. Khi B đồng ý, ChallengeManager.create lấy câu hỏi và tạo trận. Server kiểm tra lại hai người trước khi gắn họ vào trận rồi mới kích hoạt trận đó.
7. Nếu việc tạo trận thất bại, server đóng lời mời và trả lại chỗ đã giữ. Nếu thành công, hai client nhận thông tin trận và đến màn hình sẵn sàng.

Điểm cần phân biệt: B bấm đồng ý mới là đồng ý ghép trận. Server vẫn phải lấy đề và tạo trận thành công thì hai người mới thực sự vào trận. Đồng ý lời mời cũng chưa có nghĩa bắt đầu đếm giờ trả lời ngay.

Đối chiếu [server/challenge/ChallengeManager.java dòng 102](../src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java#L102).
Đối chiếu [server/challenge/ChallengeManager.java dòng 163](../src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java#L163).
Đối chiếu [server/session/SessionRegistry.java dòng 56](../src/main/java/vn/edu/nhom7/quiz/server/session/SessionRegistry.java#L56).

### 2.5 Khi lời mời bị hủy hoặc người chơi mất kết nối

ChallengeManager.handle nhận các hành động đồng ý, từ chối và hủy. Server kiểm tra đúng người được phép thực hiện: người gửi được hủy, người nhận được đồng ý hoặc từ chối. Hàm finish đóng lời mời, giải phóng chỗ đã giữ và báo lại hai bên.

Nếu người chơi đăng xuất hoặc mất kết nối, phần mạng đóng và dọn kết nối. ServerRuntime nối việc dọn này với danh sách phiên, ChallengeManager và MatchManager. Lời mời liên quan được hủy; nếu đang có trận thì phần của Tuấn quyết định cách kết thúc theo giai đoạn trận. Danh sách online được gửi lại để những người còn lại thấy trạng thái mới.

Với trường hợp mạng bị mất mà không có thông báo đóng rõ ràng, HeartbeatService giúp nhận ra kết nối đã im lặng quá lâu. Không nên giải thích là “vừa rút mạng là tất cả máy lập tức biết ngay”, vì còn cần thời gian phát hiện.

Đối chiếu [server/challenge/ChallengeManager.java dòng 239](../src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java#L239).
Đối chiếu [server/challenge/ChallengeManager.java dòng 268](../src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java#L268).
Đối chiếu [server/network/ConnectionManager.java dòng 68](../src/main/java/vn/edu/nhom7/quiz/server/network/ConnectionManager.java#L68).

### 2.6 Câu hỏi mẫu dành cho Minh

**M01 Khi người dùng bấm đăng nhập thì yêu cầu đi qua đâu ở server?**

“Client gửi LOGIN qua kết nối đang mở. ConnectionReader đọc thông điệp, rồi MessageRouter.route nhận và chuyển sang authenticate. Tại đó server gọi AuthService để kiểm tra tài khoản. Nếu đúng, SessionRegistry gắn tài khoản với kết nối và server trả kết quả đăng nhập.”

**M02 Nếu cần tìm nơi phân loại các yêu cầu thì mở file nào?**

“Em mở server/network/MessageRouter.java. Hàm route là điểm nhận chính; routeAuthenticated xử lý các lệnh cần đăng nhập. Từ đây có thể lần sang ChallengeManager cho lời mời, MatchManager cho thi đấu và QueryDispatcher cho các màn hình lấy dữ liệu.”

**M03 Danh sách người online được tạo và cập nhật ở đâu?**

“SessionRegistry giữ các phiên đã đăng nhập. OnlinePresenceService.snapshot dựa vào đó tạo danh sách có tên và trạng thái người chơi; broadcast gửi danh sách mới cho các client. Giao diện chỉ hiển thị lại danh sách server gửi về.”

**M04 Nút thách đấu được xử lý ở đâu và có kiểm tra gì?**

“Ở server em xem ChallengeManager.invite. Hàm này kiểm tra người được mời, bộ đề và tình trạng hai người. Nếu cả hai hợp lệ, server giữ chỗ cho cặp đó, gửi xác nhận cho người mời và gửi lời mời cho đối thủ.”

**M05 Tại sao một người đang có lời mời lại không nhận thêm lời mời khác?**

“Vì lúc tạo lời mời, SessionRegistry.reservePair đã giữ chỗ cho cả hai. Lần mời khác sẽ kiểm tra tình trạng hiện tại và bị từ chối nếu người đó không còn rảnh. Nhờ vậy hai thao tác gần nhau không làm một người bị ghép vào hai cặp.”

**M06 Khi đối thủ đồng ý thì chương trình đã bắt đầu trả lời câu hỏi ngay chưa?**

“Chưa. ChallengeManager.create còn lấy đề, chuẩn bị trận và kiểm tra lại hai phiên. Sau khi tạo thành công, MatchManager mới kích hoạt trận và gửi màn hình chuẩn bị. Hai người còn phải bấm sẵn sàng trước khi đi vào lượt câu hỏi.”

**M07 Chức năng từ chối và hủy lời mời nằm ở đâu?**

“Em mở ChallengeManager.handle để xem các nhánh nhận lệnh. Người nhận có thể từ chối, người gửi có thể hủy. Cuối cùng finish đóng lời mời, trả lại trạng thái rảnh và thông báo lý do cho hai phía.”

**M08 Nếu người nhận để nguyên cửa sổ mời thì chuyện gì xảy ra?**

“Lời mời chỉ có hiệu lực 20 giây. Khi hết thời gian, server đóng lời mời và giải phóng cặp đã giữ. Vì thời hạn do server quản lý nên dù client vẫn đang hiện cửa sổ cũ, yêu cầu đồng ý muộn cũng không tạo được trận từ lời mời hết hạn.”

**M09 Server biết người đang gửi đáp án là ai bằng cách nào?**

“Sau đăng nhập, SessionRegistry đã gắn tài khoản với kết nối. Khi nhận lệnh, MessageRouter lấy thông tin người gửi từ phiên đó để chuyển tiếp. Server không chỉ tin vào một tên người chơi do client tự ghi trong nội dung gửi lên.”

**M10 Khi người chơi mất mạng thì phần nào chịu trách nhiệm dọn trạng thái?**

“ConnectionManager và ServerRuntime nối các bước dọn kết nối. Server bỏ phiên online, thông báo cho ChallengeManager và MatchManager, rồi cập nhật danh sách online. Lời mời được hủy ở phần Minh; kết quả trận đang chơi được xử lý theo luật ở phần Tuấn.”

**M11 Vì sao cần FrameDecoder thay vì cứ nhận dữ liệu là đọc ngay?**

“Vì một yêu cầu có thể đến thành nhiều đoạn hoặc nhiều yêu cầu đến liền nhau. FrameDecoder gom và tách đủ từng thông điệp trước khi ProtocolCodec đọc nội dung. Em chỉ cần giải thích mục đích này để thấy đường truyền không làm lẫn các yêu cầu.”

**M12 Gom thành một module thì phần mạng còn cần thiết không?**

“Vẫn cần. Một module chỉ là cách tổ chức và build code bằng một pom.xml. Client và server vẫn chạy riêng và giao tiếp qua TCP, nên SocketServer, NetworkClient và MessageRouter vẫn thực hiện đúng vai trò cũ.”

## 3 Đỗ Quang Tuấn phụ trách luồng thi đấu

### 3.1 Tuấn cần trình bày được điều gì

Phần Tuấn quyết định trận đang ở bước nào, lúc nào được trả lời, đáp án có đúng không và mỗi người được bao nhiêu điểm. Có thể xem phần này là người điều hành cuộc thi: chỉ mở câu khi đủ điều kiện, chỉ nhận đáp án trong thời gian cho phép và chỉ công bố kết quả khi lượt đã kết thúc.

Cách giới thiệu mẫu: “Phần của em bắt đầu khi server tạo được trận. Em quản lý sẵn sàng, chuyển câu, nhận và chấm đáp án, tính điểm, xử lý trợ giúp, kết thúc trận và tái đấu.”

| Mở file | Để xem chức năng |
| --- | --- |
| server/match/MatchManager.java | Nhận các lệnh trong trận và kiểm tra người tham gia |
| server/match/MatchRounds.java | Chuẩn bị câu, mở trả lời, nhận đáp án và công bố kết quả |
| server/match/AnswerEvaluator.java | Kiểm tra đúng sai cho từng dạng câu |
| server/match/ScoreCalculator.java | Tính điểm dựa trên tốc độ trả lời |
| server/match/MatchLifelines.java | Xử lý quyền trợ giúp |
| server/match/MatchChat.java | Chuyển tin nhắn trong cùng trận |
| server/match/MatchResults.java | Kết thúc, rời trận và chuyển kết quả đi lưu |
| server/match/RematchCoordinator.java | Xử lý lời mời tái đấu |
| server/match/MatchPayloads.java | Chuẩn bị thông tin câu hỏi gửi cho client |

### 3.2 Từ màn hình sẵn sàng đến lúc mở câu hỏi

1. MatchManager.activate gửi thông tin bắt đầu trận. Hai người đến màn hình sẵn sàng; server chưa mở giờ làm bài.
2. Mỗi người bấm sẵn sàng và gửi MATCH_READY. MatchManager ghi nhận riêng từng người.
3. Khi cả hai đã sẵn sàng, server đếm ngược trước khi đưa câu đầu tiên ra.
4. MatchRounds.nextQuestion tạo lượt câu hỏi mới và gửi nội dung câu cho hai client. Giai đoạn này là chuẩn bị, chưa được trả lời.
5. Mỗi client tải nội dung, kể cả ảnh, rồi gửi QUESTION_READY. Server đợi cả hai cùng chuẩn bị xong.
6. Server đếm ngược vào câu, phát QUESTION_OPEN và bắt đầu nhận đáp án. Thời gian là 15 hoặc 30 giây tùy dạng câu.

Có hai lần sẵn sàng với mục đích khác nhau. MATCH_READY là đồng ý bắt đầu cả trận; QUESTION_READY là xác nhận đã chuẩn bị xong một câu cụ thể. Ví dụ A tải ảnh nhanh hơn B thì A không được bắt đầu làm trước chỉ vì máy A nhanh hơn.

Nếu có người không sẵn sàng trong thời gian cho phép, server hủy theo quy tắc chờ của trận. Chương trình không treo mãi ở bước chờ.

Đối chiếu [server/match/MatchManager.java dòng 95](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L95).
Đối chiếu [server/match/MatchRounds.java dòng 57](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L57).
Đối chiếu [server/match/MatchRounds.java dòng 19](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L19).

### 3.3 Khi người chơi gửi một đáp án

1. Client gửi ANSWER kèm trận, lượt câu hỏi và nội dung đã chọn. MessageRouter chuyển đến MatchManager.
2. MatchManager kiểm tra người gửi có thuộc trận đó không. MatchRounds.answer kiểm tra câu và lượt đang trả lời có đúng với lượt hiện tại không.
3. Server kiểm tra lượt đã mở chưa, còn thời gian không và người này đã có đáp án được nhận chưa. Gửi sớm, gửi muộn hoặc gửi cho câu cũ sẽ không được tính như một đáp án mới.
4. Với lượt gửi hợp lệ, AnswerEvaluator kiểm tra đúng sai, ScoreCalculator tính điểm và server giữ kết quả để dùng khi kết thúc lượt.
5. Server gửi xác nhận đã nhận đáp án cho người gửi. Người còn lại có thể thấy đối thủ đã trả lời nhưng chưa biết đáp án chuẩn.
6. Khi cả hai đã trả lời hoặc hết giờ, closeRound chốt lượt và gửi QUESTION_RESULT. Lúc này mới có đáp án chuẩn, giải thích và điểm được công bố.

Ví dụ A chọn đáp án B, server trả ACK. A chỉ nên hiểu là “lượt gửi đã được nhận”. Nếu B vẫn còn đang suy nghĩ thì chương trình chưa tiết lộ kết quả. Đến khi B trả lời hoặc hết giờ, cả hai mới cùng xem đáp án và điểm.

Mỗi người chỉ có một đáp án được chấp nhận trong một lượt. Gửi lại đúng yêu cầu cũ chỉ nhận lại xác nhận; gửi yêu cầu khác sau khi đã trả lời sẽ bị báo đã trả lời. Vì vậy bấm nhiều lần không đổi được kết quả đã nhận.

Đối chiếu [server/match/MatchRounds.java dòng 80](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L80).
Đối chiếu [server/match/MatchRounds.java dòng 119](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L119).
Đối chiếu [server/match/MatchRounds.java dòng 136](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L136).

### 3.4 Đúng sai và điểm được tính như thế nào

AnswerEvaluator.evaluate chọn cách kiểm tra dựa trên dạng câu. Điều quan trọng khi giải thích là mỗi dạng có cách nhập và điều kiện đúng riêng; không thể dùng cách so một lựa chọn cho tất cả câu hỏi.

| Dạng câu | Điều kiện để được tính đúng |
| --- | --- |
| Chọn một đáp án | Lựa chọn trùng đáp án đúng |
| Chọn nhiều đáp án | Chọn đủ và đúng toàn bộ tập đáp án; thứ tự chọn không quan trọng |
| Đúng sai | Giá trị đúng hoặc sai trùng đáp án |
| Trả lời ngắn | Khớp đáp án được chấp nhận sau khi chuẩn hóa chữ hoa thường và khoảng trắng; vẫn phân biệt dấu |
| Sắp xếp | Các mục nằm đúng toàn bộ thứ tự yêu cầu |
| Chọn giá trị trên khoảng | Giá trị hợp lệ theo thang chọn và nằm trong khoảng được chấp nhận |
| Chọn vị trí trên ảnh | Điểm được chọn nằm trong vùng đáp án, kể cả đường biên |

ScoreCalculator chia thời gian trả lời thành ba phần. Trả lời đúng ở phần đầu được 3 điểm, phần giữa được 2 điểm, phần cuối được 1 điểm. Câu 15 giây có các mốc 5 và 10 giây; câu 30 giây có các mốc 10 và 20 giây. Trả lời sai hoặc không trả lời được 0 điểm.

Ví dụ, ở câu 15 giây, A trả lời đúng sau 4 giây được 3 điểm; B trả lời đúng sau 8 giây được 2 điểm. Điểm phụ thuộc thời điểm server nhận đáp án hợp lệ. Quyền nhân đôi điểm chỉ có tác dụng cho câu được dùng trợ giúp đó; trả lời sai vẫn là 0.

Sau khi công bố kết quả câu, MatchRounds.leaderboard gửi bảng điểm tạm thời. Nếu còn câu thì chuyển tiếp; nếu hết câu thì chuyển sang kết thúc trận. Bảng điểm giữa trận này khác bảng xếp hạng toàn cục ở mục Xếp hạng.

Đối chiếu [server/match/AnswerEvaluator.java dòng 13](../src/main/java/vn/edu/nhom7/quiz/server/match/AnswerEvaluator.java#L13).
Đối chiếu [server/match/ScoreCalculator.java dòng 10](../src/main/java/vn/edu/nhom7/quiz/server/match/ScoreCalculator.java#L10).
Đối chiếu [server/match/MatchRounds.java dòng 193](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L193).

### 3.5 Trợ giúp và chat trong trận

Người chơi bấm một trợ giúp thì client gửi LIFELINE_USE. MatchLifelines.lifeline kiểm tra đúng thời điểm đang trả lời, chưa nộp đáp án và còn quyền dùng. Mỗi loại trợ giúp được dùng một lần trong trận, mỗi câu chỉ được dùng một trợ giúp. Hai loại bỏ bớt lựa chọn chỉ dùng được với câu chọn một đáp án có đủ lựa chọn; không áp dụng tùy ý cho mọi dạng câu.

Nếu dùng nhân đôi điểm, server ghi nhận tác dụng trên câu hiện tại rồi áp dụng khi chấm điểm. Nếu dùng loại bỏ lựa chọn, server gửi về các lựa chọn cần ẩn để client cập nhật. Server vẫn là nơi kiểm tra quyền dùng, nên bật lại nút ở client không tạo thêm lượt trợ giúp.

Với chat, client gửi CHAT; MatchManager kiểm tra người gửi thuộc trận rồi MatchChat.chat kiểm tra nội dung và chuyển cho hai người trong trận đó. Chat không mở lại câu đã hết giờ và không làm thời gian trả lời chạy lại từ đầu.

Đối chiếu [server/match/MatchLifelines.java dòng 26](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchLifelines.java#L26).
Đối chiếu [server/match/MatchChat.java dòng 18](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchChat.java#L18).

### 3.6 Kết thúc trận và tái đấu

1. Khi hoàn thành các câu, MatchResults.finish so tổng điểm để xác định thắng, thua hoặc hòa.
2. Server tạo bản tổng hợp trận và chuyển cho PersistenceService lưu. Đồng thời gửi MATCH_RESULT để hai client hiển thị kết quả.
3. Màn hình có thể đã hiện kết quả trong lúc trạng thái lưu còn chờ xử lý. Chỉ khi lưu thành công thì dữ liệu mới có trong lịch sử và thống kê phù hợp mới được cập nhật.
4. Nếu một người rời trận hoặc mất kết nối khi trận đã bắt đầu, phần kết quả xử lý theo trường hợp bỏ cuộc. Nếu trận chưa thực sự bắt đầu, server hủy mà không tạo một trận thi đấu đã hoàn thành.
5. Ở màn hình kết quả, một người có thể xin tái đấu. RematchCoordinator.commandLocked nhận thao tác; đối thủ phải đồng ý và cả hai vẫn phải còn đủ điều kiện tham gia.
6. Khi hợp lệ, server lấy đề và tạo một trận mới. Trận tái đấu có mã trận mới và điểm bắt đầu lại; câu hỏi có thể trùng vì vẫn lấy từ cùng bộ đề.

Không nên nói “cứ thoát ứng dụng là trận được hòa” hoặc “tái đấu là tiếp tục điểm cũ”. Kết quả phụ thuộc tình huống kết thúc, còn tái đấu luôn đi qua quá trình tạo trận mới.

Đối chiếu [server/match/MatchResults.java dòng 18](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchResults.java#L18).
Đối chiếu [server/match/MatchResults.java dòng 123](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchResults.java#L123).
Đối chiếu [server/match/RematchCoordinator.java dòng 29](../src/main/java/vn/edu/nhom7/quiz/server/match/RematchCoordinator.java#L29).

### 3.7 Câu hỏi mẫu dành cho Tuấn

**T01 Code nào nhận thao tác sẵn sàng và khi nào bắt đầu trận?**

“Em mở MatchManager.handle để tìm nhánh MATCH_READY. Server ghi nhận từng người đã sẵn sàng; đủ cả hai mới chạy đếm ngược và chuyển sang câu đầu. Một người bấm trước chưa làm người còn lại bắt đầu trả lời ngay.”

**T02 Vì sao vào từng câu lại cần báo sẵn sàng thêm một lần?**

“Vì câu hỏi có thể có ảnh hoặc nội dung cần tải. MatchRounds.nextQuestion gửi nội dung trước, mỗi client chuẩn bị xong mới gửi QUESTION_READY. Server đợi cả hai, đếm ngược rồi mới gửi QUESTION_OPEN để mở thời gian trả lời.”

**T03 Nếu cần tìm toàn bộ luồng gửi đáp án thì mở những hàm nào?**

“Em bắt đầu ở MatchManager.handle, đi sang MatchRounds.answer để xem kiểm tra và nhận đáp án. Trong đó gọi AnswerEvaluator.evaluate để xét đúng sai và ScoreCalculator.calculate để tính điểm. Cuối lượt, MatchRounds.closeRound công bố kết quả cho cả hai.”

**T04 Server gửi ACK thì đáp án đã đúng chưa?**

“Chưa thể kết luận là đúng. MatchRounds.ack chỉ xác nhận đáp án hợp lệ đã được nhận. Server đợi cả hai trả lời hoặc hết giờ rồi closeRound mới gửi đáp án chuẩn, giải thích và điểm để tránh lộ kết quả khi đối thủ còn đang làm.”

**T05 Người chơi bấm gửi hai lần thì xử lý ở đâu?**

“Ở MatchRounds.answer. Server đã nhớ người đó có đáp án được nhận trong lượt chưa. Nếu là cùng yêu cầu cũ thì trả lại xác nhận; nếu là yêu cầu mới sau khi đã trả lời thì báo đã trả lời, không ghi đè đáp án trước.”

**T06 Không ai trả lời hoặc chỉ một người trả lời thì có chuyển câu không?**

“Có. MatchRounds.timeout xử lý khi hết thời gian, rồi closeRound chốt kết quả. Người chưa có đáp án được tính là không trả lời và không được điểm. Sau phần công bố và bảng điểm, server tiếp tục câu sau hoặc kết thúc nếu đó là câu cuối.”

**T07 Câu chọn nhiều đáp án thiếu một lựa chọn đúng có được điểm một phần không?**

“Không. AnswerEvaluator yêu cầu tập lựa chọn khớp đầy đủ với tập đáp án. Chọn thiếu hoặc chọn thừa đều sai; thứ tự bấm các lựa chọn không ảnh hưởng. Luật hiện tại không chia điểm cho từng lựa chọn đúng.”

**T08 Muốn sửa quy tắc tính điểm thì tìm ở đâu và đang tính thế nào?**

“Em xem ScoreCalculator.calculate. Hiện tại thời gian được chia làm ba phần; trả lời đúng lần lượt được 3, 2 hoặc 1 điểm. Sai hoặc hết giờ là 0. Nếu dùng quyền nhân đôi cho câu đó thì điểm đúng được nhân đôi.”

**T09 Trợ giúp được kiểm tra ở đâu và có thể dùng liên tục không?**

“Em xem MatchLifelines.lifeline. Server kiểm tra đang trong giờ trả lời, chưa nộp đáp án, loại trợ giúp còn lượt và phù hợp dạng câu. Mỗi loại chỉ một lần trong trận, mỗi câu một trợ giúp, nên không thể bấm liên tục để lấy thêm lợi thế.”

**T10 Chat trong trận được gửi cho ai và có làm tăng thời gian làm bài không?**

“MatchChat.chat chuyển tin nhắn cho người trong cùng trận sau khi kiểm tra nội dung và tư cách người gửi. Thao tác chat không đổi thời hạn trả lời. Vì vậy người chơi vẫn phải hoàn thành đáp án theo giờ của câu hiện tại.”

**T11 Kết quả được hiện trước hay sau khi lưu database?**

“MatchResults.finish chốt kết quả, chuyển bản tổng hợp đi lưu và gửi kết quả cho client. Người chơi có thể xem màn hình kết quả khi việc lưu còn đang chờ. Lưu thành công mới làm lịch sử và thống kê liên quan được cập nhật.”

**T12 Tái đấu được viết ở đâu và có giữ điểm trận trước không?**

“Em xem RematchCoordinator, nhất là commandLocked. Server ghi nhận lời mời và sự đồng ý của hai người, kiểm tra họ còn tham gia được rồi tạo trận mới. Trận mới có mã mới và điểm tính lại từ đầu, không cộng tiếp điểm của trận cũ.”

## 4 Nguyễn Hữu Việt phụ trách giao diện và client

### 4.1 Việt cần trình bày được điều gì

Phần Việt là nơi người dùng trực tiếp thao tác: nhập tài khoản, chọn bộ đề, thách đấu, trả lời và xem kết quả. Điểm cần giải thích là một nút bấm gửi yêu cầu gì, sự kiện trả về làm màn hình đổi ra sao và lúc nào giao diện cho phép thao tác tiếp.

Cách giới thiệu mẫu: “Em làm phần client. Khi người dùng bấm nút, giao diện tạo yêu cầu và gửi lên server. Client nhận kết quả, cập nhật trạng thái rồi hiển thị đúng màn hình. Việc chấm điểm và lưu dữ liệu do server quyết định.”

| Mở file | Để xem chức năng |
| --- | --- |
| client/ClientMain.java | Nối phần mạng, trạng thái và giao diện khi khởi động |
| client/network/NetworkClient.java | Kết nối và gửi nhận thông điệp phía client |
| client/ui/AppShell.java | Điều phối màn hình và phản hồi theo sự kiện |
| client/ui/AuthForm.java | Nhập và gửi đăng nhập, đăng ký |
| client/ui/LobbyView.java và ChallengeDialog.java | Sảnh chờ, chọn đề và cửa sổ lời mời |
| client/ui/WaitingView.java và QuestionPreparation.java | Sẵn sàng cả trận và chuẩn bị từng câu |
| client/ui/GameView.java và QuestionRenderer.java | Hiển thị màn chơi và chọn giao diện cho dạng câu |
| client/ui/CommandGateway.java | Kiểm tra rồi gửi thao tác của người chơi |
| client/state/ClientStateReducer.java và ClientState.java | Cập nhật trạng thái và điều kiện được trả lời |
| client/ui/CommunityQuizView.java | Tạo và chỉnh sửa quiz cộng đồng |
| client/ui/RankingView.java và HistoryView.java | Hiển thị xếp hạng và lịch sử |

### 4.2 Từ mở ứng dụng đến vào sảnh

1. ClientMain.start khởi tạo phần kết nối, nơi giữ trạng thái và AppShell. Đây là nơi nối các phần client với nhau.
2. NetworkClient.connect mở kết nối đến server và gửi lời chào. Khi nhận HELLO_ACK, AppShell cho phép dùng biểu mẫu tài khoản.
3. Người dùng nhập thông tin và bấm đăng nhập. AuthForm.login kiểm tra các ô cần nhập rồi gửi LOGIN; trong lúc chờ, biểu mẫu được khóa để hạn chế bấm lặp.
4. Khi server trả LOGIN_RESULT, phần trạng thái lưu thông tin người chơi. AppShell yêu cầu danh sách bộ đề và người online.
5. AppShell.render và rebuildView dựa vào trạng thái mới để hiển thị sảnh chờ. LobbyView đưa danh sách và thông tin bộ đề lên màn hình.
6. Nếu server trả lỗi, giao diện hiển thị thông báo phù hợp và cho nhập lại. Nếu đăng ký thành công, giao diện nhắc đăng nhập, chưa tự chuyển sang sảnh.

ClientMain nhận thông điệp theo hai việc liên quan: cập nhật dữ liệu trạng thái và chuyển sự kiện cho giao diện. Nhờ đó, ví dụ có LOGIN_RESULT thì cả thông tin tài khoản lẫn màn hình hiển thị đều được cập nhật theo cùng kết quả từ server.

Đối chiếu [client/ClientMain.java dòng 34](../src/main/java/vn/edu/nhom7/quiz/client/ClientMain.java#L34).
Đối chiếu [client/ui/AuthForm.java dòng 77](../src/main/java/vn/edu/nhom7/quiz/client/ui/AuthForm.java#L77).
Đối chiếu [client/ui/AppShell.java dòng 340](../src/main/java/vn/edu/nhom7/quiz/client/ui/AppShell.java#L340).

### 4.3 Chọn bộ đề và thách đấu trên giao diện

1. LobbyView hiển thị thư viện bộ đề. Người chơi tìm hoặc lọc, rồi chọn bộ đề để xem thông tin chi tiết.
2. Danh sách online cho biết ai đang rảnh hoặc đang bận. Khi có bộ đề phù hợp và đối thủ có thể được mời, thao tác mời gửi CHALLENGE lên server.
3. Khi nhận xác nhận hoặc lời mời, AppShell.onEvent mở ChallengeDialog. Người gửi thấy thao tác hủy; người nhận có thao tác đồng ý và từ chối.
4. Bấm đồng ý, từ chối hoặc hủy sẽ gửi yêu cầu tương ứng. Giao diện không tự tạo kết quả lời mời chỉ từ nút bấm.
5. Server báo lời mời đóng thì cửa sổ được đóng hoặc cập nhật lý do. Khi có MATCH_START, client chuyển sang màn hình chuẩn bị trận.

Có thể minh họa với hai cửa sổ client: A chọn đề và mời, B thấy cửa sổ mời; B đồng ý thì cả hai mới cùng chuyển màn hình. Nếu A hoặc B đã bận, server có thể từ chối dù danh sách trên màn hình chưa kịp cập nhật.

Tìm LobbyView.buildLibrary để xem danh sách đề, buildOnlinePlayers để xem vùng đối thủ và AppShell.onEvent để xem cách mở, đóng cửa sổ lời mời.

Đối chiếu [client/ui/LobbyView.java dòng 96](../src/main/java/vn/edu/nhom7/quiz/client/ui/LobbyView.java#L96).
Đối chiếu [client/ui/LobbyView.java dòng 208](../src/main/java/vn/edu/nhom7/quiz/client/ui/LobbyView.java#L208).

### 4.4 Hiển thị câu hỏi và gửi đáp án

1. WaitingView có nút sẵn sàng gửi MATCH_READY. Khi server đưa câu hỏi xuống, QuestionPreparation.prepare chuẩn bị nội dung, tải ảnh nếu có.
2. Chuẩn bị xong đúng câu hiện tại, client gửi QUESTION_READY. Client chưa bật trả lời chỉ vì đã thấy câu hỏi.
3. QuestionRenderer.forType chọn giao diện theo dạng câu. Ví dụ câu chọn nhiều dùng MultipleChoiceRenderer; câu sắp xếp dùng ReorderRenderer; câu chọn vị trí dùng PinpointRenderer.
4. Khi nhận QUESTION_OPEN, trạng thái chuyển sang đang trả lời. GameView hiển thị đồng hồ và cho phép thao tác phù hợp.
5. Người chơi chọn hoặc nhập đáp án rồi gửi. CommandGateway.send kiểm tra có được trả lời lúc này không, đánh dấu đang gửi và chuyển yêu cầu đến NetworkClient.
6. Khi có xác nhận đã nhận, client giữ trạng thái đã trả lời. Khi có QUESTION_RESULT, các thành phần hiển thị kết quả mới đánh dấu đúng sai và cập nhật điểm.

Với câu chọn vị trí trên ảnh, giao diện lấy vị trí người dùng bấm trong vùng ảnh rồi gửi tọa độ tương ứng. Server mới kiểm tra điểm đó có thuộc vùng đúng không. Giao diện của bảy dạng câu khác nhau nhưng đều đi theo một đường chung: nhận nội dung, chờ mở lượt, thu đáp án, gửi và hiển thị kết quả.

Đối chiếu [client/ui/QuestionPreparation.java dòng 28](../src/main/java/vn/edu/nhom7/quiz/client/ui/QuestionPreparation.java#L28).
Đối chiếu [client/ui/QuestionRenderer.java dòng 10](../src/main/java/vn/edu/nhom7/quiz/client/ui/QuestionRenderer.java#L10).
Đối chiếu [client/ui/CommandGateway.java dòng 22](../src/main/java/vn/edu/nhom7/quiz/client/ui/CommandGateway.java#L22).

### 4.5 Vì sao nút trả lời có lúc bị khóa

ClientState mô tả tình trạng hiện tại: ở sảnh, đang chờ, đang chuẩn bị câu, đang trả lời, đang xem đáp án hoặc đã kết thúc. ClientStateReducer.apply nhận thông điệp server và chuyển trạng thái tương ứng. AppShell cùng GameView dựa vào trạng thái đó để vẽ màn hình và bật, tắt các thao tác.

ClientState.canAnswer chỉ cho trả lời khi lượt đang mở, chưa có yêu cầu trả lời đang chờ và chưa được xác nhận đã trả lời. Vì vậy sau khi bấm gửi, nút có thể bị khóa ngay để tránh gửi lặp; người chơi không phải đợi công bố đúng sai mới bị khóa.

Khóa nút là cách giúp người dùng thao tác đúng. Server vẫn kiểm tra lại vì client có thể gửi yêu cầu cũ hoặc trạng thái giao diện chưa kịp đổi. Nếu có dấu hiệu lệch trạng thái trong phiên còn hợp lệ, client có thể xin ảnh chụp trạng thái trận từ server để đồng bộ lại.

Nếu mất kết nối thật, AppShell.disconnected báo gián đoạn và vô hiệu hóa thao tác thi đấu. Người dùng cần kết nối và đăng nhập lại; bản hiện tại không tự tiếp tục trận đang chơi sau khi mất mạng.

Đối chiếu [client/state/ClientStateReducer.java dòng 22](../src/main/java/vn/edu/nhom7/quiz/client/state/ClientStateReducer.java#L22).
Đối chiếu [client/state/ClientState.java dòng 46](../src/main/java/vn/edu/nhom7/quiz/client/state/ClientState.java#L46).
Đối chiếu [client/ui/AppShell.java dòng 327](../src/main/java/vn/edu/nhom7/quiz/client/ui/AppShell.java#L327).

### 4.6 Quiz cộng đồng và các màn hình sau trận

Ở quiz cộng đồng, CommunityQuizView cho người dùng tạo hoặc mở bản nháp, nhập thông tin bộ đề và thêm câu hỏi. Các thành phần như ChoiceAnswerEditor, InteractiveAnswerEditor và QuestionPreview hỗ trợ nhập, xem trước từng dạng câu. Khi lưu, client gửi nội dung lên server; khi xuất bản, client yêu cầu server kiểm tra và tạo bản dùng để thi đấu.

Luồng dễ nhớ là: **Mở bản nháp → sửa thông tin và câu hỏi → lưu nội dung → yêu cầu xuất bản → nhận kết quả từ server.** Xem saveQuestion, saveAll và saveMeta trong CommunityQuizView để tìm các thao tác lưu. Xem trước trên client giúp kiểm tra cách trình bày, còn điều kiện được xuất bản do server xác nhận.

Màn hình kết quả hiển thị tổng điểm và trạng thái lưu, kèm thao tác rời hoặc tái đấu. RankingView gửi yêu cầu lấy bảng xếp hạng theo trang. HistoryView gửi yêu cầu lấy lịch sử; khi chọn một trận thì yêu cầu chi tiết để hiện lại câu hỏi, đáp án và kết quả đã lưu.

Những màn hình này dùng dữ liệu server trả về. Nếu kết quả vừa kết thúc còn đang chờ lưu thì lịch sử chưa chắc đã có ngay. Nếu ảnh câu hỏi cần tải từ server, RemoteMedia hỗ trợ nhận ảnh để các thành phần giao diện hiển thị.

Đối chiếu [client/ui/CommunityQuizView.java dòng 517](../src/main/java/vn/edu/nhom7/quiz/client/ui/CommunityQuizView.java#L517).
Đối chiếu [client/ui/CommunityQuizView.java dòng 531](../src/main/java/vn/edu/nhom7/quiz/client/ui/CommunityQuizView.java#L531).
Đối chiếu [client/ui/HistoryView.java dòng 54](../src/main/java/vn/edu/nhom7/quiz/client/ui/HistoryView.java#L54).

### 4.7 Câu hỏi mẫu dành cho Việt

**V01 Nút đăng nhập được code ở đâu và bấm xong xảy ra gì?**

“Em mở AuthForm.login. Hàm kiểm tra dữ liệu nhập, gửi LOGIN và khóa biểu mẫu trong lúc chờ. Khi nhận kết quả, AppShell.onEvent xử lý; đăng nhập thành công thì trạng thái có thông tin người chơi và màn hình chuyển sang sảnh.”

**V02 File nào quyết định đang hiện sảnh hay màn hình thi đấu?**

“Em xem AppShell.render và rebuildView. AppShell dựa vào trạng thái hiện tại để chọn LobbyView, WaitingView, GameView hoặc ResultView. Vì vậy đổi màn hình theo sự kiện trận và dữ liệu server, không chỉ theo nút vừa bấm.”

**V03 Muốn tìm chỗ chọn bộ đề và bấm thách đấu thì xem ở đâu?**

“Em mở LobbyView. buildLibrary dựng danh sách đề, selectQuiz xử lý phần chọn đề và buildOnlinePlayers dựng vùng người online. Khi mời đối thủ, client gửi CHALLENGE có bộ đề đã chọn rồi chờ server phản hồi.”

**V04 Cửa sổ lời mời xuất hiện do nút bấm hay do thông báo nào?**

“AppShell.onEvent mở ChallengeDialog khi nhận sự kiện xác nhận hoặc nhận lời mời từ server. Các nút trên cửa sổ gửi đồng ý, từ chối hoặc hủy. Khi server báo lời mời đóng hoặc trận bắt đầu thì giao diện xử lý đóng cửa sổ đó.”

**V05 Code nào báo cho server rằng ảnh câu hỏi đã tải xong?**

“Em xem QuestionPreparation.prepare. Client chuẩn bị câu và tải ảnh, kiểm tra vẫn là câu đang chờ rồi mới gửi QUESTION_READY. Nhờ đó server có căn cứ chờ cả hai chuẩn bị xong trước khi mở thời gian trả lời.”

**V06 Bảy dạng câu hỏi dùng chung một màn hình hay mỗi dạng một cách hiển thị?**

“GameView là màn hình chơi chung, còn QuestionRenderer.forType chọn thành phần phù hợp cho từng dạng. Ví dụ MultipleChoiceRenderer cho chọn nhiều, ReorderRenderer cho sắp xếp và PinpointRenderer cho bấm vị trí trên ảnh. Mỗi thành phần thu dữ liệu khác nhau nhưng cùng gửi đáp án lên server.”

**V07 Khi bấm gửi đáp án thì phần nào chặn bấm lần hai?**

“CommandGateway.send kiểm tra điều kiện gửi và đánh dấu câu đang chờ xác nhận. ClientState.canAnswer dựa trên trạng thái lượt cùng thông tin đã gửi, đã nhận để khóa thao tác. Ngoài ra server cũng kiểm tra riêng nên không thể vượt luật chỉ bằng cách mở lại nút.”

**V08 Tại sao hiện câu hỏi rồi mà chưa chọn đáp án được?**

“Vì đó có thể vẫn là giai đoạn chuẩn bị. Client phải tải xong nội dung rồi báo QUESTION_READY; sau khi cả hai sẵn sàng, server mới phát QUESTION_OPEN. ClientStateReducer cập nhật trạng thái và GameView mới bật phần trả lời.”

**V09 Đồng hồ trên màn hình có tự quyết định đáp án muộn hay sớm không?**

“Không. GameView hiển thị thời gian dựa trên thông tin trận và ước lượng giờ server. Đúng hạn hay quá hạn được server kiểm tra khi nhận đáp án. Đồng hồ trên màn hình là để người dùng theo dõi và thao tác kịp.”

**V10 Tạo và xuất bản quiz cộng đồng được làm ở file nào?**

“Em mở CommunityQuizView để xem biểu mẫu và các thao tác saveMeta, saveQuestion, saveAll. Người dùng sửa bản nháp, lưu rồi yêu cầu xuất bản. Client nhận thông báo thành công hoặc lỗi từ server để cập nhật giao diện; client không tự quyết định đề đã hợp lệ để thi đấu.”

**V11 Muốn xem lại một trận đã chơi thì màn hình lấy dữ liệu như thế nào?**

“HistoryView gửi yêu cầu lấy danh sách lịch sử. Khi người dùng chọn một trận, client gửi MATCH_DETAIL_REQUEST rồi hiển thị chi tiết bằng các thành phần xem lại như ReviewCard. Dữ liệu là bản server đã lưu, không phải client tự nhớ lại các lần bấm trước đó.”

**V12 Nếu mất mạng khi đang làm bài thì giao diện xử lý thế nào?**

“AppShell.disconnected báo mất kết nối và khóa thao tác thi đấu. Người dùng cần kết nối và đăng nhập lại. Chức năng xin trạng thái trận chỉ giúp đồng bộ khi phiên vẫn còn hợp lệ; hiện tại không có tự vào lại trận cũ sau khi mất kết nối.”

## 5 Trần Lê Hoàng Phúc phụ trách dữ liệu và tài khoản

### 5.1 Phúc cần trình bày được điều gì

Phần Phúc cung cấp thông tin mà ứng dụng cần và lưu những gì cần giữ lại: tài khoản, bộ câu hỏi, kết quả trận, bảng xếp hạng và lịch sử. Khi giải thích, hãy nói dữ liệu được kiểm tra trước khi lưu như thế nào và màn hình nào sẽ sử dụng dữ liệu đó.

Cách giới thiệu mẫu: “Em làm phần dữ liệu phía server. Em kiểm tra tài khoản, lấy câu hỏi để tạo trận và lưu kết quả sau khi chơi. Từ dữ liệu đã lưu, server trả bảng xếp hạng và lịch sử cho client.”

| Mở file | Để xem chức năng |
| --- | --- |
| server/auth/AuthService.java | Quy trình đăng ký và đăng nhập |
| server/auth/AccountValidator.java và PasswordHasher.java | Kiểm tra đầu vào và bảo vệ mật khẩu |
| server/auth/JdbcUserRepository.java | Đọc, ghi tài khoản trong database |
| server/quiz/JdbcQuizRepository.java | Lấy bộ câu hỏi dùng cho trận |
| server/quiz/CommunityQuizService.java | Nhận thao tác sửa bản nháp quiz cộng đồng |
| server/quiz/CommunityQuizPublisher.java | Kiểm tra và xuất bản quiz cộng đồng |
| server/persistence/PersistenceService.java | Nhận kết quả cần lưu và xử lý lưu lại khi lỗi |
| server/persistence/JdbcMatchRepository.java | Lưu trận, câu hỏi và đáp án vào database |
| server/query/QueryDispatcher.java | Chuyển yêu cầu lấy dữ liệu đến dịch vụ tương ứng |
| server/query/RankingService.java và HistoryService.java | Lấy xếp hạng, lịch sử và chi tiết trận |
| server/media/MediaService.java | Kiểm tra, lưu và cung cấp ảnh |

### 5.2 Đăng ký và đăng nhập lấy dữ liệu ở đâu

Khi đăng ký, AuthService.register kiểm tra thông tin tài khoản bằng AccountValidator. Nếu hợp lệ, PasswordHasher tạo dạng lưu bảo vệ mật khẩu rồi JdbcUserRepository.insert ghi tài khoản vào database. Nếu tên tài khoản đã có, server trả lỗi tên đã được sử dụng để client báo lại.

Đăng ký thành công chỉ tạo tài khoản. Người dùng vẫn cần đăng nhập. Điều này thể hiện ở kết quả REGISTER_RESULT riêng, không phải LOGIN_RESULT và không tự tạo phiên online.

Khi đăng nhập, AuthService.login tìm tài khoản bằng JdbcUserRepository.find rồi kiểm tra mật khẩu với PasswordHasher.verify. Nếu đúng, hàm trả thông tin người dùng để phần MessageRouter của Minh tạo phiên đăng nhập. Nếu sai thì trả lỗi, không đi tiếp vào sảnh.

Có thể giải thích việc bảo vệ mật khẩu ở mức cơ bản: “Database không lưu lại nguyên văn mật khẩu người dùng gõ. Khi đăng nhập, server dùng hàm kiểm tra để biết mật khẩu nhập vào có khớp với thông tin bảo vệ đã lưu hay không.” Không cần trình bày chi tiết thuật toán để giải thích luồng đăng nhập.

Đối chiếu [server/auth/AuthService.java dòng 19](../src/main/java/vn/edu/nhom7/quiz/server/auth/AuthService.java#L19).
Đối chiếu [server/auth/AuthService.java dòng 34](../src/main/java/vn/edu/nhom7/quiz/server/auth/AuthService.java#L34).
Đối chiếu [server/auth/JdbcUserRepository.java dòng 45](../src/main/java/vn/edu/nhom7/quiz/server/auth/JdbcUserRepository.java#L45).
Đối chiếu [server/auth/PasswordHasher.java dòng 20](../src/main/java/vn/edu/nhom7/quiz/server/auth/PasswordHasher.java#L20).

### 5.3 Lấy câu hỏi cho hai người chơi

1. Người chơi chọn bộ đề ở sảnh. Khi đối thủ đồng ý, ChallengeManager yêu cầu phần dữ liệu lấy câu hỏi cho trận.
2. JdbcQuizRepository kiểm tra bộ đề có thể sử dụng và lấy các câu phù hợp. Với quiz hệ thống, đề có cơ cấu 10 câu theo bốn dạng đã quy định.
3. Với quiz cộng đồng, server lấy bản đã xuất bản và các câu hợp lệ của bản đó. Nếu đề có cấu hình xáo trộn, server thực hiện theo cấu hình.
4. Danh sách được đưa vào một trận chung cho hai người. Server không lấy riêng một đề khác cho từng client.
5. Khi gửi câu cho người chơi, MatchPayloads.publicQuestion chỉ đưa thông tin cần hiển thị, giữ đáp án chuẩn ở server để chấm sau.

Ví dụ A mời B chơi đề X. Cả hai cùng chơi danh sách câu hỏi của trận vừa tạo. Nếu chủ đề chỉnh bản nháp sau đó, việc chỉnh này không làm hai người đang chơi tự đổi sang câu hỏi mới.

Tìm JdbcQuizRepository.loadMatchQuestions và withMatchQuestions để xem lấy đề; tìm MatchPayloads.publicQuestion ở phần Tuấn để xem dữ liệu nào được gửi ra client.

Đối chiếu [server/quiz/JdbcQuizRepository.java dòng 70](../src/main/java/vn/edu/nhom7/quiz/server/quiz/JdbcQuizRepository.java#L70).
Đối chiếu [server/quiz/JdbcQuizRepository.java dòng 99](../src/main/java/vn/edu/nhom7/quiz/server/quiz/JdbcQuizRepository.java#L99).
Đối chiếu [server/match/MatchPayloads.java dòng 90](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchPayloads.java#L90).

### 5.4 Bản nháp và bản xuất bản của quiz cộng đồng

1. Client gửi thao tác tạo hoặc sửa quiz. CommunityQuizService.execute chọn hành động cần thực hiện và kiểm tra quyền sở hữu trước khi sửa dữ liệu.
2. Người dùng lưu thông tin bộ đề, thêm câu, sửa câu hoặc đổi thứ tự. Những thay đổi này nằm trong bản nháp để tiếp tục chỉnh sửa.
3. Khi người dùng bấm xuất bản, CommunityQuizPublisher.publish kiểm tra bộ đề và toàn bộ câu hỏi. Bộ đề phải có từ 1 đến 50 câu hợp lệ.
4. Nếu có câu chưa hợp lệ, server báo lỗi để người dùng sửa. Không công bố một phần câu hỏi còn lại như một đề hoàn chỉnh.
5. Nếu hợp lệ, server tạo phiên bản dùng để thi đấu và cập nhật bộ đề trỏ đến bản xuất bản đó.
6. Các lần sửa bản nháp sau này không tự thay thế nội dung bản đang xuất bản. Muốn đưa nội dung mới ra chơi thì cần xuất bản tiếp.

Có thể ví bản nháp là bài đang soạn, bản xuất bản là bản đã chốt để mọi người sử dụng. Cách tách này giúp chủ đề sửa tiếp mà không làm hỏng nội dung đang dùng trong trận và giúp việc xem lại lịch sử giữ đúng nội dung đã chơi.

Với câu có ảnh, MediaService nhận ảnh, kiểm tra định dạng PNG hoặc JPEG và nội dung trước khi lưu. Khi đọc ảnh, server cũng kiểm tra quyền truy cập; biết mã ảnh không có nghĩa được xem mọi ảnh riêng của người khác.

Đối chiếu [server/quiz/CommunityQuizService.java dòng 23](../src/main/java/vn/edu/nhom7/quiz/server/quiz/CommunityQuizService.java#L23).
Đối chiếu [server/quiz/CommunityQuizPublisher.java dòng 23](../src/main/java/vn/edu/nhom7/quiz/server/quiz/CommunityQuizPublisher.java#L23).
Đối chiếu [server/media/MediaService.java dòng 53](../src/main/java/vn/edu/nhom7/quiz/server/media/MediaService.java#L53).

### 5.5 Lưu kết quả trận và xử lý khi chưa lưu được

1. Phần Tuấn chốt trận và tạo bản tổng hợp gồm hai người chơi, kết quả, câu hỏi và đáp án theo lượt.
2. PersistenceService.submit nhận bản tổng hợp để đưa vào xử lý lưu. Client có thể xem kết quả trong lúc việc lưu đang chờ.
3. JdbcMatchRepository.save ghi dữ liệu trận, nội dung từng câu và đáp án của từng người. Nếu là trận tính hạng đủ điều kiện, thống kê người chơi được cập nhật trong cùng đợt ghi này.
4. Chỉ khi các phần cần thiết đều ghi thành công thì server xác nhận đã lưu. Nếu lỗi giữa chừng, phần ghi đó được hủy để tránh có trận mà thiếu đáp án hoặc lệch thống kê.
5. Nếu gặp lỗi tạm thời, dịch vụ giữ công việc để thử lưu lại. Server dùng mã trận để nhận biết trận đã lưu, tránh cộng kết quả lần nữa khi thử lại.
6. Khi lưu thành công, trạng thái lưu được báo lại. Trận tính hạng còn làm thông tin hồ sơ và bảng xếp hạng được cập nhật.

Điểm cần giải thích bằng ví dụ: lưu một trận không chỉ là thêm một dòng tổng điểm. Lịch sử còn cần biết lúc đó câu hỏi là gì và mỗi người trả lời thế nào. Vì vậy các phần liên quan phải được lưu đầy đủ và gắn với cùng trận.

Cơ chế thử lại giúp khi database tạm thời có lỗi. Công việc đang chờ được giữ trong bộ nhớ của server, nên không nên khẳng định rằng tắt hẳn server vẫn chắc chắn lưu tiếp được mọi trận đang chờ. Trạng thái đã lưu thành công mới là mốc để coi dữ liệu đã được ghi ổn định vào database.

Đối chiếu [server/persistence/PersistenceService.java dòng 85](../src/main/java/vn/edu/nhom7/quiz/server/persistence/PersistenceService.java#L85).
Đối chiếu [server/persistence/JdbcMatchRepository.java dòng 34](../src/main/java/vn/edu/nhom7/quiz/server/persistence/JdbcMatchRepository.java#L34).
Đối chiếu [server/ServerRuntime.java dòng 165](../src/main/java/vn/edu/nhom7/quiz/server/ServerRuntime.java#L165).

### 5.6 Xếp hạng và lịch sử lấy thông tin gì

Khi mở Xếp hạng, client gửi RANKING_REQUEST. QueryDispatcher chuyển đến RankingService.ranking để lấy danh sách từ thống kê đã lưu. Danh sách ưu tiên tổng điểm cao hơn, sau đó đến số trận thắng. Dữ liệu được chia trang để client không phải tải toàn bộ danh sách cùng lúc.

Quiz cộng đồng không cộng vào thống kê xếp hạng toàn cục. Quiz hệ thống có tính hạng thì kết quả hợp lệ được cập nhật khi lưu trận thành công. Trận bị hủy không được coi như một kết quả tính hạng đã hoàn tất.

Khi mở Lịch sử, HistoryService.history chỉ lấy các trận có người đang yêu cầu tham gia và đã được lưu. Khi chọn một trận, HistoryService.detail kiểm tra người yêu cầu có phải một trong hai người của trận không rồi mới trả chi tiết.

Chi tiết lịch sử dùng nội dung câu hỏi đã lưu tại thời điểm chơi. Vì vậy nếu bộ đề gốc bị chỉnh sửa về sau, phần xem lại vẫn giải thích đúng trận cũ. Những phần đáp án chưa từng được công bố trong trận cũng không được tùy tiện lộ ra qua màn hình xem lại.

Đối chiếu [server/query/QueryDispatcher.java dòng 39](../src/main/java/vn/edu/nhom7/quiz/server/query/QueryDispatcher.java#L39).
Đối chiếu [server/query/RankingService.java dòng 16](../src/main/java/vn/edu/nhom7/quiz/server/query/RankingService.java#L16).
Đối chiếu [server/query/HistoryService.java dòng 23](../src/main/java/vn/edu/nhom7/quiz/server/query/HistoryService.java#L23).
Đối chiếu [server/query/HistoryService.java dòng 63](../src/main/java/vn/edu/nhom7/quiz/server/query/HistoryService.java#L63).

### 5.7 Câu hỏi mẫu dành cho Phúc

**P01 Code đăng ký tài khoản nằm ở đâu và đi qua các bước nào?**

“Em xem AuthService.register. Đầu tiên kiểm tra thông tin bằng AccountValidator, sau đó tạo dạng lưu bảo vệ mật khẩu bằng PasswordHasher rồi gọi JdbcUserRepository.insert. Nếu dữ liệu hợp lệ và tên chưa trùng thì tài khoản được tạo; client được nhắc đăng nhập tiếp.”

**P02 Đăng nhập kiểm tra tài khoản ở đâu và ai đưa người dùng vào online?**

“AuthService.login đọc tài khoản qua JdbcUserRepository.find và kiểm tra mật khẩu bằng PasswordHasher.verify. Khi đúng, dịch vụ trả thông tin người dùng. MessageRouter và SessionRegistry bên phần Minh mới gắn tài khoản vào kết nối và cập nhật online.”

**P03 Nếu tên đăng ký đã tồn tại thì chương trình xử lý thế nào?**

“Phần ghi tài khoản ở JdbcUserRepository phát hiện tên bị trùng và trả lỗi phù hợp. Lỗi được gửi lại client để hiển thị tên đã được sử dụng. Không ghi thêm một tài khoản mới có cùng tên và cũng không tự đăng nhập vào tài khoản cũ.”

**P04 Tại sao không lưu mật khẩu người dùng gõ dưới dạng đọc được?**

“Vì như vậy ai đọc được dữ liệu tài khoản có thể thấy ngay mật khẩu. PasswordHasher tạo thông tin bảo vệ để lưu; lúc đăng nhập dùng verify kiểm tra có khớp không. Trong luồng đăng nhập, server cần xác nhận mật khẩu đúng chứ không cần trả lại mật khẩu gốc.”

**P05 Câu hỏi được lấy ở file nào và hai người có nhận hai đề khác nhau không?**

“Em mở JdbcQuizRepository, tìm loadMatchQuestions và withMatchQuestions. Server lấy danh sách câu cho một trận, rồi dùng chung trận đó cho cả hai. Hai client cùng theo một danh sách câu, không tự truy vấn riêng để lấy hai đề khác nhau.”

**P06 Đáp án chuẩn có được gửi cùng câu hỏi xuống client không?**

“Không gửi ở dữ liệu mở câu cho người chơi. MatchPayloads.publicQuestion chuẩn bị phần cần hiển thị; đáp án chuẩn được giữ ở server để AnswerEvaluator chấm. Khi hết lượt, server mới gửi thông tin công bố kết quả.”

**P07 Bấm lưu quiz cộng đồng có đồng nghĩa mọi người chơi được bản vừa sửa không?**

“Chưa. CommunityQuizService lưu thay đổi vào bản nháp. Người dùng phải yêu cầu xuất bản; CommunityQuizPublisher kiểm tra toàn bộ đề rồi tạo bản dùng cho thi đấu. Chỉ sửa bản nháp không tự thay nội dung đang được công bố.”

**P08 Phần nào kiểm tra quiz cộng đồng trước khi xuất bản?**

“Em xem CommunityQuizPublisher.publish. Hàm kiểm tra đề có từ 1 đến 50 câu và các câu hợp lệ theo dạng tương ứng. Hợp lệ hết mới tạo phiên bản và cập nhật bản xuất bản; nếu còn lỗi thì trả về để người dùng sửa.”

**P09 Muốn tìm code lưu trận thì bắt đầu ở đâu?**

“Em bắt đầu ở PersistenceService.submit để xem nơi nhận kết quả cần lưu, rồi sang JdbcMatchRepository.save để xem phần ghi database. Các dữ liệu chính là thông tin trận, nội dung câu hỏi khi chơi, đáp án và thống kê phù hợp. Lưu xong mới có dữ liệu cho lịch sử.”

**P10 Nếu lưu bị lỗi hoặc server thử lưu cùng trận lần nữa thì có bị cộng điểm hai lần không?**

“Dịch vụ sẽ thử lại khi lỗi tạm thời. JdbcMatchRepository.save kiểm tra mã trận đã được lưu chưa, nên cùng kết quả đã lưu không bị ghi thành trận mới và cộng thống kê lần nữa. Các phần dữ liệu liên quan cũng được ghi cùng đợt để tránh tình trạng chỉ lưu được một nửa.”

**P11 Xếp hạng được tính ở đâu và quiz cộng đồng có được cộng không?**

“RankingService.ranking lấy danh sách theo thống kê đã lưu, ưu tiên tổng điểm rồi số trận thắng. Phần cập nhật thống kê nằm trong JdbcMatchRepository khi lưu trận tính hạng đủ điều kiện. Quiz cộng đồng vẫn có lịch sử nhưng không cộng thống kê xếp hạng toàn cục.”

**P12 Chi tiết lịch sử được kiểm tra quyền ở đâu và vì sao phải lưu cả nội dung câu hỏi?**

“HistoryService.detail kiểm tra người xem có tham gia trận đó không. Nội dung câu hỏi được lưu cùng trận để khi đề gốc chỉnh sửa, người chơi vẫn xem lại đúng câu mình từng làm. Vì vậy lịch sử không chỉ dựa vào nội dung hiện tại của ngân hàng đề.”

## 6 Ghép các phần để trình bày và chạy minh họa

### 6.1 Ví dụ A và B thi đấu một trận

A và B mở hai client và đăng nhập bằng hai tài khoản. Việt giải thích từ AuthForm.login đến lúc AppShell hiện sảnh; Minh nối tiếp bằng MessageRouter và SessionRegistry; Phúc giải thích việc AuthService đọc tài khoản và kiểm tra mật khẩu. Một chức năng đăng nhập đã có điểm phối hợp của ba phần.

A chọn một quiz hệ thống rồi mời B. Việt chỉ nơi bấm trong LobbyView và cửa sổ ChallengeDialog. Minh chỉ ChallengeManager.invite để giải thích kiểm tra, giữ chỗ và gửi lời mời. Khi B đồng ý, Phúc chỉ nơi lấy câu hỏi trong JdbcQuizRepository; Tuấn chỉ nơi nhận đề để khởi tạo trận.

Hai người bấm sẵn sàng. Tuấn giải thích điều kiện cả hai đã sẵn sàng trước khi mở câu. Việt chỉ WaitingView và QuestionPreparation để phân biệt sẵn sàng cả trận với chuẩn bị xong từng câu. Nếu câu có ảnh, hãy chỉ ra máy tải xong trước vẫn phải chờ server mở lượt.

Ở câu đầu 15 giây, A trả lời đúng sau 4 giây và B trả lời sai sau 7 giây. Việt chỉ thao tác chọn và gửi; Minh chỉ đường chuyển lệnh ANSWER; Tuấn chỉ MatchRounds.answer, AnswerEvaluator và ScoreCalculator. A được 3 điểm, B được 0; hai người cùng thấy đáp án sau khi lượt chốt.

Hết trận, Tuấn chỉ MatchResults.finish để giải thích thắng thua và tạo kết quả. Phúc chỉ PersistenceService cùng JdbcMatchRepository để giải thích lưu dữ liệu. Việt mở Lịch sử sau khi lưu thành công và trình bày cách xem chi tiết. Nếu có cập nhật xếp hạng thì giải thích đây là trận tính hạng, không áp dụng giống vậy cho quiz cộng đồng.

### 6.2 Cách tìm nhanh theo chức năng

| Cần tìm chức năng | Nơi bắt đầu đọc |
| --- | --- |
| Bấm đăng nhập | client/ui/AuthForm.java hàm login |
| Kiểm tra mật khẩu | server/auth/AuthService.java hàm login |
| Chuyển yêu cầu đến đúng nơi | server/network/MessageRouter.java hàm route |
| Online và tình trạng người chơi | server/session/SessionRegistry.java và OnlinePresenceService.java |
| Mời và chấp nhận thi đấu | server/challenge/ChallengeManager.java hàm handle, invite, create |
| Sẵn sàng và mở lượt | server/match/MatchManager.java và MatchRounds.java |
| Tải câu hỏi trước khi báo sẵn sàng | client/ui/QuestionPreparation.java hàm prepare |
| Chọn giao diện theo dạng câu | client/ui/QuestionRenderer.java hàm forType |
| Gửi đáp án từ client | client/ui/CommandGateway.java hàm send |
| Nhận đáp án trên server | server/match/MatchRounds.java hàm answer |
| Kiểm tra đúng sai và tính điểm | server/match/AnswerEvaluator.java và ScoreCalculator.java |
| Kết thúc và tái đấu | server/match/MatchResults.java và RematchCoordinator.java |
| Lưu kết quả trận | server/persistence/JdbcMatchRepository.java hàm save |
| Xuất bản quiz cộng đồng | server/quiz/CommunityQuizPublisher.java hàm publish |
| Xếp hạng và lịch sử | server/query/RankingService.java và HistoryService.java |

### 6.3 Tám câu hỏi chung về luồng phối hợp

**C01 Hãy giải thích từ lúc bấm gửi đáp án đến lúc hiện điểm mà không đi sâu kỹ thuật?**

“Giao diện thu đáp án và gửi lên server. Phần mạng xác định người gửi rồi chuyển cho phần trận đấu. Server kiểm tra đúng câu, đúng thời gian và chưa trả lời; sau đó chấm và ghi nhận. Khi cả hai trả lời hoặc hết giờ, server gửi đáp án cùng điểm để giao diện hiển thị.”

**C02 Nếu muốn sửa một chức năng thì làm sao biết mở file của ai trước?**

“Em xác định đang sửa đoạn nào của luồng. Sửa nút và cách hiển thị thì bắt đầu ở client của Việt; sửa điều phối yêu cầu và lời mời thì xem phần Minh; sửa luật chơi và điểm thì xem phần Tuấn; sửa dữ liệu và cách lưu thì xem phần Phúc. Sau đó lần theo các hàm gọi qua lại của chức năng đó.”

**C03 Vì sao client không tự chấm đáp án rồi gửi điểm lên server?**

“Vì người chơi có thể sửa chương trình phía mình. Client chỉ gửi câu trả lời; server có câu hỏi chuẩn, thời gian và luật chấm nên tự kiểm tra, tính điểm. Cả hai người cùng nhận kết quả từ một nơi để tránh mỗi máy hiển thị một kết quả khác nhau.”

**C04 Một người đã thấy câu hỏi thì vì sao vẫn phải chờ người còn lại?**

“Vì thấy nội dung chưa đồng nghĩa cả hai đã chuẩn bị xong. Client có thể cần tải ảnh. Mỗi bên báo QUESTION_READY, server đợi đủ rồi mới mở thời gian trả lời chung, giúp hai người bắt đầu theo cùng một mốc của trận.”

**C05 Đã hiện kết quả rồi mà chưa thấy trong lịch sử có phải luôn là lỗi không?**

“Không phải lúc nào cũng là lỗi. Kết quả có thể được hiển thị khi server đang lưu. Cần xem trạng thái lưu; khi lưu thành công, lịch sử mới đọc được trận đó. Nếu có lỗi lưu thì phần dữ liệu xử lý thử lại, chứ client không tự thêm một dòng lịch sử để thay thế.”

**C06 Đổi nội dung một quiz có làm lịch sử trận cũ đổi theo không?**

“Lịch sử dùng nội dung đã lưu tại thời điểm chơi nên vẫn giữ câu hỏi của trận cũ. Quiz cộng đồng còn tách bản nháp với bản xuất bản; sửa bản nháp chưa thay bản đang dùng. Cách làm này giúp chỉnh đề mà vẫn xem lại được đúng những gì đã thi đấu.”

**C07 Nếu bấm trên client mà không thấy kết quả thì lần theo luồng thế nào?**

“Em kiểm tra nút có được phép thao tác và có gửi yêu cầu chưa, tiếp theo xem kết nối có hoạt động không. Sau đó xem MessageRouter có chuyển đến đúng dịch vụ, dịch vụ có trả kết quả hay lỗi không. Cuối cùng xem client đã nhận và cập nhật trạng thái, màn hình theo kết quả đó chưa.”

**C08 Một module nhưng vẫn chia common server client thì có mâu thuẫn không?**

“Không. Một module nghĩa là cùng được tổ chức và build từ một pom.xml. Các package vẫn chia trách nhiệm để dễ tìm code: common dùng chung, server xử lý và lưu dữ liệu, client nhận thao tác và hiển thị. Khi chạy, client và server vẫn là các chương trình giao tiếp qua mạng.”

### 6.4 Thứ tự minh họa khi bảo vệ

1. Mở cấu trúc src/main/java/vn/edu/nhom7/quiz/ và giải thích một module cùng ba package.
2. Chạy server rồi mở hai client. Đăng nhập để cho thấy danh sách online thay đổi.
3. Chọn đề, gửi lời mời và nhận lời. Mở một file phía client và một file phía server của chức năng này.
4. Bấm sẵn sàng ở cả hai máy. Minh họa một người bấm trước thì trận vẫn chờ người còn lại.
5. Làm một câu: một người gửi trước, người kia gửi sau. Phân biệt xác nhận đã nhận với công bố đúng sai.
6. Minh họa một trợ giúp hoặc một dạng câu tương tác nếu bộ đề có hỗ trợ. Chỉ đúng nơi xử lý thay vì đọc hết file.
7. Hoàn thành trận, xem trạng thái lưu, mở lịch sử và giải thích điều kiện cập nhật xếp hạng.
8. Nếu còn thời gian, mở quiz cộng đồng để minh họa lưu bản nháp và xuất bản là hai bước khác nhau.

Khi trả lời một câu hỏi bất kỳ, có thể dùng mẫu: “Chức năng này bắt đầu ở file nào; khi người dùng làm gì thì gửi yêu cầu nào; server kiểm tra và xử lý ra sao; cuối cùng kết quả được trả về đâu.” Chỉ cần nêu tên lệnh khi nó giúp chỉ đường trong code, không cần học thuộc toàn bộ thông điệp hoặc đọc từng dòng.

## 7 Vị trí đối chiếu trong mã nguồn

Các vị trí dưới đây tính từ thư mục gốc của phần Java là src/main/java/vn/edu/nhom7/quiz/. Có thể dùng để mở đúng đoạn đang giải thích; không cần học thuộc số dòng. Trong bản Markdown trên GitHub, các vị trí được gắn liên kết đến file nguồn.

1. [server/network/SocketServer.java dòng 30](../src/main/java/vn/edu/nhom7/quiz/server/network/SocketServer.java#L30)

2. [server/network/ConnectionReader.java dòng 25](../src/main/java/vn/edu/nhom7/quiz/server/network/ConnectionReader.java#L25)

3. [server/network/MessageRouter.java dòng 63](../src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java#L63)

4. [server/network/MessageRouter.java dòng 169](../src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java#L169)

5. [server/session/SessionRegistry.java dòng 14](../src/main/java/vn/edu/nhom7/quiz/server/session/SessionRegistry.java#L14)

6. [server/session/OnlinePresenceService.java dòng 29](../src/main/java/vn/edu/nhom7/quiz/server/session/OnlinePresenceService.java#L29)

7. [server/challenge/ChallengeManager.java dòng 102](../src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java#L102)

8. [server/challenge/ChallengeManager.java dòng 163](../src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java#L163)

9. [server/session/SessionRegistry.java dòng 56](../src/main/java/vn/edu/nhom7/quiz/server/session/SessionRegistry.java#L56)

10. [server/challenge/ChallengeManager.java dòng 239](../src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java#L239)

11. [server/challenge/ChallengeManager.java dòng 268](../src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java#L268)

12. [server/network/ConnectionManager.java dòng 68](../src/main/java/vn/edu/nhom7/quiz/server/network/ConnectionManager.java#L68)

13. [server/match/MatchManager.java dòng 95](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java#L95)

14. [server/match/MatchRounds.java dòng 57](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L57)

15. [server/match/MatchRounds.java dòng 19](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L19)

16. [server/match/MatchRounds.java dòng 80](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L80)

17. [server/match/MatchRounds.java dòng 119](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L119)

18. [server/match/MatchRounds.java dòng 136](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L136)

19. [server/match/AnswerEvaluator.java dòng 13](../src/main/java/vn/edu/nhom7/quiz/server/match/AnswerEvaluator.java#L13)

20. [server/match/ScoreCalculator.java dòng 10](../src/main/java/vn/edu/nhom7/quiz/server/match/ScoreCalculator.java#L10)

21. [server/match/MatchRounds.java dòng 193](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchRounds.java#L193)

22. [server/match/MatchLifelines.java dòng 26](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchLifelines.java#L26)

23. [server/match/MatchChat.java dòng 18](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchChat.java#L18)

24. [server/match/MatchResults.java dòng 18](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchResults.java#L18)

25. [server/match/MatchResults.java dòng 123](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchResults.java#L123)

26. [server/match/RematchCoordinator.java dòng 29](../src/main/java/vn/edu/nhom7/quiz/server/match/RematchCoordinator.java#L29)

27. [client/ClientMain.java dòng 34](../src/main/java/vn/edu/nhom7/quiz/client/ClientMain.java#L34)

28. [client/ui/AuthForm.java dòng 77](../src/main/java/vn/edu/nhom7/quiz/client/ui/AuthForm.java#L77)

29. [client/ui/AppShell.java dòng 340](../src/main/java/vn/edu/nhom7/quiz/client/ui/AppShell.java#L340)

30. [client/ui/LobbyView.java dòng 96](../src/main/java/vn/edu/nhom7/quiz/client/ui/LobbyView.java#L96)

31. [client/ui/LobbyView.java dòng 208](../src/main/java/vn/edu/nhom7/quiz/client/ui/LobbyView.java#L208)

32. [client/ui/QuestionPreparation.java dòng 28](../src/main/java/vn/edu/nhom7/quiz/client/ui/QuestionPreparation.java#L28)

33. [client/ui/QuestionRenderer.java dòng 10](../src/main/java/vn/edu/nhom7/quiz/client/ui/QuestionRenderer.java#L10)

34. [client/ui/CommandGateway.java dòng 22](../src/main/java/vn/edu/nhom7/quiz/client/ui/CommandGateway.java#L22)

35. [client/state/ClientStateReducer.java dòng 22](../src/main/java/vn/edu/nhom7/quiz/client/state/ClientStateReducer.java#L22)

36. [client/state/ClientState.java dòng 46](../src/main/java/vn/edu/nhom7/quiz/client/state/ClientState.java#L46)

37. [client/ui/AppShell.java dòng 327](../src/main/java/vn/edu/nhom7/quiz/client/ui/AppShell.java#L327)

38. [client/ui/CommunityQuizView.java dòng 517](../src/main/java/vn/edu/nhom7/quiz/client/ui/CommunityQuizView.java#L517)

39. [client/ui/CommunityQuizView.java dòng 531](../src/main/java/vn/edu/nhom7/quiz/client/ui/CommunityQuizView.java#L531)

40. [client/ui/HistoryView.java dòng 54](../src/main/java/vn/edu/nhom7/quiz/client/ui/HistoryView.java#L54)

41. [server/auth/AuthService.java dòng 19](../src/main/java/vn/edu/nhom7/quiz/server/auth/AuthService.java#L19)

42. [server/auth/AuthService.java dòng 34](../src/main/java/vn/edu/nhom7/quiz/server/auth/AuthService.java#L34)

43. [server/auth/JdbcUserRepository.java dòng 45](../src/main/java/vn/edu/nhom7/quiz/server/auth/JdbcUserRepository.java#L45)

44. [server/auth/PasswordHasher.java dòng 20](../src/main/java/vn/edu/nhom7/quiz/server/auth/PasswordHasher.java#L20)

45. [server/quiz/JdbcQuizRepository.java dòng 70](../src/main/java/vn/edu/nhom7/quiz/server/quiz/JdbcQuizRepository.java#L70)

46. [server/quiz/JdbcQuizRepository.java dòng 99](../src/main/java/vn/edu/nhom7/quiz/server/quiz/JdbcQuizRepository.java#L99)

47. [server/match/MatchPayloads.java dòng 90](../src/main/java/vn/edu/nhom7/quiz/server/match/MatchPayloads.java#L90)

48. [server/quiz/CommunityQuizService.java dòng 23](../src/main/java/vn/edu/nhom7/quiz/server/quiz/CommunityQuizService.java#L23)

49. [server/quiz/CommunityQuizPublisher.java dòng 23](../src/main/java/vn/edu/nhom7/quiz/server/quiz/CommunityQuizPublisher.java#L23)

50. [server/media/MediaService.java dòng 53](../src/main/java/vn/edu/nhom7/quiz/server/media/MediaService.java#L53)

51. [server/persistence/PersistenceService.java dòng 85](../src/main/java/vn/edu/nhom7/quiz/server/persistence/PersistenceService.java#L85)

52. [server/persistence/JdbcMatchRepository.java dòng 34](../src/main/java/vn/edu/nhom7/quiz/server/persistence/JdbcMatchRepository.java#L34)

53. [server/ServerRuntime.java dòng 165](../src/main/java/vn/edu/nhom7/quiz/server/ServerRuntime.java#L165)

54. [server/query/QueryDispatcher.java dòng 39](../src/main/java/vn/edu/nhom7/quiz/server/query/QueryDispatcher.java#L39)

55. [server/query/RankingService.java dòng 16](../src/main/java/vn/edu/nhom7/quiz/server/query/RankingService.java#L16)

56. [server/query/HistoryService.java dòng 23](../src/main/java/vn/edu/nhom7/quiz/server/query/HistoryService.java#L23)

57. [server/query/HistoryService.java dòng 63](../src/main/java/vn/edu/nhom7/quiz/server/query/HistoryService.java#L63)

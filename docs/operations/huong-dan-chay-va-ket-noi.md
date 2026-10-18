# Quiz Arena — Hướng dẫn chạy, mở cổng và kết nối với người khác

Tài liệu dành cho người chạy **Quiz Arena trên Windows**, từ mở lại ứng dụng đến tổ chức một trận đấu trên hai máy. Các lệnh sử dụng **Windows PowerShell**.

> Quiz Arena là ứng dụng desktop JavaFX. `localhost:5555` là địa chỉ kết nối TCP nhập **trong ứng dụng**, không phải trang web để mở bằng Chrome hoặc Edge.

## Mục lục

1. [Hiểu mô hình kết nối](#1-hiểu-mô-hình-kết-nối)
2. [Chạy lại nhanh trên máy đã thiết lập](#2-chạy-lại-nhanh-trên-máy-đã-thiết-lập)
3. [Thiết lập lần đầu](#3-thiết-lập-lần-đầu)
4. [Thử hai người trên cùng một máy](#4-thử-hai-người-trên-cùng-một-máy)
5. [Mở cổng và chơi trên hai máy qua LAN](#5-mở-cổng-và-chơi-trên-hai-máy-qua-lan)
6. [Ghép trận và chơi](#6-ghép-trận-và-chơi)
7. [Kết nối khi ở hai mạng khác nhau](#7-kết-nối-khi-ở-hai-mạng-khác-nhau)
8. [Đổi cổng, tắt và mở lại](#8-đổi-cổng-tắt-và-mở-lại)
9. [Xử lý lỗi thường gặp](#9-xử-lý-lỗi-thường-gặp)
10. [Kịch bản thực hành hai máy](#10-kịch-bản-thực-hành-hai-máy)

## 1. Hiểu mô hình kết nối

```text
Máy A — máy chủ                         Máy B — người chơi khác
┌──────────────────────────┐            ┌──────────────────────┐
│ MySQL                    │            │ Client JavaFX        │
│       ↕ JDBC             │            │ Host: IP của máy A   │
│ Quiz Server — TCP 5555   │◄───────────│ Port: 5555           │
│       ↕ TCP              │            └──────────────────────┘
│ Client JavaFX            │
│ Host: localhost          │
│ Port: 5555               │
└──────────────────────────┘
```

| Thành phần | Chạy ở đâu? | Cần gì? |
| --- | --- | --- |
| MySQL | Máy A | Database và tài khoản MySQL |
| Quiz Server | Máy A | JDK 21, cấu hình database, file JAR đã build |
| Client của người chơi A | Máy A hoặc một máy khác | JDK 21, Maven và mã nguồn để chạy theo tài liệu này |
| Client của người chơi B | Máy B | JDK 21, Maven và cùng phiên bản mã nguồn |

**Chỉ cần một Quiz Server cho cả nhóm.** Người chơi phải đăng nhập vào cùng server bằng các tài khoản khác nhau. Database và tài khoản người chơi thuộc server đó.

| Địa chỉ/cổng | Ý nghĩa |
| --- | --- |
| `localhost` hoặc `127.0.0.1` | Chính máy đang chạy client; chỉ đúng nếu server cũng chạy trên máy đó |
| `192.168.1.20` | Ví dụ IP LAN của máy chủ; thay bằng IP thực tế của máy A |
| `5555/TCP` | Cổng Quiz Server, dùng để đăng nhập và thi đấu |
| `3306/TCP` | Cổng MySQL mặc định, chỉ server cần truy cập |
| `0.0.0.0` | Địa chỉ server dùng để lắng nghe các giao diện mạng; không nhập địa chỉ này vào client |

Để chơi LAN, mở **cổng TCP 5555 trên máy chủ**. Máy B không cần cài MySQL hoặc nhận cấu hình mật khẩu database của máy A.

## 2. Chạy lại nhanh trên máy đã thiết lập

Áp dụng khi đã có database `quiz_arena`, file `config/server.local.properties` hợp lệ và đã build repo. Thư mục hiện tại của dự án trên máy đã được thiết lập là:

```text
C:\Users\Admin\Desktop\quizz
```

### Bước 1 — Kiểm tra MySQL

```powershell
Get-Service -Name '*mysql*'
```

Trạng thái cần là `Running`. Nếu dịch vụ tên `MySQL80` đang dừng, mở PowerShell bằng **Run as administrator**, rồi chạy:

```powershell
Start-Service -Name 'MySQL80'
```

Tên dịch vụ có thể khác, ví dụ `MySQL84`; dùng tên hiển thị từ lệnh kiểm tra.

### Bước 2 — Mở terminal chạy server

Mở PowerShell tại thư mục gốc repo. Có thể mở thư mục đó bằng File Explorer rồi chọn **Open in Terminal**.

Trên máy hiện tại, gọi thẳng Java 21 để tránh Java 17 đang nằm trước trong `PATH`:

```powershell
& 'C:\Program Files\Java\jdk-21\bin\java.exe' -jar server/target/quiz-arena-server.jar --config=config/server.local.properties
```

Khi thành công, terminal có dòng:

```text
Quiz Arena listening on 5555
```

Giữ terminal này mở trong suốt thời gian sử dụng ứng dụng.

### Bước 3 — Mở terminal khác chạy client

Terminal mới cũng phải đứng tại thư mục gốc repo:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -f client/pom.xml javafx:run
```

Nếu JDK 21 được cài ở đường dẫn khác, thay đường dẫn trong hai ví dụ trên.

### Bước 4 — Kết nối và đăng nhập

1. Trong màn hình đầu của Quiz Arena, mở **Kết nối máy chủ** nếu phần này đang thu gọn.
2. Nhập **Host:** `localhost`, **Port:** `5555`.
3. Nhấn **Kết nối**; chờ thông báo **Đã kết nối. Bạn có thể đăng nhập.**
4. Nhập tên tài khoản và mật khẩu, nhấn **Đăng nhập**.

Các tài khoản demo đã tạo trong lần thiết lập máy hiện tại:

| Tài khoản | Mật khẩu |
| --- | --- |
| `demo1` | `DemoQuiz123!` |
| `demo2` | `DemoQuiz123!` |
| `demo3` | `DemoQuiz123!` |
| `demo4` | `DemoQuiz123!` |

Mật khẩu ở bảng này là **mật khẩu đăng nhập ứng dụng**, không phải mật khẩu MySQL.

## 3. Thiết lập lần đầu

Máy đã thiết lập theo mục 2 không cần tạo lại database hoặc nạp lại SQL mỗi lần mở app.

### 3.1. Cài công cụ và chọn Java 21

Theo cấu hình và README của repo, cần **JDK 21**, **Maven 3.9+** và **MySQL 8.4** trên máy chủ. Máy chỉ chạy client cần JDK 21 và Maven.

> Máy hiện tại đã chạy được ứng dụng với MySQL 8.0.43. Đây là kết quả thiết lập cục bộ; khi cài mới, dùng MySQL 8.4 theo hướng dẫn của dự án.

Mở PowerShell ở thư mục gốc repo, chọn JDK 21 rồi kiểm tra:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -version
```

`mvn -version` phải báo Java 21. Hai dòng gán `$env:` chỉ có hiệu lực trong terminal hiện tại; chạy lại ở mỗi terminal mới hoặc thiết lập `JAVA_HOME` và `PATH` trong Windows Environment Variables.

### 3.2. Build repo

Tại thư mục có file `pom.xml` cha:

```powershell
mvn clean install
```

Chờ `BUILD SUCCESS`. Lệnh build cả `common`, `server`, `client`, chạy kiểm thử thông thường và cài các module vào kho Maven cục bộ. Lần đầu cần Internet để tải thư viện.

File server sau build:

```text
server/target/quiz-arena-server.jar
```

Máy B chỉ chạy client có thể build các module cần thiết bằng:

```powershell
mvn -pl client -am clean install
```

### 3.3. Tạo database mới và nạp dữ liệu

Mở MySQL bằng tài khoản quản trị. Nếu `mysql` đã có trong `PATH`:

```powershell
mysql --default-character-set=utf8mb4 -u root -p
```

Nếu chưa có trong `PATH`, dùng đường dẫn thực tế; ví dụ trên máy hiện tại:

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --default-character-set=utf8mb4 -u root -p
```

Nhập mật khẩu khi được hỏi. Trong cửa sổ MySQL, chạy các lệnh SQL dưới đây **khi chưa có database `quiz_arena`**. Thay chuỗi mật khẩu ví dụ bằng mật khẩu bạn chọn:

```sql
CREATE DATABASE quiz_arena CHARACTER SET utf8mb4;
CREATE USER 'quiz'@'localhost' IDENTIFIED BY 'THAY_BANG_MAT_KHAU_CUA_BAN';
GRANT SELECT, INSERT, UPDATE, DELETE ON quiz_arena.* TO 'quiz'@'localhost';
USE quiz_arena;
SOURCE database/001_schema.sql;
SOURCE database/002_seed.sql;
SOURCE database/003_community_quizzes.sql;
SELECT version, asset_pack_version FROM SCHEMA_METADATA;
SELECT COUNT(*) AS quiz_count FROM QUIZZES;
SELECT COUNT(*) AS question_count FROM QUESTIONS;
EXIT;
```

Mở MySQL từ thư mục gốc repo để các đường dẫn `SOURCE database/...` được giải quyết đúng. `--default-character-set=utf8mb4` giúp nạp đúng nội dung câu hỏi tiếng Việt. Tài khoản quản trị chạy SQL; tài khoản `quiz` dùng cho server sau đó.

Kết quả cần có **schema version 3**, **asset pack 1**, **3 quiz**, **60 câu hỏi**. Nếu MySQL báo lỗi tại một bước, xử lý lỗi trước khi chạy bước tiếp theo.

**Nếu đã có database:** kiểm tra `SCHEMA_METADATA` trước. Bản 3 thì dùng tiếp, không chạy lại migration `003`. Bản 1 cần dừng server, sao lưu database và `data/media`, rồi nâng cấp bằng `003` một lần; xem [hướng dẫn database](database-setup.md). Không xóa database để xử lý lỗi kết nối.

### 3.4. Tạo cấu hình server

Nếu chưa có file cấu hình riêng:

```powershell
Copy-Item -LiteralPath 'config/server.properties.example' -Destination 'config/server.local.properties'
```

Mở file `config/server.local.properties` bằng trình soạn thảo và điền:

```properties
db.url=jdbc:mysql://127.0.0.1:3306/quiz_arena
db.username=quiz
db.password=THAY_BANG_MAT_KHAU_CUA_BAN
server.port=5555
```

Nếu dùng tài khoản MySQL khác, thay `db.username` và `db.password` tương ứng. Không sao chép file mẫu đè lên cấu hình đã điền. File này được Git bỏ qua và chỉ cần trên máy chủ.

Server ưu tiên biến môi trường `QUIZ_DB_URL`, `QUIZ_DB_USER`, `QUIZ_DB_PASSWORD`, `QUIZ_PORT` hơn giá trị trong file. Nếu sửa file mà server vẫn dùng cấu hình cũ, kiểm tra các biến này trong Windows và terminal khởi chạy.

### 3.5. Tạo tài khoản demo hoặc tự đăng ký

Sau khi database và file cấu hình đã sẵn sàng, chạy tại gốc repo với Java 21:

```powershell
java -cp server/target/quiz-arena-server.jar vn.edu.nhom7.quiz.server.tools.DemoUserSeeder
```

Công cụ tạo `demo1`–`demo4`, mật khẩu `DemoQuiz123!`. Nếu tài khoản đã tồn tại, công cụ giữ nguyên tài khoản và không đặt lại mật khẩu.

Để tự đăng ký: mở client, kết nối server, chọn **Đăng ký**, nhập tên đăng nhập, tên hiển thị, mật khẩu, xác nhận mật khẩu và avatar; nhấn **Tạo tài khoản**. Sau khi thành công, chuyển sang **Đăng nhập**.

Cuối cùng, chạy server và client theo mục 2.

## 4. Thử hai người trên cùng một máy

1. Chạy **một** server.
2. Mở client thứ nhất bằng `mvn -f client/pom.xml javafx:run`.
3. Mở terminal mới, chọn JDK 21 như mục 2, chạy lại lệnh client để mở cửa sổ thứ hai.
4. Cả hai cửa sổ kết nối **Host:** `localhost`, **Port:** `5555`.
5. Cửa sổ thứ nhất đăng nhập `demo1`, cửa sổ thứ hai đăng nhập `demo2`.
6. Ghép trận theo mục 6.

Không mở server thứ hai trên cùng cổng. Không cần thiết lập router hoặc mở cổng LAN để thử hai client trên cùng máy.

## 5. Mở cổng và chơi trên hai máy qua LAN

### 5.1. Chuẩn bị mạng

Máy A và B cần có đường kết nối mạng với nhau, thường là cùng Wi-Fi hoặc cùng router qua dây LAN. Wi-Fi khách, mạng trường học hoặc chức năng **AP/client isolation** có thể chặn hai thiết bị giao tiếp dù cùng tên Wi-Fi.

Trên máy A, chạy:

```powershell
ipconfig
```

Tìm **IPv4 Address** của card **Wi-Fi/Ethernet đang kết nối mạng chung với máy B**. Ví dụ:

```text
IPv4 Address: 192.168.1.20
```

Không chọn IP của Docker, WSL hoặc card mạng ảo nếu đang chơi qua Wi-Fi/Ethernet thông thường. Gửi cho người chơi B **IP này và cổng `5555`**. IP có thể đổi sau khi kết nối lại mạng.

### 5.2. Kiểm tra server có đang lắng nghe

Chạy server trên máy A, sau đó kiểm tra bằng một terminal khác:

```powershell
Get-NetTCPConnection -State Listen -LocalPort 5555
```

Cần có dòng `Listen`. `LocalAddress` có thể hiển thị `0.0.0.0` hoặc `::` tùy hệ thống. Repo khởi chạy server với địa chỉ bind `0.0.0.0`.

Nếu không có dòng lắng nghe, kiểm tra terminal server và lỗi MySQL trước khi xử lý firewall.

### 5.3. Mở cổng TCP 5555 trên Windows Firewall

Thực hiện **trên máy A**, bằng PowerShell **Run as administrator**.

Kiểm tra loại mạng đang sử dụng:

```powershell
Get-NetConnectionProfile
```

Ví dụ dưới đây áp dụng cho mạng **Private**, phù hợp Wi-Fi/LAN gia đình hoặc nhóm tin cậy. Nếu mạng chung đang là Public, vào **Settings → Network & internet → Wi-Fi/Ethernet → Network profile type → Private** khi đó là mạng tin cậy. Mạng do tổ chức quản lý có thể cần quản trị viên cho phép.

Tạo rule một lần:

```powershell
New-NetFirewallRule -Name 'QuizArena-TCP-5555' -DisplayName 'Quiz Arena TCP 5555' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5555 -Profile Private -RemoteAddress LocalSubnet
```

Rule cho phép thiết bị trong các subnet cục bộ kết nối tới cổng ứng dụng. Chạy lại nhiều lần không cần thiết; nếu rule đã tồn tại, kiểm tra và bật lại:

```powershell
Get-NetFirewallRule -Name 'QuizArena-TCP-5555' | Format-Table Name, Enabled, Direction, Action, Profile
Get-NetFirewallRule -Name 'QuizArena-TCP-5555' | Get-NetFirewallPortFilter
Enable-NetFirewallRule -Name 'QuizArena-TCP-5555'
```

**Cách dùng giao diện:** mở **Windows Defender Firewall with Advanced Security → Inbound Rules → New Rule → Port → TCP → Specific local ports: 5555 → Allow the connection → Private → đặt tên Quiz Arena TCP 5555**. Có thể sửa tab **Scope** của rule để giới hạn remote IP thành **Local subnet** như lệnh trên.

Nếu tạo rule bằng giao diện, Windows có thể cấp `Name` nội bộ khác. Khi kiểm tra/bật/gỡ bằng PowerShell, thay `-Name 'QuizArena-TCP-5555'` bằng `-DisplayName 'Quiz Arena TCP 5555'` để chọn đúng rule đó.

Máy B chỉ chủ động kết nối ra server nên thường không cần rule inbound. Mạng LAN thông thường không cần port forwarding trên router. Cổng MySQL `3306` vẫn để server truy cập nội bộ.

### 5.4. Kiểm tra từ máy B

Trên máy B, thay IP ví dụ bằng IP thực của máy A:

```powershell
Test-NetConnection -ComputerName 192.168.1.20 -Port 5555
```

Kết quả mong đợi:

```text
TcpTestSucceeded : True
```

`True` nghĩa là kết nối TCP tới cổng thành công; cần tiếp tục kết nối trong app để kiểm tra phiên bản và đăng nhập. Ping có thể bị chặn dù TCP vẫn hoạt động, nên dùng kết quả kiểm tra cổng làm căn cứ.

Nếu `False`, kiểm tra theo thứ tự: server có đang chạy → đúng IP và cổng → đúng profile firewall → hai máy có thể giao tiếp → mạng có chặn thiết bị hay không.

### 5.5. Mở client trên máy B

1. Lấy cùng phiên bản repo với máy A.
2. Chọn JDK 21 và build theo mục 3.1–3.2.
3. Chạy `mvn -f client/pom.xml javafx:run` từ gốc repo.
4. Trong **Kết nối máy chủ**, nhập **Host:** `192.168.1.20`, **Port:** `5555`.
5. Nhấn **Kết nối**, đăng nhập `demo2` hoặc tài khoản riêng đã đăng ký trên server A.

Chỉ nhập IP vào Host; không thêm `http://` hoặc `:5555`. Cổng nhập ở ô Port. Máy A có thể đăng nhập `demo1` với Host `localhost`.

## 6. Ghép trận và chơi

### 6.1. Gửi và nhận lời mời

Ứng dụng ghép trận bằng **lời thách đấu người đang online**, không dùng mã phòng.

**Người chơi A:**

1. Đăng nhập để vào sảnh.
2. Chọn danh mục, chọn một quiz khả dụng bằng nút **Chọn bộ câu hỏi**.
3. Nếu bảng chi tiết quiz xuất hiện, nhấn **Đóng** để tiếp tục chọn người chơi trong sảnh.
4. Trong danh sách **Cùng đấu trí**, chọn người chơi B có trạng thái **Sẵn sàng**.
5. Nhấn **Gửi lời thách đấu**.

**Người chơi B:**

1. Khi lời mời xuất hiện, xem tên đối thủ và bộ câu hỏi.
2. Nhấn **Chấp nhận** để vào trận, hoặc **Từ chối**.

Người gửi có thể nhấn **Hủy lời mời** khi đang chờ. Lời mời có thời hạn **20 giây**; hết hạn thì gửi lại. Người đang bận trong lời mời hoặc trận không nhận thêm lời mời mới.

Nếu chưa thấy nhau, cả hai xác nhận đang đăng nhập vào **cùng server**, rồi nhấn **Tải lại** ở sảnh. Dùng hai tài khoản khác nhau.

### 6.2. Sẵn sàng và bắt đầu

Sau khi chấp nhận, cả hai vào phòng chờ và nhấn **Tôi đã sẵn sàng** trong thời hạn **30 giây**. Trận bắt đầu khi đủ hai người sẵn sàng, sau đó có đếm ngược và chuẩn bị câu hỏi/ảnh.

| Loại quiz | Số câu mỗi trận | Điểm tối đa |
| --- | --- | --- |
| Quiz hệ thống | 10 câu được chọn từ ngân hàng | 30 |
| Quiz cộng đồng | Toàn bộ 1–50 câu đã xuất bản | 3 × số câu |

Mỗi câu có **15 giây** trả lời. Thời gian tải ảnh và chuẩn bị câu diễn ra trước thời gian trả lời.

### 6.3. Trả lời, chat và điểm số

| Loại câu hỏi | Cách trả lời |
| --- | --- |
| Một đáp án | Chọn một phương án; câu trả lời gửi ngay |
| Nhiều đáp án | Chọn tất cả phương án đúng rồi nhấn gửi |
| Đúng/sai | Chọn Đúng hoặc Sai; gửi ngay |
| Trả lời ngắn | Nhập đáp án rồi nhấn gửi |

Mỗi người chỉ gửi một câu trả lời hợp lệ cho mỗi câu. Gửi xong thì chờ đối thủ hoặc hết giờ; server mới công bố đáp án và điểm. Trả lời ngắn không phân biệt hoa/thường, nhưng dấu tiếng Việt vẫn có ý nghĩa theo đáp án được khai báo.

Điểm cho câu đúng: **3 điểm** nếu server nhận trong 5 giây đầu; **2 điểm** nếu trên 5 đến 10 giây; **1 điểm** nếu trên 10 giây và còn hạn. Sai hoặc hết giờ được **0 điểm**. Độ trễ mạng có thể ảnh hưởng thời điểm server nhận đáp án.

Chat trong trận chỉ tới đối thủ: **Enter** để gửi, **Shift+Enter** để xuống dòng. Chat không dừng thời gian trả lời.

### 6.4. Kết quả và đấu lại

- Người có tổng điểm cao hơn thắng; bằng điểm thì hòa.
- Chờ trạng thái lưu `SAVED` trước khi kiểm tra dữ liệu mới trong **Lịch sử** hoặc **Xếp hạng**. `PENDING` là đang chờ lưu; `FAILED` là gặp lỗi lưu và server sẽ tiếp tục thử khi còn chạy.
- Quiz hệ thống đủ điều kiện cập nhật bảng xếp hạng; quiz cộng đồng lưu lịch sử riêng nhưng không cộng thống kê xếp hạng toàn cục.
- Có thể yêu cầu đấu lại ở màn hình kết quả; đối thủ nhấn **Chấp nhận đấu lại**. Phiên kết quả có thời hạn 60 giây. Trận mới bắt đầu với điểm 0 và cả hai cần sẵn sàng lại.
- Rời trận hoặc mất kết nối khi đã bắt đầu có thể bị xử thua bỏ cuộc. Kết nối lại không tiếp tục được trận cũ.

## 7. Kết nối khi ở hai mạng khác nhau

IP dạng `192.168.x.x` hoặc `10.x.x.x` là IP nội bộ, không tự truy cập được từ một mạng Internet khác. Mở Windows Firewall chỉ cho phép kết nối tới máy; không tự tạo đường đi xuyên router.

### 7.1. Dùng mạng riêng ảo

Cách phù hợp để chơi từ xa là cho hai máy tham gia cùng một **VPN/mạng riêng ảo** đã được thiết lập:

1. Đảm bảo hai máy có thể giao tiếp qua mạng riêng ảo.
2. Chạy MySQL và Quiz Server trên máy A như bình thường.
3. Lấy IP của máy A trên mạng riêng ảo; máy B nhập IP này vào Host.
4. Tạo rule firewall theo **profile thực của giao diện mạng riêng ảo**, cho phép TCP 5555 từ IP riêng ảo của máy B. Rule `LocalSubnet` ở mục 5 không đảm bảo bao phủ địa chỉ VPN.
5. Từ máy B chạy `Test-NetConnection -ComputerName <IP_MAY_A> -Port 5555`, thay `<IP_MAY_A>` bằng IP thật rồi kết nối trong app.

Ví dụ rule khi giao diện VPN thuộc profile **Private**; thay IP `10.10.0.2` bằng IP VPN của máy B:

```powershell
New-NetFirewallRule -Name 'QuizArena-VPN-5555' -DisplayName 'Quiz Arena VPN TCP 5555' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5555 -Profile Private -RemoteAddress 10.10.0.2
```

### 7.2. Nếu dùng port forwarding trên router

Repo hiện được thiết kế cho mạng LAN tin cậy và **chưa có TLS cho TCP**, nên ưu tiên mạng riêng ảo khi chơi từ xa. Nếu triển khai trực tiếp qua router, cấu hình cần có:

| Mục router | Giá trị ví dụ |
| --- | --- |
| Protocol | TCP |
| External/WAN port | 5555 |
| Internal/LAN IP | IP cố định hoặc DHCP reservation của máy A, ví dụ `192.168.1.20` |
| Internal/LAN port | 5555 |

Cần thêm rule firewall phù hợp với địa chỉ nguồn từ xa; rule `LocalSubnet` ở mục 5 chỉ dành cho mạng cục bộ. Máy B nhập **IP public hoặc tên miền của phía máy A**, Port `5555`. Không chuyển tiếp cổng MySQL `3306` cho người chơi.

Nếu nhà mạng dùng **CGNAT** hoặc có hai lớp NAT, một rule port forwarding trên router của bạn có thể chưa đủ. Kiểm tra từ máy B ở mạng ngoài; một số router không hỗ trợ dùng IP public để quay lại server từ chính mạng LAN. Cấu hình cụ thể phụ thuộc router và mạng, không được xác nhận bằng việc chạy thành công trên `localhost`.

## 8. Đổi cổng, tắt và mở lại

### Đổi cổng ứng dụng

Ví dụ đổi từ `5555` sang `5556`:

1. Dừng server bằng `Ctrl+C` ở terminal chạy server.
2. Sửa `server.port=5556` trong `config/server.local.properties`; nếu có `QUIZ_PORT`, cập nhật hoặc bỏ biến ghi đè tương ứng.
3. Khởi động lại server; kiểm tra dòng `listening on 5556`.
4. Cho cả hai client nhập Port `5556`.
5. Nếu chơi LAN, tạo rule TCP cho cổng mới trên máy chủ, ví dụ:

```powershell
New-NetFirewallRule -Name 'QuizArena-TCP-5556' -DisplayName 'Quiz Arena TCP 5556' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5556 -Profile Private -RemoteAddress LocalSubnet
```

Thay đổi cổng ứng dụng không yêu cầu thay cổng MySQL trong `db.url`. Nếu dùng router/VPN, cập nhật cấu hình liên quan tới cổng ứng dụng.

### Tắt và mở lại

Đóng các cửa sổ client, rồi dùng `Ctrl+C` ở terminal server để dừng bình thường. Server có thể chờ tối đa 10 giây cho kết quả đang lưu; các kết quả chưa commit còn trong RAM có thể mất nếu tắt cưỡng bức.

Lần sau chỉ cần MySQL đang chạy → chạy server → chạy client → kết nối → đăng nhập. Không cần build lại nếu mã nguồn và bản build còn nguyên; sau khi đổi mã nguồn hoặc cập nhật repo, dừng app rồi build lại.

Nếu muốn gỡ rule LAN đã tạo, mở PowerShell quản trị và chạy:

```powershell
Remove-NetFirewallRule -Name 'QuizArena-TCP-5555'
```

Thay tên rule tương ứng nếu dùng cổng khác hoặc rule VPN.

## 9. Xử lý lỗi thường gặp

| Hiện tượng | Kiểm tra và cách xử lý |
| --- | --- |
| Mở `localhost:5555` trong trình duyệt không được | Mở client JavaFX và nhập Host/Port trong **Kết nối máy chủ** |
| `mvn` hoặc `java` không được nhận diện | Kiểm tra cài JDK/Maven và `PATH`; mở terminal mới sau khi thay biến môi trường |
| `release version 21 not supported` | `mvn -version` đang dùng JDK cũ; đặt `JAVA_HOME` và thêm JDK 21 vào đầu `PATH` |
| Thiếu `quiz-arena-common` khi mở client | Build từ gốc bằng `mvn clean install` hoặc `mvn -pl client -am clean install` |
| Không tìm thấy JAR server | Kiểm tra đang đứng tại gốc repo và đã build thành công |
| `mysql` không được nhận diện | Gọi `mysql.exe` bằng đường dẫn đầy đủ như mục 3.3 |
| Server lỗi kết nối database | Kiểm tra dịch vụ MySQL, cổng trong JDBC URL, database và cấu hình user/password |
| MySQL báo `Access denied` | Kiểm tra tài khoản, mật khẩu, quyền trên `quiz_arena` và biến `QUIZ_DB_*` ghi đè |
| Server báo sai phiên bản schema | Kiểm tra `SCHEMA_METADATA`; schema hiện tại cần version 3; không chạy migration lặp lại |
| Server không bind được cổng | Có tiến trình đang chiếm cổng; dùng lệnh dưới để xác định trước khi dừng hoặc đổi cổng |
| Máy A kết nối được nhưng B không được | B phải dùng IP máy A, không dùng `localhost`; kiểm tra firewall/profile và mạng chung |
| `TcpTestSucceeded : False` | Kiểm tra server đang Listen, đúng IP/cổng, rule inbound, AP isolation và khả năng định tuyến |
| Cổng TCP thông nhưng app từ chối phiên bản | Build server/client từ cùng phiên bản mã nguồn, chạy client thật không có `--fixture` |
| Không thấy bạn chơi trong sảnh | Hai người phải đăng nhập cùng server, dùng tài khoản khác nhau; nhấn **Tải lại** |
| Nút gửi thách đấu bị vô hiệu hóa | Chọn quiz khả dụng và một đối thủ **Sẵn sàng**; người đang bận không nhận lời mời |
| Chấp nhận lời mời nhưng trận chưa chạy | Cả hai phải nhấn **Tôi đã sẵn sàng** trong phòng chờ |
| Quiz tự tạo chưa hiện ở sảnh | Lưu câu hỏi và **Xuất bản**; bản nháp chưa công khai |
| Lịch sử/xếp hạng chưa cập nhật | Chờ `SAVED`; nếu lỗi, kiểm tra database; quiz cộng đồng không cập nhật xếp hạng toàn cục |
| Trận mẫu tự chạy, không đấu thật được | Bỏ `--fixture`, chạy server và mở client bằng lệnh ở mục 2 |
| Ngắt Wi-Fi rồi không trở lại trận cũ | Kết nối lại tạo phiên mới; đăng nhập lại và gửi lời mời cho trận mới |

Xác định tiến trình đang giữ cổng trên máy chủ:

```powershell
Get-NetTCPConnection -State Listen -LocalPort 5555 | Format-Table LocalAddress, LocalPort, OwningProcess
```

Lấy giá trị `OwningProcess`, ví dụ `12345`, rồi kiểm tra:

```powershell
Get-Process -Id 12345
```

Nếu đó là server đang chạy đúng, dùng tiếp thay vì mở thêm. Nếu cần dừng, ưu tiên `Ctrl+C` tại terminal của tiến trình đó.

## 10. Kịch bản thực hành hai máy

Giả sử máy A có IP `192.168.1.20`:

| Bước | Máy A | Máy B |
| --- | --- | --- |
| 1 | Kiểm tra MySQL và chạy Quiz Server | Kết nối mạng có thể truy cập máy A |
| 2 | Xác nhận `listening on 5555`, mở rule LAN | Kiểm tra `Test-NetConnection -ComputerName 192.168.1.20 -Port 5555` |
| 3 | Mở client, Host `localhost`, Port `5555` | Mở client, Host `192.168.1.20`, Port `5555` |
| 4 | Đăng nhập `demo1` | Đăng nhập `demo2` |
| 5 | Chọn quiz, chọn đối thủ, gửi thách đấu | Nhấn **Chấp nhận** |
| 6 | Nhấn **Tôi đã sẵn sàng** | Nhấn **Tôi đã sẵn sàng** |
| 7 | Trả lời câu hỏi, thử chat | Trả lời câu hỏi, nhận chat |
| 8 | Xem kết quả và chờ `SAVED` | Xem kết quả và chờ `SAVED` |

Hai tài khoản demo dùng mật khẩu `DemoQuiz123!` nếu đã được tạo bằng công cụ demo và chưa có mật khẩu khác từ trước.

**Kết quả cần quan sát:** hai người thấy nhau trong sảnh; nhận được lời mời; cùng vào trận; câu hỏi và kết quả đồng bộ; chat chỉ tới đối thủ; trận đã lưu xuất hiện trong lịch sử. Kết nối thành công trên một máy không thay thế việc kiểm tra thực tế qua hai máy LAN.

## Tài liệu tham khảo

- [README của dự án](../../README.md): công nghệ, kiến trúc và hướng dẫn tổng thể.
- [Thiết lập database](database-setup.md): tài khoản, schema và nâng cấp.
- [Vận hành lưu kết quả](persistence-runbook.md): lưu, thử lại và phục hồi khi database lỗi.
- [Kịch bản demo nhiều client](../demo/demo-script.md).
- [Giao thức TCP](../protocol/protocol-v1.md).
- [Microsoft: New-NetFirewallRule](https://learn.microsoft.com/en-us/powershell/module/netsecurity/new-netfirewallrule).
- [Microsoft: Test-NetConnection](https://learn.microsoft.com/en-us/powershell/module/nettcpip/test-netconnection).

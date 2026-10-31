# Hướng dẫn chạy, kết nối 2 máy và triển khai server lên VPS

Tài liệu này tóm tắt các bước chạy Quiz Arena, cách để hai máy khác nhau nhìn thấy nhau,
và cách đưa phần server lên VPS.

---

## 1. Các bước chạy cơ bản

### Máy chạy server (cần JDK 21 + Maven + MySQL)

```sh
# 1. Build
mvn clean install

# 2. Tạo database (chỉ 1 lần)
mysql -u root -p
```

```sql
CREATE DATABASE quiz_arena CHARACTER SET utf8mb4;
CREATE USER 'quiz'@'localhost' IDENTIFIED BY 'MAT_KHAU_RIENG';
GRANT SELECT, INSERT, UPDATE, DELETE ON quiz_arena.* TO 'quiz'@'localhost';
EXIT;
```

```sh
mysql -u root -p quiz_arena < database/001_schema.sql
mysql -u root -p quiz_arena < database/002_seed.sql
mysql -u root -p quiz_arena < database/003_community_quizzes.sql
mysql -u root -p quiz_arena < database/004_lifelines.sql
mysql -u root -p quiz_arena < database/005_interactive_questions.sql

# 3. Cấu hình
cp config/server.properties.example config/server.local.properties
# sửa db.url / db.username / db.password

# 4. Chạy server
java -jar target/quiz-arena-server.jar --config=config/server.local.properties
# Thấy: "Quiz Arena listening on 5555"
```

### Máy chạy client (chỉ cần JDK 21 + Maven, không cần MySQL)

```sh
mvn javafx:run
```

Trong app nhập **host + port `5555`** rồi đăng nhập.

> Lưu ý: config hiện tại trong máy (`config/server.local.properties`) có thể đang dùng MySQL
> cổng `13306`, DB `quiz_arena_demo`. Khi copy sang máy/VPS khác thì sửa lại theo thực tế.

---

## 2. Làm sao 2 máy khác nhau "nhìn thấy" nhau

### Cùng mạng LAN (wifi/switch)

1. Chọn 1 máy làm **server + MySQL** (máy A).
2. Lấy IPv4 LAN của máy A:
   - macOS: `ipconfig getifaddr en0`
   - Windows: `ipconfig` (mục IPv4)
   - Linux: `hostname -I`
   - Ví dụ: `192.168.1.20`
3. Mở firewall cho cổng `5555` trên máy A (macOS: System Settings → Network → Firewall;
   Windows: Inbound Rule; Linux: `ufw allow 5555/tcp`).
4. Máy B chạy client, nhập host = `192.168.1.20`, port `5555`.
5. Đăng nhập **2 tài khoản khác nhau** (ví dụ `demo1`, `demo2`) rồi thách đấu.

Điểm quan trọng đã có sẵn trong code: `ServerMain` bind `0.0.0.0` (`ServerMain.java:16`)
nên server nghe mọi interface — chỉ cần client nhập **địa chỉ thật** của máy server,
**đừng nhập `localhost`** (vì `localhost` ở máy B là chính máy B).

Kiểm tra nhanh kết nối từ máy B trước khi mở app:

```sh
nc -vz 192.168.1.20 5555    # báo "succeeded/open" là OK
```

---

## 3. Cho server lên VPS

**Được.** Server chỉ là Java + MySQL, chạy VPS rất phù hợp và thực ra còn tốt hơn LAN nhà
(có IP public, không phụ thuộc NAT). Server đã bind `0.0.0.0` nên không cần sửa code.

### Trên VPS (Ubuntu)

```sh
# 1. Cài JDK 21 + Maven + MySQL 8.4
sudo apt update && sudo apt install -y openjdk-21-jdk maven mysql-server

# 2. Lấy code và build
git clone <repo> && cd LTM
mvn clean install

# 3. Tạo DB + nạp schema (giống mục 1)
mysql -u root -p < database/001_schema.sql
mysql -u root -p < database/002_seed.sql
mysql -u root -p < database/003_community_quizzes.sql

# 4. Cấu hình trỏ MySQL localhost của VPS
cp config/server.properties.example config/server.local.properties
# db.url=jdbc:mysql://127.0.0.1:3306/quiz_arena
# server.port=5555

# 5. Chạy nền (giữ chạy sau khi thoát SSH)
nohup java -Dquiz.mediaDir=/var/lib/quiz-arena/media \
  -jar target/quiz-arena-server.jar \
  --config=config/server.local.properties > server.log 2>&1 &
```

**Mở firewall trên VPS** (và cả Security Group nếu là cloud): cho phép TCP `5555`.
Giữ `3306` (MySQL) chỉ nội bộ, **đừng mở cho Internet**.

**Client ở máy bất kỳ:** nhập **IP public của VPS** + port `5555`.

### ⚠️ Lưu ý bảo mật quan trọng

Dự án **chưa có TLS** (README mục 10 ghi rõ: *"chưa triển khai TLS cho kết nối
Internet"*). Nghĩa là mọi dữ liệu — **mật khẩu đăng nhập, đáp án, chat** — đi qua mạng dạng
**plaintext**. Mở cổng 5555 ra Internet công khai là **không an toàn**. Có 3 cách xử lý,
chọn 1:

| Cách | Cách làm | Phù hợp |
| --- | --- | --- |
| **Giới hạn theo IP (dễ nhất)** | Firewall VPS chỉ cho phép IP của người chơi vào cổng 5555 | Chơi với vài người biết trước |
| **SSH tunnel (khuyến nghị)** | `ssh -L 5555:localhost:5555 user@IP_VPS`, rồi client nhập `localhost:5555`. Cổng 5555 **không cần mở public** | An toàn, mã hóa |
| **VPN** (Tailscale/WireGuard) | Cho máy chơi vào cùng mạng VPN, client nhập IP VPN của VPS | Nhiều người, tiện |

### Lưu ý vận hành khác khi lên VPS

- **Ảnh**: đặt `-Dquiz.mediaDir=/đường/dẫn/persistent` (mặc định `data/media` tính theo thư
  mục chạy) để ảnh không mất khi cập nhật code.
- **Giữ server chạy**: dùng `nohup`, `tmux`, hoặc tốt hơn là tạo **systemd service** để tự
  khởi động lại.
- **Sao lưu**: phải backup **cả MySQL lẫn thư mục media** (chỉ backup DB là mất ảnh).
- **Giới hạn**: server tối đa **32 kết nối**, **100 trận** đồng thời — thừa sức cho demo nhóm.
- Kết quả chưa lưu nằm trong RAM; tắt server đột ngột (`kill -9`) có thể mất kết quả đang chờ.
  Dùng `Ctrl+C` / `systemctl stop` để tắt êm.

---

## Tóm lại

- **LAN**: máy client nhập IP LAN của máy server (không dùng `localhost`).
- **VPS**: build + chạy y hệt, client nhập IP public — nhưng nên dùng SSH tunnel / IP allowlist
  thay vì mở 5555 trần ra Internet, vì app chưa có TLS.

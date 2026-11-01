# Bản app mở trực tiếp

Các bản phân phối được đặt trong `dist/` (không đưa file nhị phân vào Git):

- `QuizArena-3.0.0-macos-arm64.zip`: giải nén, mở **Quiz Arena.app** trên Mac Apple Silicon.
- `QuizArena-3.0.0-windows-x64.zip`: giải nén toàn bộ, mở **Quiz Arena.exe** trên Windows 10/11 x64. Giữ thư mục `runtime` và `lib` cạnh file exe.

Cả hai gói có Eclipse Temurin Java 21 và JavaFX 21.0.6, không cần cài JDK, Maven hoặc MySQL trên máy chơi. Đây là client desktop; nhập host/port để kết nối server Quiz Arena 3.0 đã nâng cấp schema 5. Dùng IP/domain server nếu server ở máy khác, thay cho `localhost`.

Runtime Java và thư viện native đúng hệ điều hành được đóng gói riêng; không chép bản `.app` sang Windows. Windows dùng launcher GUI viết bằng Go gọi `runtime/bin/javaw.exe` với classpath trong gói, không mở cửa sổ terminal. Lỗi khởi động được ghi vào `%LOCALAPPDATA%\Quiz Arena\logs\client.log` và hiện hộp thoại nếu runtime không khởi động được.

## Build lại

Trên macOS, cần JDK 21, Maven, Python 3.12+ và curl:

```sh
python3 scripts/package-desktop.py
```

Chỉ build một nền tảng hoặc đổi phiên bản tên gói:

```sh
python3 scripts/package-desktop.py --platform macos --version 3.0.2
python3 scripts/package-desktop.py --platform windows --version 3.0.2
```

Đầu ra cũ được giữ nguyên; script dừng nếu tên gói đã tồn tại. Di chuyển bản cũ ra chỗ khác hoặc dùng phiên bản mới trước khi build lại. File tải/công cụ build nằm trong `target/desktop-packaging/`, không cài Go vào hệ thống. Nếu đã có Go trên PATH, script dùng công cụ đó; nếu chưa có, tải bản Go stable từ go.dev để biên dịch chéo launcher Windows.

Script build các JAR bằng Maven, lấy JavaFX từ Maven Central, tải JRE Temurin 21 từ API Adoptium, xác minh checksum rồi tạo `.app` bằng jpackage và `.exe` bằng Go. JRE và Go được kiểm SHA-256; các JAR JavaFX đối chiếu SHA-1 do Maven Central phát hành. `build-info.json` ghi phiên bản, nguồn và SHA-256 của JRE. Giấy phép runtime nằm trong thư mục `legal` của runtime; các JAR giữ nguyên META-INF.

Script chỉ đóng gói JAR và runtime; không thêm cấu hình database, tài khoản, mật khẩu hoặc media người dùng. Build macOS phải chạy trên macOS; bản Windows portable được biên dịch chéo, cần thử mở trên máy Windows để xác nhận môi trường đích.

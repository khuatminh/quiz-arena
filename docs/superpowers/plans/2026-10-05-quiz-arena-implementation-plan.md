# Quiz Arena — Kế hoạch triển khai tổng thể

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng và nghiệm thu ứng dụng JavaFX thi đấu quiz 1v1 qua TCP, có bảng xếp hạng sau từng câu, lưu lịch sử và ranking toàn hệ thống.

**Architecture:** Maven gồm `common`, `server`, `client`; server quyết định trạng thái, thời gian, đáp án và điểm. JavaFX chỉ gửi ý định và hiển thị snapshot server; JDBC và hashing chạy ngoài reader/scheduler của trận. Mỗi match có một khóa riêng, mỗi connection có một writer, kết quả được lưu bằng transaction idempotent.

**Tech Stack:** JDK 21, JavaFX 21, TCP Socket, Jackson, JUnit 5, Maven, JDBC, MySQL 8.4; không dùng Spring, WebSocket hoặc ORM trong v1.

---

## 1. Cách dùng bộ plan

Nguồn yêu cầu là [spec Quiz Arena](../specs/2026-10-05-quiz-arena-design.md). Nếu có mâu thuẫn về luật chơi, spec là chuẩn; sửa hợp đồng và test cùng lúc trước khi tiếp tục. Các đường dẫn mã trong bộ plan là **đường dẫn dự kiến tính từ root project**, chưa phải mã nguồn đã tồn tại.

| Tài liệu | Người phụ trách | Kết quả độc lập có thể kiểm tra |
| --- | --- | --- |
| [Network và protocol](2026-10-05-quiz-arena-network-protocol.md) | Khuất Quang Minh | Socket harness gửi/nhận được message, online/challenge không giữ chỗ sai. |
| [Database, tài khoản và persistence](2026-10-05-quiz-arena-database-auth.md) | Trần Lê Hoàng Phúc | Repository/auth/query chạy với MySQL thử nghiệm; lưu một summary đúng một lần. |
| [Gameplay và vòng đời trận](2026-10-05-quiz-arena-match-gameplay.md) | Đỗ Quang Tuấn | Trận đủ 10 câu chạy bằng clock/scheduler giả và transport ghi nhận event. |
| [JavaFX và trải nghiệm người chơi](2026-10-05-quiz-arena-client-javafx.md) | Nguyễn Hữu Việt | UI chạy bằng fixture trước, sau đó chạy với server thật. |
| Tài liệu hiện tại | Cả nhóm, Minh điều phối | Nền tảng, thứ tự tích hợp, nghiệm thu, demo và bàn giao. |

Bộ plan có 42 task: F01–F03, N01–N08, D01–D08, G01–G09, C01–C09, I01–I05. Mỗi task là một thay đổi review được; các checkbox là bước thực hiện. Khối lượng một task có thể mất vài giờ, vì vậy chạy chu trình test đỏ → code → test xanh cho từng hành vi nhỏ trong task, không gom toàn bộ module rồi mới kiểm tra.

Đây là kế hoạch **triển khai sau khi nhóm bắt đầu code**. Lệnh build, test, Git và setup DB dưới đây chưa được thực thi khi viết plan. Workspace hiện có tài liệu, chưa có reactor Maven hoặc repository Git.

## 2. Phạm vi và những quyết định khóa từ đầu

- Thi đấu 1v1 bằng chọn quiz rồi thách đấu người đang FREE. Không thêm PIN, matchmaking hoặc spectator vào v1.
- Mỗi trận 10 câu: 4 Single Choice, 2 Multiple Choice, 2 True/False, 2 Short Answer; chọn ngẫu nhiên không lặp trong cùng trận.
- Sẵn sàng đầu trận tối đa 30s; countdown trận 3s. Mỗi câu: chuẩn bị tối đa 5s → countdown 2s → trả lời 15s → reveal 2s → leaderboard 3s.
- Đúng ở ≤5s được 3 điểm, >5–10s được 2, >10–15s được 1 nếu câu còn OPEN; sai/timeout 0. Timestamp lấy dưới khóa match; không tin thời gian client.
- Leaderboard có đủ hai người **sau mỗi câu**, kể cả câu 10; bằng điểm cùng rank 1. Animation không kéo dài phase server.
- Single/True-False bấm là gửi. Multiple/Short có nút gửi; đang pending thì khóa, ACK mới xác nhận đã nhận. Timeout ACK không tự gửi lại hoặc mở lại lựa chọn.
- Từ countdown đầu trận, rời/logout/disconnect là forfeit; trước đó hủy và không tăng thống kê. Lỗi chuẩn bị hoặc lỗi server là ABORTED, không thưởng thắng.
- Chat, rematch, result session 60s, history và ranking là phần bắt buộc. Global ranking chỉ đổi sau DB commit.
- Không tiếp tục trận sau reconnect; không bảo đảm summary chưa commit sống qua server crash. Những giới hạn này phải có trong README/demo.

## 3. Cấu trúc mã và quyền sở hữu

Package gốc: `vn.edu.nhom7.quiz`. Không thêm `module-info.java` trong v1 để giảm cấu hình JPMS. Code từng file được liệt kê cụ thể trong plan module; bảng này chốt ranh giới trước khi làm.

| Đường dẫn | Nội dung | Chủ sở hữu |
| --- | --- | --- |
| `pom.xml`, `common/pom.xml`, `server/pom.xml`, `client/pom.xml` | Reactor, dependency management, plugin và profile kiểm thử. | Minh, review cả nhóm |
| `common/src/main/java/vn/edu/nhom7/quiz/common/protocol/Envelope.java` | Envelope v1 với IDs và JsonNode payload. | Minh |
| `common/src/main/java/vn/edu/nhom7/quiz/common/protocol/Payloads.java` | Public records trong một namespace, không private key. | Minh, review phía sử dụng |
| `common/src/main/java/vn/edu/nhom7/quiz/common/protocol/ProtocolCodec.java` | Parse strict envelope/payload; serialize compact UTF-8. | Minh |
| `common/src/main/java/vn/edu/nhom7/quiz/common/net/FrameDecoder.java` | Decoder giữ prefix/body qua partial read và timeout. | Minh |
| `common/src/main/java/vn/edu/nhom7/quiz/common/assets/AssetRegistry.java` | Registry/pack version, ID và attribution dùng chung. | Minh + Việt |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/SocketServer.java` | Accept loop và cấu hình reader/writer. | Minh |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java` | Handshake, auth guard, chuyển command/query. | Minh |
| `server/src/main/java/vn/edu/nhom7/quiz/server/session/SessionRegistry.java` | Một login/user, reservation và assignment generation. | Minh |
| `server/src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java` | Invite TTL, accept race, nạp câu ngoài khóa. | Minh |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java` | Lookup/membership và điều phối aggregate Match. | Tuấn |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/Match.java` | State/lock/phaseVersion/eventSeq/timer của một trận. | Tuấn |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/AnswerEvaluator.java` | Bốn kiểu đáp án và chuẩn hóa short answer. | Tuấn |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/ScoreCalculator.java` | Điểm theo elapsed nanoseconds. | Tuấn |
| `server/src/main/java/vn/edu/nhom7/quiz/server/domain/QuestionSnapshot.java` | Snapshot private dùng evaluator và persistence. | Phúc + Tuấn |
| `server/src/main/java/vn/edu/nhom7/quiz/server/domain/MatchSummary.java` | Summary immutable; không serialize trực tiếp ra client. | Tuấn + Phúc |
| `server/src/main/java/vn/edu/nhom7/quiz/server/auth/AuthService.java` | Validation/register/login và hash. | Phúc |
| `server/src/main/java/vn/edu/nhom7/quiz/server/db/JdbcConnectionFactory.java` | Connection theo từng tác vụ, UTC, timeout. | Phúc |
| `server/src/main/java/vn/edu/nhom7/quiz/server/quiz/JdbcQuizRepository.java` | Counts/availability và bộ 10 câu private. | Phúc |
| `server/src/main/java/vn/edu/nhom7/quiz/server/persistence/PersistenceService.java` | Pending/retry/DB health và post-commit notification. | Phúc |
| `client/src/main/java/vn/edu/nhom7/quiz/client/network/NetworkClient.java` | Socket nền, writer duy nhất, request correlation. | Việt |
| `client/src/main/java/vn/edu/nhom7/quiz/client/state/ClientStateReducer.java` | State từ event, dedupe, snapshot không cộng lại điểm. | Việt |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/GameView.java` | Một màn game, renderer thay theo type, panel thay theo phase. | Việt |
| `client/src/main/resources/ui/quiz-arena.css` | Tokens màu, layout, state của controls. | Việt |
| `database/001_schema.sql`, `database/002_seed.sql` | Schema và ngân hàng mẫu; không chứa mật khẩu thật. | Phúc |
| `docs/testing/acceptance-report.md`, `docs/demo/demo-script.md` | Evidence AC và kịch bản bảo vệ. | Cả nhóm |

Không chia server thành bốn bản project riêng. Mỗi người sở hữu package trong cùng reactor; thay đổi shared DTO/schema có review của người tiêu thụ. Không để client phụ thuộc `server` hoặc có driver JDBC.

## 4. Hợp đồng tích hợp cần chốt ở F02

### 4.1. Kiểu dữ liệu và envelope

UUID trên wire là string chuẩn; trong Java dùng `UUID`. User/quiz/question ID dùng `long`; `requestId`, `roundId`, `matchId`, `eventSeq` có thể null đúng catalogue. DTO answer trên wire dùng JsonNode để giữ single string, array string, boolean và short string, rồi validate theo question type trên server.

```java
package vn.edu.nhom7.quiz.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record Envelope(
    int protocolVersion,
    MessageType type,
    UUID requestId,
    UUID matchId,
    UUID roundId,
    Long eventSeq,
    JsonNode payload
) {}
```

`MessageType` chứa toàn bộ catalogue mục 9.3 spec. `Payloads` định nghĩa records cho từng payload, giữ nguyên tên field trong spec. Không bổ sung field `userId` cho client tự khai khi gửi ANSWER/CHAT/READY. `ProtocolCodec` phải validate payload object và strict unknown fields trước khi route.

### 4.2. Các API Java giữa module server

Các file interface sau thuộc F02. Implementation phải giữ chữ ký; đổi API cần sửa contract test và người gọi trong cùng thay đổi.

```java
// server/session/SessionContext.java
public record SessionContext(UUID connectionId, long userId) {}

// server/network/OutboundTransport.java
public interface OutboundTransport {
    boolean tryEnqueue(UUID connectionId, Envelope event);
}

// server/match/MatchGateway.java
public interface MatchGateway {
    void handle(SessionContext caller, Envelope command);
    void onDisconnected(SessionContext caller);
}

// server/quiz/QuizRepository.java
public interface QuizRepository {
    List<QuestionSnapshot> loadMatchQuestions(long quizId, RandomGenerator random);
}

// server/persistence/ResultSink.java
public interface ResultSink {
    void submit(MatchSummary summary);
    boolean canCreateMatch();
    boolean reserveMatch(UUID matchId);
    void cancelReservation(UUID matchId);
}

// server/match/GameClock.java
public interface GameClock {
    long nanoTime();
    Instant instant();
}

// server/match/GameScheduler.java
public interface GameScheduler {
    Cancellable schedule(Duration delay, Runnable action);
    interface Cancellable { void cancel(); }
}
```

Imports dùng kiểu Java chuẩn `java.util.UUID/List`, `java.util.random.RandomGenerator`, `java.time.Instant/Duration`; Envelope từ common; QuestionSnapshot/MatchSummary từ domain. `GameClock.instant()` chỉ phục vụ UTC history/log. Nghiệp vụ/điểm dùng `nanoTime()`. ResultSink.canCreateMatch là health/advisory; reserveMatch atomically giữ một trong100 slot cho active hoặc pending summary, không JDBC/network. CANCELLED/load/activation rollback gọi cancelReservation, submit tiêu thụ reservation; SAVED giải phóng slot. Nhờ vậy nhiều challenge đồng thời không làm pending vượt giới hạn và mất kết quả.

`SessionRegistry` API dưới đây trả/nhận **snapshot**, không gọi MatchGateway khi còn giữ registry lock:

```java
boolean tryAuthenticate(UUID connectionId, long userId);
Optional<SessionContext> authenticated(UUID connectionId);
boolean reservePair(long firstUserId, long secondUserId, UUID challengeId);
boolean attachReservedPair(UUID challengeId, UUID newMatchId);
boolean transferPair(UUID oldMatchId, long expectedGeneration, UUID newMatchId);
void releaseChallenge(UUID challengeId);
void detach(long userId, UUID expectedMatchId);
Optional<SessionContext> removeConnection(UUID connectionId);
```

`transferPair` kiểm tra cả hai connection online, còn attached cùng oldMatch và generation; không tạo khoảng FREE. Match không lấy registry lock dưới match lock. Minh/Tuấn chốt `AssignmentSnapshot(UUID matchId, long generation, List<SessionContext> players)` để lấy generation ngoài match trước coordinator.

### 4.3. Fixture và dữ liệu mẫu dùng chung

F02 tạo `common/src/test/resources/protocol/catalogue-v1.json`: mỗi message một entry gồm direction, phase, required/optional fields, valid payload và một invalid payload. Đủ HELLO/auth/query/challenge/game/chat/rematch/result/save/history/error, không chỉ game.

F02 tạo `common/src/main/resources/protocol/demo-match-v1.json`: chuỗi event public cho một trận 10 câu theo tỷ lệ 4/2/2/2, gồm private ACK theo từng client, hai score khác nhau và một câu hòa. Đây là dữ liệu demo public để Việt chạy UI; không chứa private key trước reveal. Fixture schema và asset IDs được khóa cùng `assetPackVersion="1"`.

F03 tạo `server/src/test/java/vn/edu/nhom7/quiz/server/support/ServerFixtures.java`: private 10 QuestionSnapshot tương ứng fixture public, hai participant `101/102`, hai UUID connection cố định, và factory `completedSummary()` có 10 round/20 outcome. Factory dùng domain records của G01; Phúc không phải chờ game engine mới kiểm thử transaction.

## 5. Nền tảng và quy trình kiểm thử

### Phiên bản baseline đề xuất

| Dependency/plugin | Phiên bản khóa ở F01 |
| --- | --- |
| Java compiler release | 21 |
| `org.openjfx:javafx-controls` | 21.0.6 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.18.3 |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.18.3 |
| `com.mysql:mysql-connector-j` | 8.4.0 |
| `org.junit.jupiter:junit-jupiter` | 5.11.4, test scope |
| Maven compiler / Surefire / Failsafe | 3.13.0 / 3.5.2 / 3.5.2 |
| JavaFX Maven plugin / Maven Shade | 0.0.8 / 3.6.0 |

Đây là baseline cố định cho nhóm, không phải tuyên bố phiên bản mới nhất. F01 phải xác nhận dependency resolve và build bằng JDK21; nếu nâng version, nâng chung tại parent và chạy lại regression. MySQL integration tests dùng MySQL thật với database riêng, không yêu cầu Docker hoặc Testcontainers.

Máy workspace đang chọn Java25 mặc định, có JDK21 tại `/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`. Khi bắt đầu code trên máy này, chọn JDK21 trong IDE hoặc dùng lệnh:

```bash
JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" mvn -version
```

Expected: Maven dùng Java21. Trên máy khác dùng đường dẫn JDK21 của máy đó. `release=21` riêng lẻ không chứng minh runtime test đang là21.

### Quy ước test và lệnh

- `*Test.java`: unit/loopback bounded test, chạy Surefire, không yêu cầu MySQL.
- `*IT.java`: integration MySQL/system dùng Failsafe, profile `mysql-it`; profile phải báo lỗi nếu thiếu DB config, không skip và vẫn báo xanh.
- Test điều kiện biên dùng fake clock/scheduler, CountDownLatch/barrier cho race; không dựa vào `Thread.sleep(15000)`.
- UI thuần state/reducer unit test; layout, keyboard, ảnh, animation và native JavaFX thread kiểm tra thủ công có evidence.

```bash
# Một unit test ở module server, xây common trước
mvn -pl server -am -Dtest=ScoreCalculatorTest -Dsurefire.failIfNoSpecifiedTests=false test

# Toàn bộ unit + build artifact
mvn clean verify

# Nghiệm thu có DB (cần QUIZ_TEST_DB_URL/USER/PASSWORD đã cấu hình)
mvn -Pmysql-it clean verify

# Một integration test; không dùng integration-test thay verify
mvn -pl server -am -Pmysql-it -Dit.test=MatchPersistenceIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Expected: test được chỉ định có `Tests run` >0, failures/errors0 và `BUILD SUCCESS`. `failIfNoSpecifiedTests=false` chỉ cho phép upstream common không có lớp đó; người chạy phải kiểm tra target thực sự chạy test, không chấp nhận zero tests ở target. Phân tách test theo [Surefire](https://maven.apache.org/surefire/maven-surefire-plugin/examples/single-test.html) và chạy integration bằng [Failsafe verify](https://maven.apache.org/surefire/maven-failsafe-plugin/).

## 6. Task nền tảng

### F01 — Reactor, cấu hình, entry point

**Owner:** Minh. **Dependency:** không. **Ước lượng:** 1–1,5 ngày công.

**Create:** `pom.xml`, `common/pom.xml`, `server/pom.xml`, `client/pom.xml`, `.gitignore`, `server/src/main/java/vn/edu/nhom7/quiz/server/ServerMain.java`, `client/src/main/java/vn/edu/nhom7/quiz/client/ClientMain.java`, `config/server.properties.example`, `README.md`.
**Test:** `common/src/test/java/vn/edu/nhom7/quiz/common/BuildSmokeTest.java`.

- [ ] Chọn JDK21 và ghi versions vào README. Nếu nhóm bắt đầu quản lý source bằng Git, khởi tạo repository lúc triển khai; không tạo Git chỉ để đọc plan.
- [ ] Tạo parent packaging pom với modules common/server/client; `groupId=vn.edu.nhom7`, `version=1.0.0-SNAPSHOT`, artifactIds `quiz-arena-common/server/client`. Thêm JUnit test; server/client phụ thuộc common cùng version.
- [ ] Thêm smoke test dưới đây rồi chạy `mvn test`; trước khi chốt JDK/POM đúng, test phải fail khi runtime không phải21 hoặc compiler chưa cấu hình.

```java
package vn.edu.nhom7.quiz.common;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class BuildSmokeTest {
    @Test void usesAgreedRuntime() { assertEquals(21, Runtime.version().feature()); }
}
```

- [ ] Khóa dependency/plugin theo bảng; parent có `maven.compiler.release=21` và UTF-8. Failsafe trong profile `mysql-it` bind `integration-test` + `verify`; server Shade có `finalName=quiz-arena-server`, mainClass `vn.edu.nhom7.quiz.server.ServerMain`, không minimize. Client JavaFX plugin mainClass `vn.edu.nhom7.quiz.client.ClientMain`.
- [ ] `.gitignore` loại `target/`, `.idea/`, `*.iml`, `.DS_Store`, `config/*.local.properties`, `.env`, file log; không loại schema/seed/assets. Cấu hình mẫu chỉ có placeholder vô hại `DB_PASSWORD_FROM_ENV`, không password thật. Chạy `mvn clean install`; expected reactor 4 projects success và server jar tồn tại. Ghi commit `build: bootstrap Java 21 Maven reactor` nếu repository đã được thiết lập.

### F02 — Shared contract trước code độc lập

**Owner:** Minh; review Tuấn/Việt/Phúc. **Dependency:** F01. **Ước lượng:** 1,5–2 ngày công chia nhóm.

**Create:** các file interface/record mục4; `common/src/main/java/vn/edu/nhom7/quiz/common/protocol/MessageType.java`, `common/src/main/java/vn/edu/nhom7/quiz/common/protocol/Payloads.java`, `common/src/main/resources/protocol/demo-match-v1.json`, `common/src/test/resources/protocol/catalogue-v1.json`, `docs/protocol/protocol-v1.md`.
**Test:** `common/src/test/java/vn/edu/nhom7/quiz/common/protocol/ProtocolContractTest.java`.

- [ ] Chuyển từng hàng catalogue spec9.3 thành enum và payload record; tạo fixture valid/invalid tương ứng. Ghi nullability, direction, allowed phase, error code; public Question không có explanation/key.
- [ ] Viết contract test serialize→deserialize tất cả fixtures bằng Jackson theo DTO đã khai báo; trước khi có DTO/mapping thì đỏ. Lệnh `mvn -pl common -Dtest=ProtocolContractTest test`. N02 bổ sung strict validation vào cùng lớp test sau khi contract nền đã xanh.
- [ ] Tạo Envelope và interfaces mục4; tạo Payloads bằng records theo đúng spec, không Java class-name polymorphism. Interface QuizRepository là synchronous API nhưng người gọi bắt buộc submit service executor; ResultSink.submit chỉ enqueue bounded.
- [ ] Tạo bảng trace `message → payload record → handler owner → fixture entry` trong `docs/protocol/protocol-v1.md`; mỗi message có một hàng. Dùng assertion mọi MessageType có fixture; strict unknown-field assertion thuộc N02. Tuấn bắt đầu G01 từ bản draft DTO đã thống nhất để các server interfaces có domain types thực, không stub private model khác nhau giữa thành viên.
- [ ] Chạy contract test xanh và compile server sau G01 model; review IDs/type/nullable/asset version. Chốt commit `feat: define v1 protocol and integration contracts`; F02/G01 cùng hoàn tất tại gate M1, N02 mở rộng codec mà không tạo vòng phụ thuộc F02→N02→F02.

### F03 — Test doubles và đường chạy fixture

**Owner:** Tuấn làm test clock/domain; Minh fixture transport; Việt replay; Phúc summary fixture. **Dependency:** F02 và phần model G01. **Ước lượng:** 1 ngày công chia nhóm.

**Create:** `server/src/test/java/vn/edu/nhom7/quiz/server/support/FakeGameClock.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/support/ManualGameScheduler.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/support/RecordingTransport.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/support/RecordingResultSink.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/support/ServerFixtures.java`, `client/src/main/java/vn/edu/nhom7/quiz/client/dev/FixtureReplay.java`.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/support/TestSupportTest.java`.

- [ ] Định nghĩa fake clock bắt đầu nano0/UTC cố định; `advance(Duration)` tăng cả hai độc lập kiểm soát được. Manual scheduler API `runDueTasks()` chạy deadline≤now, hỗ trợ cancel và thứ tự FIFO nếu cùng deadline.
- [ ] Viết test schedule2s/cancel/advance1s/advance1s: chỉ task chưa cancel chạy một lần. RecordingTransport lưu `(connectionId, Envelope)` trong list đồng bộ và có flag mô phỏng queue đầy. RecordingResultSink thực hiện ResultSink bằng reservation set/summary map trong RAM, có health flag/capacity để engine tests không cần persistence runtime.
- [ ] Tạo ServerFixtures private questions và completedSummary theo mục4.3; assert đúng 10/20/tỷ lệ và summary immutable. `FixtureReplay` nhận consumer Envelope, lọc client participant, phát event bằng JavaFX timer trong mode `--fixture`; không kết nối DB/socket.
- [ ] Chạy `mvn -pl server -am -Dtest=TestSupportTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected PASS, không ngủ theo thời gian gameplay.
- [ ] Chốt commit `test: add deterministic gameplay and protocol fixtures`; mọi member chạy được phần mình bằng test doubles.

## 7. Mốc sản phẩm và dependency

| Gate | Task cần xong | Bằng chứng để qua gate |
| --- | --- | --- |
| M1 — Hợp đồng | F01/F02, model G01, N01/N02, D01, asset contract C01; F03 bắt đầu | Reactor build, strict protocol fixtures, schema, shared APIs được review. |
| M2 — Kết nối và Lobby | N01–N07, D01–D04, C01–C04, G04 phần WAITING_READY/MATCH_START, I01 | Hai client login/list quiz/challenge accept/reject/expire bằng server thật. |
| M3 — Một trận đầy đủ | G01–G05/G07, C05/C06/C08, N08 | 10 câu, đủ bốn loại, server chấm đúng, ACK và result đúng. |
| M4 — Trải nghiệm giữa câu | G06, C07, I02 | Hai client reveal và leaderboard sau cả 10 câu, tie và animation khớp snapshot. |
| M5 — Dữ liệu/ổn định | D05–D08, G08/G09, C08/C09, I03/I04 | Chat/rematch/history/ranking/forfeit/retry và hai trận đồng thời. |
| M6 — Bàn giao | I05 và toàn bộ AC | Báo cáo evidence, LAN demo, README/config/seed/assets và source build được. |

Đường găng: F01 → F02 → N01/N02 + G01 → N07/D04 → G04/G05 → C06/C07 → I02 → D05/G07 → I03/I04 → I05. Phúc có thể làm D01/D02 trong lúc Minh làm framing; Việt dựng fixture UI ngay khi F02/C01 xong. D05 không chờ game runtime vì có ServerFixtures.completedSummary.

### Lịch đề xuất 4 tuần, chưa phải deadline đã cam kết

Giả định mỗi người có 20–24 giờ/tuần và cùng dự một buổi tích hợp/tuần. Ngân sách dự kiến khoảng 300–380 giờ nhóm, gồm phần dự phòng sửa lỗi/tích hợp khoảng20%; ước lượng module đã bao gồm phối hợp trong I01–I03 nên không cộng lặp nguyên task tích hợp. Ngày công tính6 giờ tập trung, không phải ngày lịch. Nếu mỗi người chỉ có15–20 giờ/tuần, dự kiến5–6 tuần hoặc giảm trang trí sau review; giữ TCP, bốn loại câu, realtime leaderboard và AC cốt lõi.

| Tuần | Minh | Tuấn | Việt | Phúc | Review cuối tuần |
| --- | --- | --- | --- | --- | --- |
| 1 | Reactor/protocol/frame/router/session | Model/evaluator/score/fake time | Assets/theme/network/reducer/Auth/Lobby fixture | Schema/hash/user/seed/repository | M1; loopback có HELLO/login/query. |
| 2 | Online/challenge/races/server wiring | Ready/phases/answer/reveal/rank/forfeit | Waiting/Game/4 renderers/pending/reveal/leaderboard | Quiz query/transaction/history/ranking | M2/M3; một trận thật đủ10 câu. |
| 3 | Heartbeat/backpressure/disconnect/harness | Chat/rematch/result expiry/snapshot | Result/chat/history/ranking/resync | Save retry/health/idempotency/DB faults | M4/M5; hai trận, forfeit, lưu/ranking. |
| 4 | LAN setup/performance/network evidence | Race regression/game demo | Resize/keyboard/motion/UI evidence | DB evidence/seed/runbook | M6; rehearsal, fix bugs, freeze build. |

Mỗi task cập nhật owner, ngày bắt đầu, commit và evidence vào `docs/testing/acceptance-report.md` khi thực hiện. Cuối buổi tích hợp mỗi member bàn giao: commit đang chạy, lệnh chạy, contract thay đổi, test đã qua và lỗi còn mở. Không báo “xong UI” khi chỉ có screenshot mà reducer/server chưa khớp.

## 8. Task tích hợp và nghiệm thu

### I01 — Một luồng end-to-end từ login đến MATCH_START

**Owner:** Minh điều phối. **Dependency:** M1, N01–N07, D01–D04, C01–C04.
**Create:** `server/src/test/java/vn/edu/nhom7/quiz/server/system/LobbyFlowIT.java`, `docs/testing/lobby-checklist.md`.
**Modify:** `server/src/main/java/vn/edu/nhom7/quiz/server/ServerMain.java`, `README.md`.

- [ ] Viết IT dùng hai socket raw/ProtocolCodec: HELLO→LOGIN→quiz query→CHALLENGE→ACCEPT, assert hai MATCH_START cùng matchId và hai user BUSY; thêm Reject/Cancel/Expire với clock kiểm soát.
- [ ] Chạy `mvn -pl server -am -Pmysql-it -Dit.test=LobbyFlowIT -Dfailsafe.failIfNoSpecifiedTests=false verify`; phải đỏ nếu bất kỳ service còn stub hoặc nhận auth callback của connection đã đóng.
- [ ] Wire dependency graph ở ServerMain: config→connection factory→repositories/auth→registry/transport→match→challenge→router→socket server. Không giữ cyclic constructor; MatchGateway/ResultSink/OutboundTransport là ranh giới.
- [ ] Chạy IT xanh; mở hai JavaFX client chọn hai demo user, xác nhận popup challenge và thời gian20s. Ghi evidence và commit `feat: integrate authenticated lobby and challenges`.

### I02 — Golden path 10 câu và leaderboard mỗi câu

**Owner:** Tuấn + Việt. **Dependency:** M3 và G06/C07.
**Create:** `server/src/test/java/vn/edu/nhom7/quiz/server/system/TenRoundMatchIT.java`, `docs/testing/gameplay-checklist.md`.

- [ ] Viết hai bot client bằng SocketHarness N08, gửi READY/QUESTION_READY và answer theo private test fixture; đếm10 QUESTION_RESULT +10 ROUND_LEADERBOARD +1 MATCH_RESULT mỗi participant, thứ tự câu10 leaderboard trước result.
- [ ] Chạy `mvn -pl server -am -Pmysql-it -Dit.test=TenRoundMatchIT -Dfailsafe.failIfNoSpecifiedTests=false verify`; expected đỏ nếu event/điểm/phase còn thiếu.
- [ ] Đối chiếu mọi scoreBefore+earned=total, tie rank1, không key trước reveal, ACK private không leak đối thủ; sửa tại module sở hữu, không vá bằng điểm client.
- [ ] Chạy xanh và thực hiện trận thật bằng hai JavaFX client. Chụp một reveal và leaderboard có đổi hạng, một câu hòa. Ghi commit `test: verify ten-round match and per-question leaderboard`.

### I03 — Fault/race regression và transaction

**Owner:** Minh/Tuấn/Phúc. **Dependency:** D05–D08, G07–G09, N08.
**Create:** `server/src/test/java/vn/edu/nhom7/quiz/server/system/ConcurrentMatchesIT.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/system/DatabaseOutageIT.java`.

- [ ] Viết IT bốn client hai match: chat riêng, match1 disconnect, match2 hoàn tất; assert không cross-match event, registry sạch, stats đúng. Dùng barriers tái hiện ANSWER/timeout và terminal disconnect race.
- [ ] Chạy `mvn -pl server -am -Pmysql-it -Dit.test=ConcurrentMatchesIT,DatabaseOutageIT -Dfailsafe.failIfNoSpecifiedTests=false verify`; expected đỏ trước khi hoàn tất cleanup/retry.
- [ ] DatabaseOutageIT dùng fault-injecting ConnectionFactory/delegate thật: lỗi trước commit, lỗi giữa transaction, commit thành công nhưng báo lỗi; phục hồi health và retry. Assert FAILED/PENDING→SAVED, chỉ một history/counter update, trận mới bị chặn khi DB unavailable.
- [ ] Chạy xanh cùng các test module liên quan; ghi giới hạn restart rõ trong report, commit `test: cover concurrent matches and persistence recovery`.

### I04 — UI, LAN và đo hiệu năng

**Owner:** Việt layout; Minh telemetry; cả nhóm chạy. **Dependency:** M5.
**Create:** `docs/testing/ui-checklist.md`, `docs/testing/performance-report.md`, `server/src/test/java/vn/edu/nhom7/quiz/server/system/LoadProbe.java`.

- [ ] Lập bảng evidence theo C09: 1024×720/1280×800/DPI cao, câu dài, missing optional image, unicode, chat focus, pending ACK, local0s, snapshot, motion off; ghi expected/actual cho từng hàng.
- [ ] LoadProbe là test utility có `main`, chạy socket thật tối đa32 connections/16 matches với demo data riêng; đo client enqueue→ACK/chat receive và server expectedDeadline→dispatch bằng monotonic time, lưu CSV `event,type,latencyMs,connections,matches,rttMs`.
- [ ] Chạy hai máy LAN với RTT<50ms; thu ít nhất100 event mỗi nhóm metric, ghi CPU/JDK/OS/DB/client count. Tính p95 bằng sorted index `ceil(0.95*n)-1`; mục tiêu ACK/chat<300ms, timer<100ms, login<3s, save<3s, online<500ms.
- [ ] Nếu metric không đạt, báo kết quả thật và phân tách queue/service/lock/network; sửa nút thắt đã chứng minh rồi đo lại. Ghi commit `docs: record UI and LAN verification evidence` sau khi checklist đã có evidence.

Lệnh probe sau khi source đã được triển khai (chạy terminal khác với server):

```bash
mvn -pl server -am test-compile package
java -cp "server/target/test-classes:server/target/quiz-arena-server.jar" vn.edu.nhom7.quiz.server.system.LoadProbe --host=localhost --port=5555 --connections=4 --matches=2 --samples=100 --csv=docs/testing/evidence/latency.csv
```

Probe dùng bốn demo users, lấy password thử nghiệm từ env `QUIZ_DEMO_PASSWORD`, không echo ra CSV/log. SocketHarness dùng JDK/common APIs, không gọi JUnit Assertions để chạy main độc lập. Muốn đo32 connections/16 matches phải tạo32 tài khoản thử nghiệm riêng trước, không login trùng bốn demo users; quy mô đó là bài đo bổ sung, gate bắt buộc vẫn4 clients/2 matches. Windows thay dấu phân tách classpath `:` bằng `;`. Timer dispatch CSV lấy từ telemetry server; latency client không tự chứng minh scheduler delay.

### I05 — Build bàn giao và rehearsal bảo vệ

**Owner:** Cả nhóm. **Dependency:** I01–I04, 50 AC có trạng thái rõ.
**Create:** `docs/testing/acceptance-report.md`, `docs/demo/demo-script.md`, `docs/operations/runbook.md`, `docs/architecture/network-explanation.md`.
**Modify:** `README.md`, `config/server.properties.example`.

- [ ] Acceptance report mỗi AC có command/manual steps, build commit, actual result, evidence path và người xác minh; không đánh PASS trước khi chạy. Re-run `mvn -Pmysql-it clean verify` trên database thử nghiệm, expected mọi test chạy không skip.
- [ ] Build sạch `mvn clean install`; chạy server jar và JavaFX theo mục10. Kiểm tra client artifact không JDBC/secret, source package có schema/seed/assets attribution và config mẫu.
- [ ] Rehearsal kịch bản mục11, mỗi member giải thích phần mình và một tích hợp sang phần khác; kiểm tra graceful shutdown giới hạn10s và restart không hồi phục trận live.
- [ ] Ghi known limitations, số đo thật, phiên bản môi trường; freeze một Git tag `v1.0-demo` sau khi review nếu dùng Git. Commit `docs: finalize demo and project handoff`; không tuyên bố nghiệm thu nếu AC bắt buộc còn FAIL/NOT_RUN.

## 9. Ma trận trace đủ 50 acceptance criteria

Task được chỉ ra là nơi implement và nơi đặt test chính; I05 tổng hợp evidence cuối. Không coi mapping này là kết quả PASS.

| AC | Task | Evidence chính khi triển khai |
| --- | --- | --- |
| AC01 | D02, D03, I01 | PasswordHasherTest/AuthServiceTest/UserRepositoryIT. |
| AC02 | N05, I01 | SessionRegistryTest concurrent login. |
| AC03 | N05, N06, I01 | OnlinePresenceTest + hai client Login/Logout. |
| AC04 | D04, N07 | QuizRepositoryIT với đủ/thiếu tỷ lệ. |
| AC05 | N07 | ChallengeManagerTest reject/cancel/expire. |
| AC06 | N07 | ChallengeRaceTest opposite/shared target. |
| AC07 | N07 | Accept duplicate và accept-expire barrier. |
| AC08 | N07, D04 | Late load/failure rollback reservation. |
| AC09 | G04 | MatchReadinessTest30s cancellation. |
| AC10 | G04, C05 | Question readiness5s/countdown và manual UI. |
| AC11 | G02, G05, C06 | Single renderer + evaluator/ACK tests. |
| AC12 | G02, C06 | Exact-set multiple cases. |
| AC13 | G02, C06 | Boolean strict test. |
| AC14 | G02 | NFC/Unicode whitespace/accents tests. |
| AC15 | G02, G05 | Invalid payload không tiêu thụ answer slot. |
| AC16 | G03, G05 | Nanosecond boundaries. |
| AC17 | G05 | Timeout outcomes/null values. |
| AC18 | N04, G05 | Request cache và semantic duplicate guard. |
| AC19 | G05, I03 | MatchAnswerRaceTest close once. |
| AC20 | N04, G05 | Non-member/stale/unopened errors. |
| AC21 | N02, G04, G09, I02 | Payload leak assertions. |
| AC22 | G06, C07, I02 | Reveal snapshot và avatar/manual evidence. |
| AC23 | G06, C07, I02 |10 leaderboards, score delta. |
| AC24 | G03, G06, C07 | Tie rank1/stable row order. |
| AC25 | G08, C08 | Chat per match/no timer reset. |
| AC26 | G08, N04 | Codepoint/rate limits không chặn answer. |
| AC27 | I03 | ConcurrentMatchesIT4 clients. |
| AC28 | G04, G07 | Stale timer token tests. |
| AC29 | G06, G07, I02 |10 rounds/result win/lose/draw. |
| AC30 | N08, G07, C08 | Exit/logout/EOF phase matrix. |
| AC31 | G07, N08, I03 | Terminal disconnect guard. |
| AC32 | G09, C08 | Rematch accept/reject/expiry/new ID. |
| AC33 | G09, C08 |60s result expiry/attachment. |
| AC34 | G09, C03, C09 | Snapshot phase/remaining/no key. |
| AC35 | C03, C07 | Reducer duplicate event/no animation replay. |
| AC36 | N01 | FrameDecoderTest fragmentation/coalescing. |
| AC37 | N01, N02, N08 | Invalid prefix/EOF/bad JSON recovery. |
| AC38 | N03 | ConnectionWriterTest whole-frame ordering. |
| AC39 | N03, N08 | Queue saturation isolates slow connection. |
| AC40 | D05 | MatchPersistenceIT10/20/counters. |
| AC41 | D05 | Rollback fault injection. |
| AC42 | D05, D06 | Idempotency/commit-unknown retry. |
| AC43 | D06, I03 | DatabaseOutageIT recover/history/ranking. |
| AC44 | D07, C09 | Participant-only detail/history. |
| AC45 | D07, C09 | Competition rank across pages/myRank/invalidation. |
| AC46 | C03, C05, C09 | Local0s/FX lag/manual resync. |
| AC47 | C01, C06, C09, I04 | Resize/long content/assets. |
| AC48 | C07, C08, C09 | Focus/reduced motion/manual checks. |
| AC49 | N02, C02 | Protocol/asset mismatch before login. |
| AC50 | N08, D08, I05 | Shutdown/restart known-limit exercise. |

## 10. Lệnh chạy sau khi triển khai

Sau F01 và các module xong:

```bash
mvn clean install
java -jar server/target/quiz-arena-server.jar --config=config/server.local.properties
mvn -f client/pom.xml javafx:run
```

Mở terminal mới cho mỗi client. Không dùng `mvn -pl client -am javafx:run` vì goal JavaFX có thể bị áp dụng vào upstream không có main JavaFX. JavaFX resources/dependencies được Maven xử lý theo [hướng dẫn OpenJFX Maven](https://openjfx.io/openjfx-docs/).

`server.local.properties` được người chạy tạo từ config mẫu, không commit secrets. Server nhận db.url/db.username/db.password từ env `QUIZ_DB_URL`, `QUIZ_DB_USER`, `QUIZ_DB_PASSWORD` ưu tiên hơn local file; test dùng `QUIZ_TEST_DB_*` riêng. LAN: server bind0.0.0.0:5555, client nhập IPv4 máy server; MySQL chỉ do server truy cập. Chỉ mở cổng5555 cần thiết cho demo.

DB tests bắt buộc database tên kết thúc `_test`; D01 bootstrap schema thử nghiệm riêng. Runbook hướng dẫn tạo user thử nghiệm quyền giới hạn trên đúng database, không reset schema runtime để chạy test. Việc chuẩn bị database thực hiện trước `mvn -Pmysql-it verify`.

## 11. Demo bảo vệ và nội dung từng người trình bày

Demo chính 8–12 phút, có build commit và dữ liệu demo cố định:

1. Phúc: giới thiệu schema, tài khoản thử nghiệm và private answer key chỉ ở server; login bốn user.
2. Minh: chọn quiz, hai cặp thách đấu; chỉ ra TCP persistent connection, frame length prefix, reader/writer/heartbeat và session isolation.
3. Tuấn: READY, countdown, một câu mỗi loại, ACK/điểm; giải thích server monotonic clock và race answer-timeout.
4. Việt: reveal có avatar và leaderboard sau mỗi câu; thể hiện tie/đổi hạng, chat trong lúc timer chạy, UI pending và reduced motion.
5. Đóng một client của match1: forfeit đúng, match2 tiếp tục. Cặp còn lại hoàn tất10 câu, xem result/history/ranking và request rematch.
6. Phúc: đối chiếu match hoàn tất1 header/10 questions/20 outcomes và counters commit một lần; trình bày report fault injection DB thay vì sửa dữ liệu thật ngay trên sân khấu.
7. Minh/Tuấn: chạy test/harness fragmented frame, duplicate answer và race; chỉ ra log matchId/roundId/eventSeq. Kết luận bằng giới hạn v1 đã ghi trong runbook.

Chuẩn bị sẵn database và hai máy; giữ bản quay/chụp minh chứng các case khó tái hiện. Không dựng điểm hoặc sửa response server để làm demo trông đúng.

## 12. Rủi ro và cách giảm khối lượng

| Rủi ro | Dấu hiệu | Xử lý trong plan |
| --- | --- | --- |
| DTO lệch giữa thành viên | Deserialize errors, answer type bị string hóa | F02 contract gate, fixture từng message, review thay đổi shared. |
| Chậm tích hợp | Từng module chạy riêng nhưng không có match thật | I01 ở tuần2; fixture không thay thế E2E. |
| Deadlock hoặc timer bị JDBC chặn | Trận đứng khi auth/save/chat | N03 bounded queue, G04/G05 per-match lock, service executor riêng. |
| Thống kê tăng hai lần | Retry/commit-unknown history nhân đôi | D05 unique+transaction, D06 single in-flight worker và retry lookup. |
| Quá tải UI Việt | Bốn màn game gần giống nhau | Một GameView, bốn renderer, phase panel; state test độc lập FX. |
| Animation sai điểm/phase | Cộng lại khi snapshot hoặc bỏ qua câu | C03 state authoritative, C07 cancel animation theo presentationVersion local. |
| Không đủ thời gian | Tuần3 chưa có M3 | Hoãn audio/confetti/trang trí bổ sung; không hoãn framing, server score, leaderboard, transaction. |
| Môi trường khác nhau | JDK25 test nhưng JDK21 demo lỗi | F01 runtime21, pin dependencies, clean build máy demo. |

Hoàn thành kế hoạch thực thi khi mọi gate qua, 50 AC có evidence PASS, tài liệu chạy đúng artifact bàn giao và từng thành viên giải thích được kỹ thuật lập trình mạng thuộc phần mình. File kế hoạch này chưa khẳng định phần mềm đã đạt các điều kiện đó.

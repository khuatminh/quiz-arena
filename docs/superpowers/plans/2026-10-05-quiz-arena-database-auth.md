# Quiz Arena — Database & Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bàn giao tài khoản, ngân hàng quiz, transaction lưu trận, history/ranking và retry có kiểm chứng trên MySQL thử nghiệm.

**Architecture:** Server là bên duy nhất truy cập MySQL; mỗi tác vụ lấy connection riêng và đóng bằng try-with-resources. Match dùng private immutable snapshots trong RAM. Persistence nhận summary immutable, commit một lần theo matchId rồi mới cập nhật profile/ranking.

**Tech Stack:** JDK21 JDBC, MySQL8.4/Connector-J8.4.0, PBKDF2-HMAC-SHA256 có sẵn trong Java, JUnit5/Surefire/Failsafe.

---

**Owner:** Trần Lê Hoàng Phúc. Đọc [plan tổng](2026-10-05-quiz-arena-implementation-plan.md), spec11/13/15. Ước lượng D01–D08 khoảng8–10 ngày công. Private domain records do G01 định nghĩa; repository/persistence test dùng F03, không chờ UI.

## 1. File map và contract

| File | Trách nhiệm |
| --- | --- |
| `database/001_schema.sql` | Schema7 bảng, keys/constraints/index. |
| `database/002_seed.sql` | Ba category/quiz và60 câu hợp lệ. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/config/ServerConfig.java` | Env/local config, không log password. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/db/ConnectionFactory.java` | Interface `Connection open() throws SQLException`. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/db/JdbcConnectionFactory.java` | DriverManager, UTC và timeout. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/db/SchemaVerifier.java` | Kiểm tra bảng/index/version trước server ready. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/auth/PasswordHasher.java` | Versioned PBKDF2 hash, verify constant-time. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/auth/AccountValidator.java` | Username/display/password/avatar validation. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/auth/AuthService.java` | Register/login, không sở hữu session online. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/auth/JdbcUserRepository.java` | User lookup/insert/profile/statistics. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/quiz/QuestionValidator.java` | Private key/options/import validation. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/quiz/JdbcQuizRepository.java` | Public quiz query, private match sampling. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/persistence/JdbcMatchRepository.java` | Một transaction match/round/outcomes/stats. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/persistence/PersistenceService.java` | Bounded pending, retry, save status và health. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/persistence/DatabaseHealth.java` | Available/unavailable và health probe. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/query/RankingService.java` | Global competition rank/pagination/myRank. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/query/HistoryService.java` | Participant-only history/detail. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/tools/DemoUserSeeder.java` | Tạo bốn demo user bằng PasswordHasher. |
| `server/src/test/java/vn/edu/nhom7/quiz/server/support/TestDatabase.java` | Test DB config và guard tên `_test`. |

Chữ ký dùng giữa modules:

```java
// AuthService; chạy qua service executor, SessionRegistry xử lý duplicate login sau đó.
Payloads.Profile register(String username, String displayName, String password, String avatarId);
Payloads.Profile login(String username, String password);
// PasswordHasher
String hash(char[] password);
boolean verify(char[] password, String encodedHash);
// JdbcMatchRepository
SaveResult save(MatchSummary summary); // SAVED hoặc ALREADY_SAVED, không phát network event
// RankingService
Payloads.Ranking ranking(long requesterUserId, int page, int pageSize);
// HistoryService
Payloads.History history(long requesterUserId, int page, int pageSize);
Payloads.MatchDetail detail(long requesterUserId, UUID historyMatchId);
```

`SaveResult` enum nằm trong `server/persistence/SaveResult.java`; ALREADY_SAVED chỉ khi nội dung đã lưu khớp summary. Nếu cùng matchId khác immutable summary, báo INTERNAL_ERROR và không tăng counters. Payloads.Profile/Ranking/History/MatchDetail là records F02 đúng wire spec; repository không trả PasswordHash trong public Profile.

## 2. D01 — Schema, connection và test DB guard

**Dependency:** F01. **Create:** schema, ServerConfig, ConnectionFactory/JdbcConnectionFactory, SchemaVerifier, TestDatabase.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/db/SchemaIT.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/db/ServerConfigTest.java`.

- [ ] Viết SchemaIT kiểm tra7 bảng, utf8mb4, unique username/(match,index)/(round,user), score/counter check và FK; insert counter sai phải SQLException. TestDatabase từ chối URL thiếu, schema không kết thúc `_test`, host/config runtime trỏ nhầm production.
- [ ] Sau khi tạo profile Failsafe, chạy `mvn -pl server -am -Pmysql-it -Dit.test=SchemaIT -Dfailsafe.failIfNoSpecifiedTests=false verify`; expected FAIL có hướng dẫn nếu chưa có schema/config, không SKIP.
- [ ] Tạo schema theo toàn bộ spec11.2: ID BIGINT, UUID CHAR36, timestamps DATETIME(3) UTC, json column cho options/key/snapshot/answer, enums bằng VARCHAR + CHECK. Thêm schema version để verifier đối chiếu bằng bảng metadata hoặc resource version; bảy bảng nghiệp vụ giữ nguyên.

```sql
CREATE TABLE USERS (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
  display_name VARCHAR(128) NOT NULL,
  avatar_id VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  total_score BIGINT NOT NULL DEFAULT 0,
  total_matches INT NOT NULL DEFAULT 0,
  wins INT NOT NULL DEFAULT 0,
  losses INT NOT NULL DEFAULT 0,
  draws INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  CHECK (total_score >= 0 AND wins >= 0 AND losses >= 0 AND draws >= 0),
  CHECK (total_matches = wins + losses + draws),
  INDEX idx_users_ranking(total_score DESC, wins DESC, id ASC)
) CHARACTER SET utf8mb4;
```

`display_name` đủ lưu32 codepoint; validator vẫn giới hạn32. Tables MATCHES/MATCH_QUESTIONS/MATCH_ANSWERS giữ snapshot names/quiz title/outcome/reason và unique keys đúng spec. Không FK cascade-delete history. USERS counters update một statement để CHECK không thấy trạng thái trung gian.

- [ ] ConnectionFactory.open() tạo connection theo task; configure connect/socket timeout hữu hạn, UTC/session time_zone, PreparedStatement, try-with-resources. Env QUIZ_DB_* ưu tiên local file; test QUIZ_TEST_DB_* hoàn toàn riêng. Verifier lỗi thì server không ready, safe message không URL password.
- [ ] Chạy SchemaIT/ServerConfigTest xanh; ghi hướng dẫn MySQL setup trong `docs/operations/database-setup.md`, commit `feat: establish MySQL schema and guarded test database`.

## 3. D02 — Validation và password hashing

**Dependency:** F02 asset registry hoặc danh sách ID đã khóa ở C01. **Create:** AccountValidator/PasswordHasher.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/auth/AccountValidatorTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/auth/PasswordHasherTest.java`.

- [ ] Test username `toLowerCase(Locale.ROOT)` rồi[a-z0-9_.]3–24, display2–32 codepoints strip/no control, password8–128 không trim/lower/NFC, avatar ngoài registry invalid; hash cùng password hai lần khác salt; verify đúng/sai/version malformed.

```java
@Test void saltedHashDoesNotNormalizePassword() {
    var hasher = new PasswordHasher();
    String first = hasher.hash(" Abc12345 ".toCharArray());
    String second = hasher.hash(" Abc12345 ".toCharArray());
    assertNotEquals(first, second);
    assertTrue(hasher.verify(" Abc12345 ".toCharArray(), first));
    assertFalse(hasher.verify("Abc12345".toCharArray(), first));
}
```

- [ ] Chạy `mvn -pl server -am -Dtest=AccountValidatorTest,PasswordHasherTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước validation/hash.
- [ ] Salt SecureRandom16bytes, iterations600000, key256bits, PBEKeySpec clearPassword sau derive. Encoded format khóa `v1$pbkdf2-sha256$600000$<base64salt>$<base64key>`; parse giới hạn iterations/length trước derive để stored malformed không gây workload tùy ý.

```java
PBEKeySpec spec = new PBEKeySpec(password, salt, 600_000, 256);
try {
    byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(spec).getEncoded();
    // verify dùng MessageDigest.isEqual(expectedKey, key), không String.equals.
} finally {
    spec.clearPassword();
}
```

- [ ] Chạy test xanh; production work factor600000 vẫn được test ít nhất một roundtrip, không benchmark bằng workfactor giả. Hash/auth chỉ service executor; không scheduler/FX thread. Không log encoded hash/plaintext.
- [ ] Commit `feat: validate accounts and store versioned salted password hashes`.

## 4. D03 — User repository và auth service

**Dependency:** D01/D02/N05 contract. **Create:** JdbcUserRepository/AuthService.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/auth/AuthServiceTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/auth/UserRepositoryIT.java`.

- [ ] Test register/login/profile, duplicate concurrent insert chỉ một user, wrong password generic INVALID_CREDENTIALS, SQL-injection string không query đúng user, public DTO không password_hash; optional display mặc định username.
- [ ] Chạy unit `mvn -pl server -am -Dtest=AuthServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` và IT `mvn -pl server -am -Pmysql-it -Dit.test=UserRepositoryIT -Dfailsafe.failIfNoSpecifiedTests=false verify`; expected FAIL trước service/query.
- [ ] InsertPreparedStatement và unique(username) là chốt race; map duplicate key USERNAME_TAKEN. Login canonical username, verify hash, trả Profile nhưng không tự authenticated. Minh tryAuthenticate atomic, lần thua ALREADY_LOGGED_IN, không đá phiên cũ.

```sql
SELECT id, username, display_name, avatar_id, password_hash,
       total_score, total_matches, wins, losses, draws
FROM USERS WHERE username = ?;
```

- [ ] Lỗi DB→DB_UNAVAILABLE an toàn, không raw SQL stack trace client; trả sai user/password cùng message. Callback login chỉ apply vào connection còn sống như N04. Close resources cả fail branch.
- [ ] Tests xanh + I01 login thật; commit `feat: implement persistent registration and login`.

## 5. D04 — Quiz seed, validation và sample10 câu

**Dependency:** D01/G01 QuestionSnapshot/G02 normalization; không cần match runtime. **Create:** seed, QuestionValidator/JdbcQuizRepository.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/quiz/QuestionValidatorTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/quiz/QuizRepositoryIT.java`.

- [ ] Test3 category/3 quiz/20 câu mỗi quiz, counts8/4/4/4; AVAILABLE khi active counts≥4/2/2/2, thiếu một loại UNAVAILABLE. Sampling lấy10 questionIds unique, đúng4/2/2/2, shuffle với RandomGenerator seed cố định để test tái lập.
- [ ] Chạy `mvn -pl server -am -Dtest=QuestionValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test` và `mvn -pl server -am -Pmysql-it -Dit.test=QuizRepositoryIT -Dfailsafe.failIfNoSpecifiedTests=false verify`; expected đỏ trước seed/repository.
- [ ] Seed nội dung có tiếng Việt/dấu/NFC variants, multiple thiếu/thừa, câu dài và asset/explanation. Validate nội dung≤500, Choice2–6 options/text≤120, explanation≤500, option IDs unique, key Choice nằm trong options, multi nonempty/no duplicates, TF key boolean, short aliases≤120 codepoints/nonempty sau chuẩn hóa; invalid active question không được dùng trong match.

```java
// Sampling sau khi query private snapshots; RandomGenerator được inject.
Map<QuestionType, Integer> required = Map.of(
    QuestionType.SINGLE_CHOICE, 4, QuestionType.MULTIPLE_CHOICE, 2,
    QuestionType.TRUE_FALSE, 2, QuestionType.SHORT_ANSWER, 2);
// Shuffle từng bucket bằng Fisher-Yates với random.nextInt(i + 1), lấy required count;
// shuffle tiếp danh sách10 đã chọn. Không ORDER BY RAND() để giấu logic tỷ lệ.
```

- [ ] Quiz list/detail chỉ counts/summary public, không questions/key. loadMatchQuestions trả List.copyOf snapshots, snapshot private giữ key/explanation/assets để history độc lập ngân hàng; query/validation ngoài match/challenge locks, timeout5s do coordinator.
- [ ] Tests xanh; chạy seed lại không nhân đôi data theo IDs seed cố định. Commit `feat: seed validated quiz bank and sample typed match questions`.

## 6. D05 — Transaction lưu trận và counters đúng một lần

**Dependency:** D01, G01 MatchSummary, F03.completedSummary. **Create:** JdbcMatchRepository/SaveResult.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/persistence/MatchPersistenceIT.java`.

- [ ] Viết IT fixture completedSummary:1 match/10 rounds/20 outcomes, total_matches=w+l+d, score cộng đúng, draw hai draws; forfeit unfinished ABANDONED không cộng điểm; ABORTED lưu nhưng không stats; CANCELLED không submit. Save hai lần counters chỉ tăng một.
- [ ] Thêm fault injection sau insert round, sau answers, giữa hai userupdates và commit response lost; chạy `mvn -pl server -am -Pmysql-it -Dit.test=MatchPersistenceIT -Dfailsafe.failIfNoSpecifiedTests=false verify`; expected FAIL trước atomic/idempotent repository.
- [ ] open connection/setAutoCommit(false), lookup matchId; nếu chưa có insert MATCHES +batch rounds/outcomes. Khóa USERS ascending ID bằng SELECT FOR UPDATE, update cả counters một statement. Completed10/20 bắt buộc; forfeit/abort chỉ opened/prepared, hai outcomes mỗi round, elapsed ms=floor(nanos/1_000_000). MATCH_ANSWERS không thêm cột scoreBefore/totalScore ngoài spec; hai field auxiliary trong summary được kiểm tra bằng cộng earnedPoints theo thứ tự round và dựng lại khi query history.

```sql
-- Execute hai lần với userId nhỏ trước, userId lớn sau:
SELECT id FROM USERS WHERE id = ? FOR UPDATE;
UPDATE USERS SET total_score = total_score + ?,
  total_matches = total_matches + 1,
  wins = wins + ?, losses = losses + ?, draws = draws + ?
WHERE id = ?;
```

- [ ] commit rồi trả SAVED; SQLException trước commit rollback. Nếu commit báo exception, không khẳng định rollback thành công: retry ở connection mới lookup matchId, đối chiếu persisted projection của summary, UTC timestamps millisecond và elapsed floor millisecond cùng snapshot/outcomes/counters invariant. Unique matchId chặn concurrent insert; rollback duplicate transaction rồi lookup ở connection mới, không update lại users. So sánh không yêu cầu DB khôi phục nanoseconds đã chủ ý không lưu.
- [ ] Tests xanh trên MySQL, assert không orphan records/counter nửa transaction. Commit `feat: persist match summaries atomically and idempotently`.

## 7. D06 — Save worker, retry và DB health

**Dependency:** D05/F02 ResultSink; N06 callback postcommit. **Create:** PersistenceService/DatabaseHealth.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/persistence/PersistenceServiceTest.java`.

- [ ] Test PENDING→SAVED, transient retries1/2/4s, FAILED retryable rồi30s→SAVED, capacity100 không drop, một in-flight worker/matchId; commit unknown retry không double counters, old match notification không ảnh hưởng rematch.
- [ ] Chạy `mvn -pl server -am -Dtest=PersistenceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước retry/health, dùng ManualGameScheduler không ngủ thật.
- [ ] ResultSink.submit bounded enqueue summary; service worker thực hiện JDBC, scheduler chỉ submit retry. Record pending map theo matchId trạng thái/generation; callback stale no-op, retry idempotent D05. FailedDB đặt unavailable chặn challenge/rematch mới, match live vẫn chạy; health SELECT1 thành công +pending capacity còn cho phép restore.

```java
long[] retryDelaySeconds = {1, 2, 4};
// Sau lần thử ban đầu, schedule ba retry tuần tự; không song song cho một match.
// Khi hết ba lần: FAILED và retry mỗi30s, summary vẫn nằm trong bounded pending map.
```

- [ ] Chỉ sau commit/ALREADY_SAVED khớp summary mới MATCH_SAVE_STATUS SAVED, refresh profile+online và RANKING_INVALIDATED. Initial MATCH_RESULT PENDING không hứa stats đã lưu. Implement reserveMatch/cancelReservation bằng atomic capacity guard: active reservation+pending≤100; submit chuyển slot active→pending, SAVED giải phóng. Health/capacity chặn create mới, không âm thầm bỏ summary cũ.
- [ ] Tests xanh, I03 DatabaseOutageIT xanh. Commit `feat: retry match persistence and publish post-commit updates`.

## 8. D07 — Ranking, history và detail được phân quyền

**Dependency:** D05/N04. **Create:** RankingService/HistoryService.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/query/RankingHistoryIT.java`.

- [ ] Test rank1,1,3 cho hai user cùng score+wins; ID chỉ ổn định thứ tự, không phá ranktie. MyRank tính cả bảng khi user ngoài page; page1/pageSize20 mặc định, max50; history onlyparticipant sort ended DESC/id ASC; outsider detail NOT_MATCH_MEMBER.
- [ ] Chạy `mvn -pl server -am -Pmysql-it -Dit.test=RankingHistoryIT -Dfailsafe.failIfNoSpecifiedTests=false verify`; expected FAIL trước query/guards.
- [ ] Dùng window RANK() theo score+wins, ROW_NUMBER/order ID chỉ pagination; đồng nhất entries/myRank trong cùng query snapshot. Requester userID từ SessionContext, historyMatchId là payloadfield không envelope live matchId.

```sql
WITH ranked AS (
  SELECT id, display_name, avatar_id, total_score, wins, total_matches,
         RANK() OVER (ORDER BY total_score DESC, wins DESC) AS rank_value
  FROM USERS
)
SELECT * FROM ranked ORDER BY total_score DESC, wins DESC, id ASC LIMIT ? OFFSET ?;
```

- [ ] Detail kiểm tra participant và committed match trước fetch snapshot; dùng snapshots, không join bank lấy nội dung mới. Sắp rounds theo round_index, giữ running total riêng hai user: scoreBefore=running total, totalScore=scoreBefore+earnedPoints; TIMEOUT/ABANDONED không tăng điểm, final tổng phải khớp MATCHES. Pending chưa có history báo UI chưa lưu, không insert fallback. FORFEIT outcome theo winner, score rank vẫn theo điểm; ABORTED outcome NONE.
- [ ] Tests xanh + C09UI, commit `feat: expose committed rankings and participant match history`.

## 9. D08 — Demo data, startup/shutdown và bàn giao DB

**Dependency:** D01–D07/C01/N08. **Create:** DemoUserSeeder, `docs/operations/database-setup.md`, `docs/operations/persistence-runbook.md`.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/db/StartupChecksIT.java`.
**Modify:** ServerMain/config mẫu/README/server pom.

- [ ] Test DB unavailable/missing schema/bank invalid/asset registry version mismatch → startup không nhận challenge; database hợp lệ ready. Graceful shutdown đợi tối đa10s pending và báo số chưa lưu; restart không khôi phục RAM pending/live match.
- [ ] Chạy `mvn -pl server -am -Pmysql-it -Dit.test=StartupChecksIT -Dfailsafe.failIfNoSpecifiedTests=false verify`; expected FAIL trước verifier/wiring.
- [ ] DemoUserSeeder nhận password từ console/env của tác vụ seed, tạo users minh/tuan/viet/phuc, display names và8avatars registry; hashD02 và idempotent username. Không hardcode hash chứa password thật trong SQL, không đưa credential vào client bundle.

```bash
# Sau khi build; env demo password do người demo đặt ở terminal, không trong Git.
java -cp server/target/quiz-arena-server.jar vn.edu.nhom7.quiz.server.tools.DemoUserSeeder
```

- [ ] Runbook ghi schema/seed thứ tự, testDBguard, DB user quyền giới hạn, config environment, cách đối chiếu10/20/counters, xử lý PENDING/FAILED và giới hạn RAM qua restart. StartupVerifier chỉ đọc/check, không tự drop/migrate database runtime.
- [ ] Tests xanh, `mvn -Pmysql-it clean verify` chạy đủ IT; commit `docs: deliver repeatable database setup and persistence runbook`.

## 10. Gate bàn giao

- [ ] DB IT trên schema `_test` thật, không skip; fixtures/seed đúng private/public boundary.
- [ ] AC40/41/42 có SQL assert records/counters; AC43 có fault/recovery evidence.
- [ ] Global ranking chỉ committed stats và participant detail được kiểm tra từ socket authenticated.
- [ ] Người phụ trách giải thích được transaction, unique guard, commit unknown và password hashing.

Tham khảo triển khai: [Connector/J compatibility](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html), [JDBC transactions](https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html), [PBEKeySpec Java21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html). Work factor bám quyết định đã khóa trong spec; không tự hạ để demo nhanh.

# Quiz Arena — Network & Protocol Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bàn giao TCP transport, protocol v1, session/online và challenge đủ an toàn cho nhiều trận đồng thời.

**Architecture:** Một persistent socket có một reader và một writer; decoder tích lũy frame, router chỉ dispatch sau handshake/session/validation. Registry và challenge có khóa ngắn; database không chạy trong reader hoặc dưới khóa reservation. Disconnect cleanup chỉ xảy ra một lần.

**Tech Stack:** JDK21 Socket, bounded ExecutorService/BlockingQueue, Jackson, Maven/JUnit5; phụ thuộc common và các server interfaces F02.

---

**Owner:** Khuất Quang Minh. Đọc [plan tổng](2026-10-05-quiz-arena-implementation-plan.md) và spec mục7–10/12–14. Ước lượng N01–N08 khoảng8–10 ngày công, gồm test/race/integration. Mọi path dưới đây tính từ root.

## 1. File map và API

| File | Trách nhiệm |
| --- | --- |
| `common/src/main/java/vn/edu/nhom7/quiz/common/net/FrameCodec.java` | Encode 4-byte prefix + body. |
| `common/src/main/java/vn/edu/nhom7/quiz/common/net/FrameDecoder.java` | Incremental decode, elapsed partial frame, EOF. |
| `common/src/main/java/vn/edu/nhom7/quiz/common/protocol/ProtocolCodec.java` | Strict envelope/payload codec. |
| `common/src/main/java/vn/edu/nhom7/quiz/common/protocol/PayloadRegistry.java` | MessageType→payload class/validator/direction. |
| `common/src/main/java/vn/edu/nhom7/quiz/common/protocol/ProtocolException.java` | Exception có public error code, không private payload. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/HandshakeValidator.java` | HELLO đầu tiên, hạn5s, protocol/asset version. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/Connection.java` | ID/socket/queue/closed flag/lastValidFrame. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/ConnectionReader.java` | Read bytes→decoder→codec→router. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/ConnectionWriter.java` | Writer duy nhất, dequeue→write/flush. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/ConnectionManager.java` | OutboundTransport + close/cleanup ngoài match lock. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/SocketServer.java` | Accept/max32, executor lifecycle. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/MessageRouter.java` | Command guards/auth/query dispatch. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/RequestCache.java` | 100 replies/60s per connection. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/RateLimiter.java` | Window counters theo connection/IP/command class. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/session/SessionRegistry.java` | Authentication/reservation/match assignment atomic. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/session/OnlinePresenceService.java` | Revision và ONLINE_LIST snapshots. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/challenge/Challenge.java` | Immutable challenge view + internal state. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManager.java` | TTL/state/load/create/rollback. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/network/HeartbeatService.java` | Kiểm tra valid-frame timeout30s; server trả PONG cho PING client. |
| `server/src/test/java/vn/edu/nhom7/quiz/server/support/SocketHarness.java` | Test client fragmented write, await type, bounded timeouts. |

Các chữ ký codec khóa ở N01/N02:

```java
// FrameCodec
public static byte[] encode(byte[] jsonUtf8);
// FrameDecoder; một instance/connection, chỉ reader gọi
public List<byte[]> accept(byte[] bytes, int offset, int length, long nowNanos);
public void checkTimeout(long nowNanos);
public void endOfInput();
// ProtocolCodec
public Envelope decode(byte[] jsonUtf8);
public byte[] encode(Envelope envelope);
// RequestCache
public Optional<Envelope> find(UUID requestId, byte[] fingerprint, long nowNanos);
public void remember(UUID requestId, byte[] fingerprint, Envelope reply, long nowNanos);
```

ProtocolException có `String code()` và constructor `(String code, String safeMessage)`. Decode/validation không lộ answer key, password hoặc toàn bộ body trong safeMessage/log. Fingerprint thường là SHA-256 canonical command; auth dùng HMAC-SHA256 với khóa ngẫu nhiên riêng connection, chỉ giữ digest để so sánh request trùng, không giữ plaintext, raw frame hoặc password hash database.

## 2. Task N01 — Framing đúng trên TCP byte stream

**Dependency:** F01; Envelope không cần để viết decoder. **Create:** FrameCodec/FrameDecoder/ProtocolException theo file map.
**Test:** `common/src/test/java/vn/edu/nhom7/quiz/common/net/FrameDecoderTest.java`.

- [ ] Viết test prefix chia1+1+2, body từng byte, hai frame gộp, UTF8 tiếng Việt; invalid length0/-1/65537, EOF prefix/body, partial frame10s, timeout read không reset prefix.

```java
@Test void acceptsTwoFramesAcrossArbitraryChunks() {
    var decoder = new FrameDecoder();
    byte[] a = FrameCodec.encode("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
    byte[] b = FrameCodec.encode("{\"b\":2}".getBytes(StandardCharsets.UTF_8));
    var joined = ByteBuffer.allocate(a.length + b.length).put(a).put(b).array();
    var frames = new ArrayList<byte[]>();
    for (int i = 0; i < joined.length; i++) frames.addAll(decoder.accept(joined, i, 1, i));
    assertEquals(2, frames.size());
    assertEquals("{\"b\":2}", new String(frames.get(1), StandardCharsets.UTF_8));
}
```

- [ ] Chạy `mvn -pl common -Dtest=FrameDecoderTest test`; expected FAIL trước khi codec/decoder có hành vi đúng.
- [ ] Encode bằng `ByteBuffer.allocate(4 + body.length).putInt(body.length).put(body).array()`, reject ngoài1..65536. Decoder giữ `byte[4] prefix`, `prefixRead`, `byte[] body`, `bodyRead`, `Long firstByteAt`; chỉ allocate body sau length valid. Khi đủ body, append một frame rồi reset state và tiếp tục bytes còn lại.

```java
// Các guard bắt buộc trong FrameDecoder; firstByteAt=null khi không có frame dở.
public void checkTimeout(long nowNanos) {
    if (firstByteAt != null && nowNanos - firstByteAt >= Duration.ofSeconds(10).toNanos())
        throw new ProtocolException("INCOMPLETE_FRAME", "Frame incomplete for 10 seconds");
}
public void endOfInput() {
    if (firstByteAt != null)
        throw new ProtocolException("INCOMPLETE_FRAME", "EOF inside a frame");
}
```

- [ ] Chạy test xanh với body length65536 và emoji để chứng minh length theo byte; SocketTimeoutException chỉ gọi checkTimeout, không tạo decoder mới. EOF không partial vẫn cleanup connection bình thường.
- [ ] Commit `feat: implement incremental length-prefixed TCP framing`.

## 3. Task N02 — Strict protocol, handshake và fixture coverage

**Dependency:** F02, N01. **Create:** ProtocolCodec/PayloadRegistry/HandshakeValidator. **Modify:** MessageType/Payloads F02.
**Test:** `common/src/test/java/vn/edu/nhom7/quiz/common/protocol/ProtocolContractTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/network/HandshakeTest.java`.

- [ ] Viết parameterized test đọc mọi entry catalogue-v1: roundtrip valid, unknown field invalid, payload không object invalid, UUID sai invalid, version khác invalid, unknown type UNKNOWN_TYPE. TRUE_FALSE string"true" không được tự coerce thành boolean.
- [ ] Chạy `mvn -pl common -Dtest=ProtocolContractTest test`; expected đỏ cho strict/coercion nếu mapper mặc định chưa cấu hình.
- [ ] Mapper bật FAIL_ON_UNKNOWN_PROPERTIES, FAIL_ON_TRAILING_TOKENS, strict duplicate JSON field detection, JavaTimeModule; không defaultTyping. Tắt scalar coercion hoặc validate JsonNode trước typed binding. UTF8 decoder dùng REPORT khi malformed; giới hạn độ sâu JSON/nesting100 trước bind.

```java
// Mọi payload được bind qua class đã khai báo trong PayloadRegistry.
if (!envelope.payload().isObject())
    throw new ProtocolException("INVALID_MESSAGE", "Payload must be an object");
if (envelope.protocolVersion() != 1)
    throw new ProtocolException("UNSUPPORTED_PROTOCOL", "Protocol version mismatch");
// Không sử dụng Class.forName() từ field do client gửi.
```

- [ ] HandshakeValidator có `validateFirst(Envelope,long connectedAtNanos,long nowNanos)` kiểm tra HELLO đầu tiên trong5s, protocolVersion1 và assetPackVersion string"1"; trả kết quả cho router N04. Mismatch error theo spec rồi close; HELLO lặp sau thành công INVALID_STATE. Kiểm tra fixture QUESTION/quiz list/snapshot OPEN không có key/explanation/đáp án đối thủ.
- [ ] Chạy common test và `mvn -pl server -am -Dtest=HandshakeTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected PASS, F02 fixture coverage100%. Commit `feat: validate v1 protocol and asset handshake`.

## 4. Task N03 — Reader/writer, bounded queue và accept loop

**Dependency:** N01/N02, F02 OutboundTransport. **Create:** Connection/Reader/Writer/Manager/SocketServer.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/network/ConnectionWriterTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/network/SocketServerTest.java`.

- [ ] Viết loopback test100 frame từ nhiều producer; receiver decode được100 frame nguyên vẹn theo queue order. Dùng OutputStream test double chặn write để queue256 đầy; assert tryEnqueue trả false ngay, không blocking dưới match lock. Connection33 bị từ chối.
- [ ] Chạy `mvn -pl server -am -Dtest=ConnectionWriterTest,SocketServerTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước transport đầy đủ.
- [ ] Connection giữ ArrayBlockingQueue256; tryEnqueue dùng offer. Writer duy nhất take frame rồi write/flush cả frame; reader read buffer4096, Socket SO_TIMEOUT5000ms. SocketServer cấp 32 reader +32 writer capacity chuyên trách, không dùng pool4 service cho socket tasks. ConnectionManager cung cấp `boolean isAlive(UUID connectionId)` bằng ConcurrentHashMap lookup/closed AtomicBoolean, không lấy registry lock; G07 dùng để đọc liveness khi chốt terminal.

```java
// tryEnqueue không gọi close/disconnect đồng bộ trong vùng caller đang giữ match lock.
boolean accepted = connection.outbound().offer(frame);
if (!accepted) connection.markSlowConsumer();
return accepted;
```

- [ ] Caller thu failedConnectionIds, sau khi nhả match lock gọi ConnectionManager.close. Close AtomicBoolean CAS, đóng socket để đánh thức writer/reader, hủy writer future/sentinel an toàn khi queue đầy; không đợi writer đang block để cleanup registry. Service executor4 threads queue128 dùng AbortPolicy→SERVER_BUSY; scheduler2 threads.
- [ ] Chạy tests xanh, `mvn test`, commit `feat: add bounded socket transport and isolated writers`.

## 5. Task N04 — Router, request correlation, abuse limits

**Dependency:** N03/F02. **Create:** MessageRouter/RequestCache/RateLimiter.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/network/MessageRouterTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/network/RequestCacheTest.java`.

- [ ] Test unauth ANSWER/CHAT/query bị UNAUTHENTICATED; direction server→client type bị reject; duplicate requestId cùng command trả reply cũ, đổi payload bị REQUEST_ID_REUSED theo error catalogue; ttl60s/limit100 eviction không làm mất semantic guard match.
- [ ] Chạy `mvn -pl server -am -Dtest=MessageRouterTest,RequestCacheTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL cho guards chưa có.
- [ ] Route authenticated gameplay vào MatchGateway.handle(caller,envelope), userId lấy registry. Auth/query submit bounded service executor; callback chỉ enqueue nếu connectionId vẫn sống và auth/session generation còn đúng. Không giữ reader chờ DB/hash.

```java
SessionContext caller = sessions.authenticated(connectionId)
    .orElseThrow(() -> new ProtocolException("UNAUTHENTICATED", "Login required"));
matchGateway.handle(caller, envelope);
```

- [ ] Rate limits: failed auth5/min/connection và20/min/IP, queries10/s, chat theo G08 riêng; bad JSON/schema3/30s close, đúng frame JSON sai lần đầu ERROR rồi tiếp tục frame kế. Auth replay guard giữ requestId/digest HMAC từ khóa connection và sanitized reply: cùng nội dung trả reply cũ, khác nội dung REQUEST_ID_REUSED. Không cache raw credential/password hash hoặc log digest; clear cache/key khi connection đóng. In-flight duplicate không dispatch hai lần.
- [ ] Chạy tests xanh và kiểm tra log không password/raw auth frame. Commit `feat: enforce session routing and request idempotency`.

## 6. Task N05 — SessionRegistry atomic và cleanup identity

**Dependency:** F02/N04; AuthService có thể fake trước D03. **Create:** SessionRegistry, AssignmentSnapshot F02.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/session/SessionRegistryTest.java`.

- [ ] Viết barrier tests hai login cùng user: đúng một true; remove connection cũ không xóa login mới. Reserve A/B và A/C chỉ một pair thành công; transfer rematch không có FREE gap; release terminal gọi hai lần không đổi revision lần hai.
- [ ] Chạy `mvn -pl server -am -Dtest=SessionRegistryTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL nếu atomic invariants chưa có.
- [ ] Dùng một registry lock ngắn bảo vệ user↔connection, challenge reservation, matchId/generation; API return snapshots, không call socket/DB/MatchGateway trong lock. removeConnection kiểm tra exact connectionId before delete.

```java
// Bên trong registry lock; identity guard chống cleanup phiên cũ.
if (Objects.equals(userToConnection.get(userId), connectionId)) {
    userToConnection.remove(userId);
    connectionToUser.remove(connectionId);
}
```

- [ ] Disconnect cập nhật registry trước rồi chuyển immutable SessionContext tới challenge/match cleanup ngoài registry lock. LOGOUT đi qua detach/forfeit trước unauthenticate; router không nhầm Socket offline với user FREE.
- [ ] Tests xanh, AC02/06/30/31; commit `feat: maintain atomic player sessions and assignments`.

## 7. Task N06 — Online revision và query dispatch

**Dependency:** N05/D03/D04 interface. **Create:** OnlinePresenceService.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/session/OnlinePresenceTest.java`.

- [ ] Viết test login→FREE, invite→CHALLENGING, accept→BUSY, result→BUSY, exit→FREE, logout→absent. Revision tăng khi visible state thay đổi; late callback không hồi sinh ghost user.
- [ ] Chạy `mvn -pl server -am -Dtest=OnlinePresenceTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước presence service.
- [ ] Build ONLINE_LIST từ registry immutable snapshot+profile cache; phát full list kèm revision, không fetchDB dưới registry lock. Client bỏ list revision cũ; query QUIZ_LIST/DETAIL, PROFILE, RANKING, HISTORY, MATCH_DETAIL qua services Phúc.

```java
// Sau mutation thành công; revision dùng AtomicLong hoặc nằm trong registry lock.
long nextRevision = revision.incrementAndGet();
// Snapshot tại cùng revision; serialize/enqueue ngoài registry lock.
```

- [ ] Sau DB commit Phúc callback refresh profiles rồi invalidate ranking và online revision; không tăng global score từ live Match. Test query failure MATCH_NOT_FOUND/NOT_MATCH_MEMBER/DB_UNAVAILABLE theo spec, không gửi nội dung SQL.
- [ ] Tests xanh và I01 hai client; commit `feat: broadcast authoritative online presence and query replies`.

## 8. Task N07 — Challenge reservation, timeout và nạp câu

**Dependency:** N05/N06, D04 QuizRepository, G01/G04 create match contract. **Create:** Challenge/ChallengeManager.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/challenge/ChallengeManagerTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/challenge/ChallengeRaceTest.java`.

- [ ] Test20s expiry/reject/cancel/disconnect, tự mời/busy/quiz unavailable; race opposite invitations, shared target, duplicate accept, accept vs expiry; load lỗi/timeout5s, callback late sau invalidation. Assert một terminal event, không user BUSY vĩnh viễn.
- [ ] Chạy `mvn -pl server -am -Dtest=ChallengeManagerTest,ChallengeRaceTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước manager.
- [ ] Khóa challenge mutation ngắn; PENDING→CREATING_MATCH atomic khi reservation hợp lệ. Submit QuizRepository.loadMatchQuestions và MatchManager.prepare ở service executor, timeout5s. Completion kiểm tra lại state/connections/reservation rồi attachReservedPair và activate aggregate đã chuẩn bị đúng một lần. Fail INVALIDATED+release; nếu đã attach mà activate lỗi, detach hai user với expected newMatchId. Không gửi ACCEPTED trước activation thành công.

```java
// Guard của completion callback, nằm dưới challenge lock.
if (challenge.state() != ChallengeState.CREATING_MATCH) return;
if (!bothConnectionsAlive || !reservationStillOwned) {
    // Chốt INVALIDATED, giải phóng đúng challengeId, thu event để enqueue sau lock.
    return;
}
```

- [ ] Countdown/ready giao cho Tuấn; challenge không tự mở câu. Trong CREATING_MATCH accept/cancel khác INVALID_STATE; disconnect vẫn invalidate. Chặn create khi ResultSink.canCreateMatch=false hoặc đã16 active match; trước attach/activate gọi reserveMatch(newMatchId) atomic. Mọi load/activation rollback và submit executor rejected giải phóng đúng reservation, không để slot pending bị giữ vĩnh viễn. Activation events/failed-connection cleanup được publish sau khi nhả challenge/registry lock.
- [ ] Tests xanh, seed ratio test D04 xanh, I01 xanh; commit `feat: coordinate race-safe quiz challenges`.

## 9. Task N08 — Heartbeat, disconnect và harness vận hành

**Dependency:** N01–N07 và MatchGateway contract F02; G07 cần để kiểm tra forfeit end-to-end cuối task. **Create:** HeartbeatService/SocketHarness, `server/src/test/java/vn/edu/nhom7/quiz/server/network/DisconnectTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/network/HeartbeatTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/network/BackpressureTest.java`.
**Modify:** SocketServer/ConnectionManager/ServerMain.

- [ ] Test no valid frame30s→close, client PING10s/server PONG giữ alive, read timeout5s không tự disconnect, partial10sclose; reader/writer/heartbeat cùng close chỉ một cleanup. Slow consumer queue đầy không dừng match khác. Chưa HELLO sau5s phải close, không giữ32slots vĩnh viễn.
- [ ] Chạy `mvn -pl server -am -Dtest=DisconnectTest,HeartbeatTest,BackpressureTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected đỏ trước lifecycle hoàn chỉnh.
- [ ] Ghi lastValidFrame monotonic sau decode/validation thành công; heartbeat chỉ schedule kiểm tra/enqueue, không I/O trực tiếp. Close thực hiện ngoài match/registry locks, detached session thông báo MatchGateway một lần; attach identity/generation guard.

```java
if (closed.compareAndSet(false, true)) {
    socket.close();
    Optional<SessionContext> removed = sessions.removeConnection(connectionId);
    removed.ifPresent(matchGateway::onDisconnected); // registry lock đã nhả
}
```

- [ ] SocketHarness API `connect(host,port)`, `send(Envelope)`, `sendFragmented(Envelope,int chunkBytes)`, `await(MessageType,Duration)`, `close()`, AutoCloseable; await giữ unrelated events trong inbox, timeout có types/IDs đã nhận nhưng redacted. Server shutdown ngừng accept/create, ABORTED SERVER_SHUTDOWN live match, đợi save tối đa10s rồi đóng pools và báo pending chưa lưu.
- [ ] Chạy toàn bộ `mvn test` và I03; kiểm tra activeConnections/users/matches về0 sau cleanup. Commit `feat: add heartbeat and idempotent network shutdown`.

## 10. Bàn giao module

- [ ] Các test N01–N08 có PASS với số test thực sự chạy; AC36–39/49 có evidence riêng.
- [ ] `docs/protocol/protocol-v1.md` khớp MessageType/PayloadRegistry và spec; mọi message có fixture.
- [ ] Transport chứng minh không I/O dưới match lock; pool/queue/capacity và cleanup trình bày được.
- [ ] I01/I03 demo hai client/four client chạy được với DB/server thật, không sử dụng fake auth trên bản bàn giao.

Lệnh/codec Java tuân theo [Socket API Java21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html); dùng [ObjectMapper API](https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/2.18.3/com/fasterxml/jackson/databind/ObjectMapper.html) để kiểm tra cấu hình Jackson theo version đã khóa.

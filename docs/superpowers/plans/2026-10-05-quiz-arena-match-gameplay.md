# Quiz Arena — Match & Gameplay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bàn giao game engine 1v1 đủ10 câu, chấm bốn loại đáp án, realtime leaderboard, chat/forfeit/rematch và bảo vệ mọi race bằng test xác định.

**Architecture:** Một Match aggregate có một khóa và một phiên bản phase; mọi command/timer kiểm tra state dưới khóa. Clock/scheduler/transport/persistence được inject để test không cần socket/DB. Match tạo events và summary immutable, enqueue nonblocking; I/O/registry coordination chạy sau khi nhả khóa.

**Tech Stack:** JDK21 records/ReentrantLock, GameClock/GameScheduler F02, Jackson typed answer validation, JUnit5.

---

**Owner:** Đỗ Quang Tuấn. Đọc [plan tổng](2026-10-05-quiz-arena-implementation-plan.md), spec4/5/8–10/12. Ước lượng G01–G09 khoảng9–11 ngày công. Gameplay là nền tảng của leaderboard; không để animation hoặc client timer quyết định chuyển phase.

## 1. File map và model

| File | Trách nhiệm |
| --- | --- |
| `common/src/main/java/vn/edu/nhom7/quiz/common/protocol/QuestionType.java` | SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE/SHORT_ANSWER. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/domain/QuestionSnapshot.java` | Nội dung/options/private key/explanation/assets immutable. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/domain/ParticipantSummary.java` | Player ID/name/avatar và final score/correctCount. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/domain/StoredAnswerOutcome.java` | Outcome/typed answer serialized/elapsed/time/points. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/domain/RoundSummary.java` | Round UUID/index/question snapshot/revealed/two outcomes. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/domain/MatchSummary.java` | Header và played rounds, private persistence boundary. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/domain/FinishReason.java` | COMPLETED/FORFEIT/ABORTED. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/domain/ReasonCode.java` | NORMAL/USER_EXIT/LOGOUT/DISCONNECT/BOTH_DISCONNECTED/CLIENT_NOT_READY/INTERNAL_ERROR/SERVER_SHUTDOWN. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/domain/MatchOutcome.java` | PLAYER1_WIN/PLAYER2_WIN/DRAW/NONE. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/domain/AnswerOutcomeType.java` | ANSWERED/TIMEOUT/ABANDONED. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/MatchPhase.java` | WAITING_READY/MATCH_COUNTDOWN/QUESTION_PREPARING/ROUND_COUNTDOWN/ANSWERING/REVEAL/LEADERBOARD/RESULT/CLOSED/CANCELLED. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/Match.java` | State machine của một trận. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/MatchManager.java` | Lookup/membership/activate/detach/snapshot coordinator. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/AnswerEvaluator.java` | Strict answer validation + correct check. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/ShortAnswerNormalizer.java` | NFC/Unicode whitespace/Locale.ROOT. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/ScoreCalculator.java` | Nanos→0/1/2/3. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/MatchRanker.java` | Hai participant, tie và stable order. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/PhaseToken.java` | matchId/roundId/expectedPhase/phaseVersion. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/SystemGameClock.java` | System.nanoTime/Instant.now. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/ExecutorGameScheduler.java` | Adapter scheduler2threads/cancel. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/ChatService.java` | Match membership/plaintext/rate/echo. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/RematchCoordinator.java` | Agreement/load/registry transfer ngoài match lock. |
| `server/src/main/java/vn/edu/nhom7/quiz/server/match/MatchSnapshotFactory.java` | Phase-safe public snapshot cho chính requester. |

## 2. G01 — Private model, Match state và test harness

**Dependency:** Bản draft shared DTO của F02. Làm model cùng F02 để cả hai compile tại M1, trước F03; Minh/Phúc review. **Create:** domain records/enums, MatchPhase/PhaseToken/Match/MatchManager skeleton.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/match/MatchModelTest.java`.

- [ ] Test constructor reject participant trùng, câu không đủ10 hoặc không đúng4/2/2/2, duplicate question IDs; immutable lists không bị thay đổi khi caller sửa list gốc. Public conversion không key/explanation.
- [ ] Chạy `mvn -pl server -am -Dtest=MatchModelTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước model.
- [ ] Tạo records với chữ ký dưới đây; mỗi record chứa list dùng `List.copyOf`, answer/key giữ **String JSON canonical** để không giữ JsonNode mutable. Public options dùng `Payloads.Option` F02.

```java
public record QuestionSnapshot(long questionId, long quizId, QuestionType questionType,
    String content, List<Payloads.Option> options, String answerKeyJson,
    String explanation, String questionAssetId, String explanationAssetId) {
    public QuestionSnapshot { options = List.copyOf(options); }
}
public record ParticipantSummary(long userId, String displayName, String avatarId,
    int totalScore, int correctCount) {}
public record StoredAnswerOutcome(long userId, String answerJson, Long elapsedNanos,
    Instant receivedAt, AnswerOutcomeType outcome, Boolean correct,
    int earnedPoints, int scoreBefore, int totalScore, UUID requestId) {}
public record RoundSummary(UUID roundId, int roundIndex, QuestionSnapshot question,
    boolean revealed, List<StoredAnswerOutcome> outcomes) {
    public RoundSummary { outcomes = List.copyOf(outcomes); }
}
public record MatchSummary(UUID matchId, long quizId, String quizTitle,
    ParticipantSummary player1, ParticipantSummary player2,
    Instant startedAt, Instant endedAt, FinishReason finishReason, ReasonCode reasonCode,
    MatchOutcome outcome, int completedRounds, int openedRounds, List<RoundSummary> rounds) {
    public MatchSummary { rounds = List.copyOf(rounds); }
}
public record PhaseToken(UUID matchId, UUID roundId, MatchPhase phase, long version) {}
```

- [ ] Match state có lock, phaseVersion, eventSeq, ready/questionReady flags, accepted answers, hai scores/correctCounts, previousRanks, monotonic phaseStart/deadline, UTC startedAt/endedAt, terminal/result attachments và persistenceStatus. Chốt API `MatchManager.prepare(UUID,Payloads.QuizSummary,List<SessionContext>,List<QuestionSnapshot>)` trả Match chưa phát event; `activate(Match)` register và MATCH_START một lần. N07 attach reservation giữa prepare/activate, rollback nếu attachment không thành công. Profiles lấy trước khóa, không query DB lúc activate.
- [ ] Test xanh và bàn giao F03 fixtures; commit `feat: establish immutable match domain and lifecycle state`.

## 3. G02 — Strict validation và evaluator bốn loại

**Dependency:** G01. **Create:** AnswerEvaluator/ShortAnswerNormalizer.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/match/AnswerEvaluatorTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/match/ShortAnswerNormalizerTest.java`.

- [ ] Parameterized tests single correct/wrong/unknownoption; multi exactset/reordered/missing/extra/duplicate; TFboolean/stringtrue; short spaces/NFC/case/accents/alias. Invalid payload phải throw INVALID_ANSWER, chưa tạo AcceptedAnswer; hợp lệ sai trả false.

```java
@Test void normalizesWhitespaceAndNfcButKeepsAccents() {
    assertEquals("hà nội", ShortAnswerNormalizer.normalize("  HÀ\u00A0  NỘI  "));
    assertEquals(ShortAnswerNormalizer.normalize("Ha\u0300 Nội"),
                 ShortAnswerNormalizer.normalize("Hà Nội"));
    assertNotEquals(ShortAnswerNormalizer.normalize("Ha Noi"),
                    ShortAnswerNormalizer.normalize("Hà Nội"));
}
```

- [ ] Chạy `mvn -pl server -am -Dtest=AnswerEvaluatorTest,ShortAnswerNormalizerTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước evaluator.
- [ ] Chữ ký `boolean evaluate(QuestionSnapshot question, JsonNode answer)`: validate type/options/length trước chấm; không coerce boolean/number/string. Multiple array nonempty/no duplicate/no unknown, compare Set; short input1–120 codepoints theo spec, so sánh aliases chuẩn hóa và giữ raw typed answer để reveal.

```java
public static String normalize(String input) {
    String nfc = Normalizer.normalize(input, Normalizer.Form.NFC);
    StringBuilder out = new StringBuilder();
    boolean pendingSpace = false;
    for (int cp : nfc.codePoints().toArray()) {
        if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
            pendingSpace = out.length() > 0;
        } else {
            if (pendingSpace) out.append(' ');
            out.appendCodePoint(cp);
            pendingSpace = false;
        }
    }
    return out.toString().toLowerCase(Locale.ROOT);
}
```

- [ ] Display correct short dùng alias gốc đầu tiên, không output normalized text; aliases explicit, không fuzzy/bỏ dấu/punctuation. JSONkey invalid là internal/import error ABORTED, không đoán key để tiếp tục.
- [ ] Tests xanh, D04 reuse normalizer validation; commit `feat: evaluate four typed quiz answers deterministically`.

## 4. G03 — Điểm nanosecond và ranktie

**Dependency:** G01/G02. **Create:** ScoreCalculator/MatchRanker.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/match/ScoreCalculatorTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/match/MatchRankerTest.java`.

- [ ] Test đúng ở0,5s−1ns,5s,5s+1ns,10s−1ns,10s,10s+1ns,15s; sai0, elapsed<0/>15s reject. Test0–0/3–0/3–3/3–6 ranks và stable player1/player2 tie.

```java
@Test void scoresUsingNanosecondsWithoutMillisecondRounding() {
    assertEquals(3, ScoreCalculator.calculate(true, 5_000_000_000L));
    assertEquals(2, ScoreCalculator.calculate(true, 5_000_000_001L));
    assertEquals(2, ScoreCalculator.calculate(true, 10_000_000_000L));
    assertEquals(1, ScoreCalculator.calculate(true, 10_000_000_001L));
    assertEquals(1, ScoreCalculator.calculate(true, 15_000_000_000L));
}
```

- [ ] Chạy `mvn -pl server -am -Dtest=ScoreCalculatorTest,MatchRankerTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước implementation.
- [ ] Implement chính xác hàm score; rank(user1)=score1≥score2?1:2 và đối xứng. Sort scoreDESC, khi equal giữ player1 trước; không correctness/time làm tiebreak.

```java
public static int calculate(boolean correct, long elapsedNanos) {
    if (elapsedNanos < 0 || elapsedNanos > 15_000_000_000L)
        throw new IllegalArgumentException("Elapsed outside answer window");
    if (!correct) return 0;
    if (elapsedNanos <= 5_000_000_000L) return 3;
    if (elapsedNanos <= 10_000_000_000L) return 2;
    return 1;
}
```

- [ ] Test sum0–30, previousRanks khởi đầu1/1 và rankDelta=previousRank−rank; correctCount không tăng theo earnedPoints mà theo correct boolean.
- [ ] Tests xanh, commit `feat: calculate server scores and shared match ranks`.

## 5. G04 — Ready, chuẩn bị câu và phase scheduler

**Dependency:** G01/F03/G03. **Create:** SystemGameClock/ExecutorGameScheduler. **Modify:** Match/MatchManager.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/match/MatchReadinessTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/match/PhaseTimerTest.java`.

- [ ] Test WAITING_READY30s thiếu một → CANCELLED không summary; hai MATCH_READY → countdown3s, startedAt khi countdown bắt đầu. QUESTION5s thiếu một → ABORTED; ready duplicate không reset deadline; đủ → countdown2s → OPEN15s. Callback sai phaseVersion/roundId phải no-op.
- [ ] Chạy `mvn -pl server -am -Dtest=MatchReadinessTest,PhaseTimerTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước transitions.
- [ ] Mỗi transition tăng phaseVersion, hủy future cũ, đặt monotonic deadline và schedule token. Callback lấy lại match lock rồi kiểm tra token/phase/round/version; scheduler không chạy DB/socket write hoặc sleep.

```java
// Trong callback, dưới match lock.
boolean isCurrent(PhaseToken token) {
    return matchId.equals(token.matchId()) && Objects.equals(roundId, token.roundId())
        && phase == token.phase() && phaseVersion == token.version();
}
// Mọi callback: if (!isCurrent(token)) return; sau đó transition đúng một lần.
```

- [ ] QUESTION chỉ public content, tăng openedRounds ngay khi phát; client render rồi QUESTION_READY. OPEN timestamp lấy lúc transition ANSWERING, QUESTION_OPEN có remainingMs15000. READY_STATUS echo requestId người gửi; QUESTION_READY không chờ ACK riêng. Thu enqueue failures rồi close ngoài match lock và challenge/registry lock nếu còn giữ.
- [ ] Tests xanh, `mvn test`, commit `feat: synchronize match readiness and server phase deadlines`.

## 6. G05 — Nhận đáp án, ACK và close-once

**Dependency:** G02–G04/N04. **Modify:** Match/MatchManager.
**Create test:** `server/src/test/java/vn/edu/nhom7/quiz/server/match/MatchAnswerTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/match/MatchAnswerRaceTest.java`.

- [ ] Test answer slot/ACK private/STATUS không nội dung đáp án, invalid chưa tiêu thụ slot, stale question/round/nonmember/state, duplicate cùng requestId hoặc requestId khác, một timeout và hai answer chốt sớm. Barrier race answer1/answer2/timeout: một close, một result, tổng điểm đúng.
- [ ] Chạy `mvn -pl server -am -Dtest=MatchAnswerTest,MatchAnswerRaceTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước receive/close guards.
- [ ] Dưới khóa lấy `receivedNano=clock.nanoTime()` ngay khi bắt đầu validate; verify membership/round/question/state/deadline/type. `receivedNano>deadline` LATE_ANSWER; tại deadline nếu timer đã close thì INVALID_STATE/LATE_ANSWER, nếu ANSWER lấy khóa trước và OPEN thì được nhận. Không làm tròn millisecond để tính điểm.

```java
// Timestamp và OPEN guard đều dưới cùng match lock.
long receivedNano = clock.nanoTime();
if (phase != MatchPhase.ANSWERING || questionClosed)
    throw new ProtocolException("INVALID_STATE", "Question is closed");
if (receivedNano > deadlineNano)
    throw new ProtocolException("LATE_ANSWER", "Answer deadline passed");
long elapsed = receivedNano - questionStartNano;
boolean correct = evaluator.evaluate(currentQuestion, answer); // invalid chưa consume slot
int points = ScoreCalculator.calculate(correct, elapsed);
```

- [ ] Lưu raw answer/elapsed/receivedAt/requestId; ACK có time/acceptedRequestId, không correct/points; STATUS chỉ userId/answered. Có thể tính correct private khi nhận nhưng chỉ cộng score/correctCount khi close. Close guard tạo hai outcomes; missing là TIMEOUT/null answer/null time/false/0; update score một lần, completedRounds++ rồi REVEAL. Queue fail được thu lại để disconnect sau lock, terminal guard bảo vệ callback đến sau.
- [ ] Tests xanh gồm đúng15s, timer-first và answer-first với khóa kiểm soát được; commit `feat: acknowledge answers and finalize each round once`.

## 7. G06 — Reveal, leaderboard và kết quả10 câu

**Dependency:** G05/G03. **Modify:** Match/MatchManager.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/match/RoundPresentationTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/match/TenRoundEngineTest.java`.

- [ ] Test result2s → leaderboard3s → next question; rows đủ previousRank/scoreBefore/earned/total/correctCount/outcome. TenRoundEngineTest chạy10 câu bằng fake clock, đếm10 reveal/10 leaderboard và leaderboard10 trước result; đủ win/lose/draw.
- [ ] Chạy `mvn -pl server -am -Dtest=RoundPresentationTest,TenRoundEngineTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước presentation/end sequence.
- [ ] Build Payloads.QuestionResult từ finalized outcomes/đáp án hiển thị/explanation; sau2s build standings theo MatchRanker, lưu previousRanks cho vòng sau. Server durations2/3s không chờ animation hoặc click continue. Score snapshot đầy đủ, không chỉ delta.

```java
// Invariant dùng trong test từng đứng hạng.
assertEquals(row.totalScore(), row.scoreBefore() + row.earnedPoints());
assertEquals(10, result.completedRounds());
assertEquals(10, result.openedRounds());
```

- [ ] Sau leaderboard10 kết thúc COMPLETED, winner theo final score, tạo immutable summary, MATCH_RESULT PENDING rồi ResultSink.submit. Result enqueue trước khi save callback có thể phát SAVED; persistence service không emit inline dưới match lock. Winner forfeit dùng luật G07, độc lập thứ hạng điểm.
- [ ] Tests xanh, C07 đối chiếu fixture và UI thật; commit `feat: publish reveals and leaderboard after every quiz round`.

## 8. G07 — Forfeit, abort và terminal guard

**Dependency:** G04–G06, MatchGateway.onDisconnected F02, liveness read N03 và ResultSink contract F02; không đợi N08 implementation mới test termination. **Modify:** Match/MatchManager.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/match/MatchTerminationTest.java`.

- [ ] Parameterized matrix rời/logout/EOF ở mọi phase: WAITING_READY hủy/không history; từ countdown forfeit; REVEAL/LEADERBOARD giữ điểm đã chốt; prepared/open chưa chốt có hai ABANDONED. Hai người offline trước close → ABORTED; forfeit đã chốt rồi disconnect thứ hai không đổi kết quả.
- [ ] Chạy `mvn -pl server -am -Dtest=MatchTerminationTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước terminal rules.
- [ ] `finish` dưới match lock kiểm tra terminal đã có thì no-op; hủy phase future, đặt endedAt/finishReason/reasonCode/outcome. Không chấm accepted answer của câu chưa chốt; ABANDONED correct=null/points0, giữ raw answer/elapsed nếu đã nhận. Summary không có câu chưa phát QUESTION; phân biệt completedRounds/openedRounds. CANCELLED không submit và gọi cancelReservation sau lock để giải phóng slot; started match submit tiêu thụ reservation đã giữ.

```java
// Terminal không thể sửa sau khi đã tạo summary.
if (terminalSummary != null || phase == MatchPhase.CANCELLED || phase == MatchPhase.CLOSED)
    return;
// StartedAt=null => cancellation event/log; không gọi ResultSink.submit.
```

- [ ] Minh cung cấp liveness read không lấy registry lock: `ConnectionManager.isAlive(UUID connectionId)` đọc closed AtomicBoolean, close đánh dấu trước cleanup. `finish` đọc hai flags ngay dưới match lock; hai người đã offline thì ABORTED. EXIT giải phóng riêng người rời, còn lại BUSY trong RESULT; LOGOUT reasonLOGOUT, lỗi nội bộ ABORTED INTERNAL_ERROR, shutdown ABORTED SERVER_SHUTDOWN. Thu assignment cleanup rồi thực hiện ngoài match lock; không lấy registry lock trong finish.
- [ ] Tests xanh cùng phase matrix I03; commit `feat: finalize forfeits and aborts without changing completed results`.

## 9. G08 — Chat không thay đổi timer

**Dependency:** G04/G07/N04. **Create:** ChatService. **Modify:** MatchGateway routing/Match.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/match/ChatServiceTest.java`.

- [ ] Test WAITING_READY đến RESULT khi attached; user ngoài trận/đã detach bị reject. Message1–300 codepoints sau strip/nonempty,5/10s, emoji đếm codepoint, duplicate request chỉ một chatID; chat100 messages không reset phase deadline hoặc làm ANSWER bị rate limit.
- [ ] Chạy `mvn -pl server -am -Dtest=ChatServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước chatrelay.
- [ ] Validate codepoint/rate, match lock ngắn kiểm tra membership và gán eventSeq; server tạo chatMessageId/name/time, echo cả hai cùng ID/requestId. Plain text, không HTML parsing hoặc client senderID; RATE_LIMITED có retryAfterMs riêng chat.

```java
String strippedText = text.strip();
int count = strippedText.codePointCount(0, strippedText.length());
if (count < 1 || count > 300)
    throw new ProtocolException("INVALID_INPUT", "Chat must contain 1–300 characters");
// CHAT_MESSAGE.text dùng strippedText đã được validate.
// Không gọi transition(), schedule() hoặc sửa deadlineNano trong xử lý CHAT.
```

- [ ] Assert deadline không đổi trước/sau chat ở OPEN/REVEAL/LEADERBOARD; result chat chỉ relay khi hai người còn attached. Client reconcile dòng local pending từ server echo, không thêm bản sao.
- [ ] Tests xanh + C08; commit `feat: relay rate-limited chat within attached match sessions`.

## 10. G09 — Snapshot, result session và rematch coordinator

**Dependency:** G07/G08/N05/N07/D04/D06. **Create:** MatchSnapshotFactory/RematchCoordinator.
**Test:** `server/src/test/java/vn/edu/nhom7/quiz/server/match/MatchSnapshotTest.java`, `server/src/test/java/vn/edu/nhom7/quiz/server/match/RematchCoordinatorTest.java`.

- [ ] Test OPEN snapshot chỉ own accepted answer, không key/đáp án đối thủ; reveal snapshot có latestResult, snapshot eventSeq mới/không đổi điểm. Result60s detach, request20s bị cap bởi expiry, REMATCH_REQUEST người thứ hai tính accept; reject/expire/một người rời-offline invalidated.
- [ ] Chạy `mvn -pl server -am -Dtest=MatchSnapshotTest,RematchCoordinatorTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước session/rematch.
- [ ] Ghi hai người đồng ý dưới match lock, nhả khóa rồi lấy AssignmentSnapshot và load quiz ngoài khóa. Completion kiểm tra result session/generation/connections/DB health, reserveMatch(newMatchId) rồi SessionRegistry.transferPair atomic old→new, không khoảng FREE; prepare/activate10 câu mới, score/ready/seq sạch; callback invalid không tạo trận và cancel đúng save reservation mới. Result hết hạn trong load thì invalidate và giải phóng đúng attachments. Load có timeout5s như challenge.

```java
long remainingResultNanos = resultDeadlineNano - clock.nanoTime();
long rematchWindowNanos = Math.min(Duration.ofSeconds(20).toNanos(), remainingResultNanos);
// Đồng ý không có nghĩa bỏ qua health/attachment/load timeout guards.
```

- [ ] Snapshot phaseRemaining theo monotonic clock/clamp; client apply không replay animation. RESULT_SESSION_CLOSED cho exit/expire/rematch; giải phóng theo expected matchId. Save PENDING trận cũ vẫn retry và notification route oldID, không ảnh hưởng trận mới. DB failed chặn rematch; pending khi DB khỏe không tự chặn. Match đã cleanup không cho live snapshot; history qua D07.
- [ ] Tests xanh + C03/C08/I03; commit `feat: coordinate result expiry snapshots and fresh rematches`.

## 11. Gate bàn giao

- [ ] Engine10 câu test không chờ15s thật; mọi timer có phaseVersion/roundId guard.
- [ ] EventSeq tăng dưới lock trước enqueue; private ACK gaps hợp lệ; không leak private data khi OPEN.
- [ ] Evaluator/score/rank/termination/retry boundaries khớp spec, client không chấm.
- [ ] I02/I03 chạy thật; dữ liệu10/20 và review câu chưa chốt đúng.
- [ ] Giải thích được thời điểm nhận nghiệp vụ, deadline race và tính bất biến của terminal result.

Clock thực dùng [System.nanoTime Java21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime()); thời lượng không tính bằng wallclock. Nghiệm thu giới hạn LAN theo spec, không tuyên bố mọi máy nhận cùng nanosecond.

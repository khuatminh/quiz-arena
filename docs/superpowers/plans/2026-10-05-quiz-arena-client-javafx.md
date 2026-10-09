# Quiz Arena — JavaFX Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bàn giao client JavaFX có trải nghiệm chọn quiz, thi đấu và leaderboard sau mỗi câu lấy cảm hứng từ phiên quiz.com đã phân tích.

**Architecture:** NetworkClient nhận/gửi TCP nền; reducer tạo state từ server events; views gửi intents qua command gateway. Một GameView dùng bốn question renderers và đổi panel theo phase. UI hiển thị điểm/kết quả của server, timer local và animation phục vụ phản hồi.

**Tech Stack:** JDK21/JavaFX21, common protocol/frame codec, CSS, JUnit5 cho state/intent/asset registry; native UI kiểm tra thủ công có evidence.

---

**Owner:** Nguyễn Hữu Việt. Đọc [plan tổng](2026-10-05-quiz-arena-implementation-plan.md), spec5/6/9/10/12. Ước lượng C01–C09 khoảng9–11 ngày công. Chạy fixture ngay từ đầu để không chờ server; nghiệm thu cuối bắt buộc server thật.

## 1. File map và nguyên tắc UI

| File | Trách nhiệm |
| --- | --- |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ClientMain.java` | Stage/app lifecycle/connection settings. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/network/NetworkClient.java` | Reader/writer/heartbeat/disconnect nền. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/network/RequestTracker.java` | requestId→pending/response/error/timeout. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/network/ServerTimeEstimator.java` | RTT/offset/remaining hiển thị. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/state/ClientState.java` | Immutable view state, profile/lobby/active match. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/state/ClientStateReducer.java` | apply event, revisions, eventSeq và snapshot. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/state/ClientStore.java` | Serialize intents/events, publish snapshot tới FX. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/UiCommandSink.java` | View intents→gateway, không Socket trực tiếp. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/CommandGateway.java` | Tạo UUID/envelope, pending, gọi NetworkClient. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/AppShell.java` | Điều hướng Auth/Lobby/Game/Result/query views. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/AuthView.java` | Login/register/connect state. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/LobbyView.java` | Header/topics/quiz grid/online panel. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/QuizDetailView.java` | Quiz rules/counts, không đáp án. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/ChallengeDialog.java` | Sender/recipient pending/TTL. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/WaitingView.java` | Match Ready/initial countdown. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/GameView.java` | Header/timer/renderer/reveal/leaderboard. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/QuestionRenderer.java` | Renderer interface và typed answer intent. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/SingleChoiceRenderer.java` | Một option tap-submit. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/MultipleChoiceRenderer.java` | Set selection +Submit. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/TrueFalseRenderer.java` | Boolean tap-submit. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/ShortAnswerRenderer.java` | Text120 codepoint +Submit/Enter. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/AnswerIntentFactory.java` | JSON shape và local nonempty/type guards. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/RevealPanel.java` | Choices/avatars/outcomes/explanation. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/LeaderboardPanel.java` | Hai rows/score delta/tie/animation. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/ChatPanel.java` | Plain text, echo reconciliation, unread drawer. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/ResultView.java` | Outcome/review/persistence/rematch. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/RankingView.java` | Global rank/page/myRank. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/ui/HistoryView.java` | Own history/detail/review. |
| `client/src/main/java/vn/edu/nhom7/quiz/client/assets/AssetLoader.java` | Classpath assets và optional placeholder. |
| `common/src/main/java/vn/edu/nhom7/quiz/common/assets/AssetRegistry.java` | Parse registry, version và valid IDs dùng cho server/client. |
| `common/src/main/resources/assets/registry.json` | assetPackVersion1,8avatar IDs, paths/attribution. |
| `client/src/main/resources/ui/quiz-arena.css` | Visual tokens/layout/states. |
| `client/src/main/resources/assets/avatars/avatar-01.png` | Avatar01. |
| `client/src/main/resources/assets/avatars/avatar-02.png` | Avatar02. |
| `client/src/main/resources/assets/avatars/avatar-03.png` | Avatar03. |
| `client/src/main/resources/assets/avatars/avatar-04.png` | Avatar04. |
| `client/src/main/resources/assets/avatars/avatar-05.png` | Avatar05. |
| `client/src/main/resources/assets/avatars/avatar-06.png` | Avatar06. |
| `client/src/main/resources/assets/avatars/avatar-07.png` | Avatar07. |
| `client/src/main/resources/assets/avatars/avatar-08.png` | Avatar08. |
| `client/src/main/resources/assets/images/quiz-cover-general.png` | Cover kiến thức chung. |
| `client/src/main/resources/assets/images/quiz-cover-science.png` | Cover khoa học. |
| `client/src/main/resources/assets/images/quiz-cover-history.png` | Cover lịch sử. |
| `client/src/main/resources/assets/images/question-sample.png` | Ảnh mẫu câu hỏi/explanation trong seed. |
| `client/src/main/resources/assets/images/placeholder.png` | Fallback ảnh tùy chọn. |

Assets tự tạo hoặc có giấy phép cho phép sử dụng, ghi attribution. Ảnh quan sát quiz.com trong spec là tham khảo luồng/bố cục; assets sản phẩm do nhóm chuẩn bị. Nếu thay PNG bằng vector, cập nhật registry/version và seed trong cùng commit.

API view khóa trước implementation:

```java
public interface UiCommandSink {
    UUID send(MessageType type, UUID matchId, UUID roundId, Object payload);
}
public interface QuestionRenderer {
    Node view();
    void bind(Payloads.PublicQuestion question, Consumer<JsonNode> onAnswer);
    void setControlsEnabled(boolean enabled);
    void showResult(Payloads.QuestionResult result, long selfUserId);
}
// ClientStateReducer
public ClientState apply(ClientState previous, Envelope event, long receivedNanos);
// NetworkClient; không block FX thread
public CompletableFuture<Void> connect(String host, int port);
public boolean send(Envelope command);
public void close();
```

UiCommandSink trả requestId cho pending reconciliation; CommandGateway tạo Envelope với eventSeq=null. ClientState không import server/domain; view không gọi evaluator/JDBC.

## 2. C01 — App shell, design tokens và asset contract

**Dependency:** F01/F02; Minh review registry. **Create:** AppShell/AssetLoader/AssetRegistry/CSS/registry và resources theo file map.
**Test:** `client/src/test/java/vn/edu/nhom7/quiz/client/assets/AssetRegistryTest.java`.

- [ ] Viết test required avatar có file, optional asset thiếu trả placeholder, registry IDs unique/path không external URL, version string"1"; attribution hợp lệ. Chạy `mvn -pl client -am -Dtest=AssetRegistryTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước loader/assets.
- [ ] Dựng Stage1280×800, min1024×720, AppShell đổi central view. Lobby cream/game tối theo spec; body16, question26–32, answer18–22, spacing8, card radius16/button24, border2–3px.

```css
.root { -fx-font-size: 16px; -fx-text-base-color: #17282C;
    -fx-background-color: #FFF9EF; }
.game-surface { -fx-background-color: #234349; }
.game-surface .label { -fx-text-fill: #FFF9EF; }
.answer-card .label { -fx-text-fill: #17282C; }
.answer-card { -fx-background-color: #C5F6DF; -fx-background-radius: 16;
    -fx-border-color: #15292E; -fx-border-width: 2; -fx-border-radius: 16; }
.answer-card:focused { -fx-border-width: 3; }
.answer-card.incorrect { -fx-background-color: #EFA4A2; }
.primary-button { -fx-background-color: #4FA477; -fx-background-radius: 24; }
```

- [ ] Đóng gói8avatar IDs `avatar-01`..`avatar-08` và covers/placeholder/question sample; AssetRegistry parse từ common resources, server kiểm tra ID/metadata còn client kiểm tra image files. JavaFX classpath load, preserveRatio, image≤35% vùng câu hỏi; font fallback hỗ trợ tiếng Việt. Registry mismatch chặn ở HELLO; optional ảnh thiếu dùng fallback vẫn QUESTION_READY.
- [ ] Chạy test xanh; mở fixture kiểm tra unicode/focus/colors, lưu screenshot `docs/testing/evidence/ui-shell.png` khi triển khai. Commit `feat: establish JavaFX visual system and bundled assets`.

## 3. C02 — Network client và request lifecycle

**Dependency:** N01/N02/F02. **Create:** NetworkClient/RequestTracker/ServerTimeEstimator/UiCommandSink/CommandGateway.
**Test:** `client/src/test/java/vn/edu/nhom7/quiz/client/network/NetworkClientTest.java`, `client/src/test/java/vn/edu/nhom7/quiz/client/network/RequestTrackerTest.java`.

- [ ] Loopback test connect→HELLO first, fragmented prefix/body, queue ordered, EOF cleanup once, mismatch trước login. UUID correlation, auth5s timeout, ANSWER2s waiting notice không resend; QUESTION_READY không await ACK; LOGOUTclose≤2s.
- [ ] Chạy `mvn -pl client -am -Dtest=NetworkClientTest,RequestTrackerTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước transport/tracker.
- [ ] Reader/writer nền dùng common codec; read timeout5s giữ partial frame,10s frame limit/30s idle. Sau HELLO_ACK client PING10s, PONG update RTT. Writer queue hữu hạn; sendfalse khi full/closed có lỗi rõ; không tự retry ANSWER.

```java
// Bridge render trên FX; socket read/write không nằm trong runnable này.
Platform.runLater(() -> appShell.render(latestState));
// Connect chạy CompletableFuture trên network executor; không join() trên FX thread.
```

- [ ] RequestTracker cancel khi connection epoch đổi; ERROR echo resolve pending. Stale/invalid state xin snapshot khi match còn active; query/auth timeout cho user retry, không tự tạo trận. Close app stop heartbeat/socket/tasks, mất kết nối không tự tuyên bố thắng.
- [ ] Tests xanh và connect serverN03; commit `feat: add asynchronous TCP client and correlated request states`.

## 4. C03 — Reducer, eventSeq và local timer

**Dependency:** F02/F03/C02. **Create:** ClientState/ClientStateReducer/ClientStore.
**Test:** `client/src/test/java/vn/edu/nhom7/quiz/client/state/ClientStateReducerTest.java`, `client/src/test/java/vn/edu/nhom7/quiz/client/network/ServerTimeEstimatorTest.java`.

- [ ] Test online revision cũ, eventSeq<=last ignored, private ACK gaps accepted, duplicate result/leaderboard không cộng lại, new match clean, old save notification. Snapshot không animation; INVALID_ANSWER còn OPEN sửa được; local0 chỉ khóa chờ server.
- [ ] Chạy `mvn -pl client -am -Dtest=ClientStateReducerTest,ServerTimeEstimatorTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước reducer/timer.
- [ ] State giữ connectionEpoch/profile/onlineRevision/quizCache/activeMatchId/lastSeqByMatch/publicQuestion/phase/presentationVersion/ownPending/accepted/outcomes/standings/attachments/persistenceByMatch/chatById. presentationVersion local tăng khi context/phase/snapshot đổi, dùng hủy animation; server phaseVersion chỉ có trong MATCH_SNAPSHOT, không tự thêm field đó vào các event ngoài contract. ClientStore serialize intents/events và publish FX snapshots; score lấy full server, không cộng delta khi replay.

```java
Long last = lastSeqByMatch.get(event.matchId());
if (event.eventSeq() != null && last != null && event.eventSeq() <= last) return previous;
// Gaps hợp lệ do private ACK; không suy luận mất event từ gap.
```

- [ ] Timer midpoint PING/PONG, chọn RTT min trong5 mẫu. Countdown local nano từ remaining hoặc phaseEnds−serverTime, trừ delay receivedNanos→FX render; có offset thì ước lượng deadline server, clamp0..duration. Local0 không tự next phase/chấm timeout. UI lag dừng animation cũ và render phase mới.
- [ ] Test xanh bằng clock giả/fixture10câu; commit `feat: reduce authoritative match events and display synchronized timers`.

## 5. C04 — Auth, Lobby, quiz detail và challenge

**Dependency:** C01–C03; D03/D04/N06/N07 hoặc fixture. **Create:** AuthView/LobbyView/QuizDetailView/ChallengeDialog.
**Test:** `client/src/test/java/vn/edu/nhom7/quiz/client/ui/LobbyIntentTest.java`.

- [ ] Intent tests: connect/login/register payload đúng, confirmPassword chỉ local, không lưu password; challenge chỉ AVAILABLE/targetFREE/nonself; accept/reject/cancel popup đúng actor, doubleclick không gửi hai request.
- [ ] Chạy `mvn -pl client -am -Dtest=LobbyIntentTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước intent gating.
- [ ] Auth hostlocalhost:5555/error/connecting/authenticating, disable related submit. Lobby header profile/score/ranking/history/logout, topics từ server, grid cards counts/availability, online không self và empty state rõ. Quiz detail ratios/15s/3-2-1, không key hoặc preview câu thi.

```java
boolean challengeEnabled = selectedQuiz != null
    && selectedQuiz.availability().equals("AVAILABLE")
    && selectedOpponent != null && selectedOpponent.userId() != selfUserId
    && selectedOpponent.status().equals("FREE") && !challengePending;
```

- [ ] Challenge popup20s countdown, CHALLENGE_CLOSED server chốt. Accept pending load5s, chỉ MATCH_START chuyển Game/Waiting; popup không chặn socket receiver. Ghi manual evidence trong `docs/testing/lobby-checklist.md`, chạy I01 thật.
- [ ] Unit/fixture/manual xanh; commit `feat: build login quiz library and challenge lobby`.

## 6. C05 — Waiting, question preparation và game layout

**Dependency:** C03/C04/G04 events. **Create:** WaitingView/GameView.
**Test:** `client/src/test/java/vn/edu/nhom7/quiz/client/state/GamePhaseStateTest.java`.

- [ ] Test MATCH_START waiting, READY_STATUS ready, countdown3s; QUESTION khóa/render/QUESTION_READY once, round countdown2s khóa, QUESTION_OPEN mở; localcountdown0 không tự mở. Optional image thiếu vẫn ready.
- [ ] Chạy `mvn -pl client -am -Dtest=GamePhaseStateTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước phase binding.
- [ ] Waiting hai avatars/quiz/rules/Ready once/30s deadline/EXIT_MATCH. Game header two players/finalized score/x10, timer/bonus estimate, renderer, chat panel260px hoặc drawer. Câu hiển thị đầy đủ ngay, không typewriter.

```java
// Sau khi bind question, layout và local optional asset hoàn tất:
commands.send(MessageType.QUESTION_READY, matchId, roundId,
    new Payloads.QuestionReady(question.questionId()));
// readySentRoundIds guard: resize/re-render không gửi lại.
```

- [ ] Countdown onFinished không mở controls; chờ QUESTION_OPEN. Không render được thì báo lỗi/chờ ABORTED, không fake READY. Event mới tới bỏ animation cũ và render đúng phase.
- [ ] Unit/manual xanh; commit `feat: render synchronized readiness and gameplay phases`.

## 7. C06 — Bốn renderer và answer pending

**Dependency:** C05/F02/G02. **Create:** QuestionRenderer/SingleChoiceRenderer/MultipleChoiceRenderer/TrueFalseRenderer/ShortAnswerRenderer/AnswerIntentFactory.
**Test:** `client/src/test/java/vn/edu/nhom7/quiz/client/ui/AnswerIntentFactoryTest.java`, `client/src/test/java/vn/edu/nhom7/quiz/client/state/AnswerPendingTest.java`.

- [ ] Factory single TextNodeoptionId, multi ArrayNodeunique, TFBooleanNode, short rawTextNode≤120codepoints/nonblank. Pending locks; ACK accepted; noACK2s waiting không resend/unlock; INVALID_ANSWER còn OPEN cho sửa, late/closed khóa.

```java
@Test void trueFalseIntentIsBooleanNotText() {
    JsonNode answer = AnswerIntentFactory.trueFalse(true);
    assertTrue(answer.isBoolean());
    assertTrue(answer.booleanValue());
}
```

- [ ] Chạy `mvn -pl client -am -Dtest=AnswerIntentFactoryTest,AnswerPendingTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước factory/pending.
- [ ] Single/TF tap submit một lần; multiple selection+Submit disable khiempty; short TextField/Gửi/Enter120codepoints. Renderer emits JsonNode; GameView thêm questionId/roundId từ state, không clientelapsed/userID/correct/score.

```java
public static JsonNode trueFalse(boolean value) { return BooleanNode.valueOf(value); }
commands.send(MessageType.ANSWER, matchId, roundId,
    new Payloads.Answer(question.questionId(), typedAnswer));
// Set pending trước enqueue để double-click không sinh hai ANSWER.
```

- [ ] Single A/B/C/D ngoài TextInputControl/chat; Space toggles focused multi card; Enter short không gửi chat. Pending khóa answer nhưng chat còn hoạt động. Options2–6/wrap120/question500, scroll vùng nội dung không cắt answer cần chọn.
- [ ] Tests xanh + manual four renderers1024×720; commit `feat: implement typed question renderers and pending answer feedback`.

## 8. C07 — Reveal và realtime leaderboard

**Dependency:** C06/G06fixture. **Create:** RevealPanel/LeaderboardPanel.
**Test:** `client/src/test/java/vn/edu/nhom7/quiz/client/ui/LeaderboardPresentationTest.java`.

- [ ] Row model test full standing/before+earned=total/tie1-1/stable order/previousRank; duplicate seq không animation hai lần, snapshot không replay, phase mới cancel animation. Avatar chỉ từ result, không ANSWER_STATUS.
- [ ] Chạy `mvn -pl client -am -Dtest=LeaderboardPresentationTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước presentation.
- [ ] Reveal giữ câu/options, tickcorrect/Xwrong/avatars dướioption; multiavatar mỗioptionselected, shorttwo rawanswerrows; TIMEOUT “Không trả lời”, explanation/ảnhnullcollapse. ROUND_LEADERBOARD thay centerpanel bằng2rows, header/chat giữ hoạt động.

```java
Duration scoreAnimation = Duration.millis(reducedMotion ? 0 : 500);
Duration rowAnimation = Duration.millis(reducedMotion ? 0 : 400);
// Animation chỉ sửa visual label; ClientState đã có final score server.
// Callback capture matchId/presentationVersion local, no-op nếu context đã đổi.
```

- [ ] Rows avatar/name/“Bạn”/rank/total/+earned/correctcount, tie “Đồng hạng”, mint/coral cóicon/chữ. Motionoff renderfinal ngay. Phasechange stop/setfinal trướcrendernew; không sendnext/onFinished/chờclick.
- [ ] Unit + I02hai client xanh, evidence câu1/câu10/tie/đổi hạng; commit `feat: reveal player answers and animate per-question standings`.

## 9. C08 — Chat, result review và rematch

**Dependency:** C03/C07/G07–G09. **Create:** ChatPanel/ResultView.
**Test:** `client/src/test/java/vn/edu/nhom7/quiz/client/state/ChatResultStateTest.java`.

- [ ] Test echo requestId replaces pending, dedup chatID, rate limit chỉ chat; forfeitreason/ABANDONED không sai, save labels PENDING/FAILED/SAVED, rematch20s cap60, detached disable chat/rematch, old save chỉ old notification.
- [ ] Chạy `mvn -pl client -am -Dtest=ChatResultStateTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước result/chat state.
- [ ] Chat plaintext1–300codepoints sau strip, Enter focusedchat/ShiftEnternewline, unread drawer và states. Result thắng/thua/hòa hai scores/correctcounts/reason, review actual openedrounds; ABANDONED “Câu chưa được chấm”, không dựng unplayedquestions.

```java
boolean rematchEnabled = resultSessionAttached && opponentAttached && !resultSessionExpired;
// Reconcile chat qua requestId/chatMessageId; save state luôn route bằng matchId.
```

- [ ] REMATCH_REQUEST/RESPONSE chờ server MATCH_START mới, không tựreset game; VềLobby EXIT_MATCH chờACKFREE. Kết quả local đọc được sau60s nhưngchat/rematchdisable. Appclose LOGOUT best-effort2s rồi socketclose, không tựwin.
- [ ] Unit/manual chat duringOPEN/rematch thật xanh; commit `feat: add match chat result review and rematch actions`.

## 10. C09 — Ranking/history, resync và native UI QA

**Dependency:** C08/D07/G09/N08. **Create:** RankingView/HistoryView, `docs/testing/ui-checklist.md`.
**Test:** `client/src/test/java/vn/edu/nhom7/quiz/client/state/QueryViewStateTest.java`, `client/src/test/java/vn/edu/nhom7/quiz/client/state/ResyncStateTest.java`.

- [ ] Test page/loading/error/empty, invalidationcoalesce refresh visible view, myRank ngoài page, own history detail và unauthorized; OPEN snapshot không animation, local0 waiting, staleevents ignored, old save không reset activegame.
- [ ] Chạy `mvn -pl client -am -Dtest=QueryViewStateTest,ResyncStateTest -Dsurefire.failIfNoSpecifiedTests=false test`; expected FAIL trước query/resync.
- [ ] Ranking committedscore/wins/myRank, pagination20max50; History opponent/time/score/outcome/reason/detailreview. Request dùng historyMatchIdpayload, không envelope match live. INVALID_STATE/STALE_ROUND xin một snapshot khi socket/session valid; không reconnectresume.

```java
commands.send(MessageType.MATCH_DETAIL_REQUEST, null, null,
    new Payloads.MatchDetailRequest(historyMatchId));
// Apply snapshot trực tiếp, không chạy +points/replay reveal.
```

- [ ] NativeQA1280×800/1024×720/DPI cao, question500/option120/explanation500, missing optional image/unicode/emoji/focus/motionoff/resize/FXlag. Ghi actual/expected/evidence; không socketread/write/hash/JDBC trênFX thread. I04metrics đo riêng độmượtanimation.
- [ ] Unit/manual/I02/I04 xanh; commit `feat: complete ranking history and resilient client presentation`.

## 11. Chạy và bàn giao

```bash
mvn clean install
mvn -f client/pom.xml javafx:run
# ClientMain.parseArgs hỗ trợ fixture và F03 FixtureReplay cung cấp data.
mvn -f client/pom.xml javafx:run -Djavafx.args="--fixture"
```

Expected: Stage1280×800, fixture không DB/network, normalmode HELLO/login thật. Dùng [OpenJFX Maven](https://openjfx.io/openjfx-docs/) và [Platform.runLater JavaFX21](https://openjfx.io/javadoc/21/javafx.graphics/javafx/application/Platform.html#runLater(java.lang.Runnable)) cho lifecycle.

- [ ] C01–C09 unit PASS, native QA có evidence, bốn renderer/leaderboard server thật chạy được.
- [ ] Client không JDBC credentials/server private key/evaluator; assets registry/attribution/version chung.
- [ ] Đã kiểm tra pending/lag/local0, duplicate event, oldsave, keyboard focus vàmotionoff.
- [ ] Giải thích được socketbackground→reducer→Platform.runLater→render, animation không quyết định luật gameplay.

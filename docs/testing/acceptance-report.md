# Acceptance report

Verification date: 2026-10-09, Asia/Ho_Chi_Minh. Implementation is present across the 42 planned tasks. This report does **not** claim all 50 acceptance criteria are fully signed off. Automated checks and native fixture snapshots are distinguished from physical interaction, high-DPI, two-machine LAN and rehearsal work.

Workspace originally had no Git repository; no commit/tag was fabricated. Final source/build identity is recorded in `evidence/source-manifest.sha256`. Verifier: Codex automated tools with independent agent review; no human sign-off is implied.

## Commands and environment

- JDK21.0.9 on Apple M4, arm64, macOS27.0; Maven3.9.11.
- Isolated MySQL8.4.7, selected schema `quiz_arena_test`, port13306; existing databases were not modified.
- `mvn clean verify`: unit/loopback build.
- `mvn -Pmysql-it clean install` with dedicated `QUIZ_TEST_DB_*`: unit, actual TCP/MySQL integration and packaging.
- Negative configuration check: `mysql-it` without DB configuration fails explicitly, never skips to green.
- Native JavaFX fixture replay captures at1280×800 and1024×720. Images are real JavaFX scene snapshots; they do not prove physical keyboard or two-client interaction.
- Operational LoadProbe against the shaded jar uses four clients/two simultaneous matches and100 requested samples per client metric; results in [performance report](performance-report.md).

The final run passed **243 tests**, with zero failures, errors or skipped tests. Fresh final command counts and exact output are in `evidence/build-summary.json` and `evidence/verification.log`. Test report XML is also retained under each module's target directory after verification.

## Acceptance matrix

PASS means the referenced automated behavior passed; PARTIAL names an unverified portion. No row is marked PASS solely because code exists.

| ID | Scenario from spec | Status | Actual evidence / remaining portion |
| --- | --- | --- | --- |
| AC01 | Register rồi Login; username trùng | PASS | UserRepositoryIT, PasswordHasherTest |
| AC02 | Login cùng tài khoản từ hai client | PASS | ProtocolRoutingIT, SessionRegistryTest |
| AC03 | Hai client Login/Logout | PASS | LobbyFlowIT, ProtocolRoutingIT |
| AC04 | Chọn quiz đủ/thiếu loại câu | PARTIAL | QuizRepositoryIT; unavailable-bank UI still needs interaction |
| AC05 | Challenge → Reject/Cancel/Expire | PASS | LobbyFlowIT, ChallengeManagerTest |
| AC06 | A/B cùng mời C hoặc A/B mời ngược nhau | PASS | SessionRegistryTest, ChallengeManagerTest |
| AC07 | Accept trùng/Accept đúng lúc expire | PARTIAL | ChallengeManagerTest; barrier at exact expiry remains |
| AC08 | Nạp câu lỗi sau Accept được xử lý | PASS | ChallengeManagerTest timed-out/late/disconnected load |
| AC09 | Chỉ một MATCH_READY/hết 30 giây | PASS | MatchEngineTest waiting timeout |
| AC10 | Hai Ready, QUESTION_READY đủ/thiếu | PARTIAL | MatchEngineTest, native waiting/countdown snapshots; live UI readiness needs interaction |
| AC11 | Single Choice đúng/sai | PARTIAL | AnswerEvaluatorTest, AnswerIntentFactoryTest; native answer click still needs interaction |
| AC12 | Multiple Choice đúng/thiếu/thừa/thứ tự khác | PARTIAL | AnswerEvaluatorTest exact sets; native multiple submit still needs interaction |
| AC13 | True/False | PARTIAL | AnswerEvaluatorTest strict boolean; native tap still needs interaction |
| AC14 | Short Answer hoa/thường/khoảng trắng/NFC | PASS | AnswerEvaluatorTest, ScoreCalculatorTest normalization |
| AC15 | Đáp án rỗng/sai kiểu/quá dài/option lạ | PASS | AnswerEvaluatorTest invalid payload/slot |
| AC16 | Đúng tại 0/5/10/15 giây và sát hai phía của mốc | PASS | ScoreCalculatorTest, MatchEngineTest exact deadline |
| AC17 | Một người không trả lời | PASS | MatchEngineTest timeout outcome |
| AC18 | Gửi ANSWER lần hai và lặp requestId | PASS | MatchEngineTest duplicate ACK, RequestCacheTest |
| AC19 | Hai ANSWER/timeout cùng chạy | PASS | MatchEngineTest three-thread close race |
| AC20 | ANSWER câu trước/câu chưa mở/match khác | PASS | MatchEngineTest stale/member guards, ProtocolRoutingIT |
| AC21 | Khi câu còn OPEN, kiểm tra payload hai client | PASS | ProtocolCatalogueTest, MatchEngineTest private snapshots |
| AC22 | REVEAL sau chốt | PARTIAL | MatchEngineTest, native reveal snapshots; interactive mapping check remains |
| AC23 | ROUND_LEADERBOARD sau mỗi câu | PARTIAL | TenRoundMatchIT, LeaderboardPresentationTest, native leaderboard snapshots |
| AC24 | Hai người bằng tổng điểm | PASS | MatchEngineTest draw/ties, native tie leaderboard |
| AC25 | Chat trong OPEN/REVEAL/LEADERBOARD | PASS | ConcurrentMatchesIT, MatchEngineTest chat deadline |
| AC26 | Chat vượt giới hạn/message quá dài | PASS | MatchEngineTest chat limits independent of answer |
| AC27 | 4 client, 2 trận đồng thời | PASS | ConcurrentMatchesIT four actual TCP clients |
| AC28 | Callback timer cũ sau next round/forfeit | PASS | MatchEngineTest stale callback tokens |
| AC29 | Hoàn tất 10 câu, thắng/thua/hòa | PASS | TenRoundMatchIT persists 10 rounds/20 outcomes |
| AC30 | Exit/Logout/EOF trong các phase | PASS | MatchTerminationTest phase × EXIT/LOGOUT/DISCONNECT |
| AC31 | Hai disconnect gần nhau | PARTIAL | MatchEngineTest offline/terminal guard; simultaneous socket EOF stress remains |
| AC32 | Rematch một bên/hai bên/Reject/Expire | PASS | TenRoundMatchIT rematch, RematchCoordinatorTest |
| AC33 | Rời RESULT hoặc hết 60 giây | PASS | MatchEngineTest result expiry, ChatResultStateTest |
| AC34 | MATCH_SNAPSHOT khi OPEN/REVEAL | PASS | ResyncStateTest, MatchEngineTest snapshot privacy |
| AC35 | Lặp QUESTION_RESULT/LEADERBOARD/eventSeq cũ | PASS | ClientStateReducerTest duplicate/gap/old save |
| AC36 | TCP tách prefix/body và gộp nhiều frame | PASS | FrameDecoderTest, SocketServerTest fragmented UTF-8 |
| AC37 | Prefix quá lớn/EOF giữa frame/JSON sai | PASS | FrameDecoderTest, ProtocolContractTest, SocketServerTest recovery |
| AC38 | Nhiều thread phát chat/result cho cùng socket | PASS | SocketServerTest concurrent 100-frame producers |
| AC39 | Client không nhận message, queue đầy | PARTIAL | SocketServerTest queue saturation; sustained OS slow-peer test remains |
| AC40 | COMMIT normal match | PASS | MatchPersistenceIT ten/20 and exactly-once counters |
| AC41 | Lỗi giữa insert answers/update users | PASS | MatchPersistenceIT rollback after rounds/answers/users |
| AC42 | Retry sau commit response bị mất/lưu cùng summary hai lần | PASS | MatchPersistenceIT lost commit reply/conflicting duplicate |
| AC43 | DB mất giữa trận rồi phục hồi | PASS | DatabaseOutageIT, PersistenceServiceTest retry |
| AC44 | History của mình và truy cập match của người khác | PASS | RankingHistoryIT participant-only detail |
| AC45 | Ranking có hòa/phân trang/myRank | PASS | RankingHistoryIT global tie/myRank, RankingService |
| AC46 | Timer local về 0 nhưng server chưa result/UI lag | PARTIAL | GamePhaseStateTest, ResyncStateTest, native timer; forced FX stall interaction remains |
| AC47 | Resize 1024×720, DPI cao, câu dài, ảnh thiếu | PARTIAL | Native snapshots at1024×720/1280×800, AssetRegistryTest; high-DPI/long-text interaction remains |
| AC48 | Chat/input keyboard và giảm chuyển động | PARTIAL | Renderer keyboard/focus and reduced-motion code; physical keyboard QA remains |
| AC49 | AssetPackVersion/protocolVersion khác | PASS | SocketServerTest handshake mismatch, ProtocolContractTest |
| AC50 | Server restart khi có trận/chưa lưu | PARTIAL | Shutdown code and documented no-reconnect/no-RAM-recovery; actual crash/restart rehearsal remains |

## Review fixes verified in source and regression tests

Challenge logout cleanup, staged activation/disconnect identity, monotonic invitation TTL, auth failure pre-checks, immutable summary/idempotent transactions, shutdown creation barrier, cancellation of stale timers, sequenced late save notifications, postcommit invalidation independent of profile refresh, frame-budget rejection before activation, and persistence scheduling before result transport were reviewed and corrected. UI review caught invisible question text; recaptured evidence uses corrected contrasting surfaces.

A sampled quiz whose complete worst-case review exceeds the fixed64KiB v1 frame budget is rejected as QUIZ_UNAVAILABLE before activation. This prevents an otherwise legal set of very long Unicode questions from producing an unsendable result/history. No implicit wire compression or truncated review was introduced.

## Remaining external acceptance

Use [UI checklist](ui-checklist.md), [lobby checklist](lobby-checklist.md), [gameplay checklist](gameplay-checklist.md), and [demo script](../demo/demo-script.md) for a physical two-machine LAN/high-DPI/keyboard/crash-restart rehearsal. These checks cannot be inferred from loopback tests or scene snapshots. A release tag and human sign-off remain outside this workspace's current verification.

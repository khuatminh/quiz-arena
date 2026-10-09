# Native JavaFX verification

Run the client with `mvn -f client/pom.xml javafx:run -Djavafx.args="--fixture"` after installing the reactor. Fixture participant IDs are 101 and 102; choose the second with `--participant=102`. Fixture mode uses the public ten-round 4/2/2/2 event stream and requires no server/database.

Native toolkit captures were generated at 1280×800 and 1024×720 with `--fixture --evidence=/absolute/output --width=1280 --height=800 --exit-after-seconds=125`. PNGs are scene snapshots from the actual JavaFX toolkit, after CSS/layout and a 650 ms animation settling interval. They contain only Quiz Arena, not the user's other applications.

| Check | Evidence / result |
|---|---|
| Auth, lobby, waiting, countdown, open, reveal, leaderboard, result | `evidence/client-1280-final/` and `evidence/client-1024-final/` |
| Unicode, option wrapping, correct labels and dark game contrast | Captures visually reviewed; question labels and leaderboard scores readable after contrast fix |
| Single tap / A-B keyboard mapping locks on first answer | `NativeViewsTest.singleTapAndKeyboardLockImmediately` |
| Multiple selection requires a choice | `NativeViewsTest.multipleRequiresNonemptySelection` |
| True/false is a boolean | `NativeViewsTest.trueFalseSendsBoolean` |
| Short raw answer preserved, 121 emoji rejected | `NativeViewsTest.shortAnswerRejectsLongInputAndPreservesRawText` |
| Late rendering subtracts monotonic receive delay; local zero locks without transition | `GamePhaseStateTest` |
| Duplicate sequence/chat ID, old save, snapshot, detached result | Reducer regression tests |
| Actual background socket to actual database-backed server | `evidence/client-live.log`, opt-in `NetworkClientLiveProbe` |
| Request timeout / typed intents / query direction | RequestTrackerTest, AnswerIntentFactoryTest, PresentationTextTest, LobbyIntentTest |

The 1024 px layout hides the chat pane by default; the Chat button toggles it. `-Dquiz.reducedMotion=true` disables leaderboard score and row animations. Each phase change stops old animations before replacing its view. No animation callback changes a match phase or score.

Physical keyboard focus traversal, high-DPI multi-monitor behavior, sustained interactive FX lag and an interactive two-window full match remain manual checks. Native control tests and screenshots do not claim those checks were performed.

# Quiz Arena interface redesign

## Direction and audit

Overhaul of an existing Vietnamese JavaFX desktop quiz product. Audience: students playing and creating quizzes. Reference: user-provided 18-screen QuizArena storyboard, plus https://quiz.com/ (category browsing and discoverable quiz creation). Retain all existing navigation labels, protocol, authoring and game rules.

Existing interface: Arial 16, cream #FFF9EF, teal #234349, pale green buttons, 24px pill radii. Screens exist for auth, lobby/details, invitations, ready/countdowns, four question types, reveal, leaderboard, result/review, ranking, history, chat and community authoring. Problems: mixed auth forms; overcrowded navigation; unstructured text rows; oversized placeholder thumbnails; inconsistent panels; raw status enums; fixed ten-question ready copy; insufficient selected-state feedback.

Design Read: a friendly learning/game desktop application, with teal, warm white and restrained gold, inspired by the supplied storyboard. Native JavaFX styling and layouts, not a web framework. DESIGN_VARIANCE 5 / MOTION_INTENSITY 3 / VISUAL_DENSITY 4. Preserve keyboard submission and server-authoritative timing. Existing motion stays optional.

## System

- Ink #183B40, muted #536B70, teal #087F70, light canvas #F4F7F5, white panels, gold #F5C65A.
- 8/12/20px radius scale; 8/12/16/24/32px spacing; system sans with Vietnamese support.
- Primary filled teal, secondary soft neutral, danger outlined red; visible keyboard focus.
- Light content panels within teal game chrome. Labels remain readable within nested light panels.
- Responsive desktop at 1024x720 and 1280x800, scroll long content rather than clip it.
- Explicit empty/loading/error states, persistent form labels, selected categories/opponents, and disabled unavailable actions.

## Coverage and verification

Auth (login/register), lobby and quiz detail, incoming/outgoing/expired challenge, waiting/countdown/preparation, all four answer renderers, reveal, per-round leaderboard, result/review, ranking, history/details, chat, quiz list/editor/preview. Render actual JavaFX screens with deterministic local fixtures, inspect representative views and minimum-size layouts; run the client/reactor test suites. Record evidence after implementation.

## Verified result (2026-10-10)

`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn install -Dquiz.uiEvidence=/tmp/quiz-redesign-evidence` completed successfully: **269 tests, zero failures/errors/skips**. This includes the opt-in real JavaFX evidence suite, new structured-editor interactions, empty search/selection preservation, and clearing stale readiness between matches. The MySQL integration profile was not rerun because this change does not modify server/database behavior.

The evidence suite rendered **63 states at each of 1280×800 and 1024×720**. Stage decorations reduce available content height, so the minimum-size run also exercises that constraint. Long content scrolls; navigation buttons are asserted within the window. Dialog previews were opened from actual editor controls for all four question types and both question/reveal modes. No CSS parser warnings were emitted. The existing unnamed-module JavaFX startup warning remains.

| Reference requirement | Current evidence |
| --- | --- |
| Login and registration | `01-login`, `02-registration`, `auth-disconnected`, `auth-validation`; separate forms and keyboard submission |
| Lobby and quiz details | `03-lobby`, `04-quiz-detail`, `lobby-selected`, `lobby-search-empty`; cover art, categories, search, opponent status, persistent valid selections |
| Incoming/outgoing/expired invitation | `05-invitation`, `06-outgoing`, `07-expired`; timer, actions, closure messages |
| Ready room and countdown | `08-ready`, `ready-one-player`, `game-match_countdown`, preparation/countdown states; correct question count and server-confirmed readiness |
| Four gameplay variants | `game-open-single_choice`, `multiple_choice`, `true_false`, `short_answer`; explicit type hints, focus, time bar, submit controls |
| Images, chat and long text | `game-question-image`, `game-shell-with-chat`, `game-long-question`; question image loading, responsive chat, wrapped/scrollable text |
| Reveal and round scores | Four `game-reveal-*` and four `game-leaderboard-*` states; correct/incorrect feedback and explanation blocks |
| Results and review | `16-result`, `result-shell`, `19-history-review`; shared ReviewCard with options, outcomes and explanation |
| Ranking and history | `17-ranking`, `18-history`, empty/loading states; aligned ranking columns and compact history rows |
| Community authoring | `21-my-quizzes`, four `editor-*`, four scrolled `question-editor-*`, eight `preview-*`; structured choices, native boolean answer, image controls and preserved drafts |

Launched `ClientMain` through `mvn -f client/pom.xml javafx:run` with the existing offline fixture and automatic evidence capture. The process exited successfully; inspected actual application auth, lobby, waiting, countdown, open, reveal and leaderboard frames. This supplements the native gallery rather than replacing its broader coverage.

Key contrast ratios: ink/white 12.08:1; muted/white 5.67:1; white/primary teal 4.91:1; placeholder/white 4.56:1; ink/gold 7.54:1. Keyboard focus remains visible. Existing optional leaderboard motion and reduced-motion support are preserved. No new animation dependency was introduced.

See [overview](evidence/overview.png) and [capture manifest](evidence/manifest.json). Full native captures are kept in the task's visualization directory under `interface-redesign`. Regenerate them with the command above. Raster cover art was generated for this project using the imagegen skill; attribution is recorded in the asset registry. Bundled images are cached across cards to avoid decoding identical art repeatedly.

This is a native desktop redesign, so web-only SEO, mobile CSS and browser performance gates do not apply. No user credentials, runtime database, or network deployment were changed. LAN/file-transfer behavior was covered by the previously completed feature verification; this pass checks UI and client regression behavior.

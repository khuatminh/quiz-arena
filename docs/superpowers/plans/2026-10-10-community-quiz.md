# Community Quiz Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development, with focused ownership and integration review. Track completion below.

**Goal:** Ship public user-authored quizzes with uploaded question/reveal images, 1–50 rounds and unranked match history.

**Architecture:** Extend the existing strict TCP catalogue with bounded authoring and media commands. Persist mutable drafts and immutable published quiz versions; pin the version when preparing a match. Keep media bytes outside match JSON and gate downloads by owner or authorized match state.

**Tech Stack:** Java 21, JavaFX 21, Jackson, Maven, MySQL 8.4, JUnit 5.

## 1. Baseline and contracts
- [x] Run `mvn test` with JDK 21 in the managed worktree; record baseline.
- [x] Define request/reply records for authoring and media. Author commands act on metadata or one question per request; list and question reads are paged to stay below 64 KiB. Media uses stop-and-wait chunks of at most 24 KiB raw.
- [x] Add failing tests for unknown/invalid commands, invalid questions, ownership and publication validity before implementation.

## 2. Authoring persistence and migration
Files: `database/003_community_quizzes.sql`, server `quiz/CommunityQuizService.java`, `quiz/JdbcQuizRepository.java`, `db/SchemaVerifier.java`, author payload records.
- [x] Add additive migration with source/owner, drafts, immutable versions, media metadata and generalized match bounds. Existing rows stay SYSTEM/ranked.
- [x] Implement per-owner list/read/save, question add/update/delete/reorder, publish/unpublish, category and question validation. Serialize changes under database row locks.
- [x] Public listing merges published community quizzes with systems, includes author/unranked label and total rounds. Gameplay loads one published version and snapshots all questions.
- [x] Test with unit validation and dedicated MySQL integration tests; do not modify runtime credentials or database without confirming isolation.

## 3. Gameplay and history
Files: server `match/MatchManager.java`, `match/Match.java`, `domain/MatchSummary.java`, `persistence/JdbcMatchRepository.java`, `query/HistoryService.java`; common `Payloads.java`.
- [x] Add regression tests for 1/50 rounds and community stats isolation, then implement dynamic total rounds, 30-second prepare deadline and immutable version/ranked metadata.
- [x] Keep system selection/scoring unchanged; persist community history without changing ranking counters.
- [x] Paginate result reviews so 50 questions cannot overflow frame. Gate media through active/current round or persisted participant history; reveal media remains private before reveal.

## 4. Media and integration
Files: common `MediaPayloads.java`, server `media/MediaService.java`, `ServerRuntime.java`, `network/MessageRouter.java`; client `assets/RemoteMedia.java`.
- [x] Write tests for PNG/JPEG content validation, size/pixel limits, owner access and chunk order/integrity, then implement server disk storage with DB metadata.
- [x] Add bounded expiring transfer sessions, 24 KiB chunks, owner/match permissions, atomic completed files and cleanup for abandoned uploads.
- [x] Wire requests through bounded I/O service; prioritize game control by transferring at most one chunk per request/ack. Require compatible client capability at handshake.

## 5. JavaFX authoring and display
Files: client `ui/CommunityQuizView.java`, `ui/AppShell.java`, `assets/AssetLoader.java`, `ui/QuizDetailView.java`, `ui/LobbyView.java`.
- [x] Build “Quiz của tôi” list and editor using existing components; preserve unsaved edits on errors. Support four question types, correct answers, ordering, shuffle and preview.
- [x] Upload local images off the JavaFX thread, show preview/replace/remove and errors. Wait for decoded question media before QUESTION_READY; reveal loading failure preserves textual result.
- [x] Render dynamic count and community label in lobby/detail/results; keep ranking statistics clearly distinct.

## 6. Verification and delivery
- [x] Run `mvn clean verify`; run isolated MySQL profile when local database is available.
- [x] Review spec coverage and code quality, fix findings, repeat relevant tests.
- [x] Update README/operations with migration, media directory, compatibility and actual verification limits.
- [x] Integrate verified commits into the original workspace without overwriting user changes; report exact commands and remaining manual checks.


## Verification evidence

- JDK21 baseline unit suite passed before changes.
- Final `mvn install -Pmysql-it` passed against isolated MySQL at127.0.0.1:3307, dedicated quiz_community_test schema:264 unit tests and33 integration tests, zero failures/errors/skips. Earlier `mvn clean verify -Pmysql-it` also passed before the final regression additions.
- Coverage includes authoring permissions/publication/version locking,1/50-round gameplay, four question types, invalid images and integrity, live/history media access, unranked persistence, all ten system review rounds via pagination, JavaFX native editor behavior, stale responses, bounded LRU cache and category-preserving lobby paging.
- No runtime database or external hosting was changed. Migration003 must be applied once to the runtime schema with the operator account; both client and server must be upgraded.
- Visual QA: actual JavaFX editor rendered at1280×900 and inspected at top/bottom scroll positions; no overlap or horizontal clipping. Persistent labels keep field limits visible after values are loaded.
- Migration preservation checked separately on MySQL8.4.7: pre-upgrade3 quizzes/60 questions, an existing match and user counters/password hash retained; legacy match becomes ranked=true,total_rounds=10,quiz_version_id=NULL.

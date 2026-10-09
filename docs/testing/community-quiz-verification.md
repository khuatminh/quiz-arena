# Community quiz verification

Date:2026-10-10. Runtime: JDK21, MySQL8.4.7.

## Automated evidence

Final `mvn install -Pmysql-it` passed264 unit tests and33 integration tests, with zero failures/errors/skips. An earlier `mvn clean verify -Pmysql-it` also passed. Verification ran using a separate MySQL process on127.0.0.1:3307 with the dedicated quiz_community_test schema. Neither runtime data nor runtime credentials were used.

Coverage includes:

- Owner-only drafts, all four question types, incomplete draft saving, publication validation,1–50 questions, ordering/shuffle, immutable published versions, stale publication rejection, media ownership and bounded metadata, draft serialization and publication review frame-budget rejection.
- TCP end-to-end creation, PNG upload, public listing, two-player challenge, question-image readiness, denial of early explanation access, reveal, withdrawal during a live match, retained history and unchanged ranking counters.
- System ten-round regression and all review pages; community1/50-round gameplay; rematches, aborts, forfeits, duplicate answers, idempotent transactional persistence and database recovery.
- Image type/hash/content/size/pixel checks, chunk sequencing and ownership, independent media/control outbound queues, and handshake compatibility rejection.
- JavaFX native editor four-type serialization, error preservation, timeout/stale response isolation, media cache eviction, stale chunk rejection and category-preserving pagination.

## Migration evidence

A second fresh isolated schema, quiz_migration_test, was initialized using001/002. Existing users with counters/password hashes and an existing match were inserted before applying003. After upgrade, all3 system quizzes and60 questions remained, schema version was3, user data remained identical, and the old match retained its title with ranked=true,total_rounds=10,quiz_version_id=NULL.

## Visual evidence and limits

Rendered the actual JavaFX editor using the existing stylesheet at1280×900 and inspected both scroll positions. No overlap or horizontal clipping; image selection, removal, save and preview controls were visible. Values retain persistent field labels and limits.

TCP tests use independent sockets on one host. Physical two-machine LAN networking, interactive OS file-picker use, long-running storage capacity and crash recovery were not manually tested. Storage retains published snapshots/images for historical review. Existing application crash-recovery limitations remain.

## Operator action

Apply database/003_community_quizzes.sql once to an existing runtime schema while the server is stopped, after backup; grant the runtime account DELETE on its own schema for draft operations. Upgrade both client and server. Configure/back up the persistent media directory with MySQL. See docs/operations/database-setup.md.

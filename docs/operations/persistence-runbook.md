# Persistence recovery

A match reserves one of 100 active/pending slots before activation. Finished immutable summaries remain in RAM until a database transaction commits. CANCELLED waiting rooms release their reservation without database writes. ABORTED games are recorded without changing player counters.

The initial result reports PENDING. A worker saves match, round snapshots, both answer outcomes and both user statistics in one transaction, locking users in ascending ID order. SAVED and profile/ranking invalidation are published only after commit or a matching prior commit is confirmed. The same match ID with different contents is rejected.

On failure, database health becomes unavailable and new matches are blocked. Live matches continue. The worker retries after 1, 2 and 4 seconds, then reports FAILED with retryable=true and keeps trying every 30 seconds. It never drops an older summary to make room. Connection loss during COMMIT is ambiguous; retries inspect the committed match projection, including millisecond timestamps and elapsed times, before deciding whether to insert.

Restore MySQL connectivity, then run a health probe and inspect pending save counts. A SELECT 1 success restores database health; capacity must also be available to create matches. Confirm MATCH_SAVE_STATUS SAVED, profile refresh and ranking invalidation. History/detail only show committed rows and detail is participant-only.

Normal shutdown stops new matches, aborts live games with a reason, and allows pending saves up to 10 seconds. Any reported remaining count is unsaved. **RAM pending summaries do not survive process crash/restart.** This version has no durable journal and does not claim to recover uncommitted results.

For transaction verification run MatchPersistenceIT in the guarded test schema. It injects failures after rounds, answers, user updates and after commit response, verifying rollback or exactly-once counters as appropriate. Never simulate these faults against runtime user data.

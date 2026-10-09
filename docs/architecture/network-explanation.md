# Network and concurrency

One persistent TCP socket per client carries a four-byte big-endian length followed by compact UTF-8 JSON. Length is1–65536 bytes. The incremental decoder retains partial prefix/body across reads and socket timeouts; incomplete frames expire after10s. Invalid length/partial EOF closes that connection. Bad JSON returns a safe error while keeping the next framed message readable.

HELLO must arrive within5s and agree on protocol1/asset pack1. Every client command has a UUID requestId and no client eventSeq or sender identity. The router obtains userId from the authenticated session, validates direction and dispatches DB/hash work to a bounded service executor. HMAC fingerprints and sanitized response caching prevent replayed request IDs from changing a command. The gameplay aggregate also guards semantic duplicate answers.

Each connection has one reader, one writer and a256-frame outbound queue. Only the writer touches the output stream. Enqueue is nonblocking; a full queue marks that peer for cleanup without blocking another match. Connection cleanup uses an atomic closed guard. The server caps connections at32; client PING/PONG heartbeat runs every10s and idle connections expire after30s.

The session registry uses short atomic mutations for authentication, pair reservations, assignment identity and rematch generations. It returns immutable snapshots and never calls gameplay under its lock. Challenge loading runs outside reservation locks with a5s deadline. Prepared aggregates are registered before attachment, so disconnect cleanup can find them during activation. An invalidated/cancelled aggregate cannot be revived by a late activation.

Each match has its own lock, monotonic clock, phase version and sequence counter. Timer callbacks carry a token and become no-ops after phase/round/version changes. Answer receipt is timestamped under that lock. Evaluation and scoring use private server question snapshots; public events omit keys until reveal. Final score comes from finalized outcomes, not client animation. A private ACK can create eventSeq gaps for the other player; gaps do not imply a missing public event.

The server phases are ready30s → initial countdown3s → question preparation≤5s → round countdown2s → answer15s → reveal2s → leaderboard3s. The tenth leaderboard precedes MATCH_RESULT. Result attachment lasts60s and permits chat/rematch only while both members remain attached. A rematch atomically transfers both assignments with a new generation and fresh questions.

Persistence receives immutable summaries outside match locks. MySQL inserts the match, round snapshots, answers and user counters in one transaction keyed by matchId. Retry after an ambiguous commit checks the stored immutable summary and does not duplicate counters. Post-commit notifications update profile/ranking; live points never directly change global ranking. Pending saves are bounded RAM state, not a crash-recoverable journal.

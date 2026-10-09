# Gameplay verification

TenRoundMatchIT uses two actual TCP clients, the production ServerRuntime, a real isolated MySQL schema and a controlled clock/scheduler. It verifies ten QUESTION_RESULT and ten ROUND_LEADERBOARD events per participant, tenth leaderboard before final result, persisted ten rounds/twenty outcomes and a fresh rematch. ConcurrentMatchesIT runs four clients/two matches, checks chat isolation, disconnects one pair and completes the other. DatabaseOutageIT injects DB connection faults and verifies retry/unique persistence recovery.

Engine/evaluator/score tests cover strict four-type answers, Unicode normalization, exact nanosecond boundaries, invalid-answer slot preservation, duplicate answers, private snapshots, phase readiness timeouts, stale callbacks, answer/timeout races, abandoned outcomes, result expiry and phase×exit/logout/disconnect outcomes. All outbound test events pass the strict public protocol codec.

Native fixture captures show question, reveal, tie leaderboard and result at two window sizes. A physical two-player complete match, keyboard/reduced-motion interaction and crash/restart rehearsal remain NOT_RUN. Use the demo script and record observations rather than treating fixtures as a live UI match.

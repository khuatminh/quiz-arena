# Four-client demonstration

1. Build with JDK21, prepare MySQL schema/seed and run the server. Create demo1–demo4 with DemoUserSeeder. Launch four JavaFX processes and log in as distinct users.
2. Select one of the three available quizzes. Challenge demo2 from demo1 and demo4 from demo3. Show Reject/Cancel on a separate invitation, then accept each pair and press Ready.
3. Show the initial countdown, question readiness and all four answer types. Single/true-false submit immediately; multiple/short use Submit. Point out that an ACK confirms receipt without revealing correctness.
4. Show reveal and the two-player leaderboard. Confirm server scoreBefore+earnedPoints=totalScore and tie rank1. The local timer cannot open a question or decide the winner.
5. Send chat during an open round. Show it only on the paired opponent and observe the unchanged deadline. Close one client mid-match; the other receives a forfeit result while the other pair continues.
6. Complete all ten questions in the surviving pair. Show the tenth leaderboard before the final result. Wait for SAVED, then inspect history, review and global ranking.
7. Request and accept a rematch. Show the new matchId in logs, fresh score/readiness and no FREE gap in the online list. Exit to release the result session.
8. Run `mvn -Pmysql-it verify` against a separate test schema for fragmented TCP, duplicate answers, simultaneous timeout/answer, transaction rollback and DB outage recovery. These race cases should be demonstrated with controlled tests rather than claimed from UI clicks.
9. Stop the server normally. Explain the10s save limit, no continuation after reconnect and loss of uncommitted RAM summaries after a hard crash.

Evidence for automated verification is in `docs/testing/acceptance-report.md`. Physical LAN, high-DPI interaction and a live human rehearsal must have their own recorded actual result; they are not implied by unit/integration tests.

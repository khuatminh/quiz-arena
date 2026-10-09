# Quiz Arena operations

Use JDK21 and MySQL8.4. Build with `mvn clean install`. Prepare the schema, seed and account permissions using [database setup](database-setup.md). Copy the example configuration into the ignored `config/server.local.properties`; environment variables override the file. Launch the shaded server jar and run each client separately with `mvn -f client/pom.xml javafx:run`.

Startup validates schema version1, asset pack1, required tables/indexes and active question validity. Fix the reported setup problem before retrying; startup does not reset an existing database. The example configuration contains no private password.

For LAN, bind is 0.0.0.0 on configured port5555; enter the server LAN IPv4 address in each client. MySQL remains server-side. Confirm connectivity and firewall rules using two clients before starting the four-client demonstration. A physical two-machine LAN rehearsal is recorded separately from loopback system tests.

For DB faults, live gameplay continues. Result saves remain PENDING, then FAILED with retryable status after repeated failure. The persistence queue reserves at most100 active/pending slots. New matches stop while DB health is unavailable or capacity is exhausted. Restore DB connectivity and wait for SAVED before expecting ranking/history to update. Never infer commit from the winner screen alone. See [persistence runbook](persistence-runbook.md).

Use Ctrl+C for normal shutdown. The server stops accepting connections/challenges, aborts active games, waits up to10s for saves, closes sockets and executors, and reports any summaries left in RAM. A hard kill can lose uncommitted results. Reconnecting creates a fresh session; it cannot restore the old match. Do not delete or recreate runtime tables to troubleshoot a test.

Integration tests require `QUIZ_TEST_DB_URL`, `QUIZ_TEST_DB_USER`, `QUIZ_TEST_DB_PASSWORD`, a separate schema ending `_test`, and `mvn -Pmysql-it verify`. Missing or unsafe configuration is a failure. Local development verification used an isolated MySQL8.4.7 process on port13306; its files were placed under `/tmp/quiz-arena-mysql-test`, separate from existing databases.

# Database setup

Requires MySQL 8.4, JDK 21 and Maven. The server alone connects to MySQL.
Create separate schemas and users for runtime and integration tests. Grant each account only its own schema. Never point test configuration at runtime data.

```sql
CREATE DATABASE quiz_arena CHARACTER SET utf8mb4;
CREATE DATABASE quiz_arena_test CHARACTER SET utf8mb4;
CREATE USER 'quiz'@'localhost' IDENTIFIED BY 'choose-a-local-password';
CREATE USER 'quiz_test'@'localhost' IDENTIFIED BY 'choose-a-test-password';
GRANT SELECT, INSERT, UPDATE, DELETE ON quiz_arena.* TO 'quiz'@'localhost';
GRANT ALL ON quiz_arena_test.* TO 'quiz_test'@'localhost';
```

As an operator, apply `database/001_schema.sql` followed by `database/002_seed.sql` and `database/003_community_quizzes.sql` to each selected schema. The fixed-ID seed is repeatable and does not duplicate entries. It contains three categories, three quizzes and 60 questions (8/4/4/4 per quiz). Authenticated clients can author community drafts through server commands; clients cannot mutate ranking counters.

Configure `QUIZ_DB_URL=jdbc:mysql://127.0.0.1:3306/quiz_arena`, `QUIZ_DB_USER`, `QUIZ_DB_PASSWORD` and optional `QUIZ_PORT=5555`. Alternatively, local `config/server.local.properties` supports `db.url`, `db.username` (or `db.user`), `db.password`, `server.port`; environment wins. Keep this file outside version control. JDBC uses UTC and 5-second connect/socket timeouts.

For integration tests set **separate** `QUIZ_TEST_DB_URL`, `QUIZ_TEST_DB_USER`, `QUIZ_TEST_DB_PASSWORD`; the URL must end in a dedicated `_test` schema. Missing configuration fails the integration suite explicitly.

```sh
mvn -pl server -am -Pmysql-it verify
```

The test suite inserts only into the selected test schema. It retains rows with randomized match IDs for inspection; reruns remain safe. To reset, drop/recreate only the dedicated test schema and apply schema/seed again.

After packaging, seed four demo accounts with the operator tool:

```sh
java -cp server/target/quiz-arena-server.jar vn.edu.nhom7.quiz.server.tools.DemoUserSeeder
```

Accounts `demo1` through `demo4` use password `DemoQuiz123!`. These are public demonstration credentials for the trusted LAN. Passwords are stored as independently salted PBKDF2-HMAC-SHA256 hashes with 600,000 iterations. Existing accounts are retained.

## Upgrade existing installations

Stop the server and back up MySQL and the media directory before applying `database/003_community_quizzes.sql` once with the schema operator account. Do not reapply `001` or reset runtime data. This migration retains system quizzes and historical statistics, adds drafts/publication versions/media metadata, and permits up to 50 rounds. It briefly disables foreign-key checks for existing primary-key auto-increment metadata changes, retaining every foreign-key definition, then immediately re-enables checks. Schema version becomes 3. The upgraded server refuses older schemas with a migration message.

The runtime account also needs DELETE on its own schema for deleting/reordering draft questions. Published questions and their referenced images are retained for history. Configure a writable persistent media directory using `-Dquiz.mediaDir=/absolute/path`; the default is `data/media`. Incomplete uploads expire after two minutes. Back up media files and database together; copying MySQL alone does not copy user images.

Integration tests require applying all three scripts to their dedicated test schema. Tests must not share runtime data or media directories; the end-to-end community test supplies its own temporary media directory.

# Database setup

Requires MySQL 8.4, JDK 21 and Maven. The server alone connects to MySQL.
Create separate schemas and users for runtime and integration tests. Grant each account only its own schema. Never point test configuration at runtime data.

```sql
CREATE DATABASE quiz_arena CHARACTER SET utf8mb4;
CREATE DATABASE quiz_arena_test CHARACTER SET utf8mb4;
CREATE USER 'quiz'@'localhost' IDENTIFIED BY 'choose-a-local-password';
CREATE USER 'quiz_test'@'localhost' IDENTIFIED BY 'choose-a-test-password';
GRANT SELECT, INSERT, UPDATE ON quiz_arena.* TO 'quiz'@'localhost';
GRANT ALL ON quiz_arena_test.* TO 'quiz_test'@'localhost';
```

As an operator, apply `database/001_schema.sql` followed by `database/002_seed.sql` to each selected schema. The fixed-ID seed is repeatable and does not duplicate entries. It contains three categories, three quizzes and 60 questions (8/4/4/4 per quiz). Runtime clients cannot import questions or mutate counters.

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

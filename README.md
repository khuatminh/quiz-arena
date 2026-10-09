# Quiz Arena

A JavaFX desktop quiz duel for two players over persistent TCP. The Maven reactor contains `common` (strict protocol and assets), `server` (sessions, challenges, gameplay and MySQL), and `client` (JavaFX). The existing `udp-student` exercise is separate.

Each match has ten randomly selected questions: four single choice, two multiple choice, two true/false and two short answers. The server owns all timing and scoring. Both players see the answer reveal and a leaderboard after every question, including question ten. Chat, rematches, global ranking and private match history are included.

## Build

Use JDK **21** and Maven 3.9+. On the development Mac:

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn -version
mvn clean install
```

Dependencies are pinned: JavaFX 21.0.6, Jackson 2.18.3, MySQL Connector/J 8.4.0 and JUnit 5.11.4. Client dependencies contain no JDBC driver. No Spring, ORM, WebSocket or Java object serialization is used.

## Database and server

Prepare a dedicated MySQL 8.4 schema using [database setup](docs/operations/database-setup.md). Apply `database/001_schema.sql` then `database/002_seed.sql` to the selected schema. The seed includes three quizzes and sixty questions. The server checks schema, asset version, indexes and question-bank validity before listening.

Copy `config/server.properties.example` to `config/server.local.properties`, fill in your own database connection, then:

```sh
java -jar server/target/quiz-arena-server.jar --config=config/server.local.properties
```

Environment variables `QUIZ_DB_URL`, `QUIZ_DB_USER` and `QUIZ_DB_PASSWORD` override local configuration. Keep credentials out of source control. To add the public demo accounts `demo1`–`demo4` with password `DemoQuiz123!`, use the `DemoUserSeeder` command in the database guide. Those are demonstration accounts, not private credentials.

## Client

After `mvn install`, open a terminal per client:

```sh
mvn -f client/pom.xml javafx:run
```

Connect to `localhost:5555`, register or sign in, choose a quiz and a free opponent, accept the invitation and press Ready on both clients. Single choice and true/false submit immediately; multiple choice and short answer have an explicit submit action. An answer stays locked while waiting for the server acknowledgement.

A public, offline UI replay needs no database or server:

```sh
mvn -f client/pom.xml javafx:run -Djavafx.args="--fixture --participant=101"
```

Reduce motion with `-Dquiz.reducedMotion=true` in the JavaFX JVM configuration. The registry and original bundled assets are documented in `common/src/main/resources/assets/registry.json`.

## Verification

```sh
mvn clean verify
# Dedicated schema name must end in _test; never use your runtime schema.
QUIZ_TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/quiz_arena_test' \
QUIZ_TEST_DB_USER='quiz_test' QUIZ_TEST_DB_PASSWORD='<your-test-password>' \
mvn -Pmysql-it clean verify
```

The MySQL profile fails when configuration is absent or unsafe; it does not silently skip database tests. Unit tests use controlled monotonic clocks, deterministic schedulers and real loopback sockets. System tests use actual TCP and MySQL with injectable clocks/faults. See [acceptance report](docs/testing/acceptance-report.md) for exact evidence and remaining manual checks.

## LAN and operations

The server binds `0.0.0.0:5555`; clients on another machine enter the server's LAN IPv4 address. Allow TCP port 5555 through the host firewall. Only the server accesses MySQL. Use a trusted local/LAN environment; Internet TLS deployment is outside this version's scope.

Normal shutdown stops accepting clients/challenges, aborts live matches, and waits at most ten seconds for pending saves before reporting any unsaved summaries. See [runbook](docs/operations/runbook.md), [network architecture](docs/architecture/network-explanation.md) and [demo script](docs/demo/demo-script.md).

A disconnected player cannot resume the live match by reconnecting. Pending, uncommitted summaries live in RAM and are lost on a server crash; committed history remains in MySQL. The application does not claim crash recovery or durable queuing. Cancellation before the initial countdown changes no statistics; forfeits after the countdown follow server rules; aborted matches award no ranking statistics.

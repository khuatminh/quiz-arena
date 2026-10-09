package vn.edu.nhom7.quiz.server.db;

import vn.edu.nhom7.quiz.server.quiz.JdbcQuizRepository;

/** Runs before listener binding; no partial readiness with a missing/incompatible bank. */
public final class StartupValidator {
  private final ConnectionFactory connections;

  public StartupValidator(ConnectionFactory c) {
    connections = c;
  }

  public void verify() {
    new SchemaVerifier(connections).verify();
    new JdbcQuizRepository(connections).verifyBank();
  }
}

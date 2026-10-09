package vn.edu.nhom7.quiz.server;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import vn.edu.nhom7.quiz.server.config.ServerConfig;
import vn.edu.nhom7.quiz.server.db.JdbcConnectionFactory;

public final class ServerMain {
  public static void main(String[] args) {
    Path config = Path.of("config/server.local.properties");
    for (String arg : args) if (arg.startsWith("--config=")) config = Path.of(arg.substring(9));
    ServerRuntime runtime = null;
    try {
      var settings = ServerConfig.load(System.getenv(), config);
      runtime = new ServerRuntime(new JdbcConnectionFactory(settings));
      runtime.start("0.0.0.0", settings.port());
      var running = runtime;
      Runtime.getRuntime().addShutdownHook(new Thread(running::close, "quiz-shutdown"));
      System.out.println(java.time.Instant.now() + " Quiz Arena listening on " + runtime.port());
      new CountDownLatch(1).await();
    } catch (Exception e) {
      if (runtime != null) runtime.close();
      System.err.println(
          "Server could not start or continue: "
              + (e instanceof java.io.IOException
                  ? "Cannot bind server port; check for an existing server"
                  : e.getMessage()));
      System.exit(1);
    }
  }
}

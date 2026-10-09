package vn.edu.nhom7.quiz.server.tools;

import java.util.*;
import vn.edu.nhom7.quiz.common.assets.AssetRegistry;
import vn.edu.nhom7.quiz.server.auth.*;
import vn.edu.nhom7.quiz.server.config.ServerConfig;
import vn.edu.nhom7.quiz.server.db.JdbcConnectionFactory;

/** Operator-only tool. Demo credentials are deliberately public, never used in production. */
public final class DemoUserSeeder {
  public static void main(String[] args) {
    var registry = AssetRegistry.loadDefault();
    var avatars =
        registry.assets().stream().filter(a -> a.kind().equals("avatar")).map(a -> a.id()).toList();
    var auth =
        new AuthService(
            new JdbcUserRepository(new JdbcConnectionFactory(ServerConfig.load())),
            new PasswordHasher(),
            Set.copyOf(avatars));
    for (int i = 0; i < 4; i++) {
      String user = "demo" + (i + 1);
      try {
        auth.register(user, "Người chơi " + (i + 1), "DemoQuiz123!", avatars.get(i));
        System.out.println("Created demo account " + user);
      } catch (ServiceException e) {
        if (!e.code().equals("USERNAME_TAKEN")) throw e;
        System.out.println("Demo account already exists: " + user);
      }
    }
  }
}

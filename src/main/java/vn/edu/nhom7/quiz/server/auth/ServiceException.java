package vn.edu.nhom7.quiz.server.auth;

public final class ServiceException extends RuntimeException {
  private final String code;

  public ServiceException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static ServiceException database() {
    return new ServiceException("DB_UNAVAILABLE", "Database unavailable; please retry");
  }
}

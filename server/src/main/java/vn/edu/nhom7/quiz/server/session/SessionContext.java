package vn.edu.nhom7.quiz.server.session;

import java.util.UUID;

public record SessionContext(UUID connectionId, long userId) {}

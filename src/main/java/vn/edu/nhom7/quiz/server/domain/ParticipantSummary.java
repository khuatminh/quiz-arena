package vn.edu.nhom7.quiz.server.domain;

import java.util.*;
import vn.edu.nhom7.quiz.common.protocol.*;

public record ParticipantSummary(
    long userId, String displayName, String avatarId, int totalScore, int correctCount) {}

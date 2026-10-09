package vn.edu.nhom7.quiz.server.quiz;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import vn.edu.nhom7.quiz.common.protocol.CommunityPayloads.*;
import vn.edu.nhom7.quiz.server.auth.ServiceException;
import vn.edu.nhom7.quiz.server.support.TestDatabase;

class CommunityQuizServiceIT {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void draftOwnershipAndImmutablePublication() throws Exception {
    var factory = TestDatabase.factory();
    long owner = 201, stranger = 202;
    users();
    var service = new CommunityQuizService(factory);
    long id =
        service
            .execute(owner, new AuthorRequest("CREATE", 0, json.createObjectNode()))
            .data()
            .path("quizId")
            .asLong();
    assertThrows(
        ServiceException.class,
        () -> service.execute(stranger, new AuthorRequest("GET", id, null)));
    service.execute(
        owner,
        new AuthorRequest(
            "SAVE_META",
            id,
            json.readTree(
                "{\"title\":\"Community"
                    + " test\",\"description\":\"\",\"categoryId\":1,\"shuffleQuestions\":false}")));
    assertThrows(
        ServiceException.class,
        () -> service.execute(owner, new AuthorRequest("PUBLISH", id, null)));
    var question =
        json.readTree(
            "{\"index\":0,\"question\":{\"questionType\":\"TRUE_FALSE\",\"content\":\"True?\",\"options\":[],\"answerKey\":true,\"explanation\":\"Yes\"}}");
    service.execute(owner, new AuthorRequest("SAVE_QUESTION", id, question));
    var published = service.execute(owner, new AuthorRequest("PUBLISH", id, null));
    long version = published.data().path("publicVersionId").asLong();
    assertTrue(version > 0);
    ((com.fasterxml.jackson.databind.node.ObjectNode) question.path("question"))
        .put("content", "Changed?");
    service.execute(owner, new AuthorRequest("SAVE_QUESTION", id, question));
    assertEquals(version, new JdbcQuizRepository(factory).summary(id).quizVersionId());
    assertFalse(
        service
            .execute(owner, new AuthorRequest("GET", id, null))
            .data()
            .toString()
            .contains("answerKey"));
    service.execute(owner, new AuthorRequest("UNPUBLISH", id, null));
    assertThrows(ServiceException.class, () -> new JdbcQuizRepository(factory).summary(id));
  }

  private void users() throws Exception {
    try (var c = TestDatabase.factory().open();
        var s = c.createStatement()) {
      s.executeUpdate(
          "INSERT IGNORE INTO USERS(id,username,display_name,avatar_id,password_hash,created_at)"
              + " VALUES(201,'community201','Author','avatar-1','unused',UTC_TIMESTAMP(3)),(202,'community202','Other','avatar-2','unused',UTC_TIMESTAMP(3))");
    }
  }

  @Test
  void draftsPermitIncompleteContentButPublicationRejectsIt() throws Exception {
    users();
    var service = new CommunityQuizService(TestDatabase.factory());
    long id =
        service.execute(201, new AuthorRequest("CREATE", 0, null)).data().path("quizId").asLong();
    service.execute(
        201,
        new AuthorRequest(
            "SAVE_QUESTION",
            id,
            json.readTree(
                "{\"index\":0,\"question\":{\"questionType\":\"SINGLE_CHOICE\",\"content\":\"\",\"options\":[],\"answerKey\":null}}")));
    assertEquals(
        1,
        service
            .execute(201, new AuthorRequest("GET", id, null))
            .data()
            .path("questionCount")
            .asInt());
    assertThrows(
        ServiceException.class, () -> service.execute(201, new AuthorRequest("PUBLISH", id, null)));
    assertThrows(
        ServiceException.class,
        () ->
            service.execute(
                202,
                new AuthorRequest("GET_QUESTION", id, json.createObjectNode().put("index", 0))));
    assertThrows(
        ServiceException.class, () -> service.execute(201, new AuthorRequest("GET", 1, null)));
  }

  @Test
  void allFourTypesPublishAndVersionsKeepTheirQuestions() throws Exception {
    users();
    var factory = TestDatabase.factory();
    var service = new CommunityQuizService(factory);
    long id =
        service.execute(201, new AuthorRequest("CREATE", 0, null)).data().path("quizId").asLong();
    String[] questions = {
      "{\"questionType\":\"SINGLE_CHOICE\",\"content\":\"One\",\"options\":[{\"id\":\"a\",\"text\":\"A\"},{\"id\":\"b\",\"text\":\"B\"}],\"answerKey\":\"a\"}",
      "{\"questionType\":\"MULTIPLE_CHOICE\",\"content\":\"Two\",\"options\":[{\"id\":\"a\",\"text\":\"A\"},{\"id\":\"b\",\"text\":\"B\"}],\"answerKey\":[\"a\",\"b\"]}",
      "{\"questionType\":\"TRUE_FALSE\",\"content\":\"Three\",\"options\":[],\"answerKey\":true,\"explanation\":\"\"}",
      "{\"questionType\":\"SHORT_ANSWER\",\"content\":\"Four\",\"options\":[],\"answerKey\":[\"yes\"]}"
    };
    for (int i = 0; i < 4; i++) {
      var data = json.createObjectNode().put("index", i);
      data.set("question", json.readTree(questions[i]));
      service.execute(201, new AuthorRequest("SAVE_QUESTION", id, data));
    }
    service.execute(201, new AuthorRequest("PUBLISH", id, null));
    var repo = new JdbcQuizRepository(factory);
    var old = repo.summary(id);
    var loaded = repo.loadMatchQuestions(old, new java.util.Random(1));
    assertEquals(
        java.util.List.of("One", "Two", "Three", "Four"),
        loaded.stream().map(q -> q.content()).toList());
    service.execute(
        201,
        new AuthorRequest(
            "MOVE_QUESTION", id, json.createObjectNode().put("index", 3).put("toIndex", 0)));
    assertEquals(
        "Four",
        service
            .execute(
                201, new AuthorRequest("GET_QUESTION", id, json.createObjectNode().put("index", 0)))
            .data()
            .path("question")
            .path("content")
            .asText());
    service.execute(
        201, new AuthorRequest("DELETE_QUESTION", id, json.createObjectNode().put("index", 1)));
    service.execute(201, new AuthorRequest("PUBLISH", id, null));
    var current = repo.summary(id);
    assertNotEquals(old.quizVersionId(), current.quizVersionId());
    assertThrows(
        ServiceException.class, () -> repo.loadMatchQuestions(old, new java.util.Random()));
    assertEquals(
        java.util.List.of("Four", "Two", "Three"),
        repo.loadMatchQuestions(current, new java.util.Random()).stream()
            .map(q -> q.content())
            .toList());
    try (var c = factory.open();
        var p = c.prepareStatement("SELECT content FROM QUESTIONS WHERE id=?")) {
      p.setLong(1, loaded.getFirst().questionId());
      try (var r = p.executeQuery()) {
        assertTrue(r.next());
        assertEquals("One", r.getString(1));
      }
    }
  }

  @Test
  void fiftyQuestionLimitAndForeignMediaAreEnforced() throws Exception {
    users();
    var factory = TestDatabase.factory();
    var service = new CommunityQuizService(factory);
    long id =
        service.execute(201, new AuthorRequest("CREATE", 0, null)).data().path("quizId").asLong();
    try (var c = factory.open();
        var p =
            c.prepareStatement(
                "INSERT IGNORE INTO MEDIA_ASSETS"
                    + " VALUES('media-foreign',202,'image/png',12,REPEAT('a',64),UTC_TIMESTAMP(3))")) {
      p.executeUpdate();
    }
    var question =
        json.readTree(
            "{\"questionType\":\"TRUE_FALSE\",\"content\":\"Question\",\"options\":[],\"answerKey\":true,\"questionAssetId\":\"media-foreign\"}");
    var data = json.createObjectNode().put("index", 0);
    data.set("question", question);
    assertThrows(
        ServiceException.class,
        () -> service.execute(201, new AuthorRequest("SAVE_QUESTION", id, data)));
    ((com.fasterxml.jackson.databind.node.ObjectNode) question).remove("questionAssetId");
    for (int i = 0; i < 50; i++) {
      data.put("index", i);
      service.execute(201, new AuthorRequest("SAVE_QUESTION", id, data));
    }
    data.put("index", 50);
    assertThrows(
        ServiceException.class,
        () -> service.execute(201, new AuthorRequest("SAVE_QUESTION", id, data)));
    service.execute(201, new AuthorRequest("PUBLISH", id, null));
    var repo = new JdbcQuizRepository(factory);
    var summary = repo.summary(id);
    assertEquals(50, summary.totalRounds());
    assertEquals(50, repo.loadMatchQuestions(summary, new java.util.Random()).size());
    service.execute(
        201,
        new AuthorRequest("SAVE_META", id, json.createObjectNode().put("shuffleQuestions", true)));
    service.execute(201, new AuthorRequest("PUBLISH", id, null));
    var shuffled = repo.summary(id);
    assertEquals(
        repo.loadMatchQuestions(shuffled, new java.util.Random(42)),
        repo.loadMatchQuestions(shuffled, new java.util.Random(42)));
    assertNotEquals(
        repo.loadMatchQuestions(shuffled, new java.util.Random(42)).stream()
            .map(q -> q.questionId())
            .toList(),
        repo.loadMatchQuestions(shuffled, new java.util.Random(43)).stream()
            .map(q -> q.questionId())
            .toList());
  }

  @Test
  void authoringResponsesAndAcceptedAnswerListsStayBounded() throws Exception {
    users();
    var service = new CommunityQuizService(TestDatabase.factory());
    long id =
        service.execute(201, new AuthorRequest("CREATE", 0, null)).data().path("quizId").asLong();
    var question =
        json.createObjectNode()
            .put("questionType", "TRUE_FALSE")
            .put("content", "x".repeat(120))
            .put("answerKey", true);
    question.putArray("options");
    var data = json.createObjectNode().put("index", 0);
    data.set("question", question);
    var saved = service.execute(201, new AuthorRequest("SAVE_QUESTION", id, data));
    assertEquals(60, saved.data().path("questions").get(0).path("content").asText().length());
    assertEquals(
        20,
        service
            .execute(201, new AuthorRequest("LIST", 0, json.createObjectNode().put("pageSize", 50)))
            .data()
            .path("pageSize")
            .asInt());
    question.put("questionType", "SHORT_ANSWER");
    var answers = question.putArray("answerKey");
    for (int i = 0; i < 21; i++) answers.add("answer" + i);
    assertThrows(
        ServiceException.class,
        () -> service.execute(201, new AuthorRequest("SAVE_QUESTION", id, data)));
  }

  @Test
  void publicationRejectsDraftWhoseReviewExceedsFrameBudget() throws Exception {
    users();
    var service = new CommunityQuizService(TestDatabase.factory());
    long id =
        service.execute(201, new AuthorRequest("CREATE", 0, null)).data().path("quizId").asLong();
    var question =
        json.createObjectNode()
            .put("questionType", "MULTIPLE_CHOICE")
            .put("content", "😀".repeat(500))
            .put("explanation", "😀".repeat(500));
    var options = question.putArray("options");
    var answers = question.putArray("answerKey");
    for (int i = 0; i < 6; i++) {
      String optionId = i + "😀".repeat(63);
      options.addObject().put("id", optionId).put("text", "😀".repeat(120));
      answers.add(optionId);
    }
    for (int i = 0; i < 2; i++) {
      var data = json.createObjectNode().put("index", i);
      data.set("question", question);
      service.execute(201, new AuthorRequest("SAVE_QUESTION", id, data));
    }
    var error =
        assertThrows(
            ServiceException.class,
            () -> service.execute(201, new AuthorRequest("PUBLISH", id, null)));
    assertEquals("VALIDATION_ERROR", error.code());
    assertEquals(
        0,
        service
            .execute(201, new AuthorRequest("GET", id, null))
            .data()
            .path("publicVersionId")
            .asLong());
  }

  @Test
  void savingDraftRejectsSingleQuestionWhoseReadWouldExceedFrameBudget() throws Exception {
    users();
    var service = new CommunityQuizService(TestDatabase.factory());
    long id =
        service.execute(201, new AuthorRequest("CREATE", 0, null)).data().path("quizId").asLong();
    var question =
        json.createObjectNode()
            .put("questionType", "SINGLE_CHOICE")
            .put("content", "😀".repeat(500))
            .put("explanation", "😀".repeat(500));
    var options = question.putArray("options");
    for (int i = 0; i < 6; i++)
      options.addObject().put("id", i + "😀".repeat(63)).put("text", "😀".repeat(120));
    question.putObject("answerKey").put("unfinished", "😀".repeat(4000));
    var data = json.createObjectNode().put("index", 0);
    data.set("question", question);
    assertThrows(
        ServiceException.class,
        () -> service.execute(201, new AuthorRequest("SAVE_QUESTION", id, data)));
  }
}

package vn.edu.nhom7.quiz.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.nhom7.quiz.client.state.ClientState;
import vn.edu.nhom7.quiz.common.protocol.MessageType;

/** Header presentation and availability; actions remain with the application coordinator. */
final class NavigationBar extends HBox {
  record Actions(
      Runnable lobby,
      Runnable community,
      Runnable ranking,
      Runnable history,
      Runnable chat,
      Runnable logout) {}

  NavigationBar(ClientState state, String query, Button settings, Actions actions) {
    super(16);
    boolean attached = state.activeMatchId() != null;
    boolean leavingGame = attached && !state.phase().equals("RESULT");
    var brand = Ui.title("QUIZ ARENA");
    brand.getStyleClass().add("brand");
    brand.setWrapText(false);
    brand.setMinWidth(Region.USE_PREF_SIZE);
    var profile =
        Ui.label(
            state.profile().path("displayName").asText()
                + " · "
                + state.profile().path("totalScore").asLong()
                + " điểm");
    profile.setMaxWidth(160);
    profile.setWrapText(false);
    HBox.setHgrow(profile, Priority.ALWAYS);

    var lobby =
        navigation(
            leavingGame ? "Rời trận" : "Sảnh", actions.lobby(), query.isEmpty() && !attached);
    if (leavingGame) lobby.getStyleClass().add("danger");
    var community = navigation("Quiz của tôi", actions.community(), query.equals("COMMUNITY"));
    JsonNode quizzes = state.payload(MessageType.QUIZ_LIST);
    community.setDisable(
        attached
            || quizzes == null
            || !quizzes.path("categories").isArray()
            || quizzes.path("categories").isEmpty());
    var ranking = navigation("Xếp hạng", actions.ranking(), query.equals("RANKING"));
    var history = navigation("Lịch sử", actions.history(), query.equals("HISTORY"));
    ranking.setDisable(attached);
    history.setDisable(attached);
    var chat = navigation("Chat", actions.chat(), false);
    chat.setVisible(attached || state.phase().equals("RESULT_DETACHED"));
    chat.setManaged(chat.isVisible());
    var logout = navigation("Đăng xuất", actions.logout(), false);
    logout.getStyleClass().add("danger");
    settings.getStyleClass().add("nav");
    getChildren()
        .addAll(
            brand,
            profile,
            Ui.spacer(),
            settings,
            lobby,
            community,
            ranking,
            history,
            chat,
            logout);
    getStyleClass().add("app-header");
  }

  private static Button navigation(String label, Runnable action, boolean active) {
    var button = Ui.button(label, action);
    button.getStyleClass().add("nav");
    if (active) button.getStyleClass().add("active");
    return button;
  }
}

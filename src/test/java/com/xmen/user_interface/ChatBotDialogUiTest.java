package com.xmen.user_interface;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI smoke test for the chat dialog. Boots a minimal JavaFX stage containing
 * the trigger button, opens the chat panel, and verifies the popup actually
 * shows on screen and is hooked back to the anchor. Catches regressions like
 * the earlier "popup.getScene().setRoot()" silent-NPE bug.
 */
class ChatBotDialogUiTest extends ApplicationTest {

  private Button anchor;
  private Stage primaryStage;

  @Override
  public void start(Stage stage) {
    this.primaryStage = stage;
    anchor = new Button("Chat with X-Men");
    anchor.setId("chat-anchor");
    StackPane root = new StackPane(anchor);
    root.setPrefSize(800, 600);
    Scene scene = new Scene(root);
    java.net.URL css = getClass().getResource("/css/main-v2.css");
    if (css != null) scene.getStylesheets().add(css.toExternalForm());
    stage.setScene(scene);
    stage.show();
  }

  @Test
  void chat_panel_opens_when_anchor_is_clicked() throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    Platform.runLater(() -> {
      ChatBotDialog.show(primaryStage, anchor);
      done.countDown();
    });
    assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    // Give the popup a tick to actually show.
    Thread.sleep(150);

    Object stored = anchor.getProperties().get("chatPopup");
    assertThat(stored)
        .as("a Popup must be stored against the anchor after show()")
        .isInstanceOf(Popup.class);
    Popup popup = (Popup) stored;
    assertThat(popup.isShowing()).as("popup must be visible").isTrue();
    assertThat(popup.getContent()).as("popup must have content").isNotEmpty();
  }

  @Test
  void second_click_toggles_chat_panel_off() throws Exception {
    Platform.runLater(() -> ChatBotDialog.show(primaryStage, anchor));
    Thread.sleep(200);
    Popup first = (Popup) anchor.getProperties().get("chatPopup");
    assertThat(first).isNotNull();
    assertThat(first.isShowing()).isTrue();

    Platform.runLater(() -> ChatBotDialog.show(primaryStage, anchor));
    Thread.sleep(200);
    assertThat(first.isShowing()).as("re-clicking anchor should hide popup").isFalse();
  }
}

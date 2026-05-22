package com.sermas.x.men.user_interface;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Small theme-aware toast popup used in place of the default JavaFX {@link
 * javafx.scene.control.Alert}.
 *
 * <p>Auto-dismisses after 2.4 s but also closes on click. Inherits the parent stage's inline
 * theme variables so the toast always matches the active palette — including text colour
 * (white for dark themes, dark for light ones, via each theme's {@code -text} variable).
 */
public final class ThemedToast {

  private ThemedToast() {}

  /** Show a transient toast centered over {@code owner}. */
  public static void show(Stage owner, String message) {
    Platform.runLater(() -> showInternal(owner, message));
  }

  private static void showInternal(Stage owner, String message) {
    Stage stage = new Stage();
    stage.initOwner(owner);
    stage.initModality(Modality.NONE);
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setAlwaysOnTop(true);

    Label text = new Label(message);
    text.getStyleClass().add("x-toast-message");

    VBox card = new VBox(text);
    card.getStyleClass().add("x-toast");
    card.setAlignment(Pos.CENTER);

    // Surround the shadow-bearing card with transparent padding so the drop-shadow
    // never clips at the parent's content bounds.
    StackPane cardWrap = new StackPane(card);
    cardWrap.getStyleClass().add("x-shadow-room");

    StackPane root = new StackPane(cardWrap);
    root.getStyleClass().add("x-root");
    if (owner != null && owner.getScene() != null && owner.getScene().getRoot() != null) {
      root.setStyle(owner.getScene().getRoot().getStyle());
    }

    Scene scene = new Scene(root);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets()
        .add(ThemedToast.class.getResource("/css/main-v2.css").toExternalForm());

    stage.setScene(scene);

    // Position centred-bottom over the owner stage.
    if (owner != null) {
      stage.setOnShown(
          e -> {
            stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2.0);
            stage.setY(owner.getY() + owner.getHeight() - stage.getHeight() - 60.0);
          });
    }

    card.setOnMouseClicked(e -> stage.close());
    stage.show();

    PauseTransition timeout = new PauseTransition(Duration.millis(2400));
    timeout.setOnFinished(e -> stage.close());
    timeout.play();
  }
}

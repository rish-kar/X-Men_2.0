package com.xmen.user_interface;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Theme-aware modal popup used in place of the default JavaFX {@link
 * javafx.scene.control.Alert}.
 *
 * <p>Renders on a transparent stage so there's no white OS chrome. Always positioned on the
 * monitor that hosts the owner window. Auto-dismisses after 5 s by default, but the user can
 * close it manually via the OK button or the close affordance.
 */
public final class ThemedDialog {

  private ThemedDialog() {}

  /** Decoded once and shared — these PNGs were re-decoded for every dialog open. */
  private static volatile javafx.scene.image.Image cachedSuccessIcon;
  private static volatile javafx.scene.image.Image cachedErrorIcon;

  public enum Kind {
    SUCCESS,
    ERROR,
    INFO,
    CONFIRM
  }

  /** Show a themed dialog. Auto-closes in 5 s if not closed first. */
  public static void show(Window owner, Kind kind, String title, String body) {
    Platform.runLater(() -> showInternal(owner, kind, title, body, null, null, 5000));
  }

  /** Show a themed dialog with no auto-close. */
  public static void showPersistent(Window owner, Kind kind, String title, String body) {
    Platform.runLater(() -> showInternal(owner, kind, title, body, null, null, 0));
  }

  /**
   * Confirmation popup with Yes/No. {@code onYes} fires on confirm. Always persistent — user
   * must choose. {@code onNo} is optional.
   */
  public static void confirm(
      Window owner, String title, String body, Runnable onYes, Runnable onNo) {
    Platform.runLater(() -> showInternal(owner, Kind.CONFIRM, title, body, onYes, onNo, 0));
  }

  private static void showInternal(
      Window owner,
      Kind kind,
      String title,
      String body,
      Runnable onYes,
      Runnable onNo,
      long autoCloseMillis) {
    Stage stage = new Stage();
    if (owner != null) stage.initOwner(owner);
    stage.initModality(kind == Kind.CONFIRM ? Modality.APPLICATION_MODAL : Modality.NONE);
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setAlwaysOnTop(true);

    Label titleLabel = new Label(title == null ? "" : title);
    titleLabel.getStyleClass().add("x-dialog-title");
    titleLabel.setWrapText(true);
    titleLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
    titleLabel.setMaxWidth(440);
    titleLabel.setMinHeight(Region.USE_PREF_SIZE);

    Label bodyLabel = new Label(body == null ? "" : body);
    bodyLabel.getStyleClass().add("x-dialog-body");
    bodyLabel.setWrapText(true);
    bodyLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
    bodyLabel.setMaxWidth(440);
    // Force the label to grow to whatever height its wrapped text needs,
    // so long messages don't get crushed into a single elided line.
    bodyLabel.setMinHeight(Region.USE_PREF_SIZE);

    ImageView icon = loadIcon(kind);

    VBox textCol = new VBox(8, titleLabel, bodyLabel);
    textCol.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(textCol, Priority.ALWAYS);

    HBox headRow = new HBox(16);
    headRow.setAlignment(Pos.CENTER_LEFT);
    if (icon != null) headRow.getChildren().add(icon);
    headRow.getChildren().add(textCol);

    HBox buttons;
    if (kind == Kind.CONFIRM) {
      Button btnYes = new Button("Yes");
      btnYes.getStyleClass().add("x-cta-primary");
      Button btnNo = new Button("No");
      btnNo.getStyleClass().add("x-cta-secondary");
      btnYes.setOnAction(
          e -> {
            stage.close();
            if (onYes != null) onYes.run();
          });
      btnNo.setOnAction(
          e -> {
            stage.close();
            if (onNo != null) onNo.run();
          });
      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      buttons = new HBox(10, spacer, wrapShadow(btnNo), wrapShadow(btnYes));
      Animations.hoverLift(btnYes, 1.04);
      Animations.hoverLift(btnNo, 1.03);
    } else {
      Button btnOk = new Button("OK");
      btnOk.getStyleClass().add("x-cta-primary");
      btnOk.setOnAction(e -> stage.close());
      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      buttons = new HBox(10, spacer, wrapShadow(btnOk));
      Animations.hoverLift(btnOk, 1.04);
    }
    buttons.setAlignment(Pos.CENTER_RIGHT);

    VBox card = new VBox(18, headRow, buttons);
    card.getStyleClass().addAll("x-dialog-card", "x-dialog-" + kind.name().toLowerCase());
    card.setPadding(new Insets(22, 24, 18, 24));
    card.setMaxWidth(560);

    StackPane wrap = new StackPane(card);
    wrap.getStyleClass().add("x-shadow-room");

    StackPane root = new StackPane(wrap);
    root.getStyleClass().add("x-root");
    root.setStyle(ThemedToast.transparentPopupStyleFrom(owner));

    Scene scene = new Scene(root);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets()
        .add(ThemedDialog.class.getResource("/css/main-v2.css").toExternalForm());

    stage.setScene(scene);

    stage.setOnShown(
        e -> {
          Rectangle2D screen = ThemedToast.screenFor(owner);
          double w = stage.getWidth();
          double h = stage.getHeight();
          double x;
          double y;
          if (owner != null) {
            x = owner.getX() + (owner.getWidth() - w) / 2.0;
            y = owner.getY() + (owner.getHeight() - h) / 2.0;
          } else {
            x = screen.getMinX() + (screen.getWidth() - w) / 2.0;
            y = screen.getMinY() + (screen.getHeight() - h) / 2.0;
          }
          if (x < screen.getMinX() + 8) x = screen.getMinX() + 8;
          if (x + w > screen.getMaxX() - 8) x = screen.getMaxX() - w - 8;
          if (y < screen.getMinY() + 8) y = screen.getMinY() + 8;
          if (y + h > screen.getMaxY() - 8) y = screen.getMaxY() - h - 8;
          stage.setX(x);
          stage.setY(y);
        });

    stage.show();

    if (autoCloseMillis > 0 && kind != Kind.CONFIRM) {
      PauseTransition timeout = new PauseTransition(Duration.millis(autoCloseMillis));
      timeout.setOnFinished(e -> {
        if (stage.isShowing()) stage.close();
      });
      timeout.play();
    }
  }

  private static StackPane wrapShadow(Button b) {
    StackPane p = new StackPane(b);
    p.getStyleClass().add("x-shadow-room");
    return p;
  }

  private static ImageView loadIcon(Kind kind) {
    Image image = cachedIcon(kind);
    if (image == null) return null;
    ImageView iv = new ImageView(image);
    iv.setFitWidth(72);
    iv.setFitHeight(72);
    iv.setPreserveRatio(true);
    iv.setSmooth(true);
    return iv;
  }

  private static Image cachedIcon(Kind kind) {
    switch (kind) {
      case SUCCESS:
        if (cachedSuccessIcon == null) cachedSuccessIcon = loadIconResource("/images/dna_logo.png");
        return cachedSuccessIcon;
      case ERROR:
        if (cachedErrorIcon == null) cachedErrorIcon = loadIconResource("/images/error_mutation.png");
        return cachedErrorIcon;
      default:
        return null;
    }
  }

  private static synchronized Image loadIconResource(String resource) {
    try (var stream = ThemedDialog.class.getResourceAsStream(resource)) {
      return stream == null ? null : new Image(stream);
    } catch (Exception ignored) {
      return null;
    }
  }
}

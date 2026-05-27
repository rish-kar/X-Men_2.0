package com.xmen.user_interface;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.*;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Small theme-aware toast popup used in place of the default JavaFX {@link
 * javafx.scene.control.Alert}.
 *
 * <p>Auto-dismisses after 5 s by default but also closes on click. Inherits the parent stage's
 * inline theme variables so the toast always matches the active palette. Always positioned on
 * the same monitor as the owner window — multi-monitor safe.
 */
public final class ThemedToast {

  private ThemedToast() {}

  private static final Queue<ToastRequest> QUEUE = new ArrayDeque<>();
  private static boolean showing;
  private static final double BOTTOM_EDGE_GAP = 2.0;

  /** Show a transient toast centered-bottom over {@code owner}. Auto-closes in 5 s. */
  public static void show(Window owner, String message) {
    Platform.runLater(() -> enqueue(resolveOwner(owner), message, 5000));
  }

  /** Backwards-compat: Stage-typed overload. */
  public static void show(Stage owner, String message) {
    show((Window) owner, message);
  }

  /** Show a transient toast with a custom dismiss delay (millis). */
  public static void show(Window owner, String message, long autoCloseMillis) {
    Platform.runLater(() -> enqueue(resolveOwner(owner), message, autoCloseMillis));
  }

  private static void enqueue(Window owner, String message, long autoCloseMillis) {
    QUEUE.offer(new ToastRequest(owner, message, autoCloseMillis));
    if (!showing) showNext();
  }

  private static void showNext() {
    ToastRequest next = QUEUE.poll();
    if (next == null) {
      showing = false;
      return;
    }
    showing = true;
    showInternal(next.owner(), next.message(), next.autoCloseMillis());
  }

  private static Window resolveOwner(Window owner) {
    if (owner != null) return owner;
    for (Window w : Window.getWindows()) {
      if (w.isShowing() && w instanceof Stage s && s.isFocused()) return w;
    }
    for (Window w : Window.getWindows()) {
      if (w.isShowing()) return w;
    }
    return null;
  }

  private static void showInternal(Window owner, String message, long autoCloseMillis) {
    Stage stage = new Stage();
    if (owner != null) stage.initOwner(owner);
    stage.initModality(Modality.NONE);
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setAlwaysOnTop(true);

    Label text = new Label(message);
    text.getStyleClass().add("x-toast-message");
    text.setWrapText(true);
    text.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
    text.setMaxWidth(440);
    // Let the label grow vertically as the message wraps — otherwise long
    // toasts get truncated to "...".
    text.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

    VBox card = new VBox(text);
    card.getStyleClass().add("x-toast");
    card.setAlignment(Pos.CENTER);

    StackPane cardWrap = new StackPane(card);
    cardWrap.getStyleClass().add("x-toast-shadow-room");

    StackPane root = new StackPane(cardWrap);
    root.getStyleClass().add("x-root");
    root.setStyle(transparentPopupStyleFrom(owner));

    Scene scene = new Scene(root);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets()
        .add(ThemedToast.class.getResource("/css/main-v2.css").toExternalForm());

    stage.setScene(scene);

    // Position on the monitor that hosts the owner; clamp so we stay on-screen.
    stage.setOnShown(
        e -> {
          Rectangle2D screen = screenFor(owner);
          double w = stage.getWidth();
          double h = stage.getHeight();
          double x;
          double y;
          if (owner != null) {
            x = owner.getX() + (owner.getWidth() - w) / 2.0;
            y = owner.getY() + owner.getHeight() - h - BOTTOM_EDGE_GAP;
          } else {
            x = screen.getMinX() + (screen.getWidth() - w) / 2.0;
            y = screen.getMaxY() - h - BOTTOM_EDGE_GAP;
          }
          // Clamp to the same monitor's visual bounds.
          if (x < screen.getMinX() + BOTTOM_EDGE_GAP) x = screen.getMinX() + BOTTOM_EDGE_GAP;
          if (x + w > screen.getMaxX() - BOTTOM_EDGE_GAP) {
            x = screen.getMaxX() - w - BOTTOM_EDGE_GAP;
          }
          if (y < screen.getMinY() + BOTTOM_EDGE_GAP) y = screen.getMinY() + BOTTOM_EDGE_GAP;
          if (y + h > screen.getMaxY() - BOTTOM_EDGE_GAP) {
            y = screen.getMaxY() - h - BOTTOM_EDGE_GAP;
          }
          stage.setX(x);
          stage.setY(y);
        });

    card.setOnMouseClicked(e -> stage.close());
    stage.setOnHidden(e -> showNext());
    stage.show();

    long delay = autoCloseMillis <= 0 ? 5000 : autoCloseMillis;
    PauseTransition timeout = new PauseTransition(Duration.millis(delay));
    timeout.setOnFinished(e -> stage.close());
    timeout.play();
  }

  private record ToastRequest(Window owner, String message, long autoCloseMillis) {}

  /** Pick the screen that contains the owner window's centre. Falls back to primary. */
  static Rectangle2D screenFor(Window owner) {
    if (owner != null) {
      double cx = owner.getX() + owner.getWidth() / 2.0;
      double cy = owner.getY() + owner.getHeight() / 2.0;
      for (Screen s : Screen.getScreens()) {
        Rectangle2D b = s.getVisualBounds();
        if (b.contains(cx, cy)) return b;
      }
    }
    Screen primary = Screen.getPrimary();
    return primary != null ? primary.getVisualBounds() : new Rectangle2D(0, 0, 1280, 800);
  }

  static String inlineStyleFrom(Window owner) {
    if (owner instanceof Stage s
        && s.getScene() != null
        && s.getScene().getRoot() != null
        && s.getScene().getRoot().getStyle() != null) {
      return s.getScene().getRoot().getStyle();
    }
    return "";
  }

  static String transparentPopupStyleFrom(Window owner) {
    return inlineStyleFrom(owner)
            + "; -fx-background-color: transparent;"
            + " -fx-background: transparent;";
  }
}

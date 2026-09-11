package com.xmen.user_interface;

import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.function.Supplier;

/** Refresh window bounds after a live DPI or monitor-work-area change. */
final class DisplayScaleSupport {
  private DisplayScaleSupport() {}

  static void install(Stage stage) {
    Runnable dispose = observe(stage, stage.outputScaleXProperty(), stage.outputScaleYProperty(),
        Screen.getScreens(), () -> XMenInterface.currentScreenBounds(stage));
    stage.showingProperty().addListener((obs, wasShowing, showing) -> {
      if (!showing) dispose.run();
    });
  }

  // Observable inputs let tests exercise the same event path without changing OS settings.
  static Runnable observe(Stage stage, Observable scaleX, Observable scaleY,
      Observable screens, Supplier<Rectangle2D> workArea) {
    PauseTransition settled = new PauseTransition(Duration.millis(150));
    settled.setOnFinished(event -> refresh(stage, workArea.get()));
    InvalidationListener changed = source -> {
      // Reading revalidates scale properties so subsequent DPI changes also fire.
      if (source instanceof javafx.beans.value.ObservableValue<?> value) value.getValue();
      settled.playFromStart();
    };
    scaleX.addListener(changed);
    scaleY.addListener(changed);
    screens.addListener(changed);
    return () -> {
      settled.stop();
      scaleX.removeListener(changed);
      scaleY.removeListener(changed);
      screens.removeListener(changed);
    };
  }

  private static void refresh(Stage stage, Rectangle2D area) {
    if (area == null || area.getWidth() <= 0 || area.getHeight() <= 0) return;
    stage.setMinWidth(Math.min(640, area.getWidth()));
    stage.setMinHeight(Math.min(480, area.getHeight()));
    if (stage.isMaximized()) {
      // An undecorated Windows stage can retain its pre-DPI maximized bounds.
      // Reapply maximization only when the actual work area no longer matches.
      if (Math.abs(stage.getWidth() - area.getWidth()) > 1
          || Math.abs(stage.getHeight() - area.getHeight()) > 1
          || Math.abs(stage.getX() - area.getMinX()) > 1
          || Math.abs(stage.getY() - area.getMinY()) > 1) {
        stage.setMaximized(false);
        stage.setX(area.getMinX());
        stage.setY(area.getMinY());
        stage.setWidth(area.getWidth());
        stage.setHeight(area.getHeight());
        stage.setMaximized(true);
      }
    } else {
      double width = Math.min(stage.getWidth(), area.getWidth());
      double height = Math.min(stage.getHeight(), area.getHeight());
      stage.setWidth(width);
      stage.setHeight(height);
      stage.setX(Math.max(area.getMinX(), Math.min(stage.getX(), area.getMaxX() - width)));
      stage.setY(Math.max(area.getMinY(), Math.min(stage.getY(), area.getMaxY() - height)));
    }
    if (stage.getScene() != null) stage.getScene().getRoot().requestLayout();
  }
}

package com.xmen.user_interface;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import java.util.function.Supplier;

/**
 * Refresh bounds after live DPI/work-area changes without rebuilding the scene.
 * Requires the native DPI fix shipped in JavaFX 26 (JDK-8346281).
 */
final class DisplayScaleSupport {
  private DisplayScaleSupport() {}

  static void install(Stage stage) {
    Runnable dispose = observe(stage, stage.outputScaleXProperty(), stage.outputScaleYProperty(),
        Screen.getScreens(), () -> XMenInterface.currentScreenBounds(stage),
        stage.getStyle() == StageStyle.UNDECORATED);
    stage.showingProperty().addListener((obs, wasShowing, showing) -> {
      if (!showing) dispose.run();
    });
  }

  // Observable inputs let tests exercise the same event path without changing OS settings.
  static Runnable observe(Stage stage, Observable scaleX, Observable scaleY,
      Observable screens, Supplier<Rectangle2D> workArea) {
    return observe(stage, scaleX, scaleY, screens, workArea, false);
  }

  static Runnable observe(Stage stage, Observable scaleX, Observable scaleY,
      Observable screens, Supplier<Rectangle2D> workArea, boolean fitBorderlessWindow) {
    // Screen geometry and native window resize notifications can arrive on
    // different pulses. Recheck briefly after settling; never poll indefinitely.
    Timeline settled = new Timeline(
        new KeyFrame(Duration.millis(150), event -> refresh(stage, workArea.get(), fitBorderlessWindow)),
        new KeyFrame(Duration.millis(500), event -> refresh(stage, workArea.get(), fitBorderlessWindow)),
        new KeyFrame(Duration.millis(1000), event -> refresh(stage, workArea.get(), fitBorderlessWindow)));
    InvalidationListener changed = source -> {
      // Reading revalidates scale properties so subsequent DPI changes also fire.
      if (source instanceof javafx.beans.value.ObservableValue<?> value) value.getValue();
      settled.playFromStart();
    };
    scaleX.addListener(changed);
    scaleY.addListener(changed);
    screens.addListener(changed);
    stage.iconifiedProperty().addListener(changed);
    stage.fullScreenProperty().addListener(changed);
    stage.sceneProperty().addListener(changed);
    settled.playFromStart();
    return () -> {
      settled.stop();
      scaleX.removeListener(changed);
      scaleY.removeListener(changed);
      screens.removeListener(changed);
      stage.iconifiedProperty().removeListener(changed);
      stage.fullScreenProperty().removeListener(changed);
      stage.sceneProperty().removeListener(changed);
    };
  }

  private static void refresh(Stage stage, Rectangle2D area, boolean fitBorderlessWindow) {
    if (area == null || area.getWidth() <= 0 || area.getHeight() <= 0) return;
    // Leave native fullscreen (including macOS Spaces) and minimization alone.
    // Their listeners recheck the work area when the user returns.
    if (stage.isFullScreen() || stage.isIconified()) return;
    stage.setMinWidth(Math.min(640, area.getWidth()));
    stage.setMinHeight(Math.min(480, area.getHeight()));
    // X-Men's borderless main window has no restore/resize chrome. Windows
    // can clear its native maximized flag during WM_DPICHANGED; it must still
    // fill the work area on the NEXT scale change. Decorated Mac/Linux windows
    // retain their normal user-controlled maximized/restored behavior.
    if (fitBorderlessWindow || stage.isMaximized()) {
      // Keep the native maximized state. Toggling it off/on here races with
      // the OS DPI transition and can restore the old small top-left bounds.
      if (Math.abs(stage.getWidth() - area.getWidth()) > 1
          || Math.abs(stage.getHeight() - area.getHeight()) > 1
          || Math.abs(stage.getX() - area.getMinX()) > 1
          || Math.abs(stage.getY() - area.getMinY()) > 1) {
        stage.setX(area.getMinX());
        stage.setY(area.getMinY());
        stage.setWidth(area.getWidth());
        stage.setHeight(area.getHeight());
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

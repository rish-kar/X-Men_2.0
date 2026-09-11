package com.xmen.user_interface;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import java.util.Comparator;
import java.util.function.Supplier;

/**
 * Refresh bounds after live DPI/work-area changes without rebuilding the scene.
 */
final class DisplayScaleSupport {
  static final String PEER_REFRESHING_PROPERTY = "xmen.displayScalePeerRefreshing";
  private static final boolean WINDOWS =
      System.getProperty("os.name", "").toLowerCase().contains("win");

  private DisplayScaleSupport() {}

  static void install(Stage stage) {
    ScaleObserver observer = new ScaleObserver(stage, stage.outputScaleXProperty(), stage.outputScaleYProperty(),
        Screen.getScreens(), () -> XMenInterface.currentScreenBounds(stage),
        stage.getStyle() == StageStyle.UNDECORATED);
    Runnable dispose = observer.install();
    stage.showingProperty().addListener((obs, wasShowing, showing) -> {
      if (!showing && !observer.refreshingPeer) dispose.run();
    });
  }

  // Observable inputs let tests exercise the same event path without changing OS settings.
  static Runnable observe(Stage stage, Observable scaleX, Observable scaleY,
      Observable screens, Supplier<Rectangle2D> workArea) {
    return new ScaleObserver(stage, scaleX, scaleY, screens, workArea, false).install();
  }

  static Runnable observe(Stage stage, Observable scaleX, Observable scaleY,
      Observable screens, Supplier<Rectangle2D> workArea, boolean fitBorderlessWindow) {
    return new ScaleObserver(stage, scaleX, scaleY, screens, workArea, fitBorderlessWindow).install();
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

  private static final class ScaleObserver {
    private final Stage stage;
    private final Observable scaleX;
    private final Observable scaleY;
    private final Observable screens;
    private final Supplier<Rectangle2D> workArea;
    private final boolean fitBorderlessWindow;
    private final Timeline settled;
    private boolean refreshingPeer;

    private ScaleObserver(Stage stage, Observable scaleX, Observable scaleY,
        Observable screens, Supplier<Rectangle2D> workArea, boolean fitBorderlessWindow) {
      this.stage = stage;
      this.scaleX = scaleX;
      this.scaleY = scaleY;
      this.screens = screens;
      this.workArea = workArea;
      this.fitBorderlessWindow = fitBorderlessWindow;
      this.settled = new Timeline(
          new KeyFrame(Duration.millis(150), event -> refreshAfterScaleChange()),
          new KeyFrame(Duration.millis(500), event -> refreshAfterScaleChange()),
          new KeyFrame(Duration.millis(1000), event -> refreshAfterScaleChange()));
    }

    private Runnable install() {
      InvalidationListener changed = source -> {
        // Reading revalidates scale properties so subsequent DPI changes also fire.
        if (source instanceof ObservableValue<?> value) value.getValue();
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

    private void refreshAfterScaleChange() {
      Rectangle2D area = workArea.get();
      refresh(stage, area, fitBorderlessWindow);
      refreshPeerIfWindowsOutputScaleIsStale(area);
    }

    private void refreshPeerIfWindowsOutputScaleIsStale(Rectangle2D area) {
      if (!WINDOWS || !fitBorderlessWindow || refreshingPeer || area == null
          || !stage.isShowing() || stage.isFullScreen() || stage.isIconified()) {
        return;
      }
      Screen screen = matchingScreen(area);
      if (screen == null
          || nearlyEqual(stage.getOutputScaleX(), screen.getOutputScaleX())
          && nearlyEqual(stage.getOutputScaleY(), screen.getOutputScaleY())) {
        return;
      }
      refreshingPeer = true;
      stage.getProperties().put(PEER_REFRESHING_PROPERTY, Boolean.TRUE);
      stage.hide();
      Platform.runLater(() -> {
        try {
          Rectangle2D latestArea = workArea.get();
          if (latestArea != null) {
            stage.setX(latestArea.getMinX());
            stage.setY(latestArea.getMinY());
            stage.setWidth(latestArea.getWidth());
            stage.setHeight(latestArea.getHeight());
          }
          stage.show();
          refresh(stage, workArea.get(), true);
        } finally {
          Platform.runLater(() -> {
            stage.getProperties().remove(PEER_REFRESHING_PROPERTY);
            refreshingPeer = false;
          });
        }
      });
    }

    private Screen matchingScreen(Rectangle2D area) {
      return Screen.getScreens().stream()
          .min(Comparator.comparingDouble(screen -> distance(screen.getVisualBounds(), area)))
          .orElse(null);
    }
  }

  private static boolean nearlyEqual(double left, double right) {
    return Math.abs(left - right) < 0.01;
  }

  private static double distance(Rectangle2D left, Rectangle2D right) {
    return Math.abs(left.getMinX() - right.getMinX())
        + Math.abs(left.getMinY() - right.getMinY())
        + Math.abs(left.getWidth() - right.getWidth())
        + Math.abs(left.getHeight() - right.getHeight());
  }
}

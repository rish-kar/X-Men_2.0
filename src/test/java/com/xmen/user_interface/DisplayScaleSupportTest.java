package com.xmen.user_interface;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.StackPane;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class DisplayScaleSupportTest extends ApplicationTest {
  private Stage window;
  private SimpleDoubleProperty scale;
  private ObservableList<Rectangle2D> areas;
  private Rectangle2D workArea;
  private Runnable dispose;
  private CheckBox selection;

  @Override public void start(Stage stage) {
    window = stage;
    selection = new CheckBox("Keep selected");
    selection.setSelected(true);
    window.setScene(new Scene(new StackPane(selection)));
    scale = new SimpleDoubleProperty(1);
    areas = FXCollections.observableArrayList(new Rectangle2D(0, 0, 1920, 1040));
    workArea = areas.get(0);
    areas.addListener((javafx.beans.InvalidationListener) ignored -> workArea = areas.get(0));
    window.setX(0); window.setY(0);
    window.setWidth(1920); window.setHeight(1040);
    window.setMaximized(true);
    dispose = DisplayScaleSupport.observe(window, scale, scale, areas, () -> workArea);
  }

  @BeforeEach void resetWindow() {
    interact(() -> {
      if (dispose != null) dispose.run();
      selection.setSelected(true);
      areas.set(0, new Rectangle2D(0, 0, 1920, 1040));
      workArea = areas.get(0);
      window.setFullScreen(false);
      window.setIconified(false);
      window.setX(0); window.setY(0);
      window.setWidth(1920); window.setHeight(1040);
      window.setMaximized(true);
      scale.set(1);
      dispose = DisplayScaleSupport.observe(window, scale, scale, areas, () -> workArea);
    });
  }

  @Test void liveScaleChangeRefreshesMaximizedWindowWithoutRestart() throws Exception {
    CountDownLatch resized = new CountDownLatch(1);
    AtomicBoolean restoredDuringResize = new AtomicBoolean();
    Scene originalScene = window.getScene();
    interact(() -> {
      window.maximizedProperty().addListener((obs, old, maximized) -> {
        if (!maximized) restoredDuringResize.set(true);
      });
      window.heightProperty().addListener((obs, old, value) -> {
        if (Math.abs(value.doubleValue() - 693) < 1) resized.countDown();
      });
      workArea = new Rectangle2D(0, 0, 1280, 693);
      scale.set(1.5);
    });
    assertTrue(resized.await(5, TimeUnit.SECONDS));
    interact(() -> {
      assertEquals(1280, window.getWidth(), 1);
      assertEquals(693, window.getHeight(), 1);
      assertTrue(window.isMaximized());
      assertFalse(restoredDuringResize.get(), "DPI handling must not toggle native maximization");
      assertSame(originalScene, window.getScene(), "Do not recreate the screen or lose input");
      assertTrue(selection.isSelected());
    });
    CountDownLatch restored = new CountDownLatch(1);
    interact(() -> {
      window.heightProperty().addListener((obs, old, value) -> {
        if (Math.abs(value.doubleValue() - 1040) < 1) restored.countDown();
      });
      workArea = new Rectangle2D(0, 0, 1920, 1040);
      scale.set(1);
    });
    assertTrue(restored.await(5, TimeUnit.SECONDS));
    interact(() -> {
      assertEquals(1920, window.getWidth(), 1);
      assertEquals(1040, window.getHeight(), 1);
      dispose.run();
    });
  }

  @Test void ordinaryWindowKeepsItsSizeAndIsClampedToTheNewMonitor() throws Exception {
    CountDownLatch moved = new CountDownLatch(1);
    interact(() -> {
      window.setMaximized(false);
      window.setWidth(800); window.setHeight(600);
      window.setX(1700); window.setY(900);
      window.yProperty().addListener((obs, old, value) -> {
        if (Math.abs(value.doubleValue() - 300) < 1) moved.countDown();
      });
      areas.set(0, new Rectangle2D(-1600, 0, 1600, 900));
    });
    assertTrue(moved.await(5, TimeUnit.SECONDS));
    interact(() -> {
      assertEquals(800, window.getWidth(), 1);
      assertEquals(600, window.getHeight(), 1);
      assertEquals(-800, window.getX(), 1);
      assertFalse(window.isMaximized());
      dispose.run();
    });
  }

  @Test void borderlessWindowStillExpandsAfterWindowsClearsMaximizedFlag() throws Exception {
    CountDownLatch expanded = new CountDownLatch(1);
    interact(() -> {
      dispose.run();
      window.setMaximized(false);
      window.setWidth(1280); window.setHeight(720);
      dispose = DisplayScaleSupport.observe(window, scale, scale, areas, () -> workArea, true);
      window.widthProperty().addListener((obs, old, value) -> {
        if (Math.abs(value.doubleValue() - 1920) < 1) expanded.countDown();
      });
      scale.set(1.5);
    });
    assertTrue(expanded.await(5, TimeUnit.SECONDS));
    interact(() -> {
      assertEquals(1920, window.getWidth(), 1);
      assertEquals(1040, window.getHeight(), 1);
      assertTrue(selection.isSelected());
      dispose.run();
    });
  }

  @Test void minimizedStateIsNotOverridden() throws Exception {
    CountDownLatch restored = new CountDownLatch(1);
    interact(() -> {
      window.setIconified(true);
      areas.set(0, new Rectangle2D(0, 0, 1280, 720));
    });
    // Wait beyond every bounded retry before checking that no resize occurred.
    javafx.application.Platform.runLater(() -> {
      var pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1200));
      pause.setOnFinished(e -> restored.countDown());
      pause.play();
    });
    assertTrue(restored.await(5, TimeUnit.SECONDS));
    interact(() -> {
      assertTrue(window.isIconified());
      assertEquals(1920, window.getWidth(), 1);
      dispose.run();
    });
  }

  @Test void monitorWorkAreaChangeIsObservedAndCanBeDetached() throws Exception {
    CountDownLatch resized = new CountDownLatch(1);
    interact(() -> {
      window.widthProperty().addListener((obs, old, value) -> {
        if (Math.abs(value.doubleValue() - 1600) < 1) resized.countDown();
      });
      areas.set(0, new Rectangle2D(1920, 0, 1600, 900));
    });
    assertTrue(resized.await(5, TimeUnit.SECONDS));
    interact(() -> {
      assertEquals(1920, window.getX(), 1);
      assertEquals(1600, window.getWidth(), 1);
      assertEquals(900, window.getHeight(), 1);
      dispose.run();
    });
  }
}

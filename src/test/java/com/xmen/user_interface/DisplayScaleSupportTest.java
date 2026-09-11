package com.xmen.user_interface;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.stage.Stage;
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

  @Override public void start(Stage stage) {
    window = stage;
    scale = new SimpleDoubleProperty(1);
    areas = FXCollections.observableArrayList(new Rectangle2D(0, 0, 1920, 1040));
    workArea = areas.get(0);
    areas.addListener((javafx.beans.InvalidationListener) ignored -> workArea = areas.get(0));
    window.setX(0); window.setY(0);
    window.setWidth(1920); window.setHeight(1040);
    window.setMaximized(true);
    dispose = DisplayScaleSupport.observe(window, scale, scale, areas, () -> workArea);
  }

  @Test void liveScaleChangeRefreshesMaximizedWindowWithoutRestart() throws Exception {
    CountDownLatch resized = new CountDownLatch(1);
    interact(() -> {
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

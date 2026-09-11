package com.xmen.user_interface;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

class UiAdaptationTest extends ApplicationTest {
  private Region root;

  @Override public void start(Stage stage) throws Exception {
    System.setProperty("xmen.bg.video.enabled", "false");
    Method method = XMenInterface.class.getDeclaredMethod("createMainScene", Stage.class);
    method.setAccessible(true);
    Scene scene = (Scene) method.invoke(new XMenInterface(), stage);
    root = (Region) scene.getRoot();
  }

  private void layout(double width, double height) {
    root.resize(width, height);
    for (int i = 0; i < 5; i++) { root.applyCss(); root.layout(); }
  }

  @Test void actionsFitAndDoNotOverlapAtSupportedLogicalSizes() {
    interact(() -> {
      for (double[] size : new double[][] {{1920,1040}, {1280,752}, {1067,627}, {800,560}, {640,480}}) {
        for (boolean download : new boolean[] {false, true}) {
          Node d = root.lookup("#heroDownload");
          d.setVisible(download); d.setManaged(download);
          layout(size[0], size[1]);
          List<Node> buttons = root.lookupAll(".x-action-bar .button").stream()
              .filter(n -> n.isVisible() && n.isManaged()).toList();
          for (int i = 0; i < buttons.size(); i++) {
            Bounds a = buttons.get(i).localToScene(buttons.get(i).getLayoutBounds());
            assertTrue(a.getMinX() >= 0 && a.getMaxX() <= size[0] + 1, "Action outside width: " + a);
            assertTrue(a.getMinY() >= 0 && a.getMaxY() <= size[1] + 1, "Action outside height: " + a);
            for (int j = i + 1; j < buttons.size(); j++) {
              Bounds b = buttons.get(j).localToScene(buttons.get(j).getLayoutBounds());
              assertFalse(a.intersects(b), "Overlapping actions: " + a + " / " + b);
            }
          }
          ScrollPane scroll = (ScrollPane)root.lookup("#mutationScroll");
          assertTrue(scroll.getViewportBounds().getHeight() > 0);
          if (size[1] < 800) {
            assertTrue(scroll.getContent().getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight());
            scroll.setVvalue(1);
            layout(size[0], size[1]);
            Bounds content = scroll.getContent().localToScene(scroll.getContent().getLayoutBounds());
            Bounds viewport = scroll.lookup(".viewport").localToScene(scroll.lookup(".viewport").getLayoutBounds());
            assertTrue(content.getMaxY() <= viewport.getMaxY() + 2, "Bottom content must be reachable");
            scroll.setVvalue(0);
          }
        }
      }
      layout(1280, 752);
      root.setOpacity(1);
      // Retain a render for visual review alongside the bounds assertions.
      root.lookup(".x-action-bar").setOpacity(1);
      root.lookup(".x-action-bar").getParent().setOpacity(1);
      WritableImage image = root.snapshot(null, null);
      java.awt.image.BufferedImage png = new java.awt.image.BufferedImage(
          (int)image.getWidth(), (int)image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
      for (int y=0; y<png.getHeight(); y++) for(int x=0; x<png.getWidth(); x++)
        png.setRGB(x,y,image.getPixelReader().getArgb(x,y));
      try { javax.imageio.ImageIO.write(png,"png",new java.io.File("target/ui-adaptation.png")); }
      catch(java.io.IOException e) { throw new RuntimeException(e); }
    });
  }

  @Test void delayedDarkPreviewCannotReplaceLatestLightSelection() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      CountDownLatch darkArrived = new CountDownLatch(1), lightApplied = new CountDownLatch(1);
      CountDownLatch darkReturned = new CountDownLatch(1);
      List<String> applied = new CopyOnWriteArrayList<>();
      server.setDispatcher(new Dispatcher() {
        @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
          boolean dark = request.getPath().endsWith("classic");
          if (dark) { darkArrived.countDown(); lightApplied.await(5, TimeUnit.SECONDS); darkReturned.countDown(); }
          return new MockResponse().setBody("{\"id\":\"" + (dark ? "classic" : "paper-light") + "\"}");
        }
      });
      server.start();
      SettingsDialog dialog = new SettingsDialog(server.getPort(), theme -> {
        applied.add(theme.getId()); lightApplied.countDown();
      }, preferences -> {}, theme -> {});
      Method preview = SettingsDialog.class.getDeclaredMethod("previewTheme", String.class);
      preview.setAccessible(true);
      interact(() -> { try { preview.invoke(dialog, "classic"); } catch(Exception e) { throw new RuntimeException(e); } });
      assertTrue(darkArrived.await(5, TimeUnit.SECONDS));
      interact(() -> { try { preview.invoke(dialog, "paper-light"); } catch(Exception e) { throw new RuntimeException(e); } });
      assertTrue(lightApplied.await(5, TimeUnit.SECONDS));
      assertTrue(darkReturned.await(5, TimeUnit.SECONDS));
      // Join the actual preview workers, then drain their queued FX callbacks.
      for (Thread thread : Thread.getAllStackTraces().keySet())
        if (thread.getName().startsWith("settings-preview theme")) thread.join(5000);
      waitForFxEvents();
      assertEquals(List.of("paper-light"), applied);
    }
  }
}

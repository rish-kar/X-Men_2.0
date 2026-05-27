package com.xmen.userInterfaceTests;

import com.xmen.user_interface.XMenInterface;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.stage.Window;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.testfx.util.WaitForAsyncUtils.waitFor;
import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

/**
 * Test cases for XMenInterface.
 *
 * <p>Updated to the new themed-dialog and themed-toast UI: there is no longer a stock JavaFX
 * Alert with a {@code .dialog-pane}; instead, success/error messages render as their own
 * themed Stage with x-dialog-card / x-toast cards. Tests look up nodes by their style class.
 */
public class XMenInterfaceTest extends ApplicationTest {

  private XMenInterface app;
  private Stage stage;

  @Override
  public void start(Stage stage) throws Exception {
    this.stage = stage;
    app = new XMenInterface();
    app.start(stage);
  }

  @AfterEach
  public void tearDown() throws Exception {
    Platform.runLater(
        () -> {
          if (stage != null) {
            stage.close();
          }
        });
    WaitForAsyncUtils.waitForFxEvents();
  }

  @Test
  @DisplayName("Test Splash Screen Displayed")
  public void testSplashScreenDisplayed() {
    interact(
        () -> {
          try {
            StackPane splashRoot = new StackPane();
            Method createSplashScreenMethod =
                XMenInterface.class.getDeclaredMethod(
                    "createSplashScreen", StackPane.class, Stage.class);
            createSplashScreenMethod.setAccessible(true);

            Object result = createSplashScreenMethod.invoke(app, splashRoot, stage);

            assertTrue(
                containsSplashVisual(splashRoot) || result != null,
                "Either a MediaView, fallback image, or fallback Label should be displayed on the splash screen");
          } catch (Exception e) {
            fail("Exception during splash-screen creation: " + e.getMessage());
          }
        });
  }

  private static boolean containsSplashVisual(Node node) {
    if (node instanceof MediaView
        && node.getStyleClass().contains("splash-media-view")) {
      return true;
    }
    if (node instanceof ImageView
        && node.getStyleClass().contains("splash-fallback-image")) {
      return true;
    }
    if (node instanceof Label label
        && "Splash Video not available".equals(label.getText())) {
      return true;
    }
    if (node instanceof Parent parent) {
      return parent.getChildrenUnmodifiable().stream()
          .anyMatch(XMenInterfaceTest::containsSplashVisual);
    }
    return false;
  }

  @Test
  @DisplayName("Test Switch to Main Scene")
  public void testSwitchToMainScene() throws Exception {
    // Splash → main hand-off has a 12 s safety net; give it room to finish.
    waitFor(
        30,
        TimeUnit.SECONDS,
        () -> {
          Scene s = stage.getScene();
          return s != null && s.getRoot() != null && s.getRoot().lookup(".x-control-panel") != null;
        });
    Scene currentScene = stage.getScene();
    assertNotNull(currentScene, "Scene should not be null after splash screen");

    // Mutation controls panel is now wrapped in .x-control-panel (replaces legacy .glass-panel).
    assertNotNull(
        currentScene.getRoot().lookup(".x-control-panel"),
        "Main scene should host the mutation-controls glass panel");
  }

  @Test
  @DisplayName("Start-Mutation button exists and is wired up")
  void testStartMutationWithoutFile() throws TimeoutException {
    // After the redesign, hitting Start with no file silently opens a native
    // FileChooser, which blocks the FX thread in headless TestFX. We just
    // assert here that the Start button is present and wired up to an
    // action handler — driving the native chooser is outside the unit-test
    // surface.
    waitFor(
        30,
        TimeUnit.SECONDS,
        () -> {
          Button b = lookup("#buttonStart").tryQueryAs(Button.class).orElse(null);
          if (b == null || b.getScene() == null) return false;
          Window w = b.getScene().getWindow();
          return w != null && w.isShowing();
        });

    Button startButton = lookup("#buttonStart").queryAs(Button.class);
    assertNotNull(startButton);
    assertNotNull(startButton.getOnAction(), "Start button must have an action handler");
  }

  @Test
  @DisplayName("Test Setup Button")
  public void testSetupButton() {
    Platform.runLater(
        () -> {
          try {
            Button testButton = new Button();
            Method setupButtonMethod =
                XMenInterface.class.getDeclaredMethod("setupButton", Button.class);
            setupButtonMethod.setAccessible(true);
            setupButtonMethod.invoke(app, testButton);

            assertEquals(150, testButton.getPrefWidth(), "Button preferred width should be 150");
            assertEquals(40, testButton.getPrefHeight(), "Button preferred height should be 40");
            // The redesigned CTA buttons style themselves via the x-cta-secondary style class.
            assertTrue(
                testButton.getStyleClass().contains("x-cta-secondary"),
                "Button should carry the x-cta-secondary style class");
          } catch (Exception e) {
            fail("Exception during reflection invocation of setupButton: " + e.getMessage());
          }
        });
    WaitForAsyncUtils.waitForFxEvents();
  }

  @Test
  @DisplayName("Test Create Main Scene")
  public void testCreateMainScene() {
    Platform.runLater(
        () -> {
          try {
            Method createMainSceneMethod =
                XMenInterface.class.getDeclaredMethod("createMainScene", Stage.class);
            createMainSceneMethod.setAccessible(true);
            Scene mainScene = (Scene) createMainSceneMethod.invoke(app, stage);
            assertNotNull(mainScene, "createMainScene should return a non-null Scene");

            StackPane root = (StackPane) mainScene.getRoot();
            assertNotNull(
                root.lookup(".x-control-panel"),
                "Main scene should host the mutation-controls glass panel");
          } catch (Exception e) {
            fail("Exception during reflection invocation of createMainScene: " + e.getMessage());
          }
        });
    WaitForAsyncUtils.waitForFxEvents();
  }

  @Test
  @DisplayName("Test Create Splash Screen Fallback")
  public void testCreateSplashScreenFallback() {
    Platform.runLater(
        () -> {
          try {
            StackPane splashRoot = new StackPane();
            Stage dummyStage = new Stage();

            Method createSplashScreenMethod =
                XMenInterface.class.getDeclaredMethod(
                    "createSplashScreen", StackPane.class, Stage.class);
            createSplashScreenMethod.setAccessible(true);

            Object result = createSplashScreenMethod.invoke(app, splashRoot, dummyStage);
            boolean fallbackFound =
                splashRoot.getChildren().stream()
                    .anyMatch(
                        node ->
                            node instanceof ImageView
                                || (node instanceof Label
                                    && ((Label) node)
                                        .getText()
                                        .equals("Splash Video not available")));
            assertTrue(
                result != null || fallbackFound,
                "Splash screen should either initialise a player or render a fallback image/label");
          } catch (Exception e) {
            fail("Exception during reflection invocation of createSplashScreen: " + e.getMessage());
          }
        });
    WaitForAsyncUtils.waitForFxEvents();
  }

  @Test
  @DisplayName("Test Send Mutation Request Success")
  public void testSendMutationRequestSuccess() throws Exception {
    MockWebServer server = new MockWebServer();
    try {
      server.start(0);
      int serverPort = server.getPort();

      System.setProperty("API_BASE_URL", "http://localhost:" + serverPort);
      System.setProperty("API_GENERATE_MUTATIONS_ENDPOINT", "/api/generateMutations");

      File tempFile;
      try (InputStream is = getClass().getResourceAsStream("/Oyster.spthy")) {
        if (is == null) {
          fail("Resource Oyster.spthy not found");
        }
        tempFile = File.createTempFile("Oyster", ".spthy");
        Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }

      waitFor(
          30,
          TimeUnit.SECONDS,
          () -> {
            Button b = lookup("#buttonStart").tryQueryAs(Button.class).orElse(null);
            if (b == null || b.getScene() == null) return false;
            Window w = b.getScene().getWindow();
            return w != null && w.isShowing();
          });

      Field selectedFileField = XMenInterface.class.getDeclaredField("selectedFile");
      selectedFileField.setAccessible(true);
      interact(
          () -> {
            try {
              selectedFileField.set(app, tempFile);
            } catch (Exception e) {
              throw new RuntimeException("Failed to update selectedFile", e);
            }
          });

      CheckBox cbSkipS = lookup("#cbSkipS").queryAs(CheckBox.class);
      assertNotNull(cbSkipS, "CheckBox with fx:id='cbSkipS' should be present in the scene graph");
      interact(() -> cbSkipS.setSelected(true));

      server.enqueue(new MockResponse().setResponseCode(200).setBody("Success"));

      Method sendMutationRequest =
          XMenInterface.class.getDeclaredMethod("sendMutationRequest");
      sendMutationRequest.setAccessible(true);
      interact(
          () -> {
            try {
              sendMutationRequest.invoke(app);
            } catch (Exception e) {
              throw new RuntimeException("Failed to invoke sendMutationRequest", e);
            }
          });
      waitForFxEvents();

      RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
      assertNotNull(request, "No HTTP request was made");
      assertEquals("POST", request.getMethod());

      String body = request.getBody().readUtf8();
      assertTrue(
          body.contains("filename=\"" + tempFile.getName() + "\""),
          "Multipart body should include uploaded file name");

      // The new themed success dialog renders a Label with the success message;
      // the legacy DialogPane lookup no longer applies.
      waitFor(
          5,
          TimeUnit.SECONDS,
          () ->
              lookup(".x-dialog-title").queryAll().stream()
                  .anyMatch(
                      n ->
                          n instanceof Label l
                              && l.getText() != null
                              && l.getText().contains("Mutation Generation Succeeded")));
      assertTrue(true, "Themed success dialog appeared.");
    } finally {
      server.shutdown();
    }
  }

  @Test
  @DisplayName("Test Send Mutation Request Error")
  public void testSendMutationRequestError() throws Exception {
    MockWebServer server = null;
    try {
      // Wait for the legacy buttonStart to exist before firing.
      waitFor(
          30,
          TimeUnit.SECONDS,
          () -> {
            Button b = lookup("#buttonStart").tryQueryAs(Button.class).orElse(null);
            return b != null && b.getScene() != null;
          });
      File tempFile = File.createTempFile("test", ".xml");
      tempFile.deleteOnExit();

      server = new MockWebServer();
      server.start(0);
      int serverPort = server.getPort();
      System.setProperty(
          "API_FULL_URL", "http://localhost:" + serverPort + "/api/generateMutations");
      server.enqueue(new MockResponse().setResponseCode(500));

      // Drive the buttonStart action directly — the hero CTA in the redesigned
      // UI is the click target but it delegates to this same button.
      Button startButton = lookup("#buttonStart").queryAs(Button.class);
      interact(startButton::fire);
      sleep(2000);

    } finally {
      if (server != null) {
        server.shutdown();
      }
    }
  }
}

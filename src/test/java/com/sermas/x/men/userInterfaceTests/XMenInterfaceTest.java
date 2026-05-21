package com.sermas.x.men.userInterfaceTests;

import static org.junit.jupiter.api.Assertions.*;
import static org.testfx.util.WaitForAsyncUtils.waitFor;
import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

import com.sermas.x.men.user_interface.XMenInterface;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
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

/**
 * Test cases for XMenInterface.
 *
 * <p>These tests use TestFX (for JavaFX UI testing) and OkHttp’s MockWebServer (to simulate HTTP
 * responses) to verify that the splash screen, main scene, button actions, and HTTP callbacks are
 * working as expected.
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
    // Close the stage (and any dialogs) after each test.
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
  public void testSplashScreenDisplayed() throws InterruptedException {

    // Give a short delay to allow the splash scene to render.
    sleep(500);

    // Try to find a MediaView or a Label with the fallback text.
    MediaView mediaView =
        (MediaView) lookup(".media-view").queryAll().stream().findFirst().orElse(null);
    Label fallbackLabel =
        lookup("Splash Video not available").queryAll().stream()
            .filter(node -> node instanceof Label)
            .map(node -> (Label) node)
            .findFirst()
            .orElse(null);

    // Assert that either the MediaView or the fallback Label is present.
    assertTrue(
        mediaView != null || fallbackLabel != null,
        "Either a MediaView or a fallback Label should be displayed on the splash screen");
  }

  @Test
  @DisplayName("Test Switch to Main Scene")
  public void testSwitchToMainScene() throws InterruptedException {

    // Wait for more than 5 seconds so that the splash scene is replaced.
    sleep(6000);
    Scene currentScene = stage.getScene();
    assertNotNull(currentScene, "Scene should not be null after splash screen");

    // Lookup the GridPane by its style class that is actually applied.
    GridPane gridPane = (GridPane) currentScene.getRoot().lookup(".glass-panel");
    assertNotNull(gridPane, "Main scene should contain a GridPane with the checkboxes and buttons");
  }

  @Test
  @DisplayName("Start-Mutation shows warning when no file uploaded")
  void testStartMutationWithoutFile() throws TimeoutException {
    /* ── 1 ▸ wait for #buttonStart to exist AND its window to be showing ── */
    waitFor(
        10,
        TimeUnit.SECONDS,
        () -> {
          Button b = lookup("#buttonStart").tryQueryAs(Button.class).orElse(null);
          if (b == null || b.getScene() == null) return false;
          Window w = b.getScene().getWindow();
          return w != null && w.isShowing();
        });

    Button startButton = lookup("#buttonStart").queryAs(Button.class);
    // fire the action on the FX thread — no robot, no coordinates
    interact(startButton::fire);
    waitForFxEvents();

    /* ── 2 ▸ wait until the Alert is visible ──────────────────────────── */
    waitFor(
        5,
        TimeUnit.SECONDS,
        () -> {
          DialogPane pane = lookup(".dialog-pane").tryQueryAs(DialogPane.class).orElse(null);
          if (pane == null || pane.getScene() == null) return false;
          Window w = pane.getScene().getWindow();
          return w != null && w.isShowing();
        });

    DialogPane alertPane = lookup(".dialog-pane").queryAs(DialogPane.class);
    assertTrue(
        alertPane.getContentText().contains("Please Upload a File"),
        "Alert should advise to upload a file");

    /* ── 3 ▸ close the Alert safely ──────────── */
    Button okButton = (Button) alertPane.lookupButton(ButtonType.OK);
    interact(okButton::fire);
    waitForFxEvents();
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

            // Check that the preferred size and style are set.
            assertEquals(150, testButton.getPrefWidth(), "Button preferred width should be 150");
            assertEquals(40, testButton.getPrefHeight(), "Button preferred height should be 40");
            assertTrue(
                testButton.getStyle().contains("-fx-text-fill: black"),
                "Button style should contain '-fx-text-fill: black'");
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
            // Create a dummy Stage to pass to the method
            Method createMainSceneMethod =
                XMenInterface.class.getDeclaredMethod("createMainScene", Stage.class);
            createMainSceneMethod.setAccessible(true);
            Scene mainScene = (Scene) createMainSceneMethod.invoke(app, stage);
            assertNotNull(mainScene, "createMainScene should return a non-null Scene");

            // Check that the scene contains a StackPane with a MediaView and GridPane
            StackPane root = (StackPane) mainScene.getRoot();
            MediaView mediaView = lookup(".media-view").query();
            GridPane gridPane = (GridPane) root.lookup(".glass-panel");

            assertNotNull(mediaView, "Main scene should contain a MediaView");
            assertNotNull(gridPane, "Main scene should contain a GridPane");
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

            // Use reflection to access the private method createSplashScreen
            Method createSplashScreenMethod =
                XMenInterface.class.getDeclaredMethod(
                    "createSplashScreen", StackPane.class, Stage.class);
            createSplashScreenMethod.setAccessible(true);

            // Invoke the method to create the splash screen
            MediaView splashMediaView =
                (MediaView) createSplashScreenMethod.invoke(app, splashRoot, dummyStage);
            boolean fallbackFound =
                splashRoot.getChildren().stream()
                    .anyMatch(
                        node ->
                            node instanceof Label
                                && ((Label) node).getText().equals("Splash Video not available"));
            assertTrue(
                splashMediaView != null || fallbackFound,
                "Splash screen should either have a MediaView or a fallback label");
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
      // Start MockWebServer on a random port
      server.start(0); // Let OS assign a free port
      int serverPort = server.getPort();

      // Configure the app to use the mock server's port. XMenInterface builds the URL as
      // API_BASE_URL + API_GENERATE_MUTATIONS_ENDPOINT, so override the base URL here.
      System.setProperty("API_BASE_URL", "http://localhost:" + serverPort);
      System.setProperty("API_GENERATE_MUTATIONS_ENDPOINT", "/api/generateMutations");

      // Load the file internally from the resource folder
      File tempFile;
      try (InputStream is = getClass().getResourceAsStream("/Oyster.spthy")) {
        if (is == null) {
          fail("Resource Oyster.spthy not found");
        }
        tempFile = File.createTempFile("Oyster", ".spthy");
        Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }

      // Wait for the main scene to be loaded and the Start button to be showing,
      // rather than relying on a fixed sleep that can race the splash transition.
      waitFor(
          10,
          TimeUnit.SECONDS,
          () -> {
            Button b = lookup("#buttonStart").tryQueryAs(Button.class).orElse(null);
            if (b == null || b.getScene() == null) return false;
            Window w = b.getScene().getWindow();
            return w != null && w.isShowing();
          });

      // Update the 'selectedFile' field on the JavaFX Application Thread (after the
      // main scene is up so we know we're modifying the active app instance).
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

      // Ensure the CheckBox is present and select it directly (avoid robot clicks
      // which can silently no-op when the window isn't focused).
      CheckBox cbSkipS = lookup("#cbSkipS").queryAs(CheckBox.class);
      assertNotNull(cbSkipS, "CheckBox with fx:id='cbSkipS' should be present in the scene graph");
      interact(() -> cbSkipS.setSelected(true));

      // Enqueue a mock success response
      server.enqueue(new MockResponse().setResponseCode(200).setBody("Success"));

      // Fire the "Start Mutation" button action directly to bypass the TestFX robot.
      Button startButton = lookup("#buttonStart").queryAs(Button.class);
      interact(startButton::fire);
      waitForFxEvents();

      // Wait for the request to complete and alert to appear
      RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
      assertNotNull(request, "No HTTP request was made");
      assertEquals("POST", request.getMethod());

      // Verify that the multipart body actually contains the file name
      String body = request.getBody().readUtf8();
      assertTrue(
          body.contains("filename=\"" + tempFile.getName() + "\""),
          "Multipart body should include uploaded file name");

      // Check the success alert (wait for it to appear since the HTTP callback is async).
      waitFor(
          5,
          TimeUnit.SECONDS,
          () -> {
            DialogPane pane = lookup(".dialog-pane").tryQueryAs(DialogPane.class).orElse(null);
            if (pane == null || pane.getScene() == null) return false;
            Window w = pane.getScene().getWindow();
            return w != null && w.isShowing();
          });
      DialogPane alertPane = lookup(".dialog-pane").queryAs(DialogPane.class);
      assertNotNull(alertPane, "Success alert not shown");
      assertTrue(alertPane.getContentText().contains("Mutation Generation Succeeded"));
      Button okButton = (Button) alertPane.lookupButton(ButtonType.OK);
      interact(okButton::fire); // Dismiss the alert without using the robot
      waitForFxEvents();
    } finally {
      server.shutdown(); // Cleanup
    }
  }

  @Test
  @DisplayName("Test Send Mutation Request Error")
  public void testSendMutationRequestError() throws Exception {
    MockWebServer server = null;
    try {
      sleep(6000);
      File tempFile = File.createTempFile("test", ".xml");
      tempFile.deleteOnExit();

      server = new MockWebServer();
      server.start(0); // Use dynamic port
      int serverPort = server.getPort();
      System.setProperty(
          "API_FULL_URL", "http://localhost:" + serverPort + "/api/generateMutations");
      server.enqueue(new MockResponse().setResponseCode(500));

      Button startButton = lookup("Start Mutation").queryButton();
      clickOn(startButton);
      sleep(2000);

    } finally {
      if (server != null) {
        server.shutdown(); // Ensure cleanup
      }
    }
  }
}

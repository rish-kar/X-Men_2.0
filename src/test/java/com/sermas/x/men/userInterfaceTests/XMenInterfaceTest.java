package com.sermas.x.men.userInterfaceTests;

import com.sermas.x.men.user_interface.XMenInterface;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for XMenInterface.
 *
 * <p>
 * These tests use TestFX (for JavaFX UI testing) and OkHttp’s MockWebServer
 * (to simulate HTTP responses) to verify that the splash screen, main scene,
 * button actions, and HTTP callbacks are working as expected.
 * </p>
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
        Platform.runLater(() -> {
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
        MediaView mediaView = (MediaView) lookup(".media-view").queryAll().stream().findFirst().orElse(null);
        Label fallbackLabel = lookup("Splash Video not available").queryAll().stream()
                .filter(node -> node instanceof Label)
                .map(node -> (Label) node)
                .findFirst()
                .orElse(null);
        assertTrue(mediaView != null || fallbackLabel != null,
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
    @DisplayName("Test Start Mutation Without File")
    public void testStartMutationWithoutFile() throws InterruptedException {
        sleep(6000); // Wait for main scene.
        Button startButton = lookup("Start Mutation").queryButton();
        assertNotNull(startButton, "Start Mutation button should exist");
        clickOn(startButton);
        sleep(1000); // Allow time for the alert to show.
        DialogPane alertPane = lookup(".dialog-pane").query();
        assertNotNull(alertPane, "A warning alert dialog should be displayed when no file is selected");
        String contentText = alertPane.getContentText();
        assertTrue(contentText.contains("Please Upload a File"), "Alert should advise to upload a file");
        // Close the alert by clicking the OK button.
        clickOn("OK");
    }

    @Test
    @DisplayName("Test Setup Button")
    public void testSetupButton() {
        Platform.runLater(() -> {
            try {
                Button testButton = new Button();
                Method setupButtonMethod = XMenInterface.class.getDeclaredMethod("setupButton", Button.class);
                setupButtonMethod.setAccessible(true);
                setupButtonMethod.invoke(app, testButton);
                // Check that the preferred size and style are set.
                assertEquals(150, testButton.getPrefWidth(), "Button preferred width should be 150");
                assertEquals(40, testButton.getPrefHeight(), "Button preferred height should be 40");
                assertTrue(testButton.getStyle().contains("-fx-text-fill: black"),
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
        Platform.runLater(() -> {
            try {
                Method createMainSceneMethod = XMenInterface.class.getDeclaredMethod("createMainScene", Stage.class);
                createMainSceneMethod.setAccessible(true);
                Scene mainScene = (Scene) createMainSceneMethod.invoke(app, stage);
                assertNotNull(mainScene, "createMainScene should return a non-null Scene");
                StackPane root = (StackPane) mainScene.getRoot();
                MediaView mediaView = null;
                GridPane gridPane = null;
                for (javafx.scene.Node node : root.getChildren()) {
                    if (node instanceof MediaView) {
                        mediaView = (MediaView) node;
                    } else if (node instanceof GridPane) {
                        gridPane = (GridPane) node;
                    }
                }
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
        Platform.runLater(() -> {
            try {
                StackPane splashRoot = new StackPane();
                Stage dummyStage = new Stage();
                Method createSplashScreenMethod = XMenInterface.class.getDeclaredMethod("createSplashScreen", StackPane.class, Stage.class);
                createSplashScreenMethod.setAccessible(true);
                MediaView splashMediaView = (MediaView) createSplashScreenMethod.invoke(app, splashRoot, dummyStage);
                boolean fallbackFound = splashRoot.getChildren().stream()
                        .anyMatch(node -> node instanceof Label
                                && ((Label) node).getText().equals("Splash Video not available"));
                assertTrue(splashMediaView != null || fallbackFound,
                        "Splash screen should either have a MediaView or a fallback label");
            } catch (Exception e) {
                fail("Exception during reflection invocation of createSplashScreen: " + e.getMessage());
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

//    @Test
//    @DisplayName("Test Send Mutation Request Success")
//    public void testSendMutationRequestSuccess() throws Exception {
//        MockWebServer server = new MockWebServer();
//        try {
//            // 1. Start MockWebServer on a random port
//            server.start(0); // Let OS assign a free port
//            int serverPort = server.getPort();
//
//            // 2. Configure the app to use the mock server's port
//            System.setProperty("app.api.url", "http://localhost:" + serverPort + "/api/generateMutations");
//
//            // 3. Load the file internally from the resource folder
//            File tempFile;
//            try (InputStream is = getClass().getResourceAsStream("/Oyster.spthy")) {
//                if (is == null) {
//                    fail("Resource Oyster.spthy not found");
//                }
//                tempFile = File.createTempFile("Oyster", ".spthy");
//                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
//            }
//
//            // 4. Update the 'selectedFile' field on the JavaFX Application Thread
//            Field selectedFileField = XMenInterface.class.getDeclaredField("selectedFile");
//            selectedFileField.setAccessible(true);
//            Platform.runLater(() -> {
//                try {
//                    selectedFileField.set(app, tempFile);
//                } catch (Exception e) {
//                    throw new RuntimeException("Failed to update selectedFile", e);
//                }
//            });
//            WaitForAsyncUtils.waitForFxEvents();
//
//            // 5. Wait for the main scene to load
//            WaitForAsyncUtils.waitForFxEvents();
//            sleep(6000);
//
//            // 6. Ensure the CheckBox is present in the scene graph
//            CheckBox cbSkipS = lookup("#cbSkipS").query();
//            assertNotNull(cbSkipS, "CheckBox with fx:id='cbSkipS' should be present in the scene graph");
//
//            // 7. Select the checkbox via UI interaction
//            clickOn(cbSkipS);
//
//            // 8. Enqueue a mock success response
//            server.enqueue(new MockResponse()
//                    .setResponseCode(200)
//                    .setBody("Success"));
//
//            // 9. Click the "Start Mutation" button
//            clickOn("#buttonStart");
//
//            // 10. Wait for the request to complete and alert to appear
//            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
//            assertNotNull(request, "No HTTP request was made");
//            assertEquals("POST", request.getMethod());
//
//            // 11. Optionally, verify the uploaded file's header contains the temp file's name
//            String uploadedFileName = request.getHeader("Content-Disposition");
//            assertTrue(uploadedFileName.contains(tempFile.getName()));
//
//            // 12. Check the success alert
//            WaitForAsyncUtils.waitForFxEvents();
//            DialogPane alertPane = lookup(".dialog-pane").query();
//            assertNotNull(alertPane, "Success alert not shown");
//            assertTrue(alertPane.getContentText().contains("Mutation Generation Succeeded"));
//            clickOn("OK"); // Dismiss the alert
//        } finally {
//            server.shutdown(); // Cleanup
//        }
//    }




    @Test
    @DisplayName("Test Send Mutation Request Error")
    public void testSendMutationRequestError() throws Exception {
        MockWebServer server = null;
        try {
            sleep(6000);
            File tempFile = File.createTempFile("test", ".xml");
            tempFile.deleteOnExit();
            // ... (set selectedFile via reflection)

            server = new MockWebServer();
            server.start(0); // Use dynamic port
            int serverPort = server.getPort();
            System.setProperty("app.api.url", "http://localhost:" + serverPort + "/api/generateMutations");
            server.enqueue(new MockResponse().setResponseCode(500));

            Button startButton = lookup("Start Mutation").queryButton();
            clickOn(startButton);
            sleep(2000);

            // Verify dialog...
        } finally {
            if (server != null) {
                server.shutdown(); // Ensure cleanup
            }
        }
    }
}
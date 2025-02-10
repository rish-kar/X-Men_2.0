package com.sermas.x.men.userInterfaceTests;

import com.sermas.x.men.user_interface.XMenInterface;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
import java.lang.reflect.Field;
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

    @Test
    @DisplayName("Test Send Mutation Request Success")
    public void testSendMutationRequestSuccess() throws Exception {
        sleep(6000); // Wait for main scene.
        // Set a temporary file as if it had been selected.
        File tempFile = File.createTempFile("test", ".xml");
        tempFile.deleteOnExit();
        Field selectedFileField = XMenInterface.class.getDeclaredField("selectedFile");
        selectedFileField.setAccessible(true);
        selectedFileField.set(app, tempFile);

        // For example, select the "Send" checkbox (cbSkipS).
        Field cbSkipSField = XMenInterface.class.getDeclaredField("cbSkipS");
        cbSkipSField.setAccessible(true);
        CheckBox cbSkipS = (CheckBox) cbSkipSField.get(app);
        Platform.runLater(() -> cbSkipS.setSelected(true));
        WaitForAsyncUtils.waitForFxEvents();

        // Set up a MockWebServer to simulate a successful HTTP response.
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("Success"));
        server.start(8081); // Ensure the server is started on port 8081.

        Button startButton = lookup("Start Mutation").queryButton();
        clickOn(startButton);
        sleep(2000); // Allow time for the asynchronous HTTP call and alert to appear.

        DialogPane alertPane = lookup(".dialog-pane").query();
        assertNotNull(alertPane, "An alert dialog should be displayed after a successful mutation request");
        String contentText = alertPane.getContentText();
        assertTrue(contentText.contains("Mutation Generation Succeeded"),
                "Success alert should indicate that mutation generation succeeded");

        // Dismiss the alert.
        clickOn("OK");
        server.shutdown();
    }

    @Test
    @DisplayName("Test Send Mutation Request Error")
    public void testSendMutationRequestError() throws Exception {
        sleep(6000); // Wait for main scene.
        // Set a temporary file as if it had been selected.
        File tempFile = File.createTempFile("test", ".xml");
        tempFile.deleteOnExit();
        Field selectedFileField = XMenInterface.class.getDeclaredField("selectedFile");
        selectedFileField.setAccessible(true);
        selectedFileField.set(app, tempFile);

        // Set up a MockWebServer to simulate an error response.
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
        server.start(8081);

        Button startButton = lookup("Start Mutation").queryButton();
        clickOn(startButton);
        sleep(2000); // Wait for asynchronous HTTP call and alert.
        DialogPane alertPane = lookup(".dialog-pane").query();
        assertNotNull(alertPane, "An alert dialog should be displayed after an error in the mutation request");
        String contentText = alertPane.getContentText();
        assertTrue(contentText.contains("Error while performing mutation"),
                "Error alert should indicate that there was an error performing the mutation");

        // Dismiss the alert.
        clickOn("OK");
        server.shutdown();
    }
}
package com.sermas.x.men.user_interface;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class XMenInterface extends Application {

    private MediaPlayer mediaPlayer;
    private File selectedFile;  // Holds the selected file

    // Declare checkboxes as class fields so they are accessible in event handlers.
    private CheckBox cbSkipS;
    private CheckBox cbSkipSR;
    private CheckBox cbSkipR;
    private CheckBox cbSkipRS;
    private CheckBox cbSkipRSR;
    private CheckBox cbAdd;
    private CheckBox cbSubmessages;
    private CheckBox cbType;
    private CheckBox cbCombineAddition;
    private CheckBox cbCombineOnly;

    private Button buttonUpload;
    private Button buttonStart;

    private final static String message = "Error while performing mutation";

    @Override
    public void start(Stage stage) {
        StackPane splashRoot = new StackPane();
        MediaView splashMediaView = createSplashScreen(splashRoot, stage);
        stage.setScene(new Scene(splashRoot));
        stage.setTitle("X-Men 3.0");
        stage.show();

        PauseTransition pause = new PauseTransition(Duration.seconds(5));
        pause.setOnFinished(e -> stage.setScene(createMainScene(stage)));
        pause.play();
    }

    /**
     * Attempts to load the splash video from resources.
     * If the resource is not found, a fallback Label is displayed.
     */
    private MediaView createSplashScreen(StackPane splashRoot, Stage stage) {
        MediaView splashMediaView = new MediaView();
        try {
            URL splashUrl = getClass().getResource("/X-Men-Logo.mp4");
            if (splashUrl == null) {
                throw new Exception("Resource /X-Men-Logo.mp4 not found.");
            }
            Media splashMedia = new Media(splashUrl.toExternalForm());
            MediaPlayer splashPlayer = new MediaPlayer(splashMedia);
            splashPlayer.setCycleCount(1);
            splashPlayer.setAutoPlay(true);
            splashMediaView.setMediaPlayer(splashPlayer);
            splashMediaView.setPreserveRatio(true);
            splashPlayer.setOnReady(() -> {
                stage.setWidth(splashMedia.getWidth());
                stage.setHeight(splashMedia.getHeight());
                stage.centerOnScreen();
            });
        } catch (Exception e) {
            log.debug("Error loading splash video from resources: {}", e.getMessage());

            // Fallback: show a Label if the video cannot be loaded.
            Label fallbackLabel = new Label("Splash Video not available");
            fallbackLabel.setStyle("-fx-text-fill: white; -fx-font-size: 20px;");
            splashRoot.getChildren().add(fallbackLabel);
        }
        splashRoot.getChildren().add(splashMediaView);
        splashRoot.setAlignment(Pos.CENTER);
        return splashMediaView;
    }

    /**
     * Builds the main scene by combining a background video (if available)
     * and the mutation option panel.
     */
    private Scene createMainScene(Stage stage) {
        StackPane root = new StackPane();
        Scene scene = new Scene(root);

        // Load the main CSS file from resources that styles the interface.
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/main.css")).toExternalForm());


        MediaView mediaView = setupMediaView(stage);
        GridPane checkboxPanel = setupGridPane(stage);

        root.getChildren().addAll(mediaView, checkboxPanel);
        return scene;
    }

    /**
     * Attempts to load the background video from resources.
     * If the resource is not found, logs the error.
     */
    private MediaView setupMediaView(Stage stage) {
        MediaView mediaView = new MediaView();
        try {
            URL mediaUrl = getClass().getResource("/DNA-Background.mp4");
            if (mediaUrl == null) {
                throw new Exception("Resource /DNA-Background.mp4 not found.");
            }
            Media media = new Media(mediaUrl.toExternalForm());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            mediaView.setMediaPlayer(mediaPlayer);
            mediaView.setPreserveRatio(true);
            mediaPlayer.setOnReady(() -> {
                stage.setWidth(1080);
                stage.setHeight(720);
                stage.centerOnScreen();
                mediaPlayer.play();
            });
        } catch (Exception e) {
            log.error("Error loading video from resources: {}", e.getMessage());
        }
        return mediaView;
    }

    /**
     * Sets up the grid pane that contains the checkboxes and buttons.
     * Also attaches event handlers for uploading a file and starting the mutation.
     */
    private GridPane setupGridPane(Stage stage) {
        GridPane checkboxPanel = new GridPane();
        checkboxPanel.setHgap(20);
        checkboxPanel.setVgap(36);
        checkboxPanel.setAlignment(Pos.CENTER);

        // Add the glass effect style class to the panel.
        checkboxPanel.getStyleClass().add("glass-panel");

        // Initialize buttons
        buttonUpload = new Button("Upload File");
        buttonStart = new Button("Start Mutation");
        setupButton(buttonUpload);
        setupButton(buttonStart);

        // Set up file chooser for the "Upload File" button.
        buttonUpload.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select a File to Upload");
            // Restrict to XML files (adjust if necessary).
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.*"));
            File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                selectedFile = file;
                log.debug("Selected file: {}", file.getAbsolutePath());
            }
        });

        // Set up HTTP request trigger for the "Start Mutation" button.
        buttonStart.setOnAction(e -> {
            if (selectedFile == null) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("File Not Selected");
                    alert.setHeaderText(null);
                    alert.setContentText("Please Upload a File before Mutating");

                    // Set your custom logo
                    ImageView customLogo = new ImageView(new Image(Objects.requireNonNull(
                            getClass().getResourceAsStream("/images/warning_mutation.png"))));
                    customLogo.setFitWidth(120);
                    customLogo.setFitHeight(120);
                    alert.setGraphic(customLogo);


                    // Load the custom CSS file from resources
                    String cssPath = Objects.requireNonNull(
                            getClass().getResource("/css/alert.css")).toExternalForm();
                    DialogPane dialogPane = alert.getDialogPane();
                    dialogPane.getStylesheets().add(cssPath);
                    dialogPane.getStyleClass().add("my-alert");

                    alert.showAndWait();
                });
                return;
            }
            sendMutationRequest();
        });

        // Initialize check boxes.
        String checkboxStyle = "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;";
        cbSkipS = new CheckBox("Send");
        cbSkipSR = new CheckBox("Send Receive");
        cbSkipR = new CheckBox("Receive");
        cbSkipRS = new CheckBox("Receive Send");
        cbSkipRSR = new CheckBox("Receive Send Receive");
        cbAdd = new CheckBox("Add");
        cbSubmessages = new CheckBox("Sub Messages");
        cbType = new CheckBox("Type");
        cbCombineAddition = new CheckBox("Combination in Addition");
        cbCombineOnly = new CheckBox("Combination Only");

        // Apply style to check boxes.
        cbSkipS.setStyle(checkboxStyle);
        cbSkipSR.setStyle(checkboxStyle);
        cbSkipR.setStyle(checkboxStyle);
        cbSkipRS.setStyle(checkboxStyle);
        cbSkipRSR.setStyle(checkboxStyle);
        cbAdd.setStyle(checkboxStyle);
        cbSubmessages.setStyle(checkboxStyle);
        cbType.setStyle(checkboxStyle);
        cbCombineAddition.setStyle(checkboxStyle);
        cbCombineOnly.setStyle(checkboxStyle);

        // Use an updated CSS drop-shadow with all required parameters.
        String labelStyle = "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 2, 0.5, 1, 1);";
        Label lblSkip = new Label("Skip mutation:");
        lblSkip.setStyle(labelStyle);
        Label lblReplace = new Label("Replace mutation:");
        lblReplace.setStyle(labelStyle);
        Label lblAdd = new Label("Add mutation:");
        lblAdd.setStyle(labelStyle);
        Label lblCombine = new Label("Combine mutation:");
        lblCombine.setStyle(labelStyle);

        // Arrange components in rows.
        checkboxPanel.addRow(0, lblSkip, cbSkipS, cbSkipSR, cbSkipR);
        checkboxPanel.addRow(1, new Label(""), cbSkipRS, cbSkipRSR);
        checkboxPanel.addRow(2, lblReplace, cbSubmessages, cbType);
        checkboxPanel.addRow(3, lblAdd, cbAdd);
        checkboxPanel.addRow(4, lblCombine, cbCombineAddition, cbCombineOnly);
        // Add the button row at the bottom.
        checkboxPanel.addRow(5, new Label(""), buttonUpload, buttonStart);

        return checkboxPanel;
    }

    /**
     * Sets up a button's preferred size and style.
     */
    private void setupButton(Button button) {
        button.setPrefSize(150, 40);
        button.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 14px;");
        GridPane.setMargin(button, new Insets(20, 0, 0, 0));
    }

    /**
     * Builds and sends an HTTP POST request (multipart/form-data) to your Spring Boot endpoint.
     * The request includes the selected file and mutation options as headers.
     */
    private void sendMutationRequest() {
        OkHttpClient client = new OkHttpClient();

        // Create a MediaType for the file.
        MediaType mediaType = MediaType.parse("application/octet-stream");
        RequestBody fileBody = RequestBody.create(selectedFile, mediaType);

        MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", selectedFile.getName(), fileBody);

        // Updated URL: using port 8081 to match the server's port.
        Request.Builder requestBuilder = new Request.Builder()
                .url("http://localhost:8081/api/generateMutations");

        // Add headers based on the state of the checkboxes.
        if (cbSkipS.isSelected())      requestBuilder.addHeader("Skip-Send", "true");
        if (cbSkipR.isSelected())       requestBuilder.addHeader("Skip-Receive", "true");
        if (cbSkipSR.isSelected())      requestBuilder.addHeader("Skip-Send-Receive", "true");
        if (cbSkipRS.isSelected())      requestBuilder.addHeader("Skip-Receive-Send", "true");
        if (cbSkipRSR.isSelected())     requestBuilder.addHeader("Skip-Receive-Send-Receive", "true");
        if (cbAdd.isSelected())         requestBuilder.addHeader("Add-Mutation", "true");
        if (cbSubmessages.isSelected()) requestBuilder.addHeader("Replace-Sub-Messages", "true");
        if (cbType.isSelected())        requestBuilder.addHeader("Replace-Type", "true");

        RequestBody requestBody = multipartBuilder.build();
        Request request = requestBuilder.post(requestBody).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException ex) {
                log.error("Error while performing mutation: {}", ex.getMessage());
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Mutation Request Failed");
                    alert.setHeaderText(null);
                    alert.setContentText("Error: " + message);

                    // Common styling for all alerts
                    String cssPath = Objects.requireNonNull(
                            getClass().getResource("/css/alert.css")).toExternalForm();
                    DialogPane dialogPane = alert.getDialogPane();
                    dialogPane.getStylesheets().add(cssPath);
                    dialogPane.getStyleClass().add("my-alert");

                    alert.showAndWait();
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    log.debug("Mutation generation succeeded!");
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Success");
                        alert.setHeaderText(null);
                        alert.setContentText("Mutation Generation Succeeded");

                        // Set your custom logo
                        ImageView customLogo = new ImageView(new Image(Objects.requireNonNull(
                                getClass().getResourceAsStream("/images/dna_logo.png"))));
                        customLogo.setFitWidth(120);
                        customLogo.setFitHeight(120);
                        alert.setGraphic(customLogo);

                        // Load the custom CSS file from resources
                        String cssPath = Objects.requireNonNull(getClass().getResource("/css/alert.css")).toExternalForm();
                        DialogPane dialogPane = alert.getDialogPane();
                        dialogPane.getStylesheets().add(cssPath);
                        dialogPane.getStyleClass().add("my-alert");

                        alert.showAndWait();
                    });
                } else {
                    log.error("Error: {} {}", response.code(), response.message());
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText(null);
                        alert.setContentText("Error: " + message);

                        // Set your custom logo
                        ImageView customLogo = new ImageView(new Image(Objects.requireNonNull(
                                getClass().getResourceAsStream("/images/error_mutation.png"))));
                        customLogo.setFitWidth(120);
                        customLogo.setFitHeight(120);
                        alert.setGraphic(customLogo);

                        // Load the custom CSS file from resources
                        String cssPath = Objects.requireNonNull(
                                getClass().getResource("/css/alert.css")).toExternalForm();
                        DialogPane dialogPane = alert.getDialogPane();
                        dialogPane.getStylesheets().add(cssPath);
                        dialogPane.getStyleClass().add("my-alert");

                        alert.showAndWait();
                    });
                }
                response.close();
            }
        });
    }


    public static void main(String[] args) {
        launch(args);
    }
}

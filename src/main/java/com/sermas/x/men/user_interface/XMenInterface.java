package com.sermas.x.men.user_interface;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.*;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.*;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

/** X-Men User Interface Application Class. */
@Slf4j
public class XMenInterface extends Application {

  private MediaPlayer mediaPlayer;
  private File selectedFile; // Holds the selected file

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
  private CheckBox cbForget; // Added forget mutation checkbox
  private CheckBox cbNeglect; // Added neglect mutation checkbox
  private RadioButton rbSimpleInput; // Simple input radio button
  private RadioButton rbComplexInput; // Complex input radio button
  private ToggleGroup forgetInputGroup; // Toggle group for forget input options

  private Button buttonUpload;
  private Button buttonStart;

  private static final String message = "Error while performing mutation";

  @Override
  public void start(Stage stage) {
    StackPane splashRoot = new StackPane();
    MediaView splashMediaView = createSplashScreen(splashRoot, stage);
    stage.setScene(new Scene(splashRoot));
    stage.setTitle("X-Men 3.0");
    stage.show();

    stage.setIconified(false);
    stage.setAlwaysOnTop(true); // prevents auto-minimize briefly
    PauseTransition pause = new PauseTransition(Duration.seconds(5));
    pause.setOnFinished(
        e -> {
          stage.setScene(createMainScene(stage));
          stage.setAlwaysOnTop(false); // revert after scene change
        });
    pause.play();
  }

  /**
   * Attempts to load the splash video from resources. If the resource is not found, a fallback
   * Label is displayed.
   */
  private MediaView createSplashScreen(StackPane splashRoot, Stage stage) {
    MediaView splashMediaView = new MediaView();
    boolean videoLoaded = false;

    try {
      InputStream videoStream = getClass().getResourceAsStream("/X-Men-Logo.mp4");
      if (videoStream == null) throw new Exception("Splash video not found");
      File tempVideoFile = File.createTempFile("splash", ".mp4");
      tempVideoFile.deleteOnExit();
      Files.copy(videoStream, tempVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      Media splashMedia = new Media(tempVideoFile.toURI().toString());
      MediaPlayer splashPlayer = new MediaPlayer(splashMedia);
      splashPlayer.setCycleCount(1);
      splashPlayer.setAutoPlay(true);
      splashMediaView.setMediaPlayer(splashPlayer);
      splashMediaView.setPreserveRatio(true);
      splashPlayer.setOnReady(
          () -> {
            stage.setWidth(splashMedia.getWidth());
            stage.setHeight(splashMedia.getHeight());
            stage.centerOnScreen();
          });
      splashRoot.getChildren().add(splashMediaView);
      videoLoaded = true;
    } catch (Exception e) {
      log.warn("Splash video not found, using fallback image.");
    }

    if (!videoLoaded) {
      InputStream imgStream = getClass().getResourceAsStream("/images/splash_fallback_logo.png");
      if (imgStream != null) {
        ImageView fallbackImage = new ImageView(new Image(imgStream));
        fallbackImage.setFitWidth(400);
        fallbackImage.setPreserveRatio(true);
        splashRoot.getChildren().add(fallbackImage);
        log.info("Loaded fallback splash image.");
      } else {
        Label label = new Label("Splash Video not available");
        label.setStyle("-fx-text-fill: white; -fx-font-size: 24px;");
        splashRoot.getChildren().add(label);
      }
    }
    splashRoot.setAlignment(Pos.CENTER);
    return splashMediaView;
  }

  /**
   * Builds the main scene by combining a background video (if available) and the mutation option
   * panel.
   */
  private Scene createMainScene(Stage stage) {
    StackPane root = new StackPane();
    Scene scene = new Scene(root, 1080, 720);

    // CSS
    URL cssUrl = getClass().getResource("/css/main.css");
    if (cssUrl != null) {
      scene.getStylesheets().add(cssUrl.toExternalForm());
    } else {
      log.error("main.css not found!");
    }

    Node backgroundNode = setupMediaOrFallback(stage);
    GridPane checkboxPanel = setupGridPane(stage);

    root.getChildren().addAll(backgroundNode, checkboxPanel);

    return scene;
  }

  /**
   * Attempts to load the background video from resources. If the resource is not found, logs the
   * error.
   */
  private Node setupMediaOrFallback(Stage stage) {
    StackPane container = new StackPane();

    Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
    double desiredWidth = screenBounds.getWidth();
    double desiredHeight = screenBounds.getHeight();
    container.setPrefSize(desiredWidth, desiredHeight);

    MediaView mediaView = null;

    try (InputStream videoStream = getClass().getResourceAsStream("/DNA-Background.mp4")) {
      if (videoStream != null) {
        File tempVideoFile = File.createTempFile("dna", ".mp4");
        tempVideoFile.deleteOnExit();
        Files.copy(videoStream, tempVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        Media media = new Media(tempVideoFile.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);

        mediaView = new MediaView(mediaPlayer);
        mediaView.setPreserveRatio(false);

        mediaPlayer.setOnReady(
            () -> {
              stage.setWidth(desiredWidth);
              stage.setHeight(desiredHeight);
              stage.centerOnScreen();
              mediaPlayer.play();
            });

        mediaView.fitWidthProperty().bind(container.widthProperty());
        mediaView.fitHeightProperty().bind(container.heightProperty());

        container.getChildren().add(mediaView);
        log.info("Media successfully loaded.");
        return container;
      }
      throw new IOException("Video stream null");
    } catch (Exception e) {
      log.warn("Video media failed to load: {}", e.getMessage());
    }

    // Fallback to Image explicitly guaranteed:
    try (InputStream imgStream =
        getClass().getResourceAsStream("/images/main_scene_dna_fallback.png")) {
      if (imgStream == null) {
        throw new IOException("Fallback image not found in resources");
      }

      ImageView fallbackImage = new ImageView(new Image(imgStream));
      fallbackImage.setPreserveRatio(false);
      fallbackImage.fitWidthProperty().bind(container.widthProperty());
      fallbackImage.fitHeightProperty().bind(container.heightProperty());

      stage.setWidth(desiredWidth);
      stage.setHeight(desiredHeight);
      stage.centerOnScreen();

      container.getChildren().add(fallbackImage);
      log.info("Fallback image loaded successfully.");

    } catch (Exception imgException) {
      log.error("Error loading fallback image explicitly: {}", imgException.getMessage());
      Label errorLabel = new Label("Critical Error: No media or fallback image found.");
      errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 18px;");
      container.getChildren().add(errorLabel);
    }

    return container;
  }

  /**
   * Sets up the grid pane that contains the checkboxes and buttons. Also attaches event handlers
   * for uploading a file and starting the mutation.
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
    buttonUpload.setId("buttonUpload");

    buttonStart = new Button("Start Mutation");
    buttonStart.setId("buttonStart");
    setupButton(buttonUpload);
    setupButton(buttonStart);

    // Set up file chooser for the "Upload File" button.
    buttonUpload.setOnAction(
        e -> {
          FileChooser fileChooser = new FileChooser();
          fileChooser.setTitle("Select a File to Upload");
          // Restrict to XML files (adjust if necessary).
          fileChooser
              .getExtensionFilters()
              .add(new FileChooser.ExtensionFilter("XML Files", "*.*"));
          File file = fileChooser.showOpenDialog(stage);
          if (file != null) {
            selectedFile = file;
            log.debug("Selected file: {}", file.getAbsolutePath());
          }
        });

    // Set up HTTP request trigger for the "Start Mutation" button.
    buttonStart.setOnAction(
        e -> {
          if (selectedFile == null) {
            Platform.runLater(
                () -> {
                  Alert alert = new Alert(Alert.AlertType.WARNING);
                  alert.setTitle("File Not Selected");
                  alert.setHeaderText(null);
                  alert.setContentText("Please Upload a File before Mutating");

                  // Set your custom logo
                  ImageView customLogo =
                      new ImageView(
                          new Image(
                              Objects.requireNonNull(
                                  getClass().getResourceAsStream("/images/warning_mutation.png"))));
                  customLogo.setFitWidth(120);
                  customLogo.setFitHeight(120);
                  alert.setGraphic(customLogo);

                  // Load the custom CSS file from resources
                  String cssPath =
                      Objects.requireNonNull(getClass().getResource("/css/alert.css"))
                          .toExternalForm();
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
    cbSkipS.setId("cbSkipS");

    cbSkipSR = new CheckBox("Send Receive");
    cbSkipSR.setId("cbSkipSR");

    cbSkipR = new CheckBox("Receive");
    cbSkipR.setId("cbSkipR");

    cbSkipRS = new CheckBox("Receive Send");
    cbSkipRS.setId("cbSkipRS");

    cbSkipRSR = new CheckBox("Receive Send Receive");
    cbSkipRSR.setId("cbSkipRSR");

    cbAdd = new CheckBox("Add");
    cbAdd.setId("cbAdd");

    cbSubmessages = new CheckBox("Sub Messages");
    cbSubmessages.setId("cbSubmessages");

    cbType = new CheckBox("Type");
    cbType.setId("cbType");

    cbCombineAddition = new CheckBox("Combination in Addition");
    cbCombineAddition.setId("cbCombineAddition");

    cbCombineOnly = new CheckBox("Combination Only");
    cbCombineOnly.setId("cbCombineOnly");

    cbForget = new CheckBox("Forget Mutation");
    cbForget.setId("cbForget");

    cbNeglect = new CheckBox("Neglect Mutation");
    cbNeglect.setId("cbNeglect");

    // Create radio buttons for forget mutation input type
    forgetInputGroup = new ToggleGroup();
    rbSimpleInput = new RadioButton("Simple Input");
    rbSimpleInput.setId("rbSimpleInput");
    rbSimpleInput.setToggleGroup(forgetInputGroup);
    rbSimpleInput.setDisable(true); // Disabled until forget mutation is selected

    rbComplexInput = new RadioButton("Complex Input");
    rbComplexInput.setId("rbComplexInput");
    rbComplexInput.setToggleGroup(forgetInputGroup);
    rbComplexInput.setDisable(true); // Disabled until forget mutation is selected

    // Add listener to enable/disable radio buttons based on Forget checkbox state
    cbForget.selectedProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue) {
        // Enable radio buttons and select Simple Input by default
        rbSimpleInput.setDisable(false);
        rbComplexInput.setDisable(false);
        rbSimpleInput.setSelected(true);
      } else {
        // Disable radio buttons and clear selection
        rbSimpleInput.setDisable(true);
        rbComplexInput.setDisable(true);
        forgetInputGroup.selectToggle(null);
      }
    });

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
    cbForget.setStyle(checkboxStyle);
    cbNeglect.setStyle(checkboxStyle);
    rbSimpleInput.setStyle(checkboxStyle);
    rbComplexInput.setStyle(checkboxStyle);

    // Use an updated CSS drop-shadow with all required parameters.
    String labelStyle =
        "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-effect: "
            + "dropshadow(gaussian, rgba(0,0,0,0.8), 2, 0.5, 1, 1);";
    Label lblSkip = new Label("Skip mutation:");
    lblSkip.setStyle(labelStyle);
    Label lblReplace = new Label("Replace mutation:");
    lblReplace.setStyle(labelStyle);
    Label lblAdd = new Label("Add mutation:");
    lblAdd.setStyle(labelStyle);
    Label lblCombine = new Label("Combine mutation:");
    lblCombine.setStyle(labelStyle);
    Label lblForget = new Label("Forget mutation:");
    lblForget.setStyle(labelStyle);
    Label lblNeglect = new Label("Neglect mutation:");
    lblNeglect.setStyle(labelStyle);

    // Arrange components in rows.
    checkboxPanel.addRow(0, lblSkip, cbSkipS, cbSkipSR, cbSkipR);
    checkboxPanel.addRow(1, new Label(""), cbSkipRS, cbSkipRSR);
    checkboxPanel.addRow(2, lblReplace, cbSubmessages, cbType);
    checkboxPanel.addRow(3, lblAdd, cbAdd);
    checkboxPanel.addRow(4, lblCombine, cbCombineAddition, cbCombineOnly);
    // Add forget mutation with radio buttons for input type
    checkboxPanel.addRow(5, lblForget, cbForget, rbSimpleInput, rbComplexInput);
    checkboxPanel.addRow(6, lblNeglect, cbNeglect);
    // Add the button row at the bottom.
    checkboxPanel.addRow(7, new Label(""), buttonUpload, buttonStart);

    return checkboxPanel;
  }

  /** Sets up a button's preferred size and style. */
  private void setupButton(Button button) {
    button.setPrefSize(150, 40);
    button.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 14px;");
    GridPane.setMargin(button, new Insets(20, 0, 0, 0));
  }

  /**
   * Builds and sends an HTTP POST request (multipart/form-data) to your Spring Boot endpoint. The
   * request includes the selected file and mutation options as headers.
   */
  private void sendMutationRequest() {
      OkHttpClient client = new OkHttpClient.Builder()
              .connectTimeout(30, TimeUnit.MINUTES)
              .writeTimeout(30, TimeUnit.MINUTES)
              .readTimeout(30, TimeUnit.MINUTES)
              .build();

    // Create a MediaType for the file.
    MediaType mediaType = MediaType.parse("application/octet-stream");
    RequestBody fileBody = RequestBody.create(selectedFile, mediaType);

    MultipartBody.Builder multipartBuilder =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", selectedFile.getName(), fileBody);

    // Updated URL: using environment variables for API configuration
    String apiBaseUrl = System.getProperty("API_BASE_URL", System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8081"));
    String apiEndpoint = System.getProperty("API_GENERATE_MUTATIONS_ENDPOINT", System.getenv().getOrDefault("API_GENERATE_MUTATIONS_ENDPOINT", "/api/generateMutations"));
    String apiUrl = System.getProperty("API_FULL_URL", System.getenv().getOrDefault("API_FULL_URL", apiBaseUrl + apiEndpoint));
    Request.Builder requestBuilder = new Request.Builder().url(apiUrl);

    // Add headers based on the state of the checkboxes.
    if (cbSkipS.isSelected()) {
      requestBuilder.addHeader("Skip-Send", "true");
    }
    if (cbSkipR.isSelected()) {
      requestBuilder.addHeader("Skip-Receive", "true");
    }
    if (cbSkipSR.isSelected()) {
      requestBuilder.addHeader("Skip-Send-Receive", "true");
    }
    if (cbSkipRS.isSelected()) {
      requestBuilder.addHeader("Skip-Receive-Send", "true");
    }
    if (cbSkipRSR.isSelected()) {
      requestBuilder.addHeader("Skip-Receive-Send-Receive", "true");
    }
    if (cbAdd.isSelected()) {
      requestBuilder.addHeader("Add-Mutation", "true");
    }
    if (cbSubmessages.isSelected()) {
      requestBuilder.addHeader("Replace-Sub-Messages", "true");
    }
    if (cbType.isSelected()) {
      requestBuilder.addHeader("Replace-Type", "true");
    }
    if (cbForget.isSelected()) {
      requestBuilder.addHeader("Forget-Mutation", "true");
      // Only send Haskell-Activate header if Complex Input is selected
      if (rbComplexInput.isSelected()) {
        requestBuilder.addHeader("Haskell-Activate", "true");
      }
    }
    if (cbNeglect.isSelected()) {
      requestBuilder.addHeader("Neglect-Mutation", "true");
    }

    RequestBody requestBody = multipartBuilder.build();
    Request request = requestBuilder.post(requestBody).build();

    client
        .newCall(request)
        .enqueue(
            new Callback() {
              @Override
              public void onFailure(@NotNull Call call, @NotNull IOException ex) {
                log.error("Error while performing mutation: {}", ex.getMessage());
                Platform.runLater(
                    () -> {
                      Alert alert = new Alert(Alert.AlertType.ERROR);
                      alert.setTitle("Mutation Request Failed");
                      alert.setHeaderText(null);
                      alert.setContentText("Error: " + message);

                      // Common styling for all alerts
                      String cssPath =
                          Objects.requireNonNull(getClass().getResource("/css/alert.css"))
                              .toExternalForm();
                      DialogPane dialogPane = alert.getDialogPane();
                      dialogPane.getStylesheets().add(cssPath);
                      dialogPane.getStyleClass().add("my-alert");

                      alert.showAndWait();
                    });
              }

              @Override
              public void onResponse(@NotNull Call call, @NotNull Response response)
                  throws IOException {
                if (response.isSuccessful()) {
                  log.debug("Mutation generation succeeded!");
                  Platform.runLater(
                      () -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Success");
                        alert.setHeaderText(null);
                        alert.setContentText("Mutation Generation Succeeded");

                        // Set your custom logo
                        ImageView customLogo =
                            new ImageView(
                                new Image(
                                    Objects.requireNonNull(
                                        getClass().getResourceAsStream("/images/dna_logo.png"))));
                        customLogo.setFitWidth(120);
                        customLogo.setFitHeight(120);
                        alert.setGraphic(customLogo);

                        // Load the custom CSS file from resources
                        String cssPath =
                            Objects.requireNonNull(getClass().getResource("/css/alert.css"))
                                .toExternalForm();
                        DialogPane dialogPane = alert.getDialogPane();
                        dialogPane.getStylesheets().add(cssPath);
                        dialogPane.getStyleClass().add("my-alert");

                        alert.showAndWait();
                      });
                } else {
                  // Log the response code and message
                  log.error("Error: {} {}", response.code(), response.message());

                  // Attempt to read the response body for details
                  String responseBodyStr = response.body() != null ? response.body().string() : "";

                  // Check for the specific "Forget function not found" text
                  if (responseBodyStr.contains("Forget function not found")) {
                    Platform.runLater(
                        () -> {
                          Alert alert = new Alert(Alert.AlertType.ERROR);
                          alert.setTitle("Forget Function Error");
                          alert.setHeaderText(null);
                          alert.setContentText("Forget function not found in the input code");

                          // Use the forget_not_found.png image
                          ImageView customLogo =
                              new ImageView(
                                  new Image(
                                      Objects.requireNonNull(
                                          getClass()
                                              .getResourceAsStream(
                                                  "/images/forget_not_found.png"))));
                          customLogo.setFitWidth(120);
                          customLogo.setFitHeight(120);
                          alert.setGraphic(customLogo);

                          // Load the custom CSS file
                          String cssPath =
                              Objects.requireNonNull(getClass().getResource("/css/alert.css"))
                                  .toExternalForm();
                          DialogPane dialogPane = alert.getDialogPane();
                          dialogPane.getStylesheets().add(cssPath);
                          dialogPane.getStyleClass().add("my-alert");

                          alert.showAndWait();
                        });
                  } else {
                    log.error("Error: {} {}", response.code(), response.message());
                    Platform.runLater(
                        () -> {
                          Alert alert = new Alert(Alert.AlertType.ERROR);
                          alert.setTitle("Error");
                          alert.setHeaderText(null);
                          alert.setContentText("Error: " + message);

                          // Set your custom logo
                          ImageView customLogo =
                              new ImageView(
                                  new Image(
                                      Objects.requireNonNull(
                                          getClass()
                                              .getResourceAsStream("/images/error_mutation.png"))));
                          customLogo.setFitWidth(120);
                          customLogo.setFitHeight(120);
                          alert.setGraphic(customLogo);

                          // Load the custom CSS file from resources
                          String cssPath =
                              Objects.requireNonNull(getClass().getResource("/css/alert.css"))
                                  .toExternalForm();
                          DialogPane dialogPane = alert.getDialogPane();
                          dialogPane.getStylesheets().add(cssPath);
                          dialogPane.getStyleClass().add("my-alert");

                          alert.showAndWait();
                        });
                  }
                }
                response.close();
              }
            });
  }

  /**
   * Main method to launch the JavaFX application.
   *
   * @param args Command line arguments
   */
  public static void main(String[] args) {
    launch(args);
  }
}

package com.sermas.x.men.user_interface;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
  private CheckBox cbForgetHaskell; // New checkbox for Haskell derivation

  private ToggleGroup derivationTypeGroup;
  private RadioButton rbDerivationLimited;
  private RadioButton rbDerivationSpecified;
  private RadioButton rbDerivationInfinite;
  private TextField tfDerivationDepth;
  private CheckBox cbShowDerivationTree;

  private Button buttonUpload;
  private Button buttonStart;

  private static final String message = "Error while performing mutation";

  // Keep a reference to the root StackPane so we can show a glass overlay.
  private StackPane mainRoot;

  // Hero logo — held so we can swap between Black.png / White.png on theme changes.
  private ImageView heroLogo;

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
   * Builds the main scene using {@link MainSceneFactory} for the elevra-style hero, and docks the
   * existing mutation-controls panel as a glass card on the right.
   *
   * <p>The old grid-pane scene is intentionally kept reachable via {@link #setupGridPane(Stage)}
   * for parity, but it's now wrapped in a glass container and positioned by the factory.
   */
  private Scene createMainScene(Stage stage) {
    int serverPort = 8081;
    MainSceneFactory.Built built =
        MainSceneFactory.build(stage, serverPort, ignored -> openSettings(stage));

    this.mainRoot = built.root();
    this.heroLogo = built.logoView();

    // Build the legacy controls panel and dock it into the right-hand 65% of the layout.
    GridPane checkboxPanel = setupGridPane(stage);
    checkboxPanel.setMaxWidth(Double.MAX_VALUE);

    // Panel header (title + sub).
    Label panelTitle = new Label("Mutation Controls");
    panelTitle.getStyleClass().add("x-control-title");
    Label panelSub = new Label("Pick the mutations to generate, then hit Start.");
    panelSub.getStyleClass().add("x-control-sub");
    VBox panelHeader = new VBox(4, panelTitle, panelSub);

    // Panel footer with "How it works" right-aligned. Icon stroke is themed via CSS
    // (.x-icon-themed → -text), so it stays visible on every palette.
    Button howItWorks = new Button("How it works");
    howItWorks.getStyleClass().add("x-cta-secondary");
    howItWorks.setGraphic(Icons.info(18, javafx.scene.paint.Color.WHITE));
    howItWorks.setOnAction(e -> AlgorithmInfoDialog.show(stage));
    Animations.hoverLift(howItWorks, 1.03);
    StackPane howItWorksWrap = new StackPane(howItWorks);
    howItWorksWrap.getStyleClass().add("x-shadow-room");
    HBox panelFooter = new HBox(howItWorksWrap);
    panelFooter.getStyleClass().add("x-control-footer");

    VBox panelWrap = new VBox(14, panelHeader, checkboxPanel, panelFooter);
    panelWrap.getStyleClass().add("x-control-panel");
    panelWrap.setMaxWidth(Double.MAX_VALUE);
    VBox.setVgrow(checkboxPanel, Priority.ALWAYS);
    built.controlsHost().getChildren().add(panelWrap);

    // The hero scene's "Start Mutation" and "Upload File" CTAs delegate to the legacy
    // buttons (which carry the upload / submit logic).
    Node heroStart = built.root().lookup("#heroStart");
    Node heroUpload = built.root().lookup("#heroUpload");
    if (heroStart instanceof Button hs && buttonStart != null) {
      hs.setOnAction(e -> buttonStart.fire());
    }
    if (heroUpload instanceof Button hu && buttonUpload != null) {
      hu.setOnAction(e -> buttonUpload.fire());
    }

    Scene scene = new Scene(built.root(), 1280, 800);

    // Modern stylesheet; keep legacy main.css as a secondary so any selectors the controls
    // panel still relies on continue to work.
    URL v2 = getClass().getResource("/css/main-v2.css");
    if (v2 != null) scene.getStylesheets().add(v2.toExternalForm());
    URL legacy = getClass().getResource("/css/main.css");
    if (legacy != null) scene.getStylesheets().add(legacy.toExternalForm());

    return scene;
  }

  /** Open the settings dialog and apply theme/logo/preference changes back into the scene. */
  private void openSettings(Stage stage) {
    int serverPort = 8081;
    SettingsDialog dialog =
        new SettingsDialog(
            serverPort,
            // 1) Live theme preview: pull the resolved theme by id and re-style the main root.
            themeId -> {
              try {
                okhttp3.OkHttpClient http = new okhttp3.OkHttpClient();
                okhttp3.Response r =
                    http.newCall(
                            new okhttp3.Request.Builder()
                                .url(
                                    "http://localhost:"
                                        + serverPort
                                        + "/api/settings/themes/"
                                        + themeId)
                                .build())
                        .execute();
                try (r) {
                  if (r.body() != null && mainRoot != null) {
                    com.fasterxml.jackson.databind.ObjectMapper m =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                    com.sermas.x.men.config.ThemeCatalog.Theme theme =
                        m.readValue(
                            r.body().bytes(),
                            com.sermas.x.men.config.ThemeCatalog.Theme.class);
                    javafx.application.Platform.runLater(
                        () -> {
                          ThemeApplier.apply(mainRoot, theme);
                          ThemeLogo.apply(heroLogo, theme);
                        });
                  }
                }
              } catch (Exception ex) {
                log.warn("Failed to refresh theme: {}", ex.getMessage());
              }
            },
            // 2) Preferences callback (logged for now).
            prefs -> log.debug("UI preferences: {}", prefs),
            // 3) Logo-swap on theme change (light themes → Black.png, dark → White.png).
            theme -> javafx.application.Platform.runLater(() -> ThemeLogo.apply(heroLogo, theme)));
    dialog.show(stage);
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

    // Legacy "glass-panel" styling intentionally NOT applied — the new
    // .x-control-panel wrapper provides the single seamless glass surface.

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
    // Since the redesigned UI exposes a single CTA, "Start Mutation" now also
    // handles file selection when nothing has been chosen yet: it opens a file
    // chooser, stores the picked file, and proceeds to submit. If the user
    // cancels the chooser, no mutation is fired.
    buttonStart.setOnAction(
        e -> {
          if (selectedFile == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select a .spthy file to mutate");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Tamarin SPTHY", "*.spthy"));
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("All files", "*.*"));
            File picked = chooser.showOpenDialog(stage);
            if (picked == null) {
              // User cancelled — silently abort.
              return;
            }
            selectedFile = picked;
            log.debug("Selected file via Start CTA: {}", picked.getAbsolutePath());
          }
          sendMutationRequest();
        });

    // Initialize check boxes.
    // NOTE: text colour is intentionally NOT hardcoded any more — the .x-check
    // selector in main-v2.css picks it up from the active theme's -text variable.
    String checkboxStyle = "-fx-font-weight: 600; -fx-font-size: 14px;";
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

    // New: Forget mutation using external Haskell script
    cbForgetHaskell = new CheckBox("Forget Mutation using external Haskell Script");
    cbForgetHaskell.setId("cbForgetHaskell");
    cbForgetHaskell.setDisable(true); // enabled only when Forget is selected
    cbForgetHaskell.setWrapText(true);
    cbForgetHaskell.setMaxWidth(220);

    // Create radio buttons for derivation type (Forget mutation)
    derivationTypeGroup = new ToggleGroup();
    rbDerivationLimited = new RadioButton("Limited Depth");
    rbDerivationLimited.setId("rbDerivationLimited");
    rbDerivationLimited.setToggleGroup(derivationTypeGroup);
    rbDerivationLimited.setDisable(true);

    rbDerivationSpecified = new RadioButton("Specified Depth");
    rbDerivationSpecified.setId("rbDerivationSpecified");
    rbDerivationSpecified.setToggleGroup(derivationTypeGroup);
    rbDerivationSpecified.setDisable(true);

    rbDerivationInfinite = new RadioButton("Infinite");
    rbDerivationInfinite.setId("rbDerivationInfinite");
    rbDerivationInfinite.setToggleGroup(derivationTypeGroup);
    rbDerivationInfinite.setDisable(true);

    tfDerivationDepth = new TextField();
    tfDerivationDepth.setId("tfDerivationDepth");
    tfDerivationDepth.setPromptText("Depth");
    tfDerivationDepth.setMaxWidth(90);
    tfDerivationDepth.setDisable(true);

    cbShowDerivationTree = new CheckBox("Show derivation tree on screen");
    cbShowDerivationTree.setId("cbShowDerivationTree");
    cbShowDerivationTree.setDisable(true);

    // Enable/disable derivation controls together with Forget
    cbForget
        .selectedProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              if (newValue) {
                rbDerivationLimited.setDisable(false);
                rbDerivationSpecified.setDisable(false);
                rbDerivationInfinite.setDisable(false);
                cbShowDerivationTree.setDisable(false);
                cbForgetHaskell.setDisable(false);

                // Default choice: Limited
                rbDerivationLimited.setSelected(true);

                // Depth box only for specified
                tfDerivationDepth.setText("");
                tfDerivationDepth.setDisable(true);

              } else {
                rbDerivationLimited.setDisable(true);
                rbDerivationSpecified.setDisable(true);
                rbDerivationInfinite.setDisable(true);
                derivationTypeGroup.selectToggle(null);

                tfDerivationDepth.setDisable(true);
                tfDerivationDepth.setText("");

                cbShowDerivationTree.setDisable(true);
                cbShowDerivationTree.setSelected(false);

                cbForgetHaskell.setDisable(true);
                cbForgetHaskell.setSelected(false);
              }
            });

    // Depth enabled only when "Specified Depth" selected
    derivationTypeGroup
        .selectedToggleProperty()
        .addListener(
            (obs, oldToggle, newToggle) -> {
              if (newToggle == rbDerivationSpecified) {
                tfDerivationDepth.setDisable(false);
              } else {
                tfDerivationDepth.setDisable(true);
                tfDerivationDepth.setText("");
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
    cbForgetHaskell.setStyle(checkboxStyle);
    rbDerivationLimited.setStyle(checkboxStyle);
    rbDerivationSpecified.setStyle(checkboxStyle);
    rbDerivationInfinite.setStyle(checkboxStyle);
    cbShowDerivationTree.setStyle(checkboxStyle);

    // Theme-aware section labels. Colour now flows from the .x-control-panel .label
    // selector in main-v2.css (i.e. the active theme's -text variable).
    String labelStyle = "-fx-font-weight: 700; -fx-font-size: 15px; -fx-letter-spacing: 0.04em;"
        + "-fx-font-family: 'Inter', 'Segoe UI Variable', 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;";
    Label lblSkip = new Label("Skip Mutation:");
    lblSkip.setStyle(labelStyle);
    Label lblReplace = new Label("Replace Mutation:");
    lblReplace.setStyle(labelStyle);
    Label lblAdd = new Label("Add Mutation:");
    lblAdd.setStyle(labelStyle);
    Label lblCombine = new Label("Combine Mutation:");
    lblCombine.setStyle(labelStyle);
    Label lblForget = new Label("Forget Mutation:");
    lblForget.setStyle(labelStyle);

    Label lblForgetHaskell = new Label("Forget Mutation (Haskell Derivation):");
    lblForgetHaskell.setStyle(labelStyle);
    lblForgetHaskell.setWrapText(true);
    lblForgetHaskell.setMaxWidth(220);

    Label lblNeglect = new Label("Neglect Mutation:");
    lblNeglect.setStyle(labelStyle);

    // Arrange components in rows.
    checkboxPanel.addRow(0, lblSkip, cbSkipS, cbSkipSR, cbSkipR);
    checkboxPanel.addRow(1, new Label(""), cbSkipRS, cbSkipRSR);
    checkboxPanel.addRow(2, lblReplace, cbSubmessages, cbType);
    checkboxPanel.addRow(3, lblAdd, cbAdd);
    checkboxPanel.addRow(4, lblCombine, cbCombineAddition, cbCombineOnly);

    // ----- Forget mutation (Java derivation) -----
    checkboxPanel.addRow(5, lblForget, cbForget);

    // Derivation-mode radios in an HBox so no label ever truncates.
    HBox derivationRadios =
        new HBox(20, rbDerivationLimited, rbDerivationSpecified, rbDerivationInfinite);
    derivationRadios.setAlignment(Pos.CENTER_LEFT);
    checkboxPanel.add(new Label(""), 0, 6);
    checkboxPanel.add(derivationRadios, 1, 6);
    GridPane.setColumnSpan(derivationRadios, 4);

    // The depth text-field appears on its own row, ONLY when "Specified Depth" is selected.
    tfDerivationDepth.setManaged(false);
    tfDerivationDepth.setVisible(false);
    tfDerivationDepth.setMaxWidth(150);
    HBox depthRow = new HBox(8, tfDerivationDepth);
    depthRow.setAlignment(Pos.CENTER_LEFT);
    depthRow.managedProperty().bind(tfDerivationDepth.managedProperty());
    depthRow.visibleProperty().bind(tfDerivationDepth.visibleProperty());
    checkboxPanel.add(new Label(""), 0, 7);
    checkboxPanel.add(depthRow, 1, 7);
    GridPane.setColumnSpan(depthRow, 4);

    rbDerivationSpecified
        .selectedProperty()
        .addListener(
            (obs, was, now) -> {
              boolean enabled = now != null && now;
              tfDerivationDepth.setManaged(enabled);
              tfDerivationDepth.setVisible(enabled);
              if (!enabled) tfDerivationDepth.setText("");
            });

    checkboxPanel.add(new Label(""), 0, 8);
    checkboxPanel.add(cbShowDerivationTree, 1, 8);
    GridPane.setColumnSpan(cbShowDerivationTree, 4);

    // ----- Forget mutation (external Haskell script) -----
    checkboxPanel.addRow(9, lblForgetHaskell, cbForgetHaskell);

    // ----- Neglect -----
    checkboxPanel.addRow(10, lblNeglect, cbNeglect);

    // Column constraints — label column left, controls column expands.
    javafx.scene.layout.ColumnConstraints labelCol = new javafx.scene.layout.ColumnConstraints();
    labelCol.setMinWidth(160);
    labelCol.setPrefWidth(180);
    javafx.scene.layout.ColumnConstraints controlsCol1 = new javafx.scene.layout.ColumnConstraints();
    controlsCol1.setHgrow(Priority.SOMETIMES);
    controlsCol1.setMinWidth(150);
    javafx.scene.layout.ColumnConstraints controlsCol2 = new javafx.scene.layout.ColumnConstraints();
    controlsCol2.setHgrow(Priority.SOMETIMES);
    controlsCol2.setMinWidth(150);
    javafx.scene.layout.ColumnConstraints controlsCol3 = new javafx.scene.layout.ColumnConstraints();
    controlsCol3.setHgrow(Priority.SOMETIMES);
    controlsCol3.setMinWidth(150);
    checkboxPanel
        .getColumnConstraints()
        .addAll(labelCol, controlsCol1, controlsCol2, controlsCol3);

    // Buttons are intentionally NOT added to the legacy panel any more —
    // the hero "Start Mutation" CTA in the redesigned scene drives the flow.
    // The Button instances still exist and remain wired so the hero CTA can
    // delegate to their onAction handlers (see createMainScene).

    return checkboxPanel;
  }

  /**
   * Sets up a button's preferred size. The legacy buttons aren't shown any more (the hero CTA
   * delegates to them), but we still call this for backwards compatibility with old tests.
   */
  private void setupButton(Button button) {
    button.setPrefSize(150, 40);
    button.getStyleClass().add("x-cta-secondary");
    GridPane.setMargin(button, new Insets(20, 0, 0, 0));
  }

  /**
   * Builds and sends an HTTP POST request (multipart/form-data) to your Spring Boot endpoint. The
   * request includes the selected file and mutation options as headers.
   */
  private void sendMutationRequest() {
    OkHttpClient client =
        new OkHttpClient.Builder()
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

    String apiBaseUrl =
        System.getProperty(
            "API_BASE_URL", System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8081"));

    // If Forget is selected, call the Forget endpoint; otherwise keep existing behavior.
    String apiEndpoint;
    if (cbForget.isSelected()) {
      apiEndpoint = "/api/forget/mutations";
    } else {
      apiEndpoint =
          System.getProperty(
              "API_GENERATE_MUTATIONS_ENDPOINT",
              System.getenv().getOrDefault("API_GENERATE_MUTATIONS_ENDPOINT", "/api/generateMutations"));
    }

    String apiUrl = apiBaseUrl + apiEndpoint;
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

      // Haskell derivation (external script path)
      if (cbForgetHaskell != null && cbForgetHaskell.isSelected()) {
        requestBuilder.addHeader("Haskell-Activate", "true");
      }

      // Derivation headers (Forget endpoint)
      String derivationTypeHeader = getSelectedDerivationTypeHeader();
      if (derivationTypeHeader != null) {
        requestBuilder.addHeader("Derivation-Type", derivationTypeHeader);
      }

      if ("DEPTH_SPECIFIED".equals(derivationTypeHeader)) {
        Integer depth = parseDepthOrNull(tfDerivationDepth.getText());
        if (depth != null) {
          requestBuilder.addHeader("Derivation-Depth", depth.toString());
        }
      }
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
                  byte[] bodyBytes = response.body() != null ? response.body().bytes() : new byte[0];

                  String derivationTreeText = null;
                  if (bodyBytes.length > 0 && cbForget.isSelected() && cbShowDerivationTree.isSelected()) {
                    derivationTreeText = extractDerivationTreeFromZip(bodyBytes);
                  }

                  String finalDerivationTreeText = derivationTreeText;
                  Platform.runLater(
                      () -> {
                        // Existing success alert
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Success");
                        alert.setHeaderText(null);
                        alert.setContentText("Mutation Generation Succeeded");

                        ImageView customLogo =
                            new ImageView(
                                new Image(
                                    Objects.requireNonNull(
                                        getClass().getResourceAsStream("/images/dna_logo.png"))));
                        customLogo.setFitWidth(120);
                        customLogo.setFitHeight(120);
                        alert.setGraphic(customLogo);

                        String cssPath =
                            Objects.requireNonNull(getClass().getResource("/css/alert.css"))
                                .toExternalForm();
                        DialogPane dialogPane = alert.getDialogPane();
                        dialogPane.getStylesheets().add(cssPath);
                        dialogPane.getStyleClass().add("my-alert");

                        alert.showAndWait();

                        if (finalDerivationTreeText != null && !finalDerivationTreeText.isBlank()) {
                          showDerivationOverlay(finalDerivationTreeText);
                        }
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

  private String getSelectedDerivationTypeHeader() {
    Toggle selected = derivationTypeGroup != null ? derivationTypeGroup.getSelectedToggle() : null;
    if (selected == rbDerivationLimited) {
      return "LIMITED";
    }
    if (selected == rbDerivationSpecified) {
      return "DEPTH_SPECIFIED";
    }
    if (selected == rbDerivationInfinite) {
      return "INFINITE";
    }
    return null;
  }

  private Integer parseDepthOrNull(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    try {
      return Integer.parseInt(trimmed);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private String extractDerivationTreeFromZip(byte[] zipBytes) {
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String name = entry.getName();
        if (name != null && name.endsWith("_DerivationTree.txt")) {
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          byte[] buffer = new byte[8192];
          int read;
          while ((read = zis.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
          }
          return baos.toString(StandardCharsets.UTF_8);
        }
        zis.closeEntry();
      }
    } catch (Exception e) {
      log.warn("Could not extract derivation tree from ZIP: {}", e.getMessage());
    }
    return null;
  }

  private void showDerivationOverlay(String derivationText) {
    if (mainRoot == null) return;

    StackPane overlay = new StackPane();
    overlay.setId("derivationOverlay");
    overlay.setPickOnBounds(true);
    overlay.setStyle(
        "-fx-background-color: rgba(0,0,0,0.72); -fx-padding: 24px;");

    VBox panel = new VBox(12);
    panel.setMaxWidth(980);
    panel.setMaxHeight(680);
    panel.setPadding(new Insets(18));
    panel.setStyle(
        "-fx-background-color: rgba(15, 15, 18, 0.92);"
            + "-fx-background-radius: 14;"
            + "-fx-border-radius: 14;"
            + "-fx-border-color: rgba(255,255,255,0.18);"
            + "-fx-border-width: 1;"
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.75), 24, 0.25, 0, 8);");

    Label title = new Label("Derivation Tree");
    title.setStyle(
        "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;");

    Label subtitle = new Label("You can copy or save the full derivation as a .txt file.");
    subtitle.setStyle("-fx-text-fill: rgba(255,255,255,0.75); -fx-font-size: 12px;");

    TextArea textArea = new TextArea(derivationText);
    textArea.setEditable(false);
    textArea.setWrapText(false);
    textArea.setStyle(
        "-fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-control-inner-background: #0b0b0d; -fx-text-fill: #e8e8e8;");
    VBox.setVgrow(textArea, Priority.ALWAYS);

    Button btnCopy = new Button("Copy");
    Button btnSave = new Button("Save as .txt");
    Button btnClose = new Button("Close");

    // Keep button sizing but make them a bit cleaner
    btnCopy.setPrefSize(140, 38);
    btnSave.setPrefSize(160, 38);
    btnClose.setPrefSize(120, 38);

    btnCopy.setStyle("-fx-font-weight: bold;");
    btnSave.setStyle("-fx-font-weight: bold;");
    btnClose.setStyle("-fx-font-weight: bold;");

    btnCopy.setOnAction(
        e -> {
          Clipboard clipboard = Clipboard.getSystemClipboard();
          ClipboardContent content = new ClipboardContent();
          content.putString(derivationText);
          clipboard.setContent(content);
        });

    btnSave.setOnAction(
        e -> {
          FileChooser fc = new FileChooser();
          fc.setTitle("Save Derivation Tree");
          fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text File", "*.txt"));
          fc.setInitialFileName("DerivationTree.txt");
          File out =
              fc.showSaveDialog(
                  mainRoot.getScene() != null ? mainRoot.getScene().getWindow() : null);
          if (out != null) {
            try (OutputStream os = Files.newOutputStream(out.toPath())) {
              os.write(derivationText.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
              log.error("Failed to save derivation tree: {}", ex.getMessage());
            }
          }
        });

    btnClose.setOnAction(e -> mainRoot.getChildren().remove(overlay));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox buttons = new HBox(10, btnCopy, btnSave, spacer, btnClose);
    buttons.setAlignment(Pos.CENTER_LEFT);

    panel.getChildren().addAll(title, subtitle, textArea, buttons);
    overlay.getChildren().add(panel);
    StackPane.setAlignment(panel, Pos.CENTER);

    mainRoot.getChildren().add(overlay);
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

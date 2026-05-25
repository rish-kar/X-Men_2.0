package com.sermas.x.men.user_interface;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
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
import javafx.stage.StageStyle;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** X-Men User Interface Application Class. */
@Slf4j
public class XMenInterface extends Application {

  private MediaPlayer mediaPlayer;
  private File selectedFile; // Holds the selected file

  // Last mutation zip held in memory so the user can re-download it.
  private byte[] lastGeneratedZip;
  private String lastGeneratedZipName = "X-Men-Mutations.zip";
  private Button heroDownloadBtn;

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
  private CheckBox cbForget;
  private CheckBox cbNeglect;
  private CheckBox cbForgetHaskell;

  private ToggleGroup derivationTypeGroup;
  private RadioButton rbDerivationLimited;
  private RadioButton rbDerivationSpecified;
  private RadioButton rbDerivationInfinite;
  private TextField tfDerivationDepth;
  private CheckBox cbShowDerivationTree;

  private Button buttonUpload;
  private Button buttonStart;
  private Timeline chatIconPulse;

  private static final String message = "Error while performing mutation";

  /** Splash video resource (under src/main/resources). */
  private static final String SPLASH_VIDEO_RESOURCE = "X - Men 2.0.mp4";

  /** Design size — used as a minimum before maximize on the chosen monitor. */
  private static final double MAIN_WIDTH = 1280;
  private static final double MAIN_HEIGHT = 800;

  // Keep a reference to the root StackPane so we can show a glass overlay.
  private StackPane mainRoot;

  // Stage handle so dialogs can resolve owner positioning on the active monitor.
  private Stage primaryStage;

  // Hero logo — held so the theme switcher can repaint it.
  private ImageView heroLogo;

  private Button howItWorksButton;

  private static final String CHAT_ICON_WHITE = "/icons/chat-white.png";
  private static final String CHAT_ICON_BLACK = "/icons/chat-black.png";

  @Override
  public void start(Stage stage) {
    this.primaryStage = stage;
    // Ask the OS for a dark title bar / window chrome — best-effort, see WindowChrome.
    // Same hint applies to the splash and main windows because it's process-wide.
    WindowChrome.requestDarkChrome(stage);
    // Detect the screen the OS placed the stage on, fall back to primary.
    Rectangle2D screen = currentScreenBounds(stage);

    StackPane splashRoot = new StackPane();
    splashRoot.setStyle("-fx-background-color: black;");
    splashRoot.setPrefSize(screen.getWidth(), screen.getHeight());
    MediaPlayer splashPlayer = createSplashScreen(splashRoot, stage);
    Scene splashScene = new Scene(splashRoot, screen.getWidth(), screen.getHeight());
    stage.setScene(splashScene);
    stage.setTitle("X-Men 3.0");
    stage.setX(screen.getMinX());
    stage.setY(screen.getMinY());
    stage.setWidth(screen.getWidth());
    stage.setHeight(screen.getHeight());
    stage.show();

    stage.setOnCloseRequest(e -> shutdownEverything());

    stage.setIconified(false);
    stage.setAlwaysOnTop(true);

    // Extract the main-scene background MP4 to a temp file off the FX thread NOW, while
    // the splash is playing. By the time handOff() builds the main scene, the file is
    // already on disk and the MediaPlayer initialises instantly instead of stalling.
    MainSceneFactory.preWarmBackgroundVideo();

    final boolean[] handedOff = {false};
    Runnable handOff =
        () -> {
          if (handedOff[0]) return;
          handedOff[0] = true;
          if (splashPlayer != null) {
            try {
              splashPlayer.stop();
              splashPlayer.dispose();
            } catch (Exception ignored) {
            }
          }
          stage.setScene(createMainScene(stage));
          stage.setAlwaysOnTop(false);
          // Maximize on whichever monitor the stage is currently sitting on.
          Rectangle2D current = currentScreenBounds(stage);

          stage.setMaximized(false);
          stage.setX(current.getMinX());
          stage.setY(current.getMinY());
          stage.setWidth(current.getWidth());
          stage.setHeight(current.getHeight() + 10);
        };

    if (splashPlayer != null) {
      splashPlayer.setOnEndOfMedia(() -> Platform.runLater(handOff));
      splashPlayer.setOnError(() -> Platform.runLater(handOff));
    }
    PauseTransition safety = new PauseTransition(Duration.seconds(12));
    safety.setOnFinished(e -> handOff.run());
    safety.play();
  }

  /**
   * Find the screen that contains the stage's centre; fall back to the primary screen.
   * Multi-monitor safe.
   */
  static Rectangle2D currentScreenBounds(Stage stage) {
    if (stage != null && !Double.isNaN(stage.getX()) && !Double.isNaN(stage.getY())) {
      double cx = stage.getX() + (stage.getWidth() > 0 ? stage.getWidth() / 2.0 : 1);
      double cy = stage.getY() + (stage.getHeight() > 0 ? stage.getHeight() / 2.0 : 1);
      for (Screen s : Screen.getScreens()) {
        Rectangle2D b = s.getVisualBounds();
        if (b.contains(cx, cy)) return b;
      }
    }
    Screen primary = Screen.getPrimary();
    return primary != null ? primary.getVisualBounds() : new Rectangle2D(0, 0, MAIN_WIDTH, MAIN_HEIGHT);
  }

  @Override
  public void stop() {
    disposeMediaOnly();
  }

  private void disposeMediaOnly() {
    try {
      if (mediaPlayer != null) {
        mediaPlayer.stop();
        mediaPlayer.dispose();
        mediaPlayer = null;
      }
    } catch (Exception ignored) {
    }
  }

  private void shutdownEverything() {
    disposeMediaOnly();
    Platform.exit();
    System.exit(0);
  }

  private MediaPlayer createSplashScreen(StackPane splashRoot, Stage stage) {
    MediaPlayer splashPlayer = null;
    MediaView splashMediaView = new MediaView();
    boolean videoLoaded = false;

    try {
      InputStream videoStream = getClass().getResourceAsStream("/" + SPLASH_VIDEO_RESOURCE);
      if (videoStream == null) {
        throw new Exception("Splash video resource not found: " + SPLASH_VIDEO_RESOURCE);
      }
      File tempVideoFile = File.createTempFile("splash", ".mp4");
      tempVideoFile.deleteOnExit();
      Files.copy(videoStream, tempVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      Media splashMedia = new Media(tempVideoFile.toURI().toString());
      splashPlayer = new MediaPlayer(splashMedia);
      splashPlayer.setCycleCount(1);
      splashPlayer.setAutoPlay(true);
      splashPlayer.setMute(false);
      splashPlayer.setVolume(1.0);
      this.mediaPlayer = splashPlayer;

      splashMediaView.setMediaPlayer(splashPlayer);
      splashMediaView.setPreserveRatio(false);

      MediaPlayer mp = splashPlayer;
      Runnable applyCover = () -> {
        double vw = splashMedia.getWidth();
        double vh = splashMedia.getHeight();
        double winW = splashRoot.getWidth() > 0 ? splashRoot.getWidth() : MAIN_WIDTH;
        double winH = splashRoot.getHeight() > 0 ? splashRoot.getHeight() : MAIN_HEIGHT;
        if (vw <= 0 || vh <= 0) {
          splashMediaView.setFitWidth(winW);
          splashMediaView.setFitHeight(winH);
          return;
        }
        double scale = Math.max(winW / vw, winH / vh);
        splashMediaView.setFitWidth(vw * scale);
        splashMediaView.setFitHeight(vh * scale);
      };
      mp.setOnReady(applyCover);
      splashRoot.widthProperty().addListener((o, a, b) -> applyCover.run());
      splashRoot.heightProperty().addListener((o, a, b) -> applyCover.run());
      applyCover.run();

      splashRoot.setClip(new javafx.scene.shape.Rectangle(MAIN_WIDTH, MAIN_HEIGHT));
      splashRoot.layoutBoundsProperty().addListener((o, a, b) -> {
        javafx.scene.shape.Rectangle r =
            (javafx.scene.shape.Rectangle) splashRoot.getClip();
        if (r != null) {
          r.setWidth(b.getWidth());
          r.setHeight(b.getHeight());
        }
      });

      splashRoot.getChildren().add(splashMediaView);
      videoLoaded = true;
      log.info("Splash video '{}' loaded; playing at {}x{} with audio.",
          SPLASH_VIDEO_RESOURCE, MAIN_WIDTH, MAIN_HEIGHT);
    } catch (Exception e) {
      log.warn("Splash video '{}' not playable; falling back to image: {}",
          SPLASH_VIDEO_RESOURCE, e.getMessage());
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
    return splashPlayer;
  }

  private Scene createMainScene(Stage stage) {
    int serverPort = 8081;
    MainSceneFactory.Built built =
        MainSceneFactory.build(stage, serverPort, ignored -> openSettings(stage));

    this.mainRoot = built.root();
    this.heroLogo = built.logoView();

    GridPane checkboxPanel = setupGridPane(stage);
    checkboxPanel.setMaxWidth(Double.MAX_VALUE);

    Label panelTitle = new Label("Mutation Controls");
    panelTitle.getStyleClass().add("x-control-title");
    Label panelSub = new Label("Pick the mutations to generate, then hit Start.");
    panelSub.getStyleClass().add("x-control-sub");
    VBox panelHeader = new VBox(4, panelTitle, panelSub);

    VBox.setMargin(checkboxPanel, new Insets(52, 0, 0, 0));

    Button howItWorks = new Button();
    this.howItWorksButton = howItWorks;

    howItWorks.getStyleClass().addAll("x-cta-secondary", "x-chat-trigger");
    howItWorks.setText("");
    howItWorks.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
    setChatIcon(false, null);

    howItWorks.setOnAction(e -> ChatBotDialog.show(stage, howItWorks));
    Animations.hoverLift(howItWorks, 1.03);

    refreshChatIconFromServer(serverPort);

    Label chatButtonText = new Label("Chat with X-Men");
    chatButtonText.getStyleClass().add("x-chat-trigger-label");

    VBox chatButtonGroup = new VBox(6, howItWorks, chatButtonText);
    chatButtonGroup.setAlignment(Pos.CENTER);

    StackPane howItWorksWrap = new StackPane(chatButtonGroup);
    howItWorksWrap.getStyleClass().add("x-shadow-room");

    HBox panelFooter = new HBox(howItWorksWrap);
    panelFooter.getStyleClass().add("x-control-footer");

    VBox panelWrap = new VBox(14, panelHeader, checkboxPanel, panelFooter);
    panelWrap.getStyleClass().add("x-control-panel");
    panelWrap.setMaxWidth(Double.MAX_VALUE);
    VBox.setVgrow(checkboxPanel, Priority.ALWAYS);
    built.controlsHost().getChildren().add(panelWrap);

    Node heroStart = built.root().lookup("#heroStart");
    Node heroUpload = built.root().lookup("#heroUpload");
    Node heroDownload = built.root().lookup("#heroDownload");
    if (heroStart instanceof Button hs && buttonStart != null) {
      hs.setOnAction(e -> buttonStart.fire());
    }
    if (heroUpload instanceof Button hu && buttonUpload != null) {
      hu.setOnAction(e -> buttonUpload.fire());
    }
    if (heroDownload instanceof Button hd) {
      this.heroDownloadBtn = hd;
      hd.setVisible(false);
      hd.setManaged(false);
      hd.setOnAction(e -> downloadLastZip(stage));
    }

    Scene scene = new Scene(built.root(), MAIN_WIDTH, MAIN_HEIGHT);

    URL v2 = getClass().getResource("/css/main-v2.css");
    if (v2 != null) scene.getStylesheets().add(v2.toExternalForm());
    URL legacy = getClass().getResource("/css/main.css");
    if (legacy != null) scene.getStylesheets().add(legacy.toExternalForm());

    return scene;
  }

  private void setChatIcon(boolean lightTheme, com.sermas.x.men.config.ThemeCatalog.Theme theme) {
    if (howItWorksButton == null) return;

    String iconPath = CHAT_ICON_WHITE; // always use white icon

    try (InputStream is = getClass().getResourceAsStream(iconPath)) {
      if (is == null) return;

      ImageView icon = new ImageView(new Image(is));
      icon.setFitWidth(84);
      icon.setFitHeight(84);
      icon.setPreserveRatio(true);
      icon.setSmooth(true);
      icon.setMouseTransparent(true);

      boolean charcoalMono =
              theme != null
                      && theme.getId() != null
                      && theme.getId().equalsIgnoreCase("charcoal-mono");

      Color glowColor = Color.rgb(155, 93, 229, charcoalMono ? 0.55 : 1.0);

      if (theme != null && theme.getAccent() != null && !theme.getAccent().isBlank()) {
        Color accent = Color.web(theme.getAccent()).deriveColor(0, 1.0, 0.80, charcoalMono ? 0.55 : 1.0);
        glowColor = accent;
      }

      DropShadow glow = new DropShadow();
      glow.setColor(glowColor);
      glow.setRadius(charcoalMono ? 14 : 24);
      glow.setSpread(charcoalMono ? 0.25 : 0.55);

      icon.setEffect(glow);

      StackPane iconWrap = new StackPane(icon);
      iconWrap.setMinSize(96, 96);
      iconWrap.setPrefSize(96, 96);
      iconWrap.setMaxSize(96, 96);
      iconWrap.setMouseTransparent(true);

      if (chatIconPulse != null) {
        chatIconPulse.stop();
      }

      chatIconPulse =
              new Timeline(
                      new KeyFrame(
                              Duration.ZERO,
                              new KeyValue(glow.radiusProperty(), charcoalMono ? 10 : 18, Interpolator.EASE_BOTH),
                              new KeyValue(glow.spreadProperty(), charcoalMono ? 0.18 : 0.34, Interpolator.EASE_BOTH),
                              new KeyValue(iconWrap.scaleXProperty(), 0.98, Interpolator.EASE_BOTH),
                              new KeyValue(iconWrap.scaleYProperty(), 0.98, Interpolator.EASE_BOTH)),
                      new KeyFrame(
                              Duration.seconds(1.8),
                              new KeyValue(glow.radiusProperty(), charcoalMono ? 18 : 32, Interpolator.EASE_BOTH),
                              new KeyValue(glow.spreadProperty(), charcoalMono ? 0.32 : 0.52, Interpolator.EASE_BOTH),
                              new KeyValue(iconWrap.scaleXProperty(), 1.08, Interpolator.EASE_BOTH),
                              new KeyValue(iconWrap.scaleYProperty(), 1.08, Interpolator.EASE_BOTH)),
                      new KeyFrame(
                              Duration.seconds(3.6),
                              new KeyValue(glow.radiusProperty(), charcoalMono ? 10 : 18, Interpolator.EASE_BOTH),
                              new KeyValue(glow.spreadProperty(), charcoalMono ? 0.18 : 0.34, Interpolator.EASE_BOTH),
                              new KeyValue(iconWrap.scaleXProperty(), 0.98, Interpolator.EASE_BOTH),
                              new KeyValue(iconWrap.scaleYProperty(), 0.98, Interpolator.EASE_BOTH)));

      chatIconPulse.setCycleCount(Animation.INDEFINITE);
      chatIconPulse.play();

      howItWorksButton.setGraphic(iconWrap);
      howItWorksButton.setGraphicTextGap(0);

    } catch (Exception ignored) {
    }
  }

  private void refreshChatIcon(com.sermas.x.men.config.ThemeCatalog.Theme theme) {
    setChatIcon(ThemeLogo.isLightTheme(theme), theme);
  }

  private void refreshChatIconFromServer(int serverPort) {
    new Thread(
            () -> {
              try {
                OkHttpClient http = new OkHttpClient();

                Response r =
                        http.newCall(
                                        new Request.Builder()
                                                .url("http://localhost:" + serverPort + "/api/settings/themes/active")
                                                .build())
                                .execute();

                try (r) {
                  if (!r.isSuccessful() || r.body() == null) return;

                  ObjectMapper mapper = new ObjectMapper();
                  com.sermas.x.men.config.ThemeCatalog.Theme theme =
                          mapper.readValue(
                                  r.body().bytes(),
                                  com.sermas.x.men.config.ThemeCatalog.Theme.class);

                  Platform.runLater(() -> refreshChatIcon(theme));
                }
              } catch (Exception ignored) {
              }
            },
            "chat-icon-theme-init")
            .start();
  }

  private void openSettings(Stage stage) {
    int serverPort = 8081;
    SettingsDialog dialog =
        new SettingsDialog(
            serverPort,
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
                          refreshChatIcon(theme);
                        });
                  }
                }
              } catch (Exception ex) {
                log.warn("Failed to refresh theme: {}", ex.getMessage());
              }
            },
            prefs -> log.debug("UI preferences: {}", prefs),
            theme -> javafx.application.Platform.runLater(() -> ThemeLogo.apply(heroLogo, theme)));
    dialog.show(stage);
  }

  /**
   * Sets up the grid pane that contains the checkboxes and buttons. Also attaches event handlers
   * for uploading a file and starting the mutation.
   */
  private GridPane setupGridPane(Stage stage) {
    GridPane checkboxPanel = new GridPane();
    checkboxPanel.setHgap(20);
    checkboxPanel.setVgap(30);
    checkboxPanel.setAlignment(Pos.CENTER);

    buttonUpload = new Button("Upload File");
    buttonUpload.setId("buttonUpload");

    buttonStart = new Button("Start Mutation");
    buttonStart.setId("buttonStart");
    setupButton(buttonUpload);
    setupButton(buttonStart);

    buttonUpload.setOnAction(
        e -> {
          FileChooser fileChooser = new FileChooser();
          fileChooser.setTitle("Select a File to Upload");
          fileChooser
              .getExtensionFilters()
              .add(new FileChooser.ExtensionFilter("XML Files", "*.*"));
          File file = fileChooser.showOpenDialog(stage);
          if (file != null) {
            selectedFile = file;
            log.debug("Selected file: {}", file.getAbsolutePath());
          }
        });

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
              return;
            }
            selectedFile = picked;
            log.debug("Selected file via Start CTA: {}", picked.getAbsolutePath());
          }
          // Mutation runs FIRST. The profile auto-switch is best-effort and happens in
          // parallel — never blocking the mutation request. (The previous flow chained
          // detect→apply→save-profile→mutate sequentially, and any hiccup in those three
          // calls would silently swallow the mutation. That's the regression the user hit
          // with "Mutated files are not generating".)
          sendMutationRequest();
          final File toMutate = selectedFile;
          Thread t = new Thread(
              () -> autoSwitchProfileForFile(toMutate),
              "xmen-auto-switch-profile");
          t.setDaemon(true);
          t.start();
        });

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

    cbForgetHaskell = new CheckBox("Forget Mutation using external Haskell Script");
    cbForgetHaskell.setId("cbForgetHaskell");
    cbForgetHaskell.setDisable(true);
    cbForgetHaskell.setWrapText(true);
    cbForgetHaskell.setMaxWidth(220);

    derivationTypeGroup = new ToggleGroup();
    rbDerivationInfinite = new RadioButton("Infinite");
    rbDerivationInfinite.setId("rbDerivationInfinite");
    rbDerivationInfinite.setToggleGroup(derivationTypeGroup);
    rbDerivationInfinite.setDisable(true);

    rbDerivationSpecified = new RadioButton("Specified Depth");
    rbDerivationSpecified.setId("rbDerivationSpecified");
    rbDerivationSpecified.setToggleGroup(derivationTypeGroup);
    rbDerivationSpecified.setDisable(true);

    rbDerivationLimited = new RadioButton("Limited Depth");
    rbDerivationLimited.setId("rbDerivationLimited");
    rbDerivationLimited.setToggleGroup(derivationTypeGroup);
    rbDerivationLimited.setDisable(true);

    tfDerivationDepth = new TextField();
    tfDerivationDepth.setId("tfDerivationDepth");
    tfDerivationDepth.setPromptText("Depth");
    tfDerivationDepth.setMaxWidth(90);
    tfDerivationDepth.setDisable(true);

    cbShowDerivationTree = new CheckBox("Show derivation tree on screen");
    cbShowDerivationTree.setId("cbShowDerivationTree");
    cbShowDerivationTree.setDisable(true);

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

                // Default choice: Infinite (per spec).
                rbDerivationInfinite.setSelected(true);

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

    checkboxPanel.addRow(0, lblSkip, cbSkipS, cbSkipSR, cbSkipR);
    checkboxPanel.addRow(1, new Label(""), cbSkipRS, cbSkipRSR);
    checkboxPanel.addRow(2, lblReplace, cbSubmessages, cbType);
    checkboxPanel.addRow(3, lblAdd, cbAdd);
    checkboxPanel.addRow(4, lblCombine, cbCombineAddition, cbCombineOnly);

    checkboxPanel.addRow(5, lblForget, cbForget);

    // Infinite first, then Specified, then Limited (per spec).
    HBox derivationRadios =
        new HBox(20, rbDerivationInfinite, rbDerivationSpecified, rbDerivationLimited);
    derivationRadios.setAlignment(Pos.CENTER_LEFT);
    checkboxPanel.add(new Label(""), 0, 6);
    checkboxPanel.add(derivationRadios, 1, 6);
    GridPane.setColumnSpan(derivationRadios, 4);

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

    checkboxPanel.addRow(9, lblForgetHaskell, cbForgetHaskell);

    checkboxPanel.addRow(10, lblNeglect, cbNeglect);

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

    // Attach the legacy upload/start buttons to the panel but render them
    // invisible+unmanaged. The hero CTAs delegate to their onAction handlers,
    // and putting them in the scene graph lets tests resolve them by id.
    buttonUpload.setVisible(false);
    buttonUpload.setManaged(false);
    buttonStart.setVisible(false);
    buttonStart.setManaged(false);
    checkboxPanel.add(buttonUpload, 0, 11);
    checkboxPanel.add(buttonStart, 1, 11);

    return checkboxPanel;
  }

  private void setupButton(Button button) {
    button.setPrefSize(150, 40);
    button.getStyleClass().add("x-cta-secondary");
    GridPane.setMargin(button, new Insets(20, 0, 0, 0));
  }

  /**
   * Best-effort profile switching driven by the uploaded file's identifiers.
   *
   * <p>Runs on a daemon background thread so it never blocks the mutation request. The
   * sequence is:
   *
   * <ol>
   *   <li>Detect the file's vocabulary.
   *   <li>Fetch every existing profile, compare against the detected vocab.
   *   <li>If a profile already matches → activate it (no duplicate profile is created).
   *   <li>Otherwise → save a new profile named after the file basename and activate it.
   * </ol>
   *
   * <p>Any error along the way is logged and swallowed; mutations keep working with
   * whatever vocabulary is currently active.
   */
  @SuppressWarnings("unchecked")
  private boolean autoSwitchProfileForFile(File file) {
    if (file == null) return false;
    try {
      OkHttpClient client =
          new OkHttpClient.Builder()
              .connectTimeout(15, TimeUnit.SECONDS)
              .readTimeout(15, TimeUnit.SECONDS)
              .writeTimeout(15, TimeUnit.SECONDS)
              .build();

      // 1) Detect vocab from the uploaded file.
      okhttp3.RequestBody fileBody =
          okhttp3.RequestBody.create(file, okhttp3.MediaType.parse("text/plain"));
      okhttp3.RequestBody mp =
          new okhttp3.MultipartBody.Builder()
              .setType(okhttp3.MultipartBody.FORM)
              .addFormDataPart("file", file.getName(), fileBody)
              .build();
      String detectedJson;
      try (okhttp3.Response detect =
          client.newCall(
                  new okhttp3.Request.Builder()
                      .url("http://localhost:8081/api/settings/vocabulary/detect")
                      .post(mp)
                      .build())
              .execute()) {
        if (!detect.isSuccessful() || detect.body() == null) return false;
        detectedJson = detect.body().string();
      }

      com.fasterxml.jackson.databind.ObjectMapper jsonMapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      java.util.Map<String, Object> detectedMap =
          jsonMapper.readValue(detectedJson, java.util.Map.class);

      // 2) Pull the list of saved profiles.
      String profilesJson;
      try (okhttp3.Response listResp =
          client.newCall(
                  new okhttp3.Request.Builder()
                      .url("http://localhost:8081/api/settings/vocabulary/profiles")
                      .build())
              .execute()) {
        if (!listResp.isSuccessful() || listResp.body() == null) return false;
        profilesJson = listResp.body().string();
      }
      java.util.Map<String, Object> profileList = jsonMapper.readValue(profilesJson, java.util.Map.class);
      java.util.List<String> names =
          (java.util.List<String>) profileList.getOrDefault("profiles", java.util.List.of());

      // 3) Walk profiles; activate the first one whose vocab matches the detected one.
      for (String name : names) {
        try (okhttp3.Response activate =
            client.newCall(
                    new okhttp3.Request.Builder()
                        .url(
                            "http://localhost:8081/api/settings/vocabulary/profiles/"
                                + java.net.URLEncoder.encode(name, "UTF-8")
                                + "/activate")
                        .post(okhttp3.RequestBody.create(new byte[0]))
                        .build())
                .execute()) {
          if (!activate.isSuccessful() || activate.body() == null) continue;
          java.util.Map<String, Object> profileVocab =
              jsonMapper.readValue(activate.body().bytes(), java.util.Map.class);
          if (vocabsMatch(profileVocab, detectedMap)) {
            log.info("Auto-switch: existing profile '{}' matches uploaded file.", name);
            return true; // already activated by the GET — done.
          }
        }
      }

      // 4) No match — apply the detected vocab to live, then save it as a new profile.
      String basename = stripExtension(file.getName());
      okhttp3.RequestBody applyBody =
          okhttp3.RequestBody.create(detectedJson, okhttp3.MediaType.parse("application/json"));
      client
          .newCall(
              new okhttp3.Request.Builder()
                  .url("http://localhost:8081/api/settings/vocabulary")
                  .post(applyBody)
                  .build())
          .execute()
          .close();

      client
          .newCall(
              new okhttp3.Request.Builder()
                  .url(
                      "http://localhost:8081/api/settings/vocabulary/profiles/"
                          + java.net.URLEncoder.encode(basename, "UTF-8"))
                  .post(okhttp3.RequestBody.create(new byte[0]))
                  .build())
          .execute()
          .close();
      log.info("Auto-switch: created new profile '{}' for uploaded file.", basename);
      return true;
    } catch (Exception ex) {
      log.warn("Auto-switch failed (mutations were not affected): {}", ex.getMessage());
      return false;
    }
  }

  /**
   * Shallow equality between two vocabularies based on the fields the mutation engine
   * actually consults: outbound/inbound channels and the core-actions set. Descriptions
   * and irrelevant fields are ignored deliberately.
   */
  @SuppressWarnings("unchecked")
  private static boolean vocabsMatch(java.util.Map<String, Object> a, java.util.Map<String, Object> b) {
    if (a == null || b == null) return false;
    java.util.Map<String, Object> factsA =
        (java.util.Map<String, Object>) a.getOrDefault("facts", java.util.Map.of());
    java.util.Map<String, Object> factsB =
        (java.util.Map<String, Object>) b.getOrDefault("facts", java.util.Map.of());
    if (!asSet(factsA.get("outbound-channels")).equals(asSet(factsB.get("outbound-channels")))) {
      return false;
    }
    if (!asSet(factsA.get("inbound-channels")).equals(asSet(factsB.get("inbound-channels")))) {
      return false;
    }
    java.util.Map<String, Object> actA =
        (java.util.Map<String, Object>) a.getOrDefault("actions", java.util.Map.of());
    java.util.Map<String, Object> actB =
        (java.util.Map<String, Object>) b.getOrDefault("actions", java.util.Map.of());
    return asSet(actA.get("core-actions")).equals(asSet(actB.get("core-actions")));
  }

  @SuppressWarnings("unchecked")
  private static java.util.Set<String> asSet(Object o) {
    if (o instanceof java.util.List<?> l) {
      java.util.Set<String> s = new java.util.LinkedHashSet<>();
      for (Object x : l) if (x != null) s.add(String.valueOf(x));
      return s;
    }
    return java.util.Set.of();
  }

  private static String stripExtension(String filename) {
    if (filename == null) return "Imported";
    int dot = filename.lastIndexOf('.');
    String base = dot > 0 ? filename.substring(0, dot) : filename;
    return base.replaceAll("[^A-Za-z0-9._ -]", "_");
  }

  private void sendMutationRequest() {
    OkHttpClient client =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.MINUTES)
            .readTimeout(30, TimeUnit.MINUTES)
            .build();

    MediaType mediaType = MediaType.parse("application/octet-stream");
    RequestBody fileBody = RequestBody.create(selectedFile, mediaType);

    MultipartBody.Builder multipartBuilder =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", selectedFile.getName(), fileBody);

    String apiBaseUrl =
        System.getProperty(
            "API_BASE_URL", System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8081"));

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

    if (cbSkipS.isSelected()) requestBuilder.addHeader("Skip-Send", "true");
    if (cbSkipR.isSelected()) requestBuilder.addHeader("Skip-Receive", "true");
    if (cbSkipSR.isSelected()) requestBuilder.addHeader("Skip-Send-Receive", "true");
    if (cbSkipRS.isSelected()) requestBuilder.addHeader("Skip-Receive-Send", "true");
    if (cbSkipRSR.isSelected()) requestBuilder.addHeader("Skip-Receive-Send-Receive", "true");
    if (cbAdd.isSelected()) requestBuilder.addHeader("Add-Mutation", "true");
    if (cbSubmessages.isSelected()) requestBuilder.addHeader("Replace-Sub-Messages", "true");
    if (cbType.isSelected()) requestBuilder.addHeader("Replace-Type", "true");
    if (cbForget.isSelected()) {
      requestBuilder.addHeader("Forget-Mutation", "true");

      if (cbForgetHaskell != null && cbForgetHaskell.isSelected()) {
        requestBuilder.addHeader("Haskell-Activate", "true");
      }

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
                    () ->
                        ThemedDialog.show(
                            primaryStage,
                            ThemedDialog.Kind.ERROR,
                            "Mutation Request Failed",
                            "Error: " + message));
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

                  // Remember the zip so the user can download it.
                  lastGeneratedZip = bodyBytes;
                  lastGeneratedZipName = suggestedZipName(selectedFile);

                  String finalDerivationTreeText = derivationTreeText;
                  Platform.runLater(
                      () -> {
                        if (heroDownloadBtn != null) {
                          heroDownloadBtn.setVisible(true);
                          heroDownloadBtn.setManaged(true);
                        }
                        ThemedDialog.show(
                            primaryStage,
                            ThemedDialog.Kind.SUCCESS,
                            "Mutation Generation Succeeded",
                            "Your file is ready. Use Download to save a zip of the generated mutations.");

                        if (finalDerivationTreeText != null && !finalDerivationTreeText.isBlank()) {
                          showDerivationOverlay(finalDerivationTreeText);
                        }
                      });
                } else {
                  log.error("Error: {} {}", response.code(), response.message());

                  String responseBodyStr = response.body() != null ? response.body().string() : "";

                  if (responseBodyStr.contains("Forget function not found")) {
                    Platform.runLater(
                        () ->
                            ThemedDialog.show(
                                primaryStage,
                                ThemedDialog.Kind.ERROR,
                                "Forget Function Error",
                                "Forget function not found in the input code"));
                  } else {
                    Platform.runLater(
                        () ->
                            ThemedDialog.show(
                                primaryStage,
                                ThemedDialog.Kind.ERROR,
                                "Error",
                                "Error: " + message));
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

  private String suggestedZipName(File source) {
    if (source == null) return "X-Men-Mutations.zip";
    String n = source.getName();
    int dot = n.lastIndexOf('.');
    String base = dot > 0 ? n.substring(0, dot) : n;
    return base + "-Mutations.zip";
  }

  /** Save the last generated zip to disk via a FileChooser. */
  private void downloadLastZip(Stage stage) {
    if (lastGeneratedZip == null || lastGeneratedZip.length == 0) {
      ThemedToast.show(stage, "No mutation output to download yet.");
      return;
    }
    FileChooser fc = new FileChooser();
    fc.setTitle("Save Mutation Output");
    fc.setInitialFileName(lastGeneratedZipName);
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip Archive", "*.zip"));
    File out = fc.showSaveDialog(stage);
    if (out == null) return;
    try (OutputStream os = Files.newOutputStream(out.toPath())) {
      os.write(lastGeneratedZip);
      ThemedToast.show(stage, "Saved " + out.getName());
    } catch (IOException ex) {
      log.error("Failed to save zip: {}", ex.getMessage());
      ThemedDialog.show(stage, ThemedDialog.Kind.ERROR, "Save failed", ex.getMessage());
    }
  }

  private void showDerivationOverlay(String derivationText) {
    if (mainRoot == null) return;

    StackPane overlay = new StackPane();
    overlay.setId("derivationOverlay");
    overlay.setPickOnBounds(true);
    overlay.getStyleClass().add("x-derivation-overlay");

    VBox panel = new VBox(12);
    panel.getStyleClass().add("x-derivation-panel");
    panel.setMaxWidth(980);
    panel.setMaxHeight(680);
    panel.setPadding(new Insets(18));

    Label title = new Label("Derivation Tree");
    title.getStyleClass().add("x-derivation-title");

    Label subtitle = new Label("Copy, save as .txt, or download the full mutation zip.");
    subtitle.getStyleClass().add("x-derivation-sub");

    TextArea textArea = new TextArea(derivationText);
    textArea.setEditable(false);
    textArea.setWrapText(false);
    textArea.getStyleClass().add("x-derivation-text");
    VBox.setVgrow(textArea, Priority.ALWAYS);

    Button btnCopy = new Button("Copy");
    btnCopy.getStyleClass().add("x-cta-secondary");
    Button btnSave = new Button("Save as .txt");
    btnSave.getStyleClass().add("x-cta-secondary");
    Button btnDownload = new Button("Download Zip");
    btnDownload.getStyleClass().add("x-cta-primary");
    Button btnClose = new Button("Close");
    btnClose.getStyleClass().add("x-cta-secondary");

    btnCopy.setOnAction(
        e -> {
          Clipboard clipboard = Clipboard.getSystemClipboard();
          ClipboardContent content = new ClipboardContent();
          content.putString(derivationText);
          clipboard.setContent(content);
          ThemedToast.show(primaryStage, "Copied derivation tree.");
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
              ThemedToast.show(primaryStage, "Saved " + out.getName());
            } catch (IOException ex) {
              log.error("Failed to save derivation tree: {}", ex.getMessage());
            }
          }
        });

    btnDownload.setOnAction(e -> downloadLastZip(primaryStage));
    btnDownload.setDisable(lastGeneratedZip == null || lastGeneratedZip.length == 0);

    btnClose.setOnAction(e -> mainRoot.getChildren().remove(overlay));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox buttons = new HBox(10, btnCopy, btnSave, btnDownload, spacer, btnClose);
    buttons.setAlignment(Pos.CENTER_LEFT);

    panel.getChildren().addAll(title, subtitle, textArea, buttons);
    overlay.getChildren().add(panel);
    StackPane.setAlignment(panel, Pos.CENTER);

    mainRoot.getChildren().add(overlay);
  }

  public static void main(String[] args) {
    launch(args);
  }
}

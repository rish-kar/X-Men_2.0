package com.sermas.x.men.user_interface;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sermas.x.men.config.ThemeCatalog.Theme;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Builds the main scene.
 *
 * <p>Layout: 35% hero on the left, 65% mutation glass panel on the right. The settings button
 * sits in the bottom-left, the logo in the top-left of the hero. The background video uses a
 * cached temp file plus async rate setup to stay smooth even while modal dialogs are open.
 */
@Slf4j
public final class MainSceneFactory {

  /** Cache the temp video file across rebuilds so we don't re-extract on every scene change. */
  private static File cachedBackgroundFile;

  private MainSceneFactory() {}

  public record Built(
      StackPane root,
      Node background,
      Pane overlay,
      BorderPane content,
      StackPane controlsHost,
      Button settingsButton,
      ImageView logoView) {}

  public static Built build(Stage stage, int serverPort, Consumer<Void> onSettingsRequested) {
    StackPane root = new StackPane();
    root.getStyleClass().add("x-root");

    Node background = buildBackground(stage);

    Pane overlay = new Pane();
    overlay.getStyleClass().add("x-overlay");
    overlay.setPickOnBounds(false);

    HBox body = new HBox();
    body.setFillHeight(true);
    body.setPickOnBounds(false);

    LogoSlot slot = buildLogoSlot();
    VBox heroLeft = buildHeroLeft(slot.container);
    StackPane controlsHost = buildControlsHost();

    HBox.setHgrow(heroLeft, Priority.ALWAYS);
    HBox.setHgrow(controlsHost, Priority.ALWAYS);
    heroLeft.setMaxWidth(Double.MAX_VALUE);
    heroLeft.setMinWidth(420);
    heroLeft.setPrefWidth(520);
    controlsHost.setMaxWidth(Double.MAX_VALUE);
    controlsHost.setMinWidth(640);
    controlsHost.setPrefWidth(960);

    body.getChildren().addAll(heroLeft, controlsHost);

    // Settings button — bottom-left corner with shadow room so the drop-shadow isn't clipped.
    Button settings = buildSettingsButton(onSettingsRequested);
    StackPane settingsHost = new StackPane(settings);
    settingsHost.getStyleClass().add("x-shadow-room");
    settingsHost.setAlignment(Pos.BOTTOM_LEFT);
    settingsHost.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    StackPane.setAlignment(settingsHost, Pos.BOTTOM_LEFT);
    StackPane.setMargin(settingsHost, new Insets(0, 0, 0, 18));

    BorderPane content = new BorderPane();
    content.setPickOnBounds(false);
    content.setCenter(body);

    root.getChildren().addAll(background, overlay, content, settingsHost);

    // Entrance animation
    fadeInScene(root);

    pullInitialTheme(
        serverPort,
        theme ->
            javafx.application.Platform.runLater(
                () -> {
                  ThemeApplier.apply(root, theme);
                  ThemeLogo.apply(slot.imageView, theme);
                }));

    return new Built(root, background, overlay, content, controlsHost, settings, slot.imageView);
  }

  /* ------------------------------------------------------------------ */
  /*  Background                                                        */
  /* ------------------------------------------------------------------ */

  private static Node buildBackground(Stage stage) {
    StackPane container = new StackPane();
    Rectangle2D screen = Screen.getPrimary().getVisualBounds();
    container.setPrefSize(screen.getWidth(), screen.getHeight());

    try {
      File tmp = ensureCachedVideo();
      if (tmp != null) {
        Media media = new Media(tmp.toURI().toString());
        MediaPlayer player = new MediaPlayer(media);
        player.setCycleCount(MediaPlayer.INDEFINITE);
        player.setMute(true);
        player.setAutoPlay(true);
        // Smoother loop — explicitly tell the player to start fresh on cycle.
        player.setOnEndOfMedia(() -> {
          player.seek(Duration.ZERO);
          player.play();
        });
        MediaView view = new MediaView(player);
        view.setPreserveRatio(false);
        view.setSmooth(true);
        view.fitWidthProperty().bind(container.widthProperty());
        view.fitHeightProperty().bind(container.heightProperty());
        player.setOnReady(
            () -> {
              stage.setWidth(screen.getWidth());
              stage.setHeight(screen.getHeight());
              stage.centerOnScreen();
              player.play();
            });
        // If the player ever errors out, log and fall back to the still image.
        player.setOnError(() -> log.warn("MediaPlayer error: {}", player.getError()));
        container.getChildren().add(view);
        return container;
      }
    } catch (Exception e) {
      log.info("Background video missing or failed; falling back to image: {}", e.getMessage());
    }

    try (InputStream img =
        MainSceneFactory.class.getResourceAsStream("/images/main_scene_dna_fallback.png")) {
      if (img != null) {
        ImageView iv = new ImageView(new Image(img));
        iv.setPreserveRatio(false);
        iv.fitWidthProperty().bind(container.widthProperty());
        iv.fitHeightProperty().bind(container.heightProperty());
        container.getChildren().add(iv);
      }
    } catch (Exception ignored) {
    }
    return container;
  }

  /** Extract the bundled MP4 to a temp file once, then reuse the file across rebuilds. */
  private static synchronized File ensureCachedVideo() throws IOException {
    if (cachedBackgroundFile != null && cachedBackgroundFile.exists()) {
      return cachedBackgroundFile;
    }
    try (InputStream videoStream =
        MainSceneFactory.class.getResourceAsStream("/DNA-Background.mp4")) {
      if (videoStream == null) return null;
      File tmp = File.createTempFile("xmen-bg", ".mp4");
      tmp.deleteOnExit();
      Files.copy(videoStream, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
      cachedBackgroundFile = tmp;
      return tmp;
    }
  }

  /* ------------------------------------------------------------------ */
  /*  Hero column                                                       */
  /* ------------------------------------------------------------------ */

  /** Logo container + the (possibly null) ImageView we re-paint when the theme changes. */
  private record LogoSlot(HBox container, ImageView imageView) {}

  /** Logo width: 265 (prev) × 1.25 = 331 per latest design feedback. */
  private static final double LOGO_WIDTH = 331;

  private static LogoSlot buildLogoSlot() {
    // Default to the dark-theme (white) logo until the active theme is resolved.
    ImageView iv = null;
    try (InputStream is = MainSceneFactory.class.getResourceAsStream("/images/White.png")) {
      if (is != null) {
        iv = new ImageView(new Image(is));
        iv.setFitWidth(LOGO_WIDTH);
        iv.setPreserveRatio(true);
        iv.getStyleClass().add("x-logo-img");
      }
    } catch (Exception ignored) {
    }
    HBox wrap = new HBox();
    wrap.getStyleClass().add("x-logo-wrap");
    wrap.setAlignment(Pos.CENTER);
    // Push the logo block further down inside the hero without shifting the
    // rest of the column (translateY does not affect layout of siblings).
    wrap.setTranslateY(60);
    if (iv != null) {
      wrap.getChildren().add(iv);
    } else {
      Pane empty = new Pane();
      empty.getStyleClass().add("x-logo-slot");
      wrap.getChildren().add(empty);
    }
    return new LogoSlot(wrap, iv);
  }

  /**
   * The hero left column. Logo + "Mutate. Analyse. Prove." sit in a centred group
   * (x-hero-center) so they line up horizontally with each other. Description, CTAs and
   * tagline are left-aligned below.
   */
  private static VBox buildHeroLeft(HBox logoWrap) {
    VBox col = new VBox();
    col.getStyleClass().add("x-hero-left");
    col.setMaxWidth(Region.USE_PREF_SIZE);

    Text title = new Text("Mutate.\nAnalyse.\nProve.");
    title.getStyleClass().add("x-hero-title");
    title.setTextAlignment(javafx.scene.text.TextAlignment.LEFT);

    // centerGroup hugs the title's width so the (narrower) logo sits centred
    // above the title, while the group itself anchors to the left of the hero.
    VBox centerGroup = new VBox(8, logoWrap, title);
    centerGroup.getStyleClass().add("x-hero-center");
    centerGroup.setAlignment(Pos.CENTER);
    centerGroup.setMaxWidth(Region.USE_PREF_SIZE);
    centerGroup.setFillWidth(false);

    Label sub =
        new Label(
            "Turn ceremony specifications into actionable\n"
                + "mutation traces. X-Men models the human in\n"
                + "the loop — forgetting, slipping, mistyping —\n"
                + "and feeds Tamarin the variants that matter.");
    sub.getStyleClass().add("x-hero-sub");
    sub.setWrapText(true);
    sub.setMinHeight(Region.USE_PREF_SIZE);
    sub.setMaxWidth(Region.USE_PREF_SIZE);
    sub.setMinWidth(Region.USE_PREF_SIZE);

    Button startBtn = new Button("Start Mutation");
    startBtn.getStyleClass().add("x-cta-primary");
    startBtn.setId("heroStart");
    Animations.hoverLift(startBtn, 1.04);

    Button uploadBtn = new Button("Upload File");
    uploadBtn.getStyleClass().add("x-cta-secondary");
    uploadBtn.setId("heroUpload");
    Animations.hoverLift(uploadBtn, 1.03);
    uploadBtn.prefWidthProperty().bind(startBtn.widthProperty());
    uploadBtn.prefHeightProperty().bind(startBtn.heightProperty());
    uploadBtn.minWidthProperty().bind(startBtn.widthProperty());
    uploadBtn.minHeightProperty().bind(startBtn.heightProperty());

    StackPane startWrap = new StackPane(startBtn);
    StackPane uploadWrap = new StackPane(uploadBtn);
    startWrap.getStyleClass().add("x-shadow-room");
    uploadWrap.getStyleClass().add("x-shadow-room");

    // Buttons share one row, centred horizontally beneath the "Turn ceremony…"
    // description block. The wrapper has the same maxWidth as the sub label so
    // the centred CTAs sit under that text rather than the whole column.
    HBox ctas = new HBox(6, startWrap, uploadWrap);
    ctas.setAlignment(Pos.CENTER_LEFT);
    ctas.setMaxWidth(500);
    ctas.setTranslateX(-20);
    VBox.setMargin(ctas, new javafx.geometry.Insets(0, 0, 0, 0));

    Label tagline =
        new Label(
            "Exploring formal-methods workflows for ceremony designers who aim higher.");
    tagline.getStyleClass().add("x-hero-tagline");
    tagline.setWrapText(true);
    tagline.setMaxWidth(540);
    tagline.setTextAlignment(javafx.scene.text.TextAlignment.LEFT);
    tagline.setAlignment(Pos.CENTER_LEFT);
    VBox.setMargin(tagline, new javafx.geometry.Insets(0, 0, 0, 20));
    tagline.translateYProperty().bind(col.heightProperty().multiply(-0.04));
    tagline.translateXProperty().bind(col.widthProperty().multiply(-0.02));

    col.getChildren().addAll(centerGroup, sub, ctas, tagline);
    return col;
  }

  /* ------------------------------------------------------------------ */
  /*  Right column (mutation glass panel slot)                          */
  /* ------------------------------------------------------------------ */

  private static StackPane buildControlsHost() {
    StackPane wrap = new StackPane();
    wrap.getStyleClass().add("x-controls-wrap");
    wrap.setPickOnBounds(false);
    return wrap;
  }

  /* ------------------------------------------------------------------ */
  /*  Settings button (bottom-left) with proper SVG gear                */
  /* ------------------------------------------------------------------ */

  private static Button buildSettingsButton(Consumer<Void> onClick) {
    Button btn = new Button("Settings");
    btn.getStyleClass().add("x-settings-btn");
    Node icon = Icons.gear(20, Color.WHITE);
    btn.setGraphic(icon);
    btn.setOnAction(
        e -> {
          if (onClick != null) onClick.accept(null);
        });
    Animations.hoverLift(btn, 1.04);
    return btn;
  }

  /* ------------------------------------------------------------------ */
  /*  Animations                                                        */
  /* ------------------------------------------------------------------ */

  private static void fadeInScene(StackPane root) {
    FadeTransition fade = new FadeTransition(Duration.millis(420), root);
    fade.setFromValue(0.0);
    fade.setToValue(1.0);
    TranslateTransition slide = new TranslateTransition(Duration.millis(420), root);
    slide.setFromY(12);
    slide.setToY(0);
    new ParallelTransition(fade, slide).play();
  }

  /* ------------------------------------------------------------------ */
  /*  Initial theme fetch                                               */
  /* ------------------------------------------------------------------ */

  private static void pullInitialTheme(int serverPort, Consumer<Theme> sink) {
    new Thread(
            () -> {
              try {
                OkHttpClient http = new OkHttpClient();
                Response r =
                    http.newCall(
                            new Request.Builder()
                                .url(
                                    "http://localhost:"
                                        + serverPort
                                        + "/api/settings/themes/active")
                                .build())
                        .execute();
                try (r) {
                  if (!r.isSuccessful() || r.body() == null) return;
                  ObjectMapper json = new ObjectMapper();
                  Theme theme = json.readValue(r.body().bytes(), Theme.class);
                  sink.accept(theme);
                }
              } catch (Exception e) {
                log.debug(
                    "Could not fetch initial theme (server not ready?): {}", e.getMessage());
              }
            },
            "theme-init")
        .start();
  }
}

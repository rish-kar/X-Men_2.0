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

  /**
   * Kicks off video extraction on a background thread so the file is ready by the time
   * {@link #buildBackground(Stage)} is called from the splash hand-off. Safe to call from any
   * thread; safe to call multiple times (the underlying op is synchronized + idempotent).
   *
   * <p>This is the single most effective fix for "main-screen video stuck on startup": the
   * MP4 extraction (~MBs of I/O) no longer happens on the FX thread during scene swap.
   */
  public static void preWarmBackgroundVideo() {
    Thread t = new Thread(() -> {
      try {
        ensureCachedVideo();
      } catch (IOException e) {
        log.debug("Background video pre-warm skipped: {}", e.getMessage());
      }
    }, "xmen-bg-prewarm");
    t.setDaemon(true);
    t.start();
  }

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

    // Subtle smoky atmosphere over the background video. Mouse-transparent so
    // it doesn't intercept clicks. The radial gradients in .x-smoke shift
    // very gently — combined with the looping video this reads as drifting
    // haze rather than a static tint.
    Pane smoke = new Pane();
    smoke.getStyleClass().add("x-smoke");
    smoke.setMouseTransparent(true);
    smoke.setPickOnBounds(false);
    animateSmoke(smoke);

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

    // Bottom-left cluster: Settings gear + (when mutation succeeds) Download button.
    // Pushed away from the hero tagline (bottom inset) so the cluster doesn't sit flush
    // against the "Exploring Formal-methods" text.
    Button settings = buildSettingsButton(onSettingsRequested);
    StackPane settingsShadowRoom = new StackPane(settings);
    settingsShadowRoom.getStyleClass().add("x-shadow-room");
    settingsShadowRoom.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

    StackPane downloadShadowRoom = buildDownloadCorner(); // contains halo + #heroDownload button

    HBox bottomLeftCluster = new HBox(8, settingsShadowRoom, downloadShadowRoom);
    bottomLeftCluster.setAlignment(Pos.BOTTOM_LEFT);
    bottomLeftCluster.setPickOnBounds(false);
    bottomLeftCluster.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    StackPane.setAlignment(bottomLeftCluster, Pos.BOTTOM_LEFT);
    StackPane.setMargin(bottomLeftCluster, new Insets(0, 0, 2, 18));

    BorderPane content = new BorderPane();
    content.setPickOnBounds(false);
    content.setCenter(body);

    root.getChildren().addAll(background, overlay, smoke, content, bottomLeftCluster);

    // Pause the looping smoke/logo Timelines whenever the stage is iconified so we
    // don't burn CPU rendering offscreen frames. Resumes automatically when restored.
    stage.iconifiedProperty().addListener((obs, was, now) -> {
      javafx.animation.Animation.Status target =
          now ? javafx.animation.Animation.Status.PAUSED : javafx.animation.Animation.Status.RUNNING;
      walkTimelines(root, target);
    });

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
    // Pick the monitor the stage currently lives on instead of always the
    // primary screen — multi-monitor users still see the video sized to the
    // window they have X-Men open on.
    Rectangle2D screen = screenForStage(stage);
    container.setPrefSize(screen.getWidth(), screen.getHeight());
    ImageView fallback = buildBackgroundFallback(container);
    if (fallback != null) {
      container.getChildren().add(fallback);
    }

    try {
      File tmp = ensureCachedVideo();
      if (tmp != null) {
        Media media = new Media(tmp.toURI().toString());
        MediaPlayer player = new MediaPlayer(media);
        player.setCycleCount(MediaPlayer.INDEFINITE);
        player.setMute(true);
        player.setAutoPlay(true);
        player.setOnReady(() -> javafx.application.Platform.runLater(player::play));
        player.setOnPlaying(
            () -> {
              if (fallback != null) fallback.setVisible(false);
            });
        player.statusProperty()
            .addListener(
                (obs, oldStatus, status) -> {
                  if (fallback != null && status != MediaPlayer.Status.PLAYING) {
                    fallback.setVisible(true);
                  }
                });
        player.setOnStalled(
            () -> {
              if (fallback != null) fallback.setVisible(true);
              player.play();
            });
        // Smoother loop — explicitly tell the player to start fresh on cycle.
        player.setOnEndOfMedia(() -> {
          player.seek(Duration.ZERO);
          player.play();
        });
        MediaView view = new MediaView(player);
        view.setPreserveRatio(false);
        view.setSmooth(false);
        view.fitWidthProperty().bind(container.widthProperty());
        view.fitHeightProperty().bind(container.heightProperty());
        // Stage sizing is handled in the splash hand-off (XMenInterface) BEFORE the scene
        // is shown — doing it again here triggered an extra layout pass right as the
        // MediaPlayer transitioned to PLAYING, which is what made the video look stuck.
        player.setOnError(
            () -> {
              if (fallback != null) fallback.setVisible(true);
              log.warn("MediaPlayer error: {}", player.getError());
            });
        if (stage != null) {
          stage.iconifiedProperty()
              .addListener(
                  (obs, was, iconified) -> {
                    if (iconified) {
                      player.pause();
                    } else {
                      player.play();
                    }
                  });
          stage.showingProperty()
              .addListener(
                  (obs, was, showing) -> {
                    if (!showing) player.dispose();
                  });
        }
        container.getChildren().add(0, view);
        container.getProperties().put("xmen.backgroundMediaPlayer", player);
        return container;
      }
    } catch (Exception e) {
      log.info("Background video missing or failed; falling back to image: {}", e.getMessage());
    }

    return container;
  }

  private static ImageView buildBackgroundFallback(StackPane container) {
    try (InputStream img =
        MainSceneFactory.class.getResourceAsStream("/images/main_scene_dna_fallback.png")) {
      if (img != null) {
        ImageView iv = new ImageView(new Image(img));
        iv.setPreserveRatio(false);
        iv.setSmooth(false);
        iv.fitWidthProperty().bind(container.widthProperty());
        iv.fitHeightProperty().bind(container.heightProperty());
        return iv;
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  /** Pick the screen containing the stage's centre. Falls back to primary. */
  private static Rectangle2D screenForStage(Stage stage) {
    if (stage != null && !Double.isNaN(stage.getX()) && !Double.isNaN(stage.getY())) {
      double cx = stage.getX() + (stage.getWidth() > 0 ? stage.getWidth() / 2.0 : 1);
      double cy = stage.getY() + (stage.getHeight() > 0 ? stage.getHeight() / 2.0 : 1);
      for (Screen s : Screen.getScreens()) {
        Rectangle2D b = s.getVisualBounds();
        if (b.contains(cx, cy)) return b;
      }
    }
    Screen primary = Screen.getPrimary();
    return primary != null ? primary.getVisualBounds() : new Rectangle2D(0, 0, 1280, 800);
  }

  /**
   * Extract the bundled MP4 to a stable temp path. Stable filename means subsequent JVM starts
   * reuse the already-extracted file (skip the disk write on every cold start).
   */
  private static synchronized File ensureCachedVideo() throws IOException {
    if (cachedBackgroundFile != null && cachedBackgroundFile.exists()) {
      return cachedBackgroundFile;
    }
    File stable = new File(System.getProperty("java.io.tmpdir"), "xmen-bg-cache.mp4");
    long expectedLength = backgroundVideoResourceLength();
    // Reuse the file across JVM restarts if a previous run already wrote it AND it isn't empty.
    if (stable.exists()
        && stable.length() > 0
        && (expectedLength <= 0 || stable.length() == expectedLength)) {
      cachedBackgroundFile = stable;
      return stable;
    }
    try (InputStream videoStream =
        MainSceneFactory.class.getResourceAsStream("/DNA-Background.mp4")) {
      if (videoStream == null) return null;
      Files.copy(videoStream, stable.toPath(), StandardCopyOption.REPLACE_EXISTING);
      cachedBackgroundFile = stable;
      return stable;
    }
  }

  private static long backgroundVideoResourceLength() {
    try {
      java.net.URL url = MainSceneFactory.class.getResource("/DNA-Background.mp4");
      if (url == null) return -1;
      return url.openConnection().getContentLengthLong();
    } catch (IOException e) {
      return -1;
    }
  }

  /* ------------------------------------------------------------------ */
  /*  Hero column                                                       */
  /* ------------------------------------------------------------------ */

  /** Logo container + the (possibly null) ImageView we re-paint when the theme changes. */
  private record LogoSlot(HBox container, ImageView imageView) {}

  /** Logo width: 331 (prev) × 1.10 ≈ 364 per latest design feedback. */
  private static final double LOGO_WIDTH = 364;

  private static LogoSlot buildLogoSlot() {
    // Single brand mark used across every theme — see ThemeLogo.
    ImageView iv = null;
    try (InputStream is = MainSceneFactory.class.getResourceAsStream("/images/SERMAS Classic.png")) {
      if (is != null) {
        // setSmooth + setCache → JavaFX uses a higher-quality scaling filter
        // and caches the rasterised result. Together they remove the
        // jaggies on the diagonal edges of the wordmark.
        Image img = new Image(is, LOGO_WIDTH * 2, 0, true, true);
        iv = new ImageView(img);
        iv.setFitWidth(LOGO_WIDTH);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.setCache(true);
        iv.getStyleClass().add("x-logo-img");

        // Cinematic directional lighting: a single distant key light angled
        // from the upper-left (azimuth 135°, elevation 35°). The surface
        // scale is small so the logo still reads as a clean wordmark — we
        // just want a subtle "lit from above" feel, not heavy embossing.
        javafx.scene.effect.Light.Distant key =
            new javafx.scene.effect.Light.Distant();
        key.setAzimuth(135);
        key.setElevation(35);
        key.setColor(Color.WHITE);
        javafx.scene.effect.Lighting lighting =
            new javafx.scene.effect.Lighting(key);
        lighting.setSurfaceScale(1.2);
        lighting.setDiffuseConstant(1.35);
        lighting.setSpecularConstant(0.45);
        lighting.setSpecularExponent(22);
        iv.setEffect(lighting);
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
      double glowRadius = LOGO_WIDTH * 0.72;
      javafx.scene.shape.Circle glow = new javafx.scene.shape.Circle(glowRadius);
      glow.getStyleClass().add("x-logo-glow");
      glow.setMouseTransparent(true);
      glow.setEffect(new javafx.scene.effect.GaussianBlur(60));
      glow.setOpacity(0.34);
      glow.setManaged(false);

      javafx.animation.Timeline pulse =
              new javafx.animation.Timeline(
                      new javafx.animation.KeyFrame(
                              Duration.ZERO,
                              new javafx.animation.KeyValue(glow.opacityProperty(), 0.26, javafx.animation.Interpolator.EASE_BOTH),
                              new javafx.animation.KeyValue(glow.scaleXProperty(), 0.97, javafx.animation.Interpolator.EASE_BOTH),
                              new javafx.animation.KeyValue(glow.scaleYProperty(), 0.97, javafx.animation.Interpolator.EASE_BOTH)),
                      new javafx.animation.KeyFrame(
                              Duration.seconds(3.2),
                              new javafx.animation.KeyValue(glow.opacityProperty(), 0.40, javafx.animation.Interpolator.EASE_BOTH),
                              new javafx.animation.KeyValue(glow.scaleXProperty(), 1.04, javafx.animation.Interpolator.EASE_BOTH),
                              new javafx.animation.KeyValue(glow.scaleYProperty(), 1.04, javafx.animation.Interpolator.EASE_BOTH)),
                      new javafx.animation.KeyFrame(
                              Duration.seconds(6.4),
                              new javafx.animation.KeyValue(glow.opacityProperty(), 0.26, javafx.animation.Interpolator.EASE_BOTH),
                              new javafx.animation.KeyValue(glow.scaleXProperty(), 0.97, javafx.animation.Interpolator.EASE_BOTH),
                              new javafx.animation.KeyValue(glow.scaleYProperty(), 0.97, javafx.animation.Interpolator.EASE_BOTH)));

      pulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
      pulse.play();

      // Tag the glow node with its Timeline so the iconify-listener can pause it.
      glow.setUserData(pulse);

      StackPane stack = new StackPane(iv);
      stack.setAlignment(Pos.CENTER);
      stack.setPickOnBounds(false);

      glow.centerXProperty().bind(stack.widthProperty().multiply(0.5));
      glow.centerYProperty().bind(stack.heightProperty().multiply(0.5));
      stack.getChildren().add(0, glow);

      wrap.getChildren().add(stack);

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
    startBtn.setWrapText(false);
    startBtn.setMinWidth(Region.USE_PREF_SIZE);
    Animations.hoverLift(startBtn, 1.04);

    Button uploadBtn = new Button("Upload File");
    uploadBtn.getStyleClass().add("x-cta-secondary");
    uploadBtn.setId("heroUpload");
    uploadBtn.setWrapText(false);
    uploadBtn.setMinWidth(Region.USE_PREF_SIZE);
    Animations.hoverLift(uploadBtn, 1.03);

    // Equalise the Start and Upload CTAs only — Download has been moved out of this row
    // (it now lives at the bottom-right of the screen, mirroring the Settings gear).
    uploadBtn.prefWidthProperty().bind(startBtn.widthProperty());
    uploadBtn.prefHeightProperty().bind(startBtn.heightProperty());
    uploadBtn.minWidthProperty().bind(startBtn.widthProperty());
    uploadBtn.minHeightProperty().bind(startBtn.heightProperty());

    StackPane startWrap = new StackPane(startBtn);
    StackPane uploadWrap = new StackPane(uploadBtn);
    startWrap.getStyleClass().add("x-shadow-room");
    uploadWrap.getStyleClass().add("x-shadow-room");

    HBox ctas = new HBox(12, startWrap, uploadWrap);
    ctas.setAlignment(Pos.CENTER_LEFT);
    ctas.setMaxWidth(620);
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
    VBox.setMargin(tagline, new javafx.geometry.Insets(6, 0, 0, 20));
    tagline.translateYProperty().unbind();
    tagline.setTranslateY(-52);
    tagline.translateXProperty().unbind();
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
  /*  Download corner (bottom-right, mirrors Settings on bottom-left)   */
  /* ------------------------------------------------------------------ */

  /**
   * Build a StackPane that contains:
   *
   * <ol>
   *   <li>a soft, theme-coloured halo Circle (a separate node so CSS {@code -fx-effect} on the
   *       button can't override it), and
   *   <li>the actual Download button.
   * </ol>
   *
   * <p>An indefinite Timeline pulses the halo's opacity + scale while the button is visible.
   * Visibility is initially off; XMenInterface flips it after a successful mutation by
   * looking up {@code #heroDownload}.
   */
  private static StackPane buildDownloadCorner() {
    Button downloadBtn = new Button("Download");
    downloadBtn.getStyleClass().add("x-cta-secondary");
    downloadBtn.setId("heroDownload");
    downloadBtn.setGraphic(Icons.download(16, Color.WHITE));
    downloadBtn.setWrapText(false);
    downloadBtn.setMinWidth(Region.USE_PREF_SIZE);
    Animations.hoverLift(downloadBtn, 1.04);

    // Halo behind the button. Sized once the button knows its real width/height.
    javafx.scene.shape.Rectangle halo = new javafx.scene.shape.Rectangle();
    halo.getStyleClass().add("x-download-halo");
    halo.setMouseTransparent(true);
    halo.setManaged(false);
    halo.setFill(Color.web("#A56BFF"));
    halo.setEffect(new javafx.scene.effect.GaussianBlur(28));
    halo.setOpacity(0.0);

    StackPane stack = new StackPane();
    stack.getStyleClass().add("x-shadow-room");
    stack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    stack.setPickOnBounds(false);
    stack.getChildren().addAll(halo, downloadBtn);
    halo.widthProperty().bind(downloadBtn.widthProperty().add(18));
    halo.heightProperty().bind(downloadBtn.heightProperty().add(10));
    halo.arcWidthProperty().bind(halo.heightProperty());
    halo.arcHeightProperty().bind(halo.heightProperty());
    halo.xProperty().bind(stack.widthProperty().subtract(halo.widthProperty()).multiply(0.5));
    halo.yProperty().bind(stack.heightProperty().subtract(halo.heightProperty()).multiply(0.5));

    // The whole corner mirrors the button's visibility — when XMenInterface hides
    // the button, the halo + container disappear too.
    stack.managedProperty().bind(downloadBtn.managedProperty());
    stack.visibleProperty().bind(downloadBtn.visibleProperty());

    // Pulse Timeline: opacity 0.25 → 0.65, scale 0.95 → 1.12 → 0.95 (about 2.8 s/cycle).
    javafx.animation.Timeline pulse =
        new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                Duration.ZERO,
                new javafx.animation.KeyValue(
                    halo.opacityProperty(), 0.25, javafx.animation.Interpolator.EASE_BOTH),
                new javafx.animation.KeyValue(
                    halo.scaleXProperty(), 0.95, javafx.animation.Interpolator.EASE_BOTH),
                new javafx.animation.KeyValue(
                    halo.scaleYProperty(), 0.95, javafx.animation.Interpolator.EASE_BOTH)),
            new javafx.animation.KeyFrame(
                Duration.seconds(1.4),
                new javafx.animation.KeyValue(
                    halo.opacityProperty(), 0.65, javafx.animation.Interpolator.EASE_BOTH),
                new javafx.animation.KeyValue(
                    halo.scaleXProperty(), 1.12, javafx.animation.Interpolator.EASE_BOTH),
                new javafx.animation.KeyValue(
                    halo.scaleYProperty(), 1.12, javafx.animation.Interpolator.EASE_BOTH)),
            new javafx.animation.KeyFrame(
                Duration.seconds(2.8),
                new javafx.animation.KeyValue(
                    halo.opacityProperty(), 0.25, javafx.animation.Interpolator.EASE_BOTH),
                new javafx.animation.KeyValue(
                    halo.scaleXProperty(), 0.95, javafx.animation.Interpolator.EASE_BOTH),
                new javafx.animation.KeyValue(
                    halo.scaleYProperty(), 0.95, javafx.animation.Interpolator.EASE_BOTH)));
    pulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
    halo.setUserData(pulse); // picked up by walkTimelines() on stage iconify

    downloadBtn
        .visibleProperty()
        .addListener(
            (obs, was, now) -> {
              if (Boolean.TRUE.equals(now)) {
                halo.setFill(pickAccentColor(stack));
                pulse.playFromStart();
              } else {
                pulse.stop();
                halo.setOpacity(0.0);
              }
            });

    // ThemeApplier writes the theme palette as inline style on the scene root. Track that
    // style string and re-pick the accent any time it changes — this is what makes the
    // halo follow live theme switches without requiring the button to hide first.
    downloadBtn.sceneProperty().addListener((obsS, oldS, newScene) -> {
      if (newScene == null) return;
      javafx.scene.Parent r = newScene.getRoot();
      if (r != null) {
        r.styleProperty().addListener((obsStyle, oldStyle, newStyle) -> {
          if (downloadBtn.isVisible()) halo.setFill(pickAccentColor(stack));
        });
      }
    });

    return stack;
  }

  /** Read the current theme's {@code -accent} CSS variable off the scene root. */
  private static Color pickAccentColor(Node anyNode) {
    try {
      javafx.scene.Scene scene = anyNode.getScene();
      if (scene != null && scene.getRoot() != null) {
        String inline = scene.getRoot().getStyle();
        if (inline != null) {
          int i = inline.indexOf("-accent:");
          if (i >= 0) {
            int end = inline.indexOf(';', i);
            String raw =
                inline.substring(i + "-accent:".length(), end < 0 ? inline.length() : end).trim();
            return Color.web(raw);
          }
        }
      }
    } catch (Exception ignored) {
    }
    return Color.web("#A56BFF");
  }

  /* ------------------------------------------------------------------ */
  /*  Animations                                                        */
  /* ------------------------------------------------------------------ */

  /**
   * Walk the scene graph and pause/resume every running Timeline / Transition attached to a
   * node's user-data list. JavaFX doesn't expose a "list of animations on a node" API, so
   * we attach them in {@link #attachAnim(Node, javafx.animation.Animation)} and replay
   * the list here when the stage iconifies.
   */
  private static void walkTimelines(Node node, javafx.animation.Animation.Status target) {
    Object data = node.getUserData();
    if (data instanceof javafx.animation.Animation a) {
      if (target == javafx.animation.Animation.Status.PAUSED && a.getStatus() == javafx.animation.Animation.Status.RUNNING) {
        a.pause();
      } else if (target == javafx.animation.Animation.Status.RUNNING && a.getStatus() == javafx.animation.Animation.Status.PAUSED) {
        a.play();
      }
    }
    if (node instanceof javafx.scene.Parent p) {
      for (Node child : p.getChildrenUnmodifiable()) walkTimelines(child, target);
    }
  }

  private static void fadeInScene(StackPane root) {
    FadeTransition fade = new FadeTransition(Duration.millis(420), root);
    fade.setFromValue(0.0);
    fade.setToValue(1.0);
    TranslateTransition slide = new TranslateTransition(Duration.millis(420), root);
    slide.setFromY(12);
    slide.setToY(0);
    new ParallelTransition(fade, slide).play();
  }

  /**
   * Slow opacity + translate cycle on the smoke pane so the haze drifts a bit
   * over the background video, giving the scene a more "alive" atmosphere
   * without distracting from the content. Indefinite, very gentle.
   */
  private static void animateSmoke(Pane smoke) {
    javafx.animation.Timeline drift = new javafx.animation.Timeline(
        new javafx.animation.KeyFrame(Duration.ZERO,
            new javafx.animation.KeyValue(smoke.opacityProperty(), 0.12,
                javafx.animation.Interpolator.EASE_BOTH),
            new javafx.animation.KeyValue(smoke.translateXProperty(), -16,
                javafx.animation.Interpolator.EASE_BOTH),
            new javafx.animation.KeyValue(smoke.translateYProperty(), -10,
                javafx.animation.Interpolator.EASE_BOTH)),
        new javafx.animation.KeyFrame(Duration.seconds(8.5),
            new javafx.animation.KeyValue(smoke.opacityProperty(), 0.20,
                javafx.animation.Interpolator.EASE_BOTH),
            new javafx.animation.KeyValue(smoke.translateXProperty(), 18,
                javafx.animation.Interpolator.EASE_BOTH),
            new javafx.animation.KeyValue(smoke.translateYProperty(), 6,
                javafx.animation.Interpolator.EASE_BOTH)),
        new javafx.animation.KeyFrame(Duration.seconds(17),
            new javafx.animation.KeyValue(smoke.opacityProperty(), 0.12,
                javafx.animation.Interpolator.EASE_BOTH),
            new javafx.animation.KeyValue(smoke.translateXProperty(), -16,
                javafx.animation.Interpolator.EASE_BOTH),
            new javafx.animation.KeyValue(smoke.translateYProperty(), -10,
                javafx.animation.Interpolator.EASE_BOTH)));
    drift.setCycleCount(javafx.animation.Animation.INDEFINITE);
    drift.play();
    smoke.setUserData(drift);
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

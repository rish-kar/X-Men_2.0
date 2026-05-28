package com.xmen.user_interface;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmen.config.ThemeCatalog.Theme;
import javafx.animation.FadeTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.jar.JarFile;

/**
 * Builds the main scene.
 *
 * <p>Layout: 35% hero on the left, 65% mutation glass panel on the right. The settings button
 * sits in the bottom-left, the logo in the top-left of the hero. The background video uses a
 * cached temp file plus async rate setup to stay smooth even while modal dialogs are open.
 */
@Slf4j
public final class MainSceneFactory {

  /** Cache temp video files across rebuilds so we don't re-extract on every scene change. */
  private static final Map<String, File> cachedBackgroundFiles = new HashMap<>();
  private static final AtomicBoolean BACKGROUND_PREWARM_STARTED = new AtomicBoolean(false);
  private static final double FULL_BLEED_OVERSCAN = 48;

  /** Shared HTTP client + JSON mapper: one connection pool / thread pool for the whole UI. */
  private static final OkHttpClient SHARED_HTTP = new OkHttpClient();
  private static final ObjectMapper SHARED_JSON = new ObjectMapper();

  /** Cached PNG resources — decoded once, reused across scene rebuilds and theme switches. */
  private static volatile Image cachedBackgroundFallback;
  private static volatile Image cachedHeroLogo;

  /**
   * Kicks off video extraction on a background thread so the file is ready by the time
   * {@link #buildBackground(Stage)} is called from the splash hand-off. Safe to call from any
   * thread; safe to call multiple times (the underlying op is synchronized + idempotent).
   *
   * <p>This is the single most effective fix for "main-screen video stuck on startup": the
   * MP4 extraction (~MBs of I/O) no longer happens on the FX thread during scene swap.
   */
  public static void preWarmBackgroundVideo() {
    if (!BACKGROUND_PREWARM_STARTED.compareAndSet(false, true)) {
      return;
    }
    Thread t = new Thread(() -> {
      try {
        for (String resource : availableBackgroundVideos()) {
          ensureCachedVideo(resource);
        }
      } catch (IOException e) {
        BACKGROUND_PREWARM_STARTED.set(false);
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

  public static Built build(
      Stage stage,
      int serverPort,
      Consumer<Void> onSettingsRequested,
      Consumer<Void> onMetricsRequested) {
    StackPane root = new StackPane();
    root.getStyleClass().add("x-root");
    root.setBackground(
        new Background(new BackgroundFill(Color.web("#020817"), CornerRadii.EMPTY, Insets.EMPTY)));
    root.setMinSize(0, 0);
    root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    Node background = buildBackground(stage);
    bindFullBleedToRoot(background, root);

    Pane overlay = new Pane();
    overlay.getStyleClass().add("x-overlay");
    overlay.setPickOnBounds(false);
    bindFullBleedToRoot(overlay, root);

    // Subtle smoky atmosphere over the background video. Mouse-transparent so
    // it doesn't intercept clicks. The radial gradients in .x-smoke shift
    // very gently — combined with the looping video this reads as drifting
    // haze rather than a static tint.
    Pane smoke = new Pane();
    smoke.getStyleClass().add("x-smoke");
    smoke.setMouseTransparent(true);
    smoke.setPickOnBounds(false);
    // The .x-smoke CSS uses two layered radial-gradients — costly to repaint.
    // Cache as a bitmap and let the drift Timeline only re-transform/blend it.
    smoke.setCache(true);
    smoke.setCacheHint(CacheHint.SPEED);
    bindFullBleedToRoot(smoke, root);
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

    Button metrics = buildMetricsButton(onMetricsRequested);
    StackPane metricsShadowRoom = new StackPane(metrics);
    metricsShadowRoom.getStyleClass().add("x-shadow-room");
    metricsShadowRoom.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

    Button settings = buildSettingsButton(onSettingsRequested);
    StackPane settingsShadowRoom = new StackPane(settings);
    settingsShadowRoom.getStyleClass().add("x-shadow-room");
    settingsShadowRoom.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

    // Download lives in the hero CTA row alongside Start/Upload; only Metrics +
    // Settings remain in the bottom-left cluster.
    HBox bottomCluster =
        new HBox(8, metricsShadowRoom, settingsShadowRoom);
    bottomCluster.setAlignment(Pos.BOTTOM_CENTER);
    bottomCluster.setPickOnBounds(false);
    bottomCluster.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    StackPane.setAlignment(bottomCluster, Pos.BOTTOM_LEFT);
    StackPane.setMargin(bottomCluster, new Insets(0, 0, -12, 150));

    BorderPane content = new BorderPane();
    content.setPickOnBounds(false);
    content.setCenter(body);
    bindToRoot(content, root);

    root.getChildren().addAll(background, overlay, smoke, content, bottomCluster);

    // Pause the looping smoke/logo Timelines whenever the stage is iconified so we
    // don't burn CPU rendering offscreen frames. Resumes automatically when restored.
    stage.iconifiedProperty().addListener((obs, was, now) -> {
      javafx.animation.Animation.Status target =
          now ? javafx.animation.Animation.Status.PAUSED : javafx.animation.Animation.Status.RUNNING;
      walkTimelines(root, target);
    });

    fadeInForeground(content, bottomCluster);

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

  private static void bindToRoot(Node node, StackPane root) {
    if (node instanceof Region region) {
      region.setMinSize(0, 0);
      region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
      region.prefWidthProperty().bind(root.widthProperty());
      region.prefHeightProperty().bind(root.heightProperty());
    }
  }

  private static void bindFullBleedToRoot(Node node, StackPane root) {
    if (node instanceof Region region) {
      region.setManaged(false);
      region.setMinSize(0, 0);
      region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
      Runnable resize =
          () ->
              region.resizeRelocate(
                  -FULL_BLEED_OVERSCAN,
                  -FULL_BLEED_OVERSCAN,
                  Math.max(1, root.getWidth() + FULL_BLEED_OVERSCAN * 2),
                  Math.max(1, root.getHeight() + FULL_BLEED_OVERSCAN * 2));
      root.widthProperty().addListener((obs, oldValue, newValue) -> resize.run());
      root.heightProperty().addListener((obs, oldValue, newValue) -> resize.run());
      root.sceneProperty()
          .addListener(
              (obs, oldScene, newScene) -> {
                if (newScene != null) {
                  javafx.application.Platform.runLater(resize);
                }
              });
      resize.run();
    }
  }

  private static Node buildBackground(Stage stage) {
    StackPane container = new StackPane();
    // Pick the monitor the stage currently lives on instead of always the
    // primary screen — multi-monitor users still see the video sized to the
    // window they have X-Men open on.
    Rectangle2D screen = screenForStage(stage);
    container.setPrefSize(screen.getWidth(), screen.getHeight());
    // Let the container stretch beyond its pref size if the root StackPane is
    // taller than the original visual bounds (which can happen on Windows
    // when the maximised stage extends slightly past the visual bounds). The
    // default max = pref behaviour leaves an un-themed strip at the bottom
    // edge where the background image/video can't reach.
    container.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    container.setMinSize(0, 0);
    ImageView fallback = buildBackgroundFallback(container);
    if (fallback != null) {
      container.getChildren().add(fallback);
    }

    try {
      List<String> resources = availableBackgroundVideos();
      if (!resources.isEmpty()) {
        BackgroundVideoRotator rotator =
            new BackgroundVideoRotator(container, fallback, stage, resources);
        container.getProperties().put("xmen.backgroundVideoRotator", rotator);
        startRotatorWhenAttached(container, rotator);
        return container;
      }
    } catch (Exception e) {
      log.info("Background video missing or failed; falling back to image: {}", e.getMessage());
    }

    return container;
  }

  private static void startRotatorWhenAttached(
      StackPane container, BackgroundVideoRotator rotator) {
    if (container.getScene() != null) {
      javafx.application.Platform.runLater(rotator::start);
      return;
    }
    ChangeListener<javafx.scene.Scene> listener =
        new ChangeListener<>() {
          @Override
          public void changed(
              javafx.beans.value.ObservableValue<? extends javafx.scene.Scene> obs,
              javafx.scene.Scene oldScene,
              javafx.scene.Scene newScene) {
            if (newScene == null) return;
            container.sceneProperty().removeListener(this);
            javafx.application.Platform.runLater(rotator::start);
          }
        };
    container.sceneProperty().addListener(listener);
  }

  private static ImageView buildBackgroundFallback(StackPane container) {
    Image img = loadBackgroundFallbackImage();
    if (img == null) return null;
    ImageView iv = new ImageView(img);
    iv.setPreserveRatio(false);
    iv.setSmooth(false);
    iv.fitWidthProperty().bind(container.widthProperty());
    iv.fitHeightProperty().bind(container.heightProperty());
    return iv;
  }

  private static Image loadBackgroundFallbackImage() {
    Image cached = cachedBackgroundFallback;
    if (cached != null) return cached;
    synchronized (MainSceneFactory.class) {
      if (cachedBackgroundFallback != null) return cachedBackgroundFallback;
      try (InputStream img =
          MainSceneFactory.class.getResourceAsStream("/images/main_scene_dna_fallback.png")) {
        if (img != null) cachedBackgroundFallback = new Image(img);
      } catch (Exception ignored) {
      }
      return cachedBackgroundFallback;
    }
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

  private static List<String> availableBackgroundVideos() {
    List<String> resources = new ArrayList<>();
    for (String resource : discoverVideosDirectoryResources()) {
      if (!resources.contains(resource)) {
        resources.add(resource);
      }
    }
    return resources;
  }

  private static List<String> discoverVideosDirectoryResources() {
    List<String> resources = new ArrayList<>();
    try {
      URL videosDir = MainSceneFactory.class.getResource("/videos");
      if (videosDir == null) return resources;
      if ("file".equals(videosDir.getProtocol())) {
        try (var files = Files.list(Path.of(videosDir.toURI()))) {
          files
              .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".mp4"))
              .sorted()
              .map(path -> "/videos/" + path.getFileName())
              .forEach(resources::add);
        }
      } else if ("jar".equals(videosDir.getProtocol())) {
        JarURLConnection connection = (JarURLConnection) videosDir.openConnection();
        try (JarFile jar = connection.getJarFile()) {
          jar.stream()
              .map(entry -> entry.getName())
              .filter(name -> name.startsWith("videos/"))
              .filter(name -> name.toLowerCase().endsWith(".mp4"))
              .sorted()
              .map(name -> "/" + name)
              .forEach(resources::add);
        }
      }
    } catch (Exception e) {
      log.debug("Background video directory scan skipped: {}", e.getMessage());
    }
    return resources;
  }

  private static synchronized File ensureCachedVideo(String resource) throws IOException {
    File cached = cachedBackgroundFiles.get(resource);
    long expectedLength = backgroundVideoResourceLength(resource);
    if (isUsableCachedVideo(cached, expectedLength)) {
      return cached;
    }
    cachedBackgroundFiles.remove(resource);
    File stable = new File(System.getProperty("java.io.tmpdir"), cacheFileName(resource));
    // Reuse the file across JVM restarts if a previous run already wrote it AND it isn't empty.
    if (isUsableCachedVideo(stable, expectedLength)) {
      cachedBackgroundFiles.put(resource, stable);
      return stable;
    }
    try (InputStream videoStream = MainSceneFactory.class.getResourceAsStream(resource)) {
      if (videoStream == null) return null;
      Path tmp = Files.createTempFile(stable.toPath().getParent(), stable.getName(), ".tmp");
      try {
        Files.copy(videoStream, tmp, StandardCopyOption.REPLACE_EXISTING);
        moveIntoPlace(tmp, stable.toPath());
      } finally {
        Files.deleteIfExists(tmp);
      }
      cachedBackgroundFiles.put(resource, stable);
      return stable;
    }
  }

  private static boolean isUsableCachedVideo(File file, long expectedLength) {
    return file != null
        && file.exists()
        && file.length() > 0
        && (expectedLength <= 0 || file.length() == expectedLength);
  }

  private static void moveIntoPlace(Path source, Path target) throws IOException {
    try {
      Files.move(
          source,
          target,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static String cacheFileName(String resource) {
    String clean = resource.replaceAll("[^A-Za-z0-9._-]", "_");
    return "xmen-bg-cache-" + clean;
  }

  private static long backgroundVideoResourceLength(String resource) {
    try {
      java.net.URL url = MainSceneFactory.class.getResource(resource);
      if (url == null) return -1;
      return url.openConnection().getContentLengthLong();
    } catch (IOException e) {
      return -1;
    }
  }

  private static final class BackgroundVideoDeck {
    private final List<String> resources;
    private final ArrayDeque<String> remaining = new ArrayDeque<>();
    private String lastPlayed;

    private BackgroundVideoDeck(List<String> resources) {
      this.resources = List.copyOf(resources);
    }

    private String next() {
      if (remaining.isEmpty()) {
        refill();
      }
      lastPlayed = remaining.removeFirst();
      return lastPlayed;
    }

    private void refill() {
      List<String> shuffled = new ArrayList<>(resources);
      Collections.shuffle(shuffled);
      if (shuffled.size() > 1 && shuffled.get(0).equals(lastPlayed)) {
        Collections.rotate(shuffled, 1);
      }
      remaining.addAll(shuffled);
    }

  }

  private static final class BackgroundVideoRotator {
    // 12s budget: cold MediaPlayer init on Windows (codec warm-up + GPU
    // pipeline) can occasionally need >6s on first launch. The watchdog only
    // exists to skip *genuinely broken* MP4s; a slightly longer timeout
    // doesn't degrade UX because the previous video stays on screen until
    // the next one actually starts playing.
    private static final Duration STARTUP_WATCHDOG = Duration.seconds(12);

    /** Crossfade duration between videos. Long enough to mask the load gap. */
    private static final Duration CROSSFADE = Duration.millis(700);

    /**
     * Start preparing the next MP4 before the current one ends. JavaFX media
     * initialisation can take a noticeable moment on Windows, and waiting for
     * EndOfMedia leaves the last decoded frame looking "stuck" during that warm-up.
     */
    private static final Duration PRELOAD_LEAD = Duration.seconds(3.5);

    private final StackPane container;
    private final ImageView fallback;
    private final Stage stage;
    private final BackgroundVideoDeck deck;

    /** The video the user is currently looking at. */
    private MediaPlayer currentPlayer;
    private MediaView currentView;

    /**
     * The next video being loaded in the background. Kept distinct from
     * {@link #currentPlayer} so the old video stays on screen while the new
     * one initialises — no black gap between videos.
     */
    private MediaPlayer incomingPlayer;
    private MediaView incomingView;

    private boolean pausedByStage;
    private boolean disposed;
    private boolean transitionPending;
    private boolean loadingNext;
    private boolean preloadRequested;
    private boolean started;
    private javafx.animation.PauseTransition retryDelay;
    private ChangeListener<Duration> preloadListener;
    private MediaPlayer preloadListenerPlayer;

    // Heartbeat watchdog: JavaFX MediaPlayer occasionally stops advancing
    // mid-playback without firing onStalled or onError (Windows decoder
    // hiccups). We poll currentTime ourselves so we can rotate to the next
    // clip without waiting for the natural preload window to arrive.
    private javafx.animation.Timeline heartbeatTimeline;
    private MediaPlayer heartbeatPlayer;
    private Duration heartbeatLastTime;
    private int heartbeatStuckTicks;

    private BackgroundVideoRotator(
        StackPane container, ImageView fallback, Stage stage, List<String> resources) {
      this.container = container;
      this.fallback = fallback;
      this.stage = stage;
      this.deck = new BackgroundVideoDeck(resources);
      installStageHooks();
    }

    private void start() {
      if (disposed || started) return;
      started = true;
      playNext(false);
    }

    private void playNext(boolean ignored) {
      if (disposed) return;
      if (pausedByStage) {
        transitionPending = true;
        return;
      }
      if (incomingPlayer != null || loadingNext) return; // another load already in flight
      loadingNext = true;
      String resource = deck.next();
      // Move JAR extraction off the FX thread — first-time decompression of a
      // multi-MB MP4 from inside the jar can block FX for a second or two.
      Thread loader = new Thread(() -> {
        File tmp;
        try {
          tmp = ensureCachedVideo(resource);
        } catch (Exception e) {
          log.warn("Skipping background video {}: {}", resource, e.getMessage());
          javafx.application.Platform.runLater(
              () -> {
                loadingNext = false;
                if (!disposed) playNext(false);
              });
          return;
        }
        if (tmp == null) {
          javafx.application.Platform.runLater(
              () -> {
                loadingNext = false;
                if (!disposed) playNext(false);
              });
          return;
        }
        final File ready = tmp;
        javafx.application.Platform.runLater(
            () -> {
              loadingNext = false;
              launchIncoming(resource, ready, 0);
            });
      }, "xmen-bg-load");
      loader.setDaemon(true);
      loader.start();
    }

    /**
     * Build the next MediaPlayer/MediaView as an <em>incoming</em> video. The
     * current video keeps playing untouched; only when the incoming one
     * actually reaches PLAYING does {@link #promoteIncoming} crossfade them.
     * This eliminates the black frame between videos that the old "dispose
     * first, then build new" flow produced.
     */
    private void launchIncoming(String resource, File tmp, int attempt) {
      if (disposed) return;
      if (pausedByStage) {
        transitionPending = true;
        return;
      }
      try {
        MediaPlayer player = new MediaPlayer(new Media(tmp.toURI().toString()));
        MediaView view = createView(player);
        view.setOpacity(0); // invisible until the crossfade fades it in
        incomingPlayer = player;
        incomingView = view;
        player.setCycleCount(1);
        player.setMute(true);
        player.setVolume(0);
        javafx.animation.PauseTransition watchdog =
            new javafx.animation.PauseTransition(STARTUP_WATCHDOG);
        watchdog.setOnFinished(
            e -> {
              if (disposed) {
                return;
              }
              if (pausedByStage) {
                watchdog.playFromStart();
                return;
              }
              if (!disposed
                  && incomingPlayer == player
                  && player.getStatus() != MediaPlayer.Status.PLAYING) {
                retryIncomingOrPlayNext(
                    resource, tmp, player, view, attempt, "did not start cleanly");
              }
            });
        player.setOnReady(
            () -> {
              if (disposed || incomingPlayer != player) return;
              watchdog.playFromStart();
              player.play();
            });
        player.setOnPlaying(
            () -> {
              if (disposed || incomingPlayer != player) return;
              watchdog.stop();
              log.debug("Background video {} playback started.", resource);
              promoteIncoming(player, view);
            });
        player.setOnEndOfMedia(
            () -> {
              if (currentPlayer == player) {
                // The preloader normally has the next clip warming already.
                // If it could not arm for this media, fall back to end-triggered rotation.
                preloadRequested = true;
                if (incomingPlayer == null) playNext(false);
              }
            });
        player.setOnStalled(
            () -> {
              if (incomingPlayer == player) {
                retryIncomingOrPlayNext(resource, tmp, player, view, attempt, "stalled");
              } else if (currentPlayer == player) {
                log.warn("Background video {} stalled; rotating.", resource);
                playNext(false);
              }
            });
        player.setOnError(
            () -> {
              watchdog.stop();
              String errorMessage =
                  player.getError() == null
                      ? "unknown media error"
                      : player.getError().getMessage();
              if (incomingPlayer == player) {
                retryIncomingOrPlayNext(resource, tmp, player, view, attempt, errorMessage);
              } else if (currentPlayer == player) {
                log.warn("Background video error for {}; rotating: {}", resource, errorMessage);
                playNext(false);
              }
            });
        // Insert above the fallback so the new view can fade in on top of it
        // (and on top of the old video, if any).
        container.getChildren().add(view);
        watchdog.playFromStart();
      } catch (Exception e) {
        log.warn("Skipping background video {}: {}", resource, e.getMessage());
        playNext(true);
      }
    }

    private void retryIncomingOrPlayNext(
        String resource,
        File tmp,
        MediaPlayer player,
        MediaView view,
        int attempt,
        String reason) {
      discardIncoming(player, view);
      if (attempt < 1 && !disposed) {
        log.warn("Background video {} failed to start ({}); retrying once.", resource, reason);
        javafx.animation.PauseTransition retry =
            new javafx.animation.PauseTransition(Duration.millis(500));
        retry.setOnFinished(
            e -> {
              if (retryDelay == retry) retryDelay = null;
              if (!disposed) launchIncoming(resource, tmp, attempt + 1);
            });
        retryDelay = retry;
        retry.play();
      } else {
        log.warn("Background video {} failed after retry ({}); rotating.", resource, reason);
        playNext(false);
      }
    }

    /**
     * The incoming player is actually rendering frames — crossfade it in and
     * the outgoing player out. The outgoing MediaPlayer/MediaView is disposed
     * only after the fade-out completes, so the user never sees a blank frame.
     */
    private void promoteIncoming(MediaPlayer newPlayer, MediaView newView) {
      MediaPlayer oldPlayer = currentPlayer;
      MediaView oldView = currentView;
      currentPlayer = newPlayer;
      currentView = newView;
      incomingPlayer = null;
      incomingView = null;
      armPreloadTrigger(newPlayer);
      armHeartbeat(newPlayer);

      // Fade the new video in.
      javafx.animation.FadeTransition fadeIn =
          new javafx.animation.FadeTransition(CROSSFADE, newView);
      fadeIn.setFromValue(0);
      fadeIn.setToValue(1);
      fadeIn.play();

      // Fade the old video out (if there was one), then dispose it.
      if (oldPlayer != null && oldView != null) {
        javafx.animation.FadeTransition fadeOut =
            new javafx.animation.FadeTransition(CROSSFADE, oldView);
        fadeOut.setFromValue(oldView.getOpacity());
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(
            ev -> {
              try {
                oldPlayer.stop();
                oldPlayer.dispose();
              } catch (Exception ignored) {
                // already torn down by another path
              }
              container.getChildren().remove(oldView);
            });
        fadeOut.play();
      }

      // First successful video — fade the fallback image out so the new
      // playback isn't tinted by it sitting underneath.
      if (fallback != null && fallback.isVisible() && fallback.getOpacity() > 0) {
        javafx.animation.FadeTransition fadeFb =
            new javafx.animation.FadeTransition(CROSSFADE, fallback);
        fadeFb.setFromValue(fallback.getOpacity());
        fadeFb.setToValue(0);
        fadeFb.setOnFinished(ev -> fallback.setVisible(false));
        fadeFb.play();
      }
    }

    /** Throw away an incoming-but-failed player. The current video is untouched. */
    private void discardIncoming(MediaPlayer player, MediaView view) {
      if (incomingPlayer == player) {
        incomingPlayer = null;
        incomingView = null;
      }
      try {
        player.stop();
        player.dispose();
      } catch (Exception ignored) {
        // best-effort cleanup
      }
      container.getChildren().remove(view);
    }

    private void armPreloadTrigger(MediaPlayer player) {
      detachPreloadTrigger();
      preloadRequested = false;
      ChangeListener<Duration> listener =
          (obs, oldTime, currentTime) -> {
            if (disposed
                || pausedByStage
                || currentPlayer != player
                || incomingPlayer != null
                || preloadRequested) {
              return;
            }
            Duration total = player.getTotalDuration();
            if (!isFinite(total) || !isFinite(currentTime)) {
              return;
            }
            double totalMillis = total.toMillis();
            double currentMillis = currentTime.toMillis();
            double leadMillis =
                Math.min(PRELOAD_LEAD.toMillis(), Math.max(750, totalMillis * 0.25));
            if (totalMillis - currentMillis <= leadMillis) {
              preloadRequested = true;
              playNext(false);
            }
          };
      preloadListener = listener;
      preloadListenerPlayer = player;
      player.currentTimeProperty().addListener(listener);
    }

    private void detachPreloadTrigger() {
      if (preloadListenerPlayer != null && preloadListener != null) {
        try {
          preloadListenerPlayer.currentTimeProperty().removeListener(preloadListener);
        } catch (Exception ignored) {
          // best-effort listener cleanup
        }
      }
      preloadListener = null;
      preloadListenerPlayer = null;
    }

    /**
     * Start a 250 ms heartbeat that asserts the current clip is actually
     * advancing. After ~750 ms without movement we try a soft seek-and-replay
     * (often enough to nudge a hiccuped decoder back to life); after ~1.5 s we
     * give up on the clip and force rotation. Without this, certain Windows
     * decoder stalls hold the video on a frozen frame until the natural
     * preload window arrives, which can be many seconds away on long clips.
     */
    private void armHeartbeat(MediaPlayer player) {
      detachHeartbeat();
      heartbeatPlayer = player;
      heartbeatLastTime = null;
      heartbeatStuckTicks = 0;
      javafx.animation.Timeline t =
          new javafx.animation.Timeline(
              new javafx.animation.KeyFrame(
                  Duration.millis(250),
                  e -> {
                    if (disposed) {
                      detachHeartbeat();
                      return;
                    }
                    if (pausedByStage) {
                      heartbeatStuckTicks = 0;
                      heartbeatLastTime = null;
                      return;
                    }
                    if (currentPlayer != player) {
                      detachHeartbeat();
                      return;
                    }
                    if (player.getStatus() != MediaPlayer.Status.PLAYING) {
                      heartbeatStuckTicks = 0;
                      heartbeatLastTime = null;
                      return;
                    }
                    Duration now = player.getCurrentTime();
                    if (!isFinite(now)) {
                      return;
                    }
                    if (heartbeatLastTime != null
                        && Math.abs(now.toMillis() - heartbeatLastTime.toMillis()) < 1.0) {
                      heartbeatStuckTicks++;
                    } else {
                      heartbeatStuckTicks = 0;
                    }
                    heartbeatLastTime = now;
                    if (heartbeatStuckTicks == 3) {
                      log.warn(
                          "Background video stuck at {} ms; attempting seek-recover.",
                          (long) now.toMillis());
                      recoverStuckPlayer(player, now);
                    } else if (heartbeatStuckTicks >= 6) {
                      log.warn("Background video did not recover from stall; rotating.");
                      heartbeatStuckTicks = 0;
                      heartbeatLastTime = null;
                      playNext(false);
                    }
                  }));
      t.setCycleCount(javafx.animation.Animation.INDEFINITE);
      t.play();
      heartbeatTimeline = t;
    }

    private void recoverStuckPlayer(MediaPlayer player, Duration now) {
      try {
        Duration target = Duration.ZERO;
        Duration total = player.getTotalDuration();
        if (isFinite(now) && now.toMillis() > 250) {
          target = now.add(Duration.millis(33));
          if (isFinite(total) && target.toMillis() >= Math.max(0, total.toMillis() - 250)) {
            target = Duration.ZERO;
          }
        }
        player.pause();
        player.seek(target);
        player.play();
      } catch (Exception ignored) {
        // best-effort recovery
      }
    }

    private void detachHeartbeat() {
      if (heartbeatTimeline != null) {
        try {
          heartbeatTimeline.stop();
        } catch (Exception ignored) {
          // best-effort
        }
      }
      heartbeatTimeline = null;
      heartbeatPlayer = null;
      heartbeatLastTime = null;
      heartbeatStuckTicks = 0;
    }

    private static boolean isFinite(Duration duration) {
      return duration != null
          && !duration.isUnknown()
          && !duration.isIndefinite()
          && duration.toMillis() >= 0;
    }

    private MediaView createView(MediaPlayer player) {
      MediaView view = new MediaView(player);
      view.setPreserveRatio(false);
      view.setSmooth(false);
      view.fitWidthProperty().bind(container.widthProperty());
      view.fitHeightProperty().bind(container.heightProperty());
      return view;
    }

    private void installStageHooks() {
      if (stage == null) return;
      stage.iconifiedProperty()
          .addListener(
              (obs, was, iconified) -> {
                pausedByStage = iconified;
                if (iconified) {
                  // Pause whatever's playing so we don't burn cycles offscreen.
                  if (currentPlayer != null) currentPlayer.pause();
                  if (incomingPlayer != null) incomingPlayer.pause();
                } else if (transitionPending) {
                  // A transition was deferred while iconified — fire it.
                  transitionPending = false;
                  playNext(true);
                } else if (currentPlayer != null) {
                  currentPlayer.play();
                  if (incomingPlayer != null) incomingPlayer.play();
                } else {
                  // No active player and nothing pending — restart rotation.
                  playNext(true);
                }
              });
      stage.showingProperty()
          .addListener(
              (obs, was, showing) -> {
                if (!showing) dispose();
              });
    }

    private void dispose() {
      disposed = true;
      if (retryDelay != null) {
        try {
          retryDelay.stop();
        } catch (Exception ignored) {
          // best-effort
        }
        retryDelay = null;
      }
      detachPreloadTrigger();
      detachHeartbeat();
      disposePlayer(currentPlayer, currentView);
      currentPlayer = null;
      currentView = null;
      disposePlayer(incomingPlayer, incomingView);
      incomingPlayer = null;
      incomingView = null;
    }

    private void disposePlayer(MediaPlayer player, MediaView view) {
      if (player != null) {
        try {
          player.stop();
          player.dispose();
        } catch (Exception ignored) {
          // best-effort
        }
      }
      if (view != null) {
        container.getChildren().remove(view);
      }
    }
  }

  /* ------------------------------------------------------------------ */
  /*  Hero column                                                       */
  /* ------------------------------------------------------------------ */

  /** Logo container + the (possibly null) ImageView we re-paint when the theme changes. */
  private record LogoSlot(HBox container, ImageView imageView) {}

  /** Logo width: 331 (prev) × 1.10 ≈ 364 per latest design feedback. */
  private static final double LOGO_WIDTH = 364;

  private static Image loadHeroLogoImage() {
    Image cached = cachedHeroLogo;
    if (cached != null) return cached;
    synchronized (MainSceneFactory.class) {
      if (cachedHeroLogo != null) return cachedHeroLogo;
      try (InputStream is = MainSceneFactory.class.getResourceAsStream("/images/Classic.png")) {
        if (is != null) {
          // Decode once at 2× target width for a crisp downscale, then
          // share the decoded bitmap across every ImageView instance.
          cachedHeroLogo = new Image(is, LOGO_WIDTH * 2, 0, true, true);
        }
      } catch (Exception ignored) {
      }
      return cachedHeroLogo;
    }
  }

  private static LogoSlot buildLogoSlot() {
    // Single brand mark used across every theme — see ThemeLogo.
    Image img = loadHeroLogoImage();
    ImageView iv = null;
    if (img != null) {
      // setSmooth + setCache → JavaFX uses a higher-quality scaling filter
      // and caches the rasterised result. Together they remove the
      // jaggies on the diagonal edges of the wordmark.
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
      // Lighting + Image is invariant per render — cache the rasterised
      // result so JavaFX doesn't re-run the lighting pass each frame
      // while the surrounding pulse Timeline drives sibling opacity.
      iv.setCacheHint(CacheHint.SPEED);
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
      // GaussianBlur(60) is one of the most expensive filters JavaFX ships;
      // caching the blurred bitmap lets the pulse Timeline animate opacity
      // and scale (cheap transforms) without re-running the blur each frame.
      glow.setCache(true);
      glow.setCacheHint(CacheHint.SPEED);

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
            "Turn ceremony specifications into actionable "
                + "mutation traces. X-Men models the human in "
                + "the loop — forgetting, slipping, mistyping — "
                + "and feeds Tamarin the variants that matter.");
    sub.getStyleClass().add("x-hero-sub");
    sub.setWrapText(true);
    sub.setTextAlignment(javafx.scene.text.TextAlignment.JUSTIFY);
    sub.setMinHeight(Region.USE_PREF_SIZE);
    sub.setPrefWidth(390);
    sub.setMaxWidth(390);
    sub.setMinWidth(390);

    Button startBtn = new Button("Start Mutation");
    startBtn.getStyleClass().add("x-cta-primary");
    startBtn.setId("heroStart");
    startBtn.setWrapText(false);
    startBtn.setMinWidth(Region.USE_PREF_SIZE);
    Animations.hoverLift(startBtn, 1.04);

    Button uploadBtn = new Button("Upload File");
    // Keep Upload as the secondary CTA (glass outline) — size and position
    // still match Start via the width/height bindings below.
    uploadBtn.getStyleClass().add("x-cta-secondary");
    uploadBtn.setId("heroUpload");
    uploadBtn.setWrapText(false);
    uploadBtn.setMinWidth(Region.USE_PREF_SIZE);
    Animations.hoverLift(uploadBtn, 1.03);

    // Equalise the Start and Upload CTAs. Download sits to the right at the
    // same height but keeps its natural (icon + label) width.
    uploadBtn.prefWidthProperty().bind(startBtn.widthProperty());
    uploadBtn.prefHeightProperty().bind(startBtn.heightProperty());
    uploadBtn.minWidthProperty().bind(startBtn.widthProperty());
    uploadBtn.minHeightProperty().bind(startBtn.heightProperty());

    StackPane startWrap = new StackPane(startBtn);
    StackPane uploadWrap = new StackPane(uploadBtn);
    startWrap.getStyleClass().add("x-shadow-room");
    uploadWrap.getStyleClass().add("x-shadow-room");
    startWrap.setTranslateX(34);

    // Download lives in the same row but only becomes visible after a successful
    // mutation. XMenInterface flips #heroDownload's managed/visible flags, and
    // the surrounding StackPane mirrors those via property bindings inside
    // buildDownloadCorner(), so the row collapses to [Start][Upload] until then.
    StackPane downloadWrap = buildDownloadCorner();
    downloadWrap.setTranslateX(-34);

    HBox ctas = new HBox(4, startWrap, uploadWrap, downloadWrap);
    ctas.setAlignment(Pos.CENTER_LEFT);
    ctas.setMaxWidth(Region.USE_PREF_SIZE);
    VBox.setMargin(ctas, new javafx.geometry.Insets(0, 0, 0, 0));

    Label tagline =
        new Label(
            "Exploring formal-methods workflows for ceremony designers who aim higher.");
    tagline.getStyleClass().add("x-hero-tagline");
    tagline.setWrapText(true);
    tagline.setMaxWidth(540);
    tagline.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
    tagline.setAlignment(Pos.CENTER);
    VBox.setMargin(tagline, new javafx.geometry.Insets(6, 0, 0, 0));
    tagline.translateYProperty().unbind();
    tagline.setTranslateY(-44);
    tagline.translateXProperty().unbind();
    tagline.setTranslateX(0);

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
    Node icon = Icons.gear(16, Color.WHITE);
    btn.setGraphic(icon);
    btn.setOnAction(
        e -> {
          if (onClick != null) onClick.accept(null);
        });
    Animations.hoverLift(btn, 1.04);
    return btn;
  }

  /**
   * Small heartbeat/pulse button sitting to the left of Settings. Opens the
   * application-metrics glass panel (memory, CPU, threads, runtime).
   */
  private static Button buildMetricsButton(Consumer<Void> onClick) {
    Button btn = new Button("Metrics");
    btn.getStyleClass().add("x-settings-btn");
    btn.setId("heroMetrics");
    Node icon = Icons.pulse(16, Color.WHITE);
    btn.setGraphic(icon);
    btn.setOnAction(
        e -> {
          if (onClick != null) onClick.accept(null);
        });
    Animations.hoverLift(btn, 1.04);
    return btn;
  }

  /* ------------------------------------------------------------------ */
  /*  Download corner — slots into the hero CTA row, right of Upload.   */
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
    // Cache the blurred halo bitmap so the pulse Timeline's opacity/scale
    // changes are GPU transforms, not fresh CPU-side Gaussian-blur passes.
    halo.setCache(true);
    halo.setCacheHint(CacheHint.SPEED);

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

  private static void fadeInForeground(Node... nodes) {
    for (Node node : nodes) {
      FadeTransition fade = new FadeTransition(Duration.millis(420), node);
      fade.setFromValue(0.0);
      fade.setToValue(1.0);
      fade.play();
    }
  }

  /**
   * Slow opacity cycle on the smoke pane. The pane does not translate: the
   * background and theme overlay must remain pinned to the window edges.
   */
  private static void animateSmoke(Pane smoke) {
    javafx.animation.Timeline drift = new javafx.animation.Timeline(
        new javafx.animation.KeyFrame(Duration.ZERO,
            new javafx.animation.KeyValue(smoke.opacityProperty(), 0.12,
                javafx.animation.Interpolator.EASE_BOTH)),
        new javafx.animation.KeyFrame(Duration.seconds(8.5),
            new javafx.animation.KeyValue(smoke.opacityProperty(), 0.20,
                javafx.animation.Interpolator.EASE_BOTH)),
        new javafx.animation.KeyFrame(Duration.seconds(17),
            new javafx.animation.KeyValue(smoke.opacityProperty(), 0.12,
                javafx.animation.Interpolator.EASE_BOTH)));
    drift.setCycleCount(javafx.animation.Animation.INDEFINITE);
    drift.play();
    smoke.setUserData(drift);
  }

  /* ------------------------------------------------------------------ */
  /*  Initial theme fetch                                               */
  /* ------------------------------------------------------------------ */

  private static void pullInitialTheme(int serverPort, Consumer<Theme> sink) {
    Thread t = new Thread(
            () -> {
              try {
                Response r =
                    SHARED_HTTP.newCall(
                            new Request.Builder()
                                .url(
                                    "http://localhost:"
                                        + serverPort
                                        + "/api/settings/themes/active")
                                .build())
                        .execute();
                try (r) {
                  if (!r.isSuccessful() || r.body() == null) return;
                  Theme theme = SHARED_JSON.readValue(r.body().bytes(), Theme.class);
                  sink.accept(theme);
                }
              } catch (Exception e) {
                log.debug(
                    "Could not fetch initial theme (server not ready?): {}", e.getMessage());
              }
            },
            "theme-init");
    t.setDaemon(true);
    t.start();
  }
}

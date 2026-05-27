package com.xmen.user_interface;

import com.sun.management.OperatingSystemMXBean;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.*;
import java.time.Duration;

/**
 * Live application-metrics overlay. Glass panel matched to the rest of the UI;
 * four sections (Memory, CPU, Threads, Runtime) with a 1 Hz refresh ticker that
 * shuts down when the dialog closes.
 */
@Slf4j
public final class MetricsDialog {

  private static final int HISTORY_POINTS = 60;
  private static final double PANEL_WIDTH = 820;
  private static final double PANEL_HEIGHT = 640;

  private MetricsDialog() {}

  public static void show(Stage owner) {
    Stage stage = new Stage();
    stage.initOwner(owner);
    stage.initModality(Modality.NONE);
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setTitle("Application Metrics");

    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
    OperatingSystemMXBean osBean = osBean();

    Label title = new Label("Application Metrics");
    title.getStyleClass().add("x-settings-title");

    Label sub =
        new Label(
            "Live JVM and host metrics, refreshed every second. Use this to keep an eye on "
                + "memory pressure, CPU load and thread activity while running large mutation "
                + "batches.");
    sub.getStyleClass().add("x-settings-sub");
    sub.setWrapText(true);

    Button close = new Button("Close");
    close.getStyleClass().add("x-cta-primary");
    close.setOnAction(e -> stage.close());
    HBox closeRow = new HBox(close);
    closeRow.setAlignment(Pos.CENTER_RIGHT);

    MemorySection memory = new MemorySection();
    CpuSection cpu = new CpuSection();
    ThreadsSection threads = new ThreadsSection();
    RuntimeSection runtime = new RuntimeSection(runtimeBean);

    GridPane grid = new GridPane();
    grid.setHgap(18);
    grid.setVgap(18);
    grid.add(memory.root, 0, 0);
    grid.add(cpu.root, 1, 0);
    grid.add(threads.root, 0, 1);
    grid.add(runtime.root, 1, 1);
    for (int c = 0; c < 2; c++) {
      javafx.scene.layout.ColumnConstraints col = new javafx.scene.layout.ColumnConstraints();
      col.setPercentWidth(50);
      col.setHgrow(Priority.ALWAYS);
      grid.getColumnConstraints().add(col);
    }
    for (int r = 0; r < 2; r++) {
      javafx.scene.layout.RowConstraints row = new javafx.scene.layout.RowConstraints();
      row.setVgrow(Priority.ALWAYS);
      grid.getRowConstraints().add(row);
    }

    VBox card = new VBox(14, title, sub, grid, closeRow);
    card.getStyleClass().add("x-metrics-pane");
    card.setPadding(new Insets(28, 30, 26, 30));
    card.setPrefSize(PANEL_WIDTH, PANEL_HEIGHT);
    card.setMaxSize(PANEL_WIDTH, PANEL_HEIGHT);
    VBox.setVgrow(grid, Priority.ALWAYS);

    StackPane root = new StackPane(card);
    root.getStyleClass().add("x-root");
    copyThemeClasses(owner, root);
    root.setStyle(ThemedToast.transparentPopupStyleFrom(owner));
    root.setPadding(new Insets(24));

    Scene scene = new Scene(root);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets()
        .add(MetricsDialog.class.getResource("/css/main-v2.css").toExternalForm());
    scene.setOnKeyPressed(
        e -> {
          if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) stage.close();
        });

    // Title bar is hidden by StageStyle.TRANSPARENT — let users drag the panel
    // by clicking anywhere on the card body.
    final double[] dragAnchor = new double[2];
    card.setOnMousePressed(
        e -> {
          dragAnchor[0] = e.getScreenX() - stage.getX();
          dragAnchor[1] = e.getScreenY() - stage.getY();
        });
    card.setOnMouseDragged(
        e -> {
          stage.setX(e.getScreenX() - dragAnchor[0]);
          stage.setY(e.getScreenY() - dragAnchor[1]);
        });

    stage.setScene(scene);

    Timeline ticker =
        new Timeline(
            new KeyFrame(
                javafx.util.Duration.seconds(1),
                e -> {
                  try {
                    memory.refresh(memoryBean, osBean);
                    cpu.refresh(osBean);
                    threads.refresh(threadBean);
                    runtime.refresh(runtimeBean);
                  } catch (Throwable t) {
                    log.warn("Metrics tick failed", t);
                  }
                }));
    ticker.setCycleCount(Animation.INDEFINITE);

    stage.setOnShown(
        e -> {
          if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2.0);
            stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2.0);
          }
          Platform.runLater(
              () -> {
                memory.refresh(memoryBean, osBean);
                cpu.refresh(osBean);
                threads.refresh(threadBean);
                runtime.refresh(runtimeBean);
              });
          ticker.play();
        });
    stage.setOnHiding(e -> ticker.stop());

    stage.show();
  }

  private static void copyThemeClasses(Stage owner, StackPane root) {
    if (owner == null || owner.getScene() == null || owner.getScene().getRoot() == null) return;
    owner.getScene().getRoot().getStyleClass().stream()
        .filter(style -> style.startsWith("x-theme-"))
        .forEach(root.getStyleClass()::add);
  }

  /* ====================================================================== */
  /*  Section helpers                                                        */
  /* ====================================================================== */

  /** Glass card with an icon + headline + body content. */
  private static VBox sectionCard(String titleText, Node icon, Node body) {
    Label label = new Label(titleText);
    label.getStyleClass().add("x-metric-section-title");

    HBox header = new HBox(10, icon, label);
    header.setAlignment(Pos.CENTER_LEFT);

    VBox card = new VBox(12, header, body);
    card.getStyleClass().add("x-metric-card");
    card.setPadding(new Insets(16, 18, 16, 18));
    VBox.setVgrow(body, Priority.ALWAYS);
    return card;
  }

  private static OperatingSystemMXBean osBean() {
    java.lang.management.OperatingSystemMXBean raw = ManagementFactory.getOperatingSystemMXBean();
    if (raw instanceof OperatingSystemMXBean sun) {
      return sun;
    }
    return null;
  }

  private static String formatBytes(long bytes) {
    if (bytes < 1024) return bytes + " B";
    double kb = bytes / 1024.0;
    if (kb < 1024) return String.format("%.1f KB", kb);
    double mb = kb / 1024.0;
    if (mb < 1024) return String.format("%.0f MB", mb);
    return String.format("%.2f GB", mb / 1024.0);
  }

  private static String formatDuration(long millis) {
    Duration d = Duration.ofMillis(millis);
    long days = d.toDays();
    long hours = d.minusDays(days).toHours();
    long minutes = d.minusDays(days).minusHours(hours).toMinutes();
    long seconds = d.minusDays(days).minusHours(hours).minusMinutes(minutes).getSeconds();
    if (days > 0) return String.format("%dd %dh %dm", days, hours, minutes);
    if (hours > 0) return String.format("%dh %dm %ds", hours, minutes, seconds);
    if (minutes > 0) return String.format("%dm %ds", minutes, seconds);
    return seconds + "s";
  }

  /* ====================================================================== */
  /*  Memory                                                                 */
  /* ====================================================================== */

  private static final class MemorySection {
    final VBox root;
    final Label heapUsedValue = new Label("—");
    final Label heapCommittedValue = new Label("—");
    final Label heapMaxValue = new Label("—");
    final Label appJvmUsedValue = new Label("—");
    final Label ramUsedValue = new Label("—");
    final Label ramTotalValue = new Label("—");
    final XYChart.Series<Number, Number> usedSeries = new XYChart.Series<>();
    final NumberAxis xAxis;
    final AreaChart<Number, Number> chart;
    int tick = 0;

    MemorySection() {
      usedSeries.setName("Heap used (MB)");
      xAxis = new NumberAxis();
      xAxis.setForceZeroInRange(false);
      xAxis.setTickLabelsVisible(false);
      xAxis.setMinorTickVisible(false);
      xAxis.setTickMarkVisible(false);
      NumberAxis yAxis = new NumberAxis();
      yAxis.setLabel("MB");
      yAxis.setForceZeroInRange(true);
      chart = new AreaChart<>(xAxis, yAxis, FXCollections.observableArrayList(usedSeries));
      chart.setLegendVisible(false);
      chart.setAnimated(false);
      chart.setCreateSymbols(false);
      chart.setHorizontalGridLinesVisible(true);
      chart.setVerticalGridLinesVisible(false);
      chart.getStyleClass().add("x-metric-chart");
      chart.setMinHeight(120);
      VBox.setVgrow(chart, Priority.ALWAYS);

      HBox heapStats =
          new HBox(
              22,
              labeled("Heap used", heapUsedValue),
              labeled("Committed", heapCommittedValue),
              labeled("Max", heapMaxValue));
      heapStats.setAlignment(Pos.CENTER_LEFT);

      HBox ramStats =
          new HBox(
              22,
              labeled("App JVM used", appJvmUsedValue),
              labeled("System RAM used", ramUsedValue),
              labeled("Total RAM", ramTotalValue));
      ramStats.setAlignment(Pos.CENTER_LEFT);

      VBox body = new VBox(8, heapStats, ramStats, chart);
      VBox.setVgrow(chart, Priority.ALWAYS);

      root = sectionCard("Memory", Icons.memoryChip(20, Color.WHITE), body);
    }

    void refresh(MemoryMXBean memoryBean, OperatingSystemMXBean osBean) {
      MemoryUsage heap = memoryBean.getHeapMemoryUsage();
      MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
      long usedMb = heap.getUsed() / 1024 / 1024;
      heapUsedValue.setText(formatBytes(heap.getUsed()));
      heapCommittedValue.setText(formatBytes(heap.getCommitted()));
      heapMaxValue.setText(heap.getMax() <= 0 ? "—" : formatBytes(heap.getMax()));
      appJvmUsedValue.setText(formatBytes(heap.getUsed() + nonHeap.getUsed()));

      if (osBean != null) {
        long total = osBean.getTotalMemorySize();
        long free = osBean.getFreeMemorySize();
        ramUsedValue.setText(formatBytes(total - free));
        ramTotalValue.setText(formatBytes(total));
      } else {
        ramUsedValue.setText("—");
        ramTotalValue.setText("—");
      }

      usedSeries.getData().add(new XYChart.Data<>(tick, usedMb));
      while (usedSeries.getData().size() > HISTORY_POINTS) {
        usedSeries.getData().remove(0);
      }
      if (!usedSeries.getData().isEmpty()) {
        xAxis.setLowerBound(usedSeries.getData().get(0).getXValue().doubleValue());
        xAxis.setUpperBound(tick);
      }
      tick++;
    }
  }

  /* ====================================================================== */
  /*  CPU                                                                    */
  /* ====================================================================== */

  private static final class CpuSection {
    final VBox root;
    final Label processValue = new Label("—");
    final Label systemValue = new Label("—");
    final Label coresValue = new Label("—");
    final XYChart.Series<Number, Number> series = new XYChart.Series<>();
    final NumberAxis xAxis;
    final LineChart<Number, Number> chart;
    int tick = 0;

    CpuSection() {
      series.setName("Process CPU %");
      xAxis = new NumberAxis();
      xAxis.setForceZeroInRange(false);
      xAxis.setTickLabelsVisible(false);
      xAxis.setMinorTickVisible(false);
      xAxis.setTickMarkVisible(false);
      NumberAxis yAxis = new NumberAxis(0, 100, 25);
      yAxis.setLabel("%");
      chart = new LineChart<>(xAxis, yAxis, FXCollections.observableArrayList(series));
      chart.setLegendVisible(false);
      chart.setAnimated(false);
      chart.setCreateSymbols(false);
      chart.setHorizontalGridLinesVisible(true);
      chart.setVerticalGridLinesVisible(false);
      chart.getStyleClass().add("x-metric-chart");
      chart.setMinHeight(140);
      VBox.setVgrow(chart, Priority.ALWAYS);

      processValue.getStyleClass().add("x-metric-value");
      systemValue.getStyleClass().add("x-metric-value");
      coresValue.getStyleClass().add("x-metric-value");

      HBox stats =
          new HBox(
              22,
              labeled("Process", processValue),
              labeled("System", systemValue),
              labeled("Cores", coresValue));
      stats.setAlignment(Pos.CENTER_LEFT);

      VBox body = new VBox(10, stats, chart);
      VBox.setVgrow(chart, Priority.ALWAYS);

      root = sectionCard("CPU", Icons.cpu(20, Color.WHITE), body);
    }

    void refresh(OperatingSystemMXBean bean) {
      double process = bean == null ? -1 : bean.getProcessCpuLoad();
      double system = bean == null ? -1 : bean.getCpuLoad();
      int cores = Runtime.getRuntime().availableProcessors();

      processValue.setText(formatPct(process));
      systemValue.setText(formatPct(system));
      coresValue.setText(Integer.toString(cores));

      double pct = process < 0 ? 0 : process * 100.0;
      series.getData().add(new XYChart.Data<>(tick, pct));
      while (series.getData().size() > HISTORY_POINTS) {
        series.getData().remove(0);
      }
      if (!series.getData().isEmpty()) {
        xAxis.setLowerBound(series.getData().get(0).getXValue().doubleValue());
        xAxis.setUpperBound(tick);
      }
      tick++;
    }

    private static String formatPct(double v) {
      if (v < 0) return "—";
      return String.format("%.1f%%", v * 100.0);
    }
  }

  /* ====================================================================== */
  /*  Threads                                                                */
  /* ====================================================================== */

  private static final class ThreadsSection {
    final VBox root;
    final Label activeValue = new Label("—");
    final Label peakValue = new Label("—");
    final Label daemonValue = new Label("—");
    final Label totalStartedValue = new Label("—");
    final javafx.scene.shape.Rectangle bar;
    final Label barCaption = new Label("Active vs peak");

    ThreadsSection() {
      activeValue.getStyleClass().add("x-metric-value");
      peakValue.getStyleClass().add("x-metric-value");
      daemonValue.getStyleClass().add("x-metric-value");
      totalStartedValue.getStyleClass().add("x-metric-value");
      barCaption.getStyleClass().add("x-metric-label");

      HBox topStats =
          new HBox(22, labeled("Active", activeValue), labeled("Peak", peakValue));
      topStats.setAlignment(Pos.CENTER_LEFT);

      HBox bottomStats =
          new HBox(
              22,
              labeled("Daemon", daemonValue),
              labeled("Total started", totalStartedValue));
      bottomStats.setAlignment(Pos.CENTER_LEFT);

      StackPane barTrack = new StackPane();
      barTrack.getStyleClass().add("x-metric-bar-track");
      barTrack.setMinHeight(14);
      barTrack.setPrefHeight(14);
      barTrack.setMaxWidth(Double.MAX_VALUE);

      bar = new javafx.scene.shape.Rectangle();
      bar.getStyleClass().add("x-metric-bar-fill");
      bar.setHeight(14);
      bar.setArcWidth(14);
      bar.setArcHeight(14);
      bar.widthProperty()
          .bind(
              barTrack
                  .widthProperty()
                  .multiply(
                      javafx.beans.binding.Bindings.createDoubleBinding(
                          () -> {
                            try {
                              double a = Double.parseDouble(activeValue.getText());
                              double p = Double.parseDouble(peakValue.getText());
                              if (p <= 0) return 0.0;
                              return Math.min(1.0, a / p);
                            } catch (NumberFormatException nfe) {
                              return 0.0;
                            }
                          },
                          activeValue.textProperty(),
                          peakValue.textProperty())));
      barTrack.getChildren().add(bar);
      StackPane.setAlignment(bar, Pos.CENTER_LEFT);

      VBox body = new VBox(10, topStats, bottomStats, barCaption, barTrack);
      VBox.setVgrow(barTrack, Priority.NEVER);

      root = sectionCard("Threads", Icons.threads(20, Color.WHITE), body);
    }

    void refresh(ThreadMXBean bean) {
      activeValue.setText(Integer.toString(bean.getThreadCount()));
      peakValue.setText(Integer.toString(bean.getPeakThreadCount()));
      daemonValue.setText(Integer.toString(bean.getDaemonThreadCount()));
      totalStartedValue.setText(Long.toString(bean.getTotalStartedThreadCount()));
    }
  }

  /* ====================================================================== */
  /*  Runtime / host                                                         */
  /* ====================================================================== */

  private static final class RuntimeSection {
    final VBox root;
    final Label uptimeValue = new Label("—");
    final Label vmValue;
    final Label osValue;
    final Label pidValue;

    RuntimeSection(RuntimeMXBean bean) {
      uptimeValue.getStyleClass().add("x-metric-value");
      vmValue = new Label(bean.getVmName() + " " + bean.getVmVersion());
      vmValue.getStyleClass().add("x-metric-mono");
      vmValue.setWrapText(true);
      osValue =
          new Label(
              System.getProperty("os.name", "?")
                  + " "
                  + System.getProperty("os.version", "")
                  + " · "
                  + System.getProperty("os.arch", ""));
      osValue.getStyleClass().add("x-metric-mono");
      osValue.setWrapText(true);
      pidValue = new Label(Long.toString(bean.getPid()));
      pidValue.getStyleClass().add("x-metric-value");

      HBox topStats = new HBox(22, labeled("Uptime", uptimeValue), labeled("PID", pidValue));
      topStats.setAlignment(Pos.CENTER_LEFT);

      VBox body =
          new VBox(
              10,
              topStats,
              labeled("Java runtime", vmValue),
              labeled("Host", osValue));

      Region spacer = new Region();
      VBox.setVgrow(spacer, Priority.ALWAYS);
      body.getChildren().add(spacer);

      root = sectionCard("Runtime", Icons.clock(20, Color.WHITE), body);
    }

    void refresh(RuntimeMXBean bean) {
      uptimeValue.setText(formatDuration(bean.getUptime()));
    }
  }

  private static VBox labeled(String captionText, Label valueLabel) {
    Label caption = new Label(captionText);
    caption.getStyleClass().add("x-metric-label");
    VBox box = new VBox(2, valueLabel, caption);
    box.setAlignment(Pos.TOP_LEFT);
    return box;
  }
}

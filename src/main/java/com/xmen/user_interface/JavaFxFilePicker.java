package com.xmen.user_interface;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Custom JavaFX file picker. Built specifically because the JavaFX-bundled native FileChooser on
 * Linux delegates to GTK's FileChooserDialog, which on Wayland + maximized JavaFX parents sizes
 * itself wider than the visual bounds of the active monitor — the filename column ends up
 * off-screen to the left while only the modified-date column stays visible.
 *
 * <p>This picker is a plain JavaFX Stage with a fixed 880x620 size, centered on the parent. It
 * supports the subset of {@link FileChooser} API the X-Men UI actually uses: open and save
 * dialogs, extension filters, initial directory, and initial filename. On Windows and macOS the
 * native picker still works well, so {@link #showOpenDialog} / {@link #showSaveDialog} fall
 * through to {@link FileChooser} there — Linux is the only platform routed through this class.
 */
@Slf4j
public final class JavaFxFilePicker {

  private static final double DIALOG_WIDTH = 880;
  private static final double DIALOG_HEIGHT = 620;

  private JavaFxFilePicker() {}

  /** True if the JVM is running on a Linux-family OS where the GTK chooser misbehaves. */
  static boolean shouldUseCustomPicker() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    return os.contains("linux") || os.contains("nix");
  }

  /**
   * Show a file-open dialog. On Linux this opens the custom JavaFX picker; on Windows/macOS it
   * delegates to {@link FileChooser#showOpenDialog} with {@code owner} as the parent.
   */
  public static File showOpenDialog(Window owner, FileChooser fc) {
    if (!shouldUseCustomPicker()) {
      return fc.showOpenDialog(owner);
    }
    return showCustom(owner, fc, Mode.OPEN);
  }

  /**
   * Show a file-save dialog. On Linux this opens the custom JavaFX picker (with a filename input
   * row); on Windows/macOS it delegates to {@link FileChooser#showSaveDialog}.
   */
  public static File showSaveDialog(Window owner, FileChooser fc) {
    if (!shouldUseCustomPicker()) {
      return fc.showSaveDialog(owner);
    }
    return showCustom(owner, fc, Mode.SAVE);
  }

  private enum Mode {
    OPEN,
    SAVE
  }

  private static File showCustom(Window owner, FileChooser fc, Mode mode) {
    Stage dlg = new Stage(StageStyle.UTILITY);
    dlg.initModality(Modality.WINDOW_MODAL);
    if (owner != null) dlg.initOwner(owner);
    dlg.setTitle(fc.getTitle() == null ? (mode == Mode.OPEN ? "Open" : "Save") : fc.getTitle());

    File initial = fc.getInitialDirectory();
    if (initial == null || !initial.isDirectory()) {
      String home = System.getProperty("user.home");
      if (home != null) {
        File h = new File(home);
        if (h.isDirectory()) initial = h;
      }
    }
    if (initial == null) initial = new File(".").getAbsoluteFile();

    final File[] currentDir = {initial};

    // ----- Top: navigation row -----
    Button upBtn = new Button("Up");
    upBtn.getStyleClass().add("x-cta-secondary");
    TextField pathField = new TextField(initial.getAbsolutePath());
    pathField.setEditable(true);
    HBox.setHgrow(pathField, Priority.ALWAYS);
    Button homeBtn = new Button("Home");
    homeBtn.getStyleClass().add("x-cta-secondary");
    HBox navRow = new HBox(8, upBtn, homeBtn, pathField);
    navRow.setAlignment(Pos.CENTER_LEFT);

    // ----- Middle: file list -----
    ListView<Entry> list = new ListView<>();
    list.setPlaceholder(new Label("(empty folder)"));
    VBox.setVgrow(list, Priority.ALWAYS);

    // ----- Bottom: filename (save only) + filter + buttons -----
    TextField nameField = new TextField();
    if (mode == Mode.SAVE && fc.getInitialFileName() != null) {
      nameField.setText(fc.getInitialFileName());
    }
    Label nameLabel = new Label("Name:");
    HBox nameRow = new HBox(8, nameLabel, nameField);
    nameRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(nameField, Priority.ALWAYS);
    nameRow.setVisible(mode == Mode.SAVE);
    nameRow.setManaged(mode == Mode.SAVE);

    ComboBox<FileChooser.ExtensionFilter> filterBox = new ComboBox<>();
    filterBox.setItems(FXCollections.observableArrayList(fc.getExtensionFilters()));
    if (!fc.getExtensionFilters().isEmpty()) {
      FileChooser.ExtensionFilter active =
          fc.getSelectedExtensionFilter() != null
              ? fc.getSelectedExtensionFilter()
              : fc.getExtensionFilters().get(0);
      filterBox.setValue(active);
    }
    filterBox.setVisibleRowCount(6);
    Button cancelBtn = new Button("Cancel");
    cancelBtn.getStyleClass().add("x-cta-secondary");
    Button actionBtn = new Button(mode == Mode.OPEN ? "Open" : "Save");
    actionBtn.getStyleClass().add("x-cta-primary");
    actionBtn.setDefaultButton(true);
    cancelBtn.setCancelButton(true);

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox actionRow = new HBox(8, filterBox, spacer, cancelBtn, actionBtn);
    actionRow.setAlignment(Pos.CENTER_RIGHT);

    VBox bottom = new VBox(8, nameRow, actionRow);

    VBox content = new VBox(10, navRow, list, bottom);
    content.setPadding(new Insets(12));
    content.setFillWidth(true);

    StackPane root = new StackPane(content);
    root.getStyleClass().add("x-root");
    root.setBackground(
        new Background(new BackgroundFill(Color.web("#020817"), CornerRadii.EMPTY, Insets.EMPTY)));
    root.setPrefSize(DIALOG_WIDTH, DIALOG_HEIGHT);

    Scene scene = new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT);
    scene.setFill(Color.web("#020817"));
    // Reuse the main CSS so .x-cta-primary / .x-cta-secondary themed buttons match the app.
    try {
      String css = JavaFxFilePicker.class.getResource("/css/main-v2.css").toExternalForm();
      scene.getStylesheets().add(css);
    } catch (Exception ignored) {
      // CSS missing in test/dev runs — default look is acceptable.
    }
    dlg.setScene(scene);
    dlg.setResizable(true);
    dlg.setMinWidth(640);
    dlg.setMinHeight(480);

    // Centre the dialog on the active monitor so it never opens off-screen.
    Rectangle2D screen = activeScreen(owner);
    dlg.setX(screen.getMinX() + Math.max(0, (screen.getWidth() - DIALOG_WIDTH) / 2.0));
    dlg.setY(screen.getMinY() + Math.max(0, (screen.getHeight() - DIALOG_HEIGHT) / 2.0));

    // ----- Behaviour wiring -----
    final File[] selected = {null};

    Runnable refresh =
        () -> {
          File dir = currentDir[0];
          List<Entry> entries = listDirectory(dir, filterBox.getValue());
          list.setItems(FXCollections.observableArrayList(entries));
          pathField.setText(dir.getAbsolutePath());
          upBtn.setDisable(dir.getParentFile() == null);
        };

    upBtn.setOnAction(
        e -> {
          File parent = currentDir[0].getParentFile();
          if (parent != null && parent.isDirectory()) {
            currentDir[0] = parent;
            refresh.run();
          }
        });

    homeBtn.setOnAction(
        e -> {
          String home = System.getProperty("user.home");
          if (home != null) {
            File h = new File(home);
            if (h.isDirectory()) {
              currentDir[0] = h;
              refresh.run();
            }
          }
        });

    pathField.setOnAction(
        e -> {
          File typed = new File(pathField.getText().trim());
          if (typed.isDirectory()) {
            currentDir[0] = typed;
            refresh.run();
          } else if (typed.isFile() && mode == Mode.OPEN) {
            selected[0] = typed;
            dlg.close();
          } else {
            pathField.setText(currentDir[0].getAbsolutePath());
          }
        });

    ChangeListener<FileChooser.ExtensionFilter> filterListener = (obs, oldF, newF) -> refresh.run();
    filterBox.valueProperty().addListener(filterListener);

    list.setOnMouseClicked(
        e -> {
          if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2) return;
          Entry sel = list.getSelectionModel().getSelectedItem();
          if (sel == null) return;
          if (sel.file.isDirectory()) {
            currentDir[0] = sel.file;
            refresh.run();
          } else if (mode == Mode.OPEN) {
            selected[0] = sel.file;
            dlg.close();
          } else {
            nameField.setText(sel.file.getName());
          }
        });
    list.setOnKeyPressed(
        e -> {
          if (e.getCode() == KeyCode.ENTER) {
            Entry sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (sel.file.isDirectory()) {
              currentDir[0] = sel.file;
              refresh.run();
            } else if (mode == Mode.OPEN) {
              selected[0] = sel.file;
              dlg.close();
            } else {
              nameField.setText(sel.file.getName());
              actionBtn.requestFocus();
            }
          } else if (e.getCode() == KeyCode.BACK_SPACE) {
            File parent = currentDir[0].getParentFile();
            if (parent != null && parent.isDirectory()) {
              currentDir[0] = parent;
              refresh.run();
              e.consume();
            }
          }
        });

    cancelBtn.setOnAction(e -> dlg.close());

    actionBtn.setOnAction(
        e -> {
          if (mode == Mode.OPEN) {
            Entry sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (sel.file.isDirectory()) {
              currentDir[0] = sel.file;
              refresh.run();
              return;
            }
            selected[0] = sel.file;
            dlg.close();
          } else {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;
            File target = new File(currentDir[0], applyDefaultExtension(name, filterBox.getValue()));
            if (target.exists()) {
              Alert confirm =
                  new Alert(
                      Alert.AlertType.CONFIRMATION,
                      target.getName() + " already exists. Replace it?",
                      ButtonType.NO,
                      ButtonType.YES);
              confirm.initOwner(dlg);
              confirm.setHeaderText(null);
              ButtonType result = confirm.showAndWait().orElse(ButtonType.NO);
              if (result != ButtonType.YES) return;
            }
            selected[0] = target;
            dlg.close();
          }
        });

    refresh.run();
    Platform.runLater(list::requestFocus);
    dlg.showAndWait();
    return selected[0];
  }

  private static List<Entry> listDirectory(File dir, FileChooser.ExtensionFilter filter) {
    File[] kids = dir.listFiles();
    if (kids == null) return List.of();
    Set<String> globs = filterGlobs(filter);
    boolean wildcard = globs.isEmpty() || globs.contains("*.*") || globs.contains("*");
    List<Entry> dirs = new ArrayList<>();
    List<Entry> files = new ArrayList<>();
    for (File f : kids) {
      String name = f.getName();
      if (name.startsWith(".")) continue; // skip dotfiles in the picker view
      if (f.isDirectory()) {
        dirs.add(new Entry(f));
      } else if (wildcard || matchesGlobs(name, globs)) {
        files.add(new Entry(f));
      }
    }
    Comparator<Entry> byName = Comparator.comparing(e -> e.file.getName().toLowerCase(Locale.ROOT));
    dirs.sort(byName);
    files.sort(byName);
    List<Entry> result = new ArrayList<>(dirs.size() + files.size());
    result.addAll(dirs);
    result.addAll(files);
    return result;
  }

  private static Set<String> filterGlobs(FileChooser.ExtensionFilter filter) {
    Set<String> out = new LinkedHashSet<>();
    if (filter == null) return out;
    for (String ext : filter.getExtensions()) {
      if (ext != null) out.add(ext.toLowerCase(Locale.ROOT));
    }
    return out;
  }

  private static boolean matchesGlobs(String name, Set<String> globs) {
    String lower = name.toLowerCase(Locale.ROOT);
    for (String glob : globs) {
      if (glob.startsWith("*.")) {
        String suffix = glob.substring(1); // ".xml"
        if (suffix.equals(".*")) return true;
        if (lower.endsWith(suffix)) return true;
      } else if (glob.equals(lower)) {
        return true;
      }
    }
    return false;
  }

  /**
   * If the user typed a bare filename (no extension) and the active filter has a single
   * extension glob, append it automatically — mirrors what GTK / Win32 / Cocoa savers do.
   */
  private static String applyDefaultExtension(String name, FileChooser.ExtensionFilter filter) {
    if (filter == null) return name;
    if (name.contains(".")) return name;
    for (String ext : filter.getExtensions()) {
      if (ext != null && ext.startsWith("*.") && !ext.equals("*.*")) {
        return name + ext.substring(1);
      }
    }
    return name;
  }

  private static Rectangle2D activeScreen(Window owner) {
    if (owner != null && !Double.isNaN(owner.getX()) && !Double.isNaN(owner.getY())) {
      double cx = owner.getX() + (owner.getWidth() > 0 ? owner.getWidth() / 2.0 : 1);
      double cy = owner.getY() + (owner.getHeight() > 0 ? owner.getHeight() / 2.0 : 1);
      for (Screen s : Screen.getScreens()) {
        Rectangle2D b = s.getVisualBounds();
        if (b.contains(cx, cy)) return b;
      }
    }
    Screen primary = Screen.getPrimary();
    return primary != null
        ? primary.getVisualBounds()
        : new Rectangle2D(0, 0, DIALOG_WIDTH, DIALOG_HEIGHT);
  }

  /** Wraps a {@link File} with a custom toString() so the list view renders folder/file names. */
  private static final class Entry {
    final File file;

    Entry(File file) {
      this.file = file;
    }

    @Override
    public String toString() {
      return file.isDirectory() ? file.getName() + "/" : file.getName();
    }
  }
}

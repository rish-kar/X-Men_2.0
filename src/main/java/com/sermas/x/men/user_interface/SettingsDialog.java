package com.sermas.x.men.user_interface;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sermas.x.men.config.ThemeCatalog.Theme;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import javafx.util.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Theme-aware settings dialog with three tabs (Vocabulary, Themes, Preferences).
 *
 * <p>Vocabulary tab supports profile load/delete and YAML import; importing a YAML prompts the
 * user to save it as a new profile after validation succeeds. Detect-from-.spthy creates a new
 * named profile after a confirmation popup.
 */
@Slf4j
public class SettingsDialog {

  private static final String BASE = "http://localhost:";
  private final OkHttpClient http = new OkHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

  private final int serverPort;
  private final Consumer<String> onThemeApplied;
  private final Consumer<Map<String, Object>> onPreferencesChanged;
  private final Consumer<Theme> onLogoSwap;

  private final ObservableList<VocabRow> vocabRows = FXCollections.observableArrayList();
  private final AtomicReference<String> selectedThemeId = new AtomicReference<>();
  private TilePane themeTiles;
  private CheckBox cbValidateOnUpload;
  private CheckBox cbShowAnimations;
  private CheckBox cbKeepDerivationTree;

  private final ObservableList<String> profileNames = FXCollections.observableArrayList();
  private ComboBox<String> profilePicker;

  private StackPane dialogRoot;

  public SettingsDialog(
      int serverPort,
      Consumer<String> onThemeApplied,
      Consumer<Map<String, Object>> onPreferencesChanged,
      Consumer<Theme> onLogoSwap) {
    this.serverPort = serverPort;
    this.onThemeApplied = onThemeApplied;
    this.onPreferencesChanged = onPreferencesChanged;
    this.onLogoSwap = onLogoSwap;
  }

  public void show(Stage owner) {
    Stage stage = new Stage();
    stage.initOwner(owner);
    stage.initModality(Modality.APPLICATION_MODAL);
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setTitle("X-Men Settings");

    VBox panel = new VBox(18);
    panel.getStyleClass().add("x-settings-pane");
    panel.setPadding(new Insets(28));
    panel.setPrefWidth(980);
    panel.setPrefHeight(860);

    Label title = new Label("Settings");
    title.getStyleClass().add("x-settings-title");
    Label sub =
        new Label(
            "Tune X-Men's vocabulary, palette, and workflow toggles. "
                + "Changes persist across runs.");
    sub.getStyleClass().add("x-settings-sub");

    TabPane tabs = new TabPane();
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    tabs.getTabs().addAll(buildVocabularyTab(owner), buildThemeTab(), buildPreferencesTab());
    VBox.setVgrow(tabs, Priority.ALWAYS);

    Button save = new Button("Save");
    save.getStyleClass().add("x-cta-primary");
    save.setOnAction(e -> saveAll(owner, stage));
    Animations.hoverLift(save, 1.04);

    Button close = new Button("Close");
    close.getStyleClass().add("x-cta-secondary");
    close.setOnAction(e -> stage.close());
    Animations.hoverLift(close, 1.03);

    StackPane saveWrap = new StackPane(save);
    saveWrap.getStyleClass().add("x-shadow-room");
    StackPane closeWrap = new StackPane(close);
    closeWrap.getStyleClass().add("x-shadow-room");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox footer = new HBox(4, spacer, saveWrap, closeWrap);
    footer.setAlignment(Pos.CENTER_RIGHT);

    panel.getChildren().addAll(title, sub, tabs, footer);

    dialogRoot = new StackPane(panel);
    dialogRoot.getStyleClass().addAll("x-root", "x-settings-scene");
    dialogRoot.setPadding(new Insets(24));
    dialogRoot.setStyle(ThemedToast.transparentPopupStyleFrom(owner));

    Scene scene = new Scene(dialogRoot);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(getClass().getResource("/css/main-v2.css").toExternalForm());

    stage.setScene(scene);

    // Multi-monitor positioning: centre over the owner stage's monitor.
    stage.setOnShown(
        e -> {
          Rectangle2D screen = ThemedToast.screenFor(owner);
          double w = stage.getWidth();
          double h = stage.getHeight();
          double x;
          double y;
          if (owner != null) {
            x = owner.getX() + (owner.getWidth() - w) / 2.0;
            y = owner.getY() + (owner.getHeight() - h) / 2.0;
          } else {
            x = screen.getMinX() + (screen.getWidth() - w) / 2.0;
            y = screen.getMinY() + (screen.getHeight() - h) / 2.0;
          }
          if (x < screen.getMinX() + 8) x = screen.getMinX() + 8;
          if (x + w > screen.getMaxX() - 8) x = screen.getMaxX() - w - 8;
          if (y < screen.getMinY() + 8) y = screen.getMinY() + 8;
          if (y + h > screen.getMaxY() - 8) y = screen.getMaxY() - h - 8;
          stage.setX(x);
          stage.setY(y);
        });

    FadeTransition fade = new FadeTransition(Duration.millis(260), dialogRoot);
    fade.setFromValue(0.0);
    fade.setToValue(1.0);
    fade.play();

    stage.showAndWait();
  }

  private void saveAll(Stage hostStage, Stage thisStage) {
    Map<String, Object> vocabBody = unflatten(vocabRows);
    String themeId = selectedThemeId.get();
    Map<String, Object> prefs = new LinkedHashMap<>();
    prefs.put("validateOnUpload", cbValidateOnUpload != null && cbValidateOnUpload.isSelected());
    prefs.put("showAnimations", cbShowAnimations != null && cbShowAnimations.isSelected());
    prefs.put(
        "keepDerivationTree", cbKeepDerivationTree != null && cbKeepDerivationTree.isSelected());

    runHttp(
        () -> {
          boolean ok = true;
          ok &= postJson("/api/settings/vocabulary", vocabBody);
          if (themeId != null && !themeId.isBlank()) {
            ok &= postJson("/api/settings/themes/active", Map.of("id", themeId));
          }
          ok &= postJson("/api/settings/preferences", prefs);

          final boolean success = ok;
          Platform.runLater(
              () -> {
                if (success) {
                  if (themeId != null && onThemeApplied != null) onThemeApplied.accept(themeId);
                  if (onPreferencesChanged != null) onPreferencesChanged.accept(prefs);
                  ThemedToast.show(hostStage, "Settings saved.");
                  javafx.animation.PauseTransition wait =
                      new javafx.animation.PauseTransition(javafx.util.Duration.millis(900));
                  wait.setOnFinished(ev -> {
                    if (thisStage != null) thisStage.close();
                  });
                  wait.play();
                } else {
                  ThemedToast.show(hostStage, "Some settings failed to save.");
                }
              });
        },
        "save all");
  }

  private boolean postJson(String path, Map<String, Object> body) throws Exception {
    RequestBody rb =
        RequestBody.create(json.writeValueAsBytes(body), MediaType.parse("application/json"));
    Response r =
        http.newCall(new Request.Builder().url(BASE + serverPort + path).post(rb).build())
            .execute();
    try (r) {
      return r.isSuccessful();
    }
  }

  /* ------------------------------------------------------------------ */
  /*  Vocabulary tab                                                    */
  /* ------------------------------------------------------------------ */

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Tab buildVocabularyTab(Stage owner) {
    Tab tab = new Tab("Vocabulary");

    Label hint =
        new Label(
            "Map each semantic role to a single atomic value. Use Import YAML to bring in a "
                + "configuration (you'll be asked to save it as a new profile after validation), "
                + "or detect a vocabulary straight from a .spthy file.");
    hint.getStyleClass().add("x-settings-sub");
    hint.setWrapText(true);

    TableView<VocabRow> table = new TableView<>(vocabRows);
    table.setEditable(true);
    table.getStyleClass().add("x-vocab-table");
    VBox.setVgrow(table, Priority.ALWAYS);

    TableColumn<VocabRow, String> keyCol = new TableColumn<>("FIELD");
    keyCol.setCellValueFactory(new PropertyValueFactory<>("key"));
    keyCol.setPrefWidth(320);
    keyCol.setEditable(false);

    TableColumn<VocabRow, String> valCol = new TableColumn<>("VALUE (atomic)");
    valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
    valCol.setPrefWidth(540);
    valCol.setEditable(true);
    valCol.setCellFactory(
        (Callback)
            (Callback<TableColumn<VocabRow, String>, TableCell<VocabRow, String>>)
                col ->
                    new TextFieldTableCell<>(
                        new javafx.util.converter.DefaultStringConverter()));
    valCol.setOnEditCommit(
        ev -> {
          String v = ev.getNewValue() == null ? "" : ev.getNewValue().trim();
          v = v.replaceAll("[,\\s]+", "");
          ev.getRowValue().setValue(v);
          ev.getTableView().refresh();
        });

    table.getColumns().addAll(keyCol, valCol);

    // ----- Profiles row (Load + Delete) -----
    Label profileLabel = new Label("Profile:");
    profileLabel.getStyleClass().add("x-settings-sub");

    profilePicker = new ComboBox<>(profileNames);
    profilePicker.setPrefWidth(220);
    profilePicker.setPromptText("Pick a profile");
    profilePicker.getStyleClass().addAll("x-input", "x-glass-choice");
    profilePicker.setVisibleRowCount(8);
    profilePicker.setCellFactory(list -> profileCell());
    profilePicker.setButtonCell(profileCell());
    // Switching the picker also refreshes the displayed vocabulary so the
    // table always mirrors the currently-selected profile (Load activates it
    // server-side; this listener gives the UI immediate feedback).
    profilePicker
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, oldName, newName) -> {
          if (newName != null && !newName.isBlank()) previewProfile(newName);
        });

    Button loadProfile = new Button("Load");
    loadProfile.getStyleClass().add("x-cta-secondary");
    loadProfile.setOnAction(e -> loadProfile(owner));

    Button deleteProfile = new Button("Delete");
    deleteProfile.getStyleClass().add("x-cta-secondary");
    deleteProfile.setOnAction(e -> deleteProfile(owner));

    HBox profileRow =
        new HBox(8, profileLabel, profilePicker, loadProfile, deleteProfile);
    profileRow.setAlignment(Pos.CENTER_LEFT);

    // ----- Actions row -----
    Button detect = new Button("Detect from .spthy");
    detect.getStyleClass().add("x-cta-secondary");
    detect.setOnAction(e -> detectFromSpthy(owner));

    Button reset = new Button("Reset defaults");
    reset.getStyleClass().add("x-cta-secondary");
    reset.setOnAction(e -> resetVocabulary());
    Animations.hoverLift(reset, 1.03);

    Button export = new Button("Export YAML");
    export.getStyleClass().add("x-cta-secondary");
    export.setOnAction(e -> exportVocabulary(owner));

    Button importYaml = new Button("Import YAML");
    importYaml.getStyleClass().add("x-cta-secondary");
    importYaml.setOnAction(e -> importVocabulary(owner));

    HBox actions = new HBox(10, detect, reset, export, importYaml);
    actions.setAlignment(Pos.CENTER_LEFT);

    VBox content = new VBox(12, hint, profileRow, table, actions);
    content.setPadding(new Insets(8, 0, 0, 0));
    VBox.setVgrow(table, Priority.ALWAYS);

    tab.setContent(content);
    loadVocabulary();
    loadProfiles();
    return tab;
  }

  /* ----- vocabulary network ops ----- */

  @SuppressWarnings("unchecked")
  private void loadVocabulary() {
    runHttp(
        () -> {
          Response r =
              http.newCall(
                      new Request.Builder()
                          .url(BASE + serverPort + "/api/settings/vocabulary")
                          .build())
                  .execute();
          try (r) {
            if (!r.isSuccessful() || r.body() == null) return;
            Map<String, Object> body = json.readValue(r.body().bytes(), Map.class);
            Platform.runLater(
                () -> {
                  vocabRows.clear();
                  flatten("", body, vocabRows);
                });
          }
        },
        "load vocabulary");
  }

  private void resetVocabulary() {
    runHttp(
        () -> {
          http.newCall(
                  new Request.Builder()
                      .url(BASE + serverPort + "/api/settings/vocabulary/reset")
                      .post(RequestBody.create(new byte[0]))
                      .build())
              .execute()
              .close();
          Platform.runLater(this::loadVocabulary);
        },
        "reset vocabulary");
  }

  private void exportVocabulary(Stage owner) {
    FileChooser fc = new FileChooser();
    fc.setTitle("Export vocabulary as YAML");
    fc.setInitialFileName("vocabulary.yaml");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML", "*.yaml", "*.yml"));
    File target = fc.showSaveDialog(owner);
    if (target == null) return;
    runHttp(
        () -> {
          Response r =
              http.newCall(
                      new Request.Builder()
                          .url(BASE + serverPort + "/api/settings/vocabulary/export")
                          .build())
                  .execute();
          try (r) {
            if (r.body() == null) return;
            Files.write(target.toPath(), r.body().bytes());
            Platform.runLater(() -> ThemedToast.show(owner, "Exported to " + target.getName()));
          }
        },
        "export vocabulary");
  }

  /**
   * Import a YAML vocabulary. Parses defensively first; on success, applies it to the table and
   * prompts the user to save it as a named profile.
   */
  private void importVocabulary(Stage owner) {
    FileChooser fc = new FileChooser();
    fc.setTitle("Import vocabulary YAML");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML", "*.yaml", "*.yml"));
    File source = fc.showOpenDialog(owner);
    if (source == null) return;
    runHttp(
        () -> {
          byte[] bytes = Files.readAllBytes(source.toPath());
          try {
            yaml.readTree(bytes);
          } catch (Exception parseError) {
            Platform.runLater(
                () ->
                    ThemedDialog.show(
                        owner,
                        ThemedDialog.Kind.ERROR,
                        "Invalid YAML",
                        parseError.getMessage()));
            return;
          }
          RequestBody fileBody =
              RequestBody.create(bytes, MediaType.parse("application/x-yaml"));
          RequestBody mp =
              new MultipartBody.Builder()
                  .setType(MultipartBody.FORM)
                  .addFormDataPart("file", source.getName(), fileBody)
                  .build();
          Response r =
              http.newCall(
                      new Request.Builder()
                          .url(BASE + serverPort + "/api/settings/vocabulary/import")
                          .post(mp)
                          .build())
              .execute();
          try (r) {
            boolean ok = r.isSuccessful();
            Platform.runLater(
                () -> {
                  if (ok) {
                    loadVocabulary();
                    String suggested = stripExt(source.getName());
                    promptSaveAsProfile(
                        owner,
                        suggested,
                        "Imported " + source.getName() + ".\nSave it as a new profile?");
                  } else {
                    ThemedDialog.show(
                        owner,
                        ThemedDialog.Kind.ERROR,
                        "Import rejected",
                        "The server rejected the YAML payload.");
                  }
                });
          }
        },
        "import vocabulary");
  }

  /** Detect-from-.spthy: parse, then prompt for a profile name and save. */
  private void detectFromSpthy(Stage owner) {
    FileChooser fc = new FileChooser();
    fc.setTitle("Pick a .spthy file to detect vocabulary from");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tamarin SPTHY", "*.spthy"));
    File source = fc.showOpenDialog(owner);
    if (source == null) return;

    runHttp(
        () -> {
          byte[] bytes = Files.readAllBytes(source.toPath());
          RequestBody fileBody =
              RequestBody.create(bytes, MediaType.parse("text/plain"));
          RequestBody mp =
              new MultipartBody.Builder()
                  .setType(MultipartBody.FORM)
                  .addFormDataPart("file", source.getName(), fileBody)
                  .build();
          Response r =
              http.newCall(
                      new Request.Builder()
                          .url(BASE + serverPort + "/api/settings/vocabulary/detect")
                          .post(mp)
                          .build())
                  .execute();
          try (r) {
            if (!r.isSuccessful() || r.body() == null) {
              Platform.runLater(
                  () ->
                      ThemedDialog.show(
                          owner,
                          ThemedDialog.Kind.ERROR,
                          "Detection failed",
                          "The file did not pass validation; nothing was detected."));
              return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = json.readValue(r.body().bytes(), Map.class);
            Platform.runLater(
                () -> {
                  vocabRows.clear();
                  flatten("", body, vocabRows);
                  String suggested = stripExt(source.getName());
                  promptSaveAsProfile(
                      owner,
                      suggested,
                      "Vocabulary detected from "
                          + source.getName()
                          + ".\nSave it as a new profile?");
                });
          }
        },
        "detect vocabulary");
  }

  /**
   * Show a confirmation dialog with a pre-filled profile name (editable). Posts the active
   * vocabulary to the server under that name once the user confirms.
   */
  private void promptSaveAsProfile(Stage owner, String suggestedName, String question) {
    Stage stage = new Stage();
    if (owner != null) stage.initOwner(owner);
    stage.initModality(Modality.APPLICATION_MODAL);
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setAlwaysOnTop(true);

    Label title = new Label("Save as profile?");
    title.getStyleClass().add("x-dialog-title");
    Label body = new Label(question);
    body.getStyleClass().add("x-dialog-body");
    body.setWrapText(true);
    body.setMaxWidth(420);

    TextField nameField = new TextField(suggestedName);
    nameField.getStyleClass().add("x-input");

    Button yes = new Button("Save profile");
    yes.getStyleClass().add("x-cta-primary");
    Button no = new Button("Skip");
    no.getStyleClass().add("x-cta-secondary");

    yes.setOnAction(
        e -> {
          String name = nameField.getText() == null ? "" : nameField.getText().trim();
          if (name.isEmpty()) {
            ThemedToast.show(stage, "Pick a name first.");
            return;
          }
          stage.close();
          persistProfile(owner, name);
        });
    no.setOnAction(e -> stage.close());

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    StackPane yesWrap = new StackPane(yes);
    yesWrap.getStyleClass().add("x-shadow-room");
    StackPane noWrap = new StackPane(no);
    noWrap.getStyleClass().add("x-shadow-room");
    HBox buttons = new HBox(10, spacer, noWrap, yesWrap);

    VBox card = new VBox(14, title, body, nameField, buttons);
    card.getStyleClass().addAll("x-dialog-card", "x-dialog-info");
    card.setPadding(new Insets(22, 24, 18, 24));
    card.setMaxWidth(520);

    StackPane wrap = new StackPane(card);
    wrap.getStyleClass().add("x-shadow-room");

    StackPane root = new StackPane(wrap);
    root.getStyleClass().add("x-root");
    root.setStyle(ThemedToast.transparentPopupStyleFrom(owner));

    Scene scene = new Scene(root);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(getClass().getResource("/css/main-v2.css").toExternalForm());
    stage.setScene(scene);

    stage.setOnShown(
        e -> {
          Rectangle2D screen = ThemedToast.screenFor(owner);
          double w = stage.getWidth();
          double h = stage.getHeight();
          double x =
              owner != null ? owner.getX() + (owner.getWidth() - w) / 2.0 : screen.getMinX() + 40;
          double y =
              owner != null ? owner.getY() + (owner.getHeight() - h) / 2.0 : screen.getMinY() + 40;
          stage.setX(Math.max(screen.getMinX() + 8, Math.min(x, screen.getMaxX() - w - 8)));
          stage.setY(Math.max(screen.getMinY() + 8, Math.min(y, screen.getMaxY() - h - 8)));
        });

    stage.show();
  }

  /** Save the current live vocabulary under {@code name}, then refresh the profile list. */
  private void persistProfile(Stage owner, String name) {
    runHttp(
        () -> {
          Response r =
              http.newCall(
                      new Request.Builder()
                          .url(
                              BASE
                                  + serverPort
                                  + "/api/settings/vocabulary/profiles/"
                                  + java.net.URLEncoder.encode(name, "UTF-8"))
                          .post(RequestBody.create(new byte[0]))
                          .build())
                  .execute();
          try (r) {
            boolean ok = r.isSuccessful();
            Platform.runLater(
                () -> {
                  if (ok) {
                    loadProfiles();
                    if (profilePicker != null) profilePicker.getSelectionModel().select(name);
                    ThemedToast.show(owner, "Saved profile '" + name + "'.");
                  } else {
                    ThemedDialog.show(
                        owner,
                        ThemedDialog.Kind.ERROR,
                        "Save failed",
                        "Could not persist profile.");
                  }
                });
          }
        },
        "save profile");
  }

  /* ----- profile network ops ----- */

  @SuppressWarnings("unchecked")
  private void loadProfiles() {
    runHttp(
        () -> {
          Response r =
              http.newCall(
                      new Request.Builder()
                          .url(BASE + serverPort + "/api/settings/vocabulary/profiles")
                          .build())
                  .execute();
          try (r) {
            if (!r.isSuccessful() || r.body() == null) return;
            Map<String, Object> body = json.readValue(r.body().bytes(), Map.class);
            List<String> names = (List<String>) body.getOrDefault("profiles", List.of());
            Platform.runLater(
                () -> {
                  profileNames.clear();
                  profileNames.addAll(names);
                  if (profilePicker != null) {
                    String def = names.stream()
                        .filter("Oyster"::equalsIgnoreCase)
                        .findFirst()
                        .orElse(names.isEmpty() ? null : names.get(0));
                    if (def != null) profilePicker.getSelectionModel().select(def);
                  }
                });
          }
        },
        "load profiles");
  }

  private ListCell<String> profileCell() {
    ListCell<String> cell = new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item);
      }
    };
    cell.getStyleClass().add("x-glass-cell");
    return cell;
  }

  /** Hit the activate endpoint AND reload the table so the user sees the change. */
  private void loadProfile(Stage owner) {
    String name = profilePicker.getValue();
    if (name == null || name.isBlank()) {
      ThemedToast.show(owner, "Pick a profile from the dropdown first.");
      return;
    }
    runHttp(
        () -> {
          Response r =
              http.newCall(
                      new Request.Builder()
                          .url(
                              BASE
                                  + serverPort
                                  + "/api/settings/vocabulary/profiles/"
                                  + java.net.URLEncoder.encode(name, "UTF-8")
                                  + "/activate")
                          .post(RequestBody.create(new byte[0]))
                          .build())
                  .execute();
          try (r) {
            boolean ok = r.isSuccessful();
            Platform.runLater(
                () -> {
                  if (ok) {
                    loadVocabulary();
                    ThemedToast.show(owner, "Loaded profile '" + name + "'.");
                  } else {
                    ThemedDialog.show(
                        owner,
                        ThemedDialog.Kind.ERROR,
                        "Activation failed",
                        "Could not activate profile '" + name + "'.");
                  }
                });
          }
        },
        "activate profile");
  }

  /**
   * Pull the named profile's JSON without activating it server-side, so changing the picker
   * shows the user what they're about to Load (matches the user's expectation that switching
   * the dropdown should refresh the vocab they see).
   */
  @SuppressWarnings("unchecked")
  private void previewProfile(String name) {
    runHttp(
        () -> {
          Response r =
              http.newCall(
                      new Request.Builder()
                          .url(
                              BASE
                                  + serverPort
                                  + "/api/settings/vocabulary/profiles/"
                                  + java.net.URLEncoder.encode(name, "UTF-8")
                                  + "/activate")
                          .post(RequestBody.create(new byte[0]))
                          .build())
                  .execute();
          try (r) {
            if (!r.isSuccessful()) return;
            // After activate, /vocabulary reflects the active profile.
            Platform.runLater(this::loadVocabulary);
          }
        },
        "preview profile " + name);
  }

  private void deleteProfile(Stage owner) {
    String name = profilePicker.getValue();
    if (name == null || name.isBlank()) {
      ThemedToast.show(owner, "Pick a profile from the dropdown first.");
      return;
    }
    ThemedDialog.confirm(
        owner,
        "Delete profile?",
        "Permanently remove profile '" + name + "'?",
        () ->
            runHttp(
                () -> {
                  Response r =
                      http.newCall(
                              new Request.Builder()
                                  .url(
                                      BASE
                                          + serverPort
                                          + "/api/settings/vocabulary/profiles/"
                                          + java.net.URLEncoder.encode(name, "UTF-8"))
                                  .delete()
                                  .build())
                          .execute();
                  try (r) {
                    boolean ok = r.isSuccessful();
                    Platform.runLater(
                        () -> {
                          if (ok) {
                            loadProfiles();
                            ThemedToast.show(owner, "Deleted '" + name + "'.");
                          } else {
                            ThemedDialog.show(
                                owner,
                                ThemedDialog.Kind.ERROR,
                                "Delete failed",
                                "Could not delete profile '" + name + "'.");
                          }
                        });
                  }
                },
                "delete profile"),
        null);
  }

  /* ------------------------------------------------------------------ */
  /*  Themes tab                                                        */
  /* ------------------------------------------------------------------ */

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Tab buildThemeTab() {
    Tab tab = new Tab("Themes");

    Label hint =
        new Label(
            "Pick a palette. Selection is previewed everywhere instantly — "
                + "click Save to persist.");
    hint.getStyleClass().add("x-settings-sub");

    themeTiles = new TilePane();
    themeTiles.setHgap(12);
    themeTiles.setVgap(14);
    themeTiles.setPrefColumns(6);
    themeTiles.setPrefTileWidth(124);
    themeTiles.setPrefTileHeight(112);
    themeTiles.setMaxWidth(Double.MAX_VALUE);
    themeTiles.setAlignment(Pos.TOP_LEFT);

    runHttp(
        () -> {
          Response r =
              http.newCall(
                      new Request.Builder().url(BASE + serverPort + "/api/settings/themes").build())
                  .execute();
          try (r) {
            if (!r.isSuccessful() || r.body() == null) return;
            Map<String, Object> body = json.readValue(r.body().bytes(), Map.class);
            String active = String.valueOf(body.getOrDefault("active", ""));
            selectedThemeId.set(active);
            List<Map<String, Object>> catalog =
                (List<Map<String, Object>>) body.getOrDefault("catalog", List.of());
            Platform.runLater(
                () -> {
                  themeTiles.getChildren().clear();
                  for (Map<String, Object> t : catalog) {
                    themeTiles
                        .getChildren()
                        .add(buildSwatch(t, active.equals(String.valueOf(t.get("id")))));
                  }
                });
          }
        },
        "load themes");

      javafx.scene.control.ScrollPane themeScroll =
              new javafx.scene.control.ScrollPane(themeTiles);

      themeScroll.setFitToWidth(true);
      themeScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
      themeScroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
      themeScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

      VBox content = new VBox(14, hint, themeScroll);
      content.setPadding(new Insets(8, 0, 0, 0));
      VBox.setVgrow(themeScroll, Priority.ALWAYS);
      tab.setContent(content);
    return tab;
  }

  /**
   * Each theme tile renders: a classy preview card (accent pill on a stacked overlay + glass
   * gradient backdrop) followed by the theme's human-readable name. Click selects the theme.
   */
  private VBox buildSwatch(Map<String, Object> theme, boolean selected) {
    String id = String.valueOf(theme.get("id"));
    String name = String.valueOf(theme.getOrDefault("name", id));
    String accent = String.valueOf(theme.getOrDefault("accent", "#A56BFF"));
    String accentSoft = String.valueOf(theme.getOrDefault("accent-soft", "#D4B4FF"));
    String glass = String.valueOf(theme.getOrDefault("glass-fill", "rgba(155,93,229,0.22)"));
    String overlay = String.valueOf(theme.getOrDefault("overlay", "rgba(26,10,48,0.70)"));

    // Two-layer preview backdrop matching the live UI's glass-over-overlay recipe.
    StackPane preview = new StackPane();
    preview.getStyleClass().add("x-theme-preview");
    preview.setStyle(
        "-fx-background-color: "
            + "linear-gradient(to bottom right, derive(" + glass + ", 22%), " + glass + "), "
            + overlay + ";"
            + "-fx-background-radius: 14;");

    // Accent pill: theme's accent gradient over a soft-accent under-shadow.
    Rectangle accentPill = new Rectangle(64, 14);
    accentPill.setArcWidth(12);
    accentPill.setArcHeight(12);
    accentPill.setStyle(
        "-fx-fill: linear-gradient(to right, " + accentSoft + ", " + accent + ");");
    preview.getChildren().add(accentPill);

    Label label = new Label(name);
    label.getStyleClass().add("x-theme-name");
    label.setWrapText(true);
    label.setMaxWidth(120);
    label.setAlignment(Pos.CENTER);
    label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

    VBox col = new VBox(6, preview, label);
    col.getStyleClass().add("x-theme-tile");
    col.setAlignment(Pos.CENTER);
    if (selected) col.getStyleClass().add("is-selected");

    col.setOnMouseClicked(
        e -> {
          selectedThemeId.set(id);
          previewTheme(id);
          if (themeTiles != null) {
            for (var node : themeTiles.getChildren()) {
              if (node instanceof VBox vb) vb.getStyleClass().remove("is-selected");
            }
          }
          col.getStyleClass().add("is-selected");
        });
    return col;
  }

  private void previewTheme(String id) {
    runHttp(
        () -> {
          Response r =
              http.newCall(
                      new Request.Builder()
                          .url(BASE + serverPort + "/api/settings/themes/" + id)
                          .build())
                  .execute();
          try (r) {
            if (!r.isSuccessful() || r.body() == null) return;
            Theme theme = json.readValue(r.body().bytes(), Theme.class);
            Platform.runLater(
                () -> {
                  if (dialogRoot != null) ThemeApplier.apply(dialogRoot, theme);
                  if (onThemeApplied != null) onThemeApplied.accept(id);
                  if (onLogoSwap != null) onLogoSwap.accept(theme);
                });
          }
        },
        "preview theme " + id);
  }

  /* ------------------------------------------------------------------ */
  /*  Preferences tab                                                   */
  /* ------------------------------------------------------------------ */

  private Tab buildPreferencesTab() {
    Tab tab = new Tab("Preferences");

    cbValidateOnUpload = new CheckBox("Validate .spthy syntax before submitting");
    cbShowAnimations = new CheckBox("Show UI animations");
    cbKeepDerivationTree = new CheckBox("Keep derivation-tree overlay open");

    Label hint =
        new Label(
            "These preferences apply to the desktop UI and are persisted between sessions.");
    hint.getStyleClass().add("x-settings-sub");

    runHttp(
        () -> {
          Response r =
              http.newCall(
                      new Request.Builder()
                          .url(BASE + serverPort + "/api/settings/preferences")
                          .build())
                  .execute();
          try (r) {
            if (!r.isSuccessful() || r.body() == null) return;
            @SuppressWarnings("unchecked")
            Map<String, Object> body = json.readValue(r.body().bytes(), Map.class);
            Platform.runLater(
                () -> {
                  cbValidateOnUpload.setSelected(asBool(body.get("validateOnUpload"), true));
                  cbShowAnimations.setSelected(asBool(body.get("showAnimations"), true));
                  cbKeepDerivationTree.setSelected(asBool(body.get("keepDerivationTree"), false));
                });
          }
        },
        "load preferences");

    VBox content =
        new VBox(12, hint, cbValidateOnUpload, cbShowAnimations, cbKeepDerivationTree);
    content.setPadding(new Insets(8, 0, 0, 0));
    tab.setContent(content);
    return tab;
  }

  /* ------------------------------------------------------------------ */
  /*  Flatten / unflatten                                               */
  /* ------------------------------------------------------------------ */

  @SuppressWarnings("unchecked")
  private static void flatten(
      String prefix, Map<String, Object> map, ObservableList<VocabRow> rows) {
    for (Map.Entry<String, Object> e : map.entrySet()) {
      String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
      Object value = e.getValue();
      if (value instanceof Map<?, ?> nested) {
        flatten(key, (Map<String, Object>) nested, rows);
      } else if (value instanceof List<?> list) {
        for (int i = 0; i < list.size(); i++) {
          rows.add(new VocabRow(key + "[" + i + "]", String.valueOf(list.get(i))));
        }
      } else if (value != null) {
        rows.add(new VocabRow(key, String.valueOf(value)));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> unflatten(ObservableList<VocabRow> rows) {
    Map<String, Object> root = new LinkedHashMap<>();
    for (VocabRow row : rows) {
      String key = row.getKey();
      String value = row.getValue() == null ? "" : row.getValue();
      java.util.regex.Matcher m =
          java.util.regex.Pattern.compile("^(.+)\\[(\\d+)\\]$").matcher(key);
      if (m.matches()) {
        String basePath = m.group(1);
        int idx = Integer.parseInt(m.group(2));
        Map<String, Object> cursor = root;
        String[] parts = basePath.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
          cursor =
              (Map<String, Object>) cursor.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
        }
        String leaf = parts[parts.length - 1];
        List<Object> list = (List<Object>) cursor.computeIfAbsent(leaf, k -> new ArrayList<>());
        while (list.size() <= idx) list.add(null);
        list.set(idx, value);
      } else {
        Map<String, Object> cursor = root;
        String[] parts = key.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
          cursor =
              (Map<String, Object>) cursor.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
        }
        cursor.put(parts[parts.length - 1], value);
      }
    }
    return root;
  }

  /* ------------------------------------------------------------------ */
  /*  Misc                                                              */
  /* ------------------------------------------------------------------ */

  private static String stripExt(String filename) {
    if (filename == null) return "";
    int dot = filename.lastIndexOf('.');
    return dot > 0 ? filename.substring(0, dot) : filename;
  }

  private static boolean asBool(Object v, boolean fallback) {
    if (v instanceof Boolean b) return b;
    if (v == null) return fallback;
    return Boolean.parseBoolean(String.valueOf(v));
  }

  private void runHttp(IOAction action, String label) {
    new Thread(
            () -> {
              try {
                action.run();
              } catch (Exception e) {
                log.warn("settings dialog: {} failed: {}", label, e.getMessage());
              }
            },
            "settings-" + label)
        .start();
  }

  @FunctionalInterface
  private interface IOAction {
    void run() throws Exception;
  }

  @Getter
  @Setter
  @AllArgsConstructor
  public static class VocabRow {
    private String key;
    private String value;
  }
}

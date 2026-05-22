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
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.ImageView;
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
 * <p>Adds in round 3:
 *
 * <ul>
 *   <li>SVG icons (no more Unicode glyphs);
 *   <li>"Detect from .spthy" — runs the file through the validator + detector;
 *   <li>Named vocabulary profiles (save, switch, delete) persisted under {@code
 *       ~/.xmen/vocabularies/};
 *   <li>Live theme preview that also updates the parent stage's hero logo to the matching
 *       light/dark PNG;
 *   <li>Removed the "Play background video" preference.
 * </ul>
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

  /* View-state captured across the three tabs, ready for the single Save. */
  private final ObservableList<VocabRow> vocabRows = FXCollections.observableArrayList();
  private final AtomicReference<String> selectedThemeId = new AtomicReference<>();
  private TilePane themeTiles;
  private CheckBox cbValidateOnUpload;
  private CheckBox cbShowAnimations;
  private CheckBox cbKeepDerivationTree;

  /* Saved-vocabulary widgets. */
  private final ObservableList<String> profileNames = FXCollections.observableArrayList();
  private ComboBox<String> profilePicker;
  private TextField profileNameField;

  /* The dialog's own root — re-themed when the user picks a swatch. */
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
    tabs.getTabs().addAll(buildVocabularyTab(), buildThemeTab(), buildPreferencesTab());
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
    dialogRoot.setStyle(
        (owner != null && owner.getScene() != null && owner.getScene().getRoot() != null)
            ? owner.getScene().getRoot().getStyle()
            : "");

    Scene scene = new Scene(dialogRoot);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(getClass().getResource("/css/main-v2.css").toExternalForm());

    stage.setScene(scene);

    // Fade in.
    FadeTransition fade = new FadeTransition(Duration.millis(260), dialogRoot);
    fade.setFromValue(0.0);
    fade.setToValue(1.0);
    fade.play();

    stage.showAndWait();
  }

  /* ------------------------------------------------------------------ */
  /*  Save-all                                                          */
  /* ------------------------------------------------------------------ */

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
                  // Auto-close after a brief delay so the toast has time to be seen.
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
  private Tab buildVocabularyTab() {
    Tab tab = new Tab("Vocabulary");

    Label hint =
        new Label(
            "Map each semantic role to a single atomic value. List-typed entries are shown one "
                + "element per row so every value stays atomic. Use Import / Export YAML for "
                + "bulk edits, or detect a vocabulary directly from a .spthy file.");
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

    // ------------ Profiles row ------------
    Label profileLabel = new Label("Profile:");
    profileLabel.getStyleClass().add("x-settings-sub");

    profilePicker = new ComboBox<>(profileNames);
    profilePicker.setPrefWidth(220);
    profilePicker.setPromptText("Oyster");
    profilePicker.getStyleClass().addAll("x-input", "x-glass-choice");
    profilePicker.setStyle(
              "-fx-background-color: -glass-fill;"
                      + "-fx-border-color: -glass-stroke;"
                      + "-fx-background-radius: 12;"
                      + "-fx-border-radius: 12;"
                      + "-fx-border-width: 1;"
                      + "-fx-text-fill: -text;"
      );
    profilePicker.setVisibleRowCount(8);
    profilePicker.setCellFactory(list -> profileCell());
    profilePicker.setButtonCell(profileCell());

    Button loadProfile = new Button("Load");
    loadProfile.getStyleClass().add("x-cta-secondary");
    loadProfile.setOnAction(e -> loadProfile());

    Button deleteProfile = new Button("Delete");
    deleteProfile.getStyleClass().add("x-cta-secondary");
    deleteProfile.setOnAction(e -> deleteProfile());

    profileNameField = new TextField();
    profileNameField.setPromptText("Save as…");
    profileNameField.setPrefWidth(180);
    profileNameField.getStyleClass().add("x-input");

    Button saveProfile = new Button("Save profile");
    saveProfile.getStyleClass().add("x-cta-secondary");
    saveProfile.setOnAction(e -> saveProfile());

    HBox profileRow =
        new HBox(8, profileLabel, profilePicker, loadProfile, deleteProfile, profileNameField, saveProfile);
    profileRow.setAlignment(Pos.CENTER_LEFT);

    // ------------ Actions row ------------
    Button detect = new Button("Detect from .spthy");
    detect.getStyleClass().add("x-cta-secondary");
    detect.setOnAction(e -> detectFromSpthy());

    Button reset = new Button("Reset defaults");
    reset.getStyleClass().add("x-cta-secondary");
    reset.setOnAction(e -> resetVocabulary());
    Animations.hoverLift(reset, 1.03);

    Button export = new Button("Export YAML");
    export.getStyleClass().add("x-cta-secondary");
    export.setOnAction(e -> exportVocabulary());

    Button importYaml = new Button("Import YAML");
    importYaml.getStyleClass().add("x-cta-secondary");
    importYaml.setOnAction(e -> importVocabulary());

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

  private void exportVocabulary() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Export vocabulary as YAML");
    fc.setInitialFileName("vocabulary.yaml");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML", "*.yaml", "*.yml"));
    File target = fc.showSaveDialog(null);
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
            Platform.runLater(() -> ThemedToast.show(null, "Exported to " + target.getName()));
          }
        },
        "export vocabulary");
  }

  private void importVocabulary() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Import vocabulary YAML");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML", "*.yaml", "*.yml"));
    File source = fc.showOpenDialog(null);
    if (source == null) return;
    runHttp(
        () -> {
          byte[] bytes = Files.readAllBytes(source.toPath());
          // Pre-validate with Jackson — if it doesn't parse, abort.
          try {
            yaml.readTree(bytes);
          } catch (Exception parseError) {
            Platform.runLater(
                () -> ThemedToast.show(null, "Invalid YAML: " + parseError.getMessage()));
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
                    ThemedToast.show(null, "Imported " + source.getName());
                  } else {
                    ThemedToast.show(null, "Import rejected by server.");
                  }
                });
          }
        },
        "import vocabulary");
  }

  /* ----- detect from .spthy ----- */

  private void detectFromSpthy() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Pick a .spthy file to detect vocabulary from");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tamarin SPTHY", "*.spthy"));
    File source = fc.showOpenDialog(null);
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
                  () -> ThemedToast.show(null, "File failed validation; nothing detected."));
              return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = json.readValue(r.body().bytes(), Map.class);
            Platform.runLater(
                () -> {
                  vocabRows.clear();
                  flatten("", body, vocabRows);
                  ThemedToast.show(
                      null, "Vocabulary detected from " + source.getName() + " (review then Save)");
                });
          }
        },
        "detect vocabulary");
  }

  /* ----- vocabulary profiles ----- */

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

  /** Themed list cell — uses the same x-glass-cell class so the popup matches the field. */
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

  private void saveProfile() {
    String name = profileNameField.getText() == null ? "" : profileNameField.getText().trim();
    if (name.isEmpty()) {
      ThemedToast.show(null, "Type a name in the 'Save as…' box first.");
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
                                  + java.net.URLEncoder.encode(name, "UTF-8"))
                          .post(RequestBody.create(new byte[0]))
                          .build())
                  .execute();
          try (r) {
            boolean ok = r.isSuccessful();
            Platform.runLater(
                () -> {
                  if (ok) {
                    profileNameField.clear();
                    loadProfiles();
                    ThemedToast.show(null, "Profile '" + name + "' saved.");
                  } else {
                    ThemedToast.show(null, "Could not save profile.");
                  }
                });
          }
        },
        "save profile");
  }

  private void loadProfile() {
    String name = profilePicker.getValue();
    if (name == null || name.isBlank()) {
      ThemedToast.show(null, "Pick a profile from the dropdown first.");
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
                    ThemedToast.show(null, "Loaded profile '" + name + "'.");
                  } else {
                    ThemedToast.show(null, "Could not activate profile.");
                  }
                });
          }
        },
        "activate profile");
  }

  private void deleteProfile() {
    String name = profilePicker.getValue();
    if (name == null || name.isBlank()) {
      ThemedToast.show(null, "Pick a profile from the dropdown first.");
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
                    ThemedToast.show(null, "Deleted '" + name + "'.");
                  } else {
                    ThemedToast.show(null, "Could not delete profile.");
                  }
                });
          }
        },
        "delete profile");
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
    themeTiles.setVgap(12);
    // 28 themes total → 7 columns × 4 rows. Each tile holds the classy
    // glass-pill swatch + theme name; the grid fits without scrolling.
    themeTiles.setPrefColumns(7);
    themeTiles.setPrefTileWidth(118);
    themeTiles.setPrefTileHeight(96);
    themeTiles.setMinWidth(7 * 118 + 6 * 12);
    themeTiles.setPrefWidth(7 * 118 + 6 * 12);
    themeTiles.setMaxWidth(7 * 118 + 6 * 12);
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

    // No scroll-pane — all 28 themes fit in the 7×4 grid by design.
    VBox content = new VBox(14, hint, themeTiles);
    content.setPadding(new Insets(8, 0, 0, 0));
    tab.setContent(content);
    return tab;
  }

  private VBox buildSwatch(Map<String, Object> theme, boolean selected) {
    String id = String.valueOf(theme.get("id"));
    String name = String.valueOf(theme.getOrDefault("name", id));
    String accent = String.valueOf(theme.getOrDefault("accent", "#A56BFF"));
    String glass = String.valueOf(theme.getOrDefault("glass-fill", "rgba(155,93,229,0.22)"));
    String overlay = String.valueOf(theme.getOrDefault("overlay", "rgba(26,10,48,0.70)"));

    // Original-style classy preview: a single accent pill sitting on top of a
    // glass + overlay background so every theme reads at a glance.
    Rectangle accentBar = new Rectangle(56, 8);
    accentBar.setStyle("-fx-fill: " + accent + ";");
    accentBar.setArcWidth(8);
    accentBar.setArcHeight(8);

    StackPane swatch = new StackPane(accentBar);
    swatch.getStyleClass().add("x-theme-swatch");
    swatch.setStyle(
        "-fx-background-color: " + glass + ", " + overlay + ";"
            + "-fx-background-radius: 14;");
    if (selected) swatch.getStyleClass().add("is-selected");

    Label label = new Label(name);
    label.getStyleClass().add("x-theme-name");

    VBox col = new VBox(6, swatch, label);
    col.setAlignment(Pos.CENTER);
    col.setOnMouseClicked(
        e -> {
          selectedThemeId.set(id);
          previewTheme(id);
          if (themeTiles != null) {
            for (var node : themeTiles.getChildren()) {
              if (node instanceof VBox vb) {
                vb.getChildrenUnmodifiable()
                    .forEach(
                        c -> {
                          if (c instanceof StackPane sp) sp.getStyleClass().remove("is-selected");
                        });
              }
            }
          }
          swatch.getStyleClass().add("is-selected");
        });
    return col;
  }

  /**
   * Live preview: re-style both the dialog root and the parent stage's root, plus swap the
   * hero logo to the matching light/dark PNG.
   */
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

  /** One row of the vocabulary table. */
  @Getter
  @Setter
  @AllArgsConstructor
  public static class VocabRow {
    private String key;
    private String value;
  }
}

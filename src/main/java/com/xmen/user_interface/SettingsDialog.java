package com.xmen.user_interface;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.xmen.config.ThemeCatalog.Theme;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
  private final java.util.Set<String> protectedProfiles =
      java.util.concurrent.ConcurrentHashMap.newKeySet();
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
    // Generous bottom padding so the footer (Save/Close) and the action buttons under the
    // vocabulary table always have visible breathing room beneath them — earlier the panel
    // hugged the screen edge and looked like it was clipping content.
    panel.setPadding(new Insets(24, 24, 32, 24));
    panel.setPrefWidth(980);
    // Cap the dialog at ~85% of the active screen height so the footer and action buttons
    // are always visible even on a 768-px screen.
    javafx.geometry.Rectangle2D screenForSize = ThemedToast.screenFor(owner);
    double maxH = Math.max(540, screenForSize.getHeight() * 0.92);
    panel.setPrefHeight(Math.min(860, maxH));
    panel.setMaxHeight(maxH);

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
    // If a profile is currently selected, save the vocabulary back into THAT profile file
    // so descriptions and edits persist across activations. Without this step the next
    // profile switch would overwrite the live vocab with the unmodified profile JSON, and
    // any descriptions the user typed would be silently lost.
    String activeProfile =
        profilePicker != null && profilePicker.getValue() != null
                && !profilePicker.getValue().isBlank()
            ? profilePicker.getValue()
            : null;
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

          // Persist into the active profile file so descriptions survive switching.
          if (activeProfile != null) {
            try {
              http.newCall(
                      new Request.Builder()
                          .url(
                              BASE
                                  + serverPort
                                  + "/api/settings/vocabulary/profiles/"
                                  + java.net.URLEncoder.encode(activeProfile, "UTF-8"))
                          .post(RequestBody.create(new byte[0]))
                          .build())
                  .execute()
                  .close();
            } catch (Exception e) {
              log.warn("Could not auto-save profile '{}': {}", activeProfile, e.getMessage());
            }
          }

          final boolean success = ok;
          Platform.runLater(
              () -> {
                if (success) {
                  if (themeId != null && onThemeApplied != null) onThemeApplied.accept(themeId);
                  if (onPreferencesChanged != null) onPreferencesChanged.accept(prefs);
                  // Close the dialog FIRST, then show the toast on the parent window so
                  // the confirmation isn't competing with the modal that's about to vanish.
                  if (thisStage != null) thisStage.close();
                  ThemedToast.show(hostStage, "Settings saved.");
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

    // Two sub-tabs: a read-only Tamarin reference (what THIS tool recognises) and the
    // editable custom-vocabulary section (profiles, import, detect-from-.spthy).
    TabPane sub = new TabPane();
    sub.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    sub.getTabs().addAll(buildTamarinReferenceSubTab(), buildCustomVocabularySubTab(owner));

    VBox wrap = new VBox(sub);
    VBox.setVgrow(sub, Priority.ALWAYS);
    tab.setContent(wrap);

    loadVocabulary();
    loadProfiles();
    return tab;
  }

  /** Read-only sub-tab listing what Tamarin keywords this tool understands. */
  private Tab buildTamarinReferenceSubTab() {
    Tab refTab = new Tab("Tamarin Reference");

    Label hint =
        new Label(
            "These are the Tamarin keywords, theories, and built-in functions this tool "
                + "recognises directly. Anything not listed here is still accepted in .spthy "
                + "files as a generic identifier, but X-Men will not act on it.");
    hint.getStyleClass().add("x-settings-sub");
    hint.setWrapText(true);
    hint.setMaxWidth(Double.MAX_VALUE);
    hint.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

    // Two-column grid: Category | Keywords. Both columns wrap.
    javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
    grid.setHgap(20);
    grid.setVgap(12);
    grid.getStyleClass().add("x-ref-table");

    javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
    col1.setPercentWidth(28);
    col1.setHalignment(javafx.geometry.HPos.LEFT);
    javafx.scene.layout.ColumnConstraints col2 = new javafx.scene.layout.ColumnConstraints();
    col2.setPercentWidth(72);
    col2.setHalignment(javafx.geometry.HPos.LEFT);
    col2.setHgrow(Priority.ALWAYS);
    grid.getColumnConstraints().addAll(col1, col2);

    Object[][] rows = new Object[][] {
        {"Top-level keywords",
            new String[] {"theory", "begin", "end", "rule", "builtins", "functions",
                "equations", "let", "in"}},
        {"Built-in message theories",
            new String[] {"diffie-hellman", "bilinear-pairing", "multiset", "xor",
                "symmetric-encryption", "asymmetric-encryption", "signing",
                "revealing-signing", "hashing"}},
        {"Built-in function symbols",
            new String[] {"aenc", "senc", "sign", "pk"}},
        {"Variable & atom prefixes",
            new String[] {"$  public", "~  fresh", "#  temporal", "!  persistent",
                "'…'  public const", "~'…'  fresh name"}},
        {"Rule syntax",
            new String[] {"[ premise ]", "--[ action ]->", "[ conclusion ]",
                "-->", "[private]", "all"}},
        {"Operators",
            new String[] {"=", "!", "*", "^", "<", ">", "/", ":", ",", "( )", "{ }", "[ ]"}},
        {"Reserved fact names",
            new String[] {"In", "Out", "Fr", "KU", "KD", "K", "State"}},
        {"Default semantic roles",
            new String[] {"Send", "Receive", "Forget", "Setup",
                "H  (human marker)", "Fr  (fresh decl)"}},
        {"Accepted (parsed as identifiers, not interpreted)",
            new String[] {"lemma", "restriction", "axiom", "process", "predicate",
                "predicates", "tactic", "heuristic", "macros",
                "All", "Ex", "not", "last", "F", "T",
                "==>", "<=>", "&", "|", "@",
                "h", "adec", "sdec", "verify", "inv", "XOR", "pmult", "em",
                "++", "%+",
                "new", "out", "in", "if", "then", "else", "event", "insert",
                "delete", "lookup", "lock", "unlock",
                "#ifdef", "#else", "#endif", "#define", "#include"}}
    };

    for (int i = 0; i < rows.length; i++) {
      Label category = new Label((String) rows[i][0]);
      category.getStyleClass().add("x-ref-cat");
      category.setWrapText(true);
      category.setMaxWidth(Double.MAX_VALUE);
      category.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
      javafx.scene.layout.GridPane.setValignment(category, javafx.geometry.VPos.TOP);

      javafx.scene.layout.FlowPane chips = new javafx.scene.layout.FlowPane(6, 6);
      chips.getStyleClass().add("x-ref-chips");
      for (String kw : (String[]) rows[i][1]) {
        Label chip = new Label(kw);
        chip.getStyleClass().add("x-ref-chip");
        chips.getChildren().add(chip);
      }

      grid.add(category, 0, i);
      grid.add(chips, 1, i);
    }

    javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(grid);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
    scroll.setPadding(new Insets(6, 8, 6, 0));

    VBox content = new VBox(12, hint, scroll);
    content.setPadding(new Insets(8, 0, 0, 0));
    VBox.setVgrow(scroll, Priority.ALWAYS);
    refTab.setContent(content);
    return refTab;
  }

  /** The editable side: table + profiles + import/detect — same surface as before. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Tab buildCustomVocabularySubTab(Stage owner) {
    Tab tab = new Tab("Custom Vocabulary");

    Label hint =
        new Label(
            "Edit the active vocabulary that X-Men uses to recognise ceremony roles and facts. "
                + "Values such as the human marker, state fact, and channel facts are configurable "
                + "because mutation logic reads them from this table rather than hardcoding names.");
    hint.getStyleClass().add("x-settings-sub");
    hint.setWrapText(true);
    // Explicit pref/max width so the Label has a finite width to wrap against — without
    // this, the surrounding VBox sometimes hands the label unconstrained width and the
    // tail of the sentence ("...what each keyword means.") gets clipped instead of wrapping.
    hint.setMaxWidth(Double.MAX_VALUE);
    hint.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

    // Filtered view: keep semantic vocabulary rows visible even when their current value is a
    // built-in token like H/State/SndS. Those names are now configuration, not hardcoding.
    // Hide only duplicate built-in core-action rows so the table stays compact.
    javafx.collections.transformation.FilteredList<VocabRow> visibleRows =
        new javafx.collections.transformation.FilteredList<>(
            vocabRows,
            r -> r != null
                && r.getValue() != null
                && !r.getValue().isBlank()
                && isVisibleVocabularyRow(r));

    TableView<VocabRow> table = new TableView<>(visibleRows);
    table.setEditable(true);
    table.getStyleClass().add("x-vocab-table");
    table.setPlaceholder(new Label(
        "No vocabulary rows available yet — use 'Detect from .spthy' or load a profile."));
    table.setFixedCellSize(36);
    double compactTableHeight = 178;
    table.setMinHeight(compactTableHeight);
    table.setPrefHeight(compactTableHeight);
    table.setMaxHeight(compactTableHeight);

    // Column 1 — Category (read-only, derived from the row's key path).
    TableColumn<VocabRow, String> catCol = new TableColumn<>("CATEGORY");
    catCol.setCellValueFactory(
        cd -> new javafx.beans.property.SimpleStringProperty(categoryFor(cd.getValue().getKey())));
    catCol.setPrefWidth(180);
    catCol.setEditable(false);

    // Column 2 — Keyword (the identifier the ceremony uses).
    TableColumn<VocabRow, String> valCol = new TableColumn<>("KEYWORD");
    valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
    valCol.setPrefWidth(260);
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

    // Column 3 — Description (free text, persisted with the profile).
    TableColumn<VocabRow, String> descCol = new TableColumn<>("DESCRIPTION");
    descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
    descCol.setPrefWidth(420);
    descCol.setEditable(true);
    descCol.setCellFactory(
            col ->
                    new TextFieldTableCell<VocabRow, String>(
                            new javafx.util.converter.DefaultStringConverter()) {

                      @Override
                      public void startEdit() {
                        super.startEdit();

                        if (getGraphic() instanceof TextField tf) {
                          tf.focusedProperty()
                                  .addListener(
                                          (obs, oldFocus, newFocus) -> {
                                            if (!newFocus && isEditing()) {
                                              commitEdit(tf.getText());
                                            }
                                          });
                        }
                      }

                      @Override
                      public void commitEdit(String value) {
                        super.commitEdit(value == null ? "" : value);

                        VocabRow row = getTableView().getItems().get(getIndex());
                        row.setDescription(value == null ? "" : value);
                      }
                    });
    descCol.setOnEditCommit(
        ev -> {
          String d = ev.getNewValue() == null ? "" : ev.getNewValue();
          ev.getRowValue().setDescription(d);
          ev.getTableView().refresh();
        });

    table.getColumns().addAll(catCol, valCol, descCol);

    // ----- Profiles row (Load + Delete) -----
    Label profileLabel = new Label("Profile:");
    profileLabel.getStyleClass().add("x-settings-sub");
    profileLabel.setMinWidth(Region.USE_PREF_SIZE);

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

    Button renameProfile = new Button("Rename");
    renameProfile.getStyleClass().add("x-cta-secondary");
    renameProfile.setOnAction(e -> renameProfile(owner));

    Button deleteProfile = new Button("Delete");
    deleteProfile.getStyleClass().add("x-cta-secondary");
    deleteProfile.setOnAction(e -> deleteProfile(owner));

    HBox profileRow =
        new HBox(8, profileLabel, profilePicker, loadProfile, renameProfile, deleteProfile);
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

    // FlowPane wraps the four action buttons to a second row if the dialog is narrower
    // than their combined width, instead of clipping the right-most ones.
    javafx.scene.layout.FlowPane actions = new javafx.scene.layout.FlowPane(10, 10);
    actions.setAlignment(Pos.CENTER_LEFT);
    actions.getChildren().addAll(detect, reset, export, importYaml);
    actions.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
    // 24-px breathing room below the action row so there's a visible margin instead of
    // the buttons sitting flush against the tab's bottom edge (which used to look like
    // they were being clipped).
    VBox.setMargin(actions, new Insets(0, 0, 24, 0));

    // Keep the vocabulary table compact; overflow stays inside the table's own scroll bar.
    VBox.setVgrow(table, Priority.NEVER);

    VBox content = new VBox(12, hint, profileRow, table, actions);
    content.setPadding(new Insets(8, 0, 12, 0));
    content.setFillWidth(true);

    tab.setContent(content);
    // loadVocabulary / loadProfiles are kicked off once at the parent tab level so we
    // don't double-load when the user clicks back and forth between the sub-tabs.
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

  /**
   * Save the current table state under {@code name}.
   *
   * <p>Important: the profile-save endpoint snapshots the LIVE vocabulary on the server, but
   * Detect-from-.spthy only fills the local table without touching the server. So we push the
   * table state up first, then ask the server to persist it as a profile — otherwise the
   * saved profile would be the stale pre-detect state (the bug behind "Library still shows
   * Oyster's vocabulary").
   */
  private void persistProfile(Stage owner, String name) {
    Map<String, Object> vocabBody = unflatten(vocabRows);
    runHttp(
        () -> {
          if (!postJson("/api/settings/vocabulary", vocabBody)) {
            Platform.runLater(
                () ->
                    ThemedDialog.show(
                        owner,
                        ThemedDialog.Kind.ERROR,
                        "Save failed",
                        "Could not push the current vocabulary to the server before saving."));
            return;
          }
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
            List<String> prot = (List<String>) body.getOrDefault("protected", List.of());
            Platform.runLater(
                () -> {
                  protectedProfiles.clear();
                  protectedProfiles.addAll(prot);
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
        if (empty || item == null) {
          setText(null);
        } else if (protectedProfiles.contains(item)) {
          // Lock glyph hints the user this one cannot be deleted.
          setText(item + "  🔒");
        } else {
          setText(item);
        }
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
    // The Load button now commits + dismisses the dialog. The picker still previews on
    // selection change, so users can still browse without closing the dialog — only Load
    // means "I want this one, take me back to the main scene".
    Stage dialogStage =
        dialogRoot != null && dialogRoot.getScene() != null
                && dialogRoot.getScene().getWindow() instanceof Stage s
            ? s
            : null;
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
                    // Close the dialog first, then toast on the parent so the message is
                    // not competing with the modal that's vanishing.
                    if (dialogStage != null) dialogStage.close();
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

  private void renameProfile(Stage owner) {
    String name = profilePicker.getValue();
    if (name == null || name.isBlank()) {
      ThemedToast.show(owner, "Pick a profile from the dropdown first.");
      return;
    }
    if (isProtectedProfileName(name)) {
      ThemedDialog.show(
          owner,
          ThemedDialog.Kind.INFO,
          "Protected profile",
          "'" + name + "' is a built-in profile and cannot be renamed.");
      return;
    }
    promptRenameProfile(owner, name);
  }

  private void promptRenameProfile(Stage owner, String currentName) {
    Stage stage = new Stage();
    if (owner != null) stage.initOwner(owner);
    stage.initModality(Modality.APPLICATION_MODAL);
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setAlwaysOnTop(true);

    Label title = new Label("Rename profile");
    title.getStyleClass().add("x-dialog-title");
    Label body = new Label("Choose a new name for '" + currentName + "'.");
    body.getStyleClass().add("x-dialog-body");
    body.setWrapText(true);
    body.setMaxWidth(420);

    TextField nameField = new TextField(currentName);
    nameField.getStyleClass().add("x-input");

    Button save = new Button("Rename");
    save.getStyleClass().add("x-cta-primary");
    Button cancel = new Button("Cancel");
    cancel.getStyleClass().add("x-cta-secondary");

    save.setOnAction(
        e -> {
          String newName = nameField.getText() == null ? "" : nameField.getText().trim();
          if (newName.isEmpty()) {
            ThemedToast.show(stage, "Pick a name first.");
            return;
          }
          if (isProtectedProfileName(newName)) {
            ThemedToast.show(stage, "Oyster and Bank are locked.");
            return;
          }
          if (newName.equals(currentName)) {
            stage.close();
            return;
          }
          stage.close();
          runHttp(
              () -> {
                boolean ok =
                    postJson(
                        "/api/settings/vocabulary/profiles/"
                            + java.net.URLEncoder.encode(currentName, "UTF-8")
                            + "/rename",
                        Map.of("name", newName));
                Platform.runLater(
                    () -> {
                      if (ok) {
                        loadProfiles();
                        if (profilePicker != null) profilePicker.getSelectionModel().select(newName);
                        ThemedToast.show(owner, "Renamed profile to '" + newName + "'.");
                      } else {
                        ThemedDialog.show(
                            owner,
                            ThemedDialog.Kind.ERROR,
                            "Rename failed",
                            "Could not rename profile '" + currentName + "'.");
                      }
                    });
              },
              "rename profile");
        });
    cancel.setOnAction(e -> stage.close());

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    StackPane cancelWrap = new StackPane(cancel);
    cancelWrap.getStyleClass().add("x-shadow-room");
    StackPane saveWrap = new StackPane(save);
    saveWrap.getStyleClass().add("x-shadow-room");
    HBox buttons = new HBox(10, spacer, cancelWrap, saveWrap);

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

  private boolean isProtectedProfileName(String name) {
    return name != null && protectedProfiles.stream().anyMatch(p -> p.equalsIgnoreCase(name.trim()));
  }

  private void deleteProfile(Stage owner) {
    String name = profilePicker.getValue();
    if (name == null || name.isBlank()) {
      ThemedToast.show(owner, "Pick a profile from the dropdown first.");
      return;
    }
    if (isProtectedProfileName(name)) {
      ThemedDialog.show(
          owner,
          ThemedDialog.Kind.INFO,
          "Protected profile",
          "'" + name + "' is a built-in profile and cannot be deleted.");
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
    // Pull `descriptions` out at the top level — it is a value→note map and should not be
    // flattened into rows like every other key. Descriptions are attached to existing rows
    // (whose value matches a description key) after the rest of the tree is processed.
    Map<String, String> descriptions = null;
    if (prefix.isEmpty() && map.containsKey("descriptions")) {
      Object raw = map.get("descriptions");
      if (raw instanceof Map<?, ?> dm) {
        descriptions = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : dm.entrySet()) {
          if (e.getKey() != null && e.getValue() != null) {
            descriptions.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
          }
        }
      }
    }

    for (Map.Entry<String, Object> e : map.entrySet()) {
      if (prefix.isEmpty() && "descriptions".equals(e.getKey())) continue; // handled above
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

    // Second pass: attach descriptions to rows where the value matches a description key.
    if (descriptions != null && !descriptions.isEmpty()) {
      for (VocabRow row : rows) {
        String d = descriptions.get(row.getValue());
        if (d != null) row.setDescription(d);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> unflatten(ObservableList<VocabRow> rows) {
    Map<String, Object> root = new LinkedHashMap<>();
    Map<String, String> descriptions = new LinkedHashMap<>();
    for (VocabRow row : rows) {
      String key = row.getKey();
      String value = row.getValue() == null ? "" : row.getValue();
      // Collect descriptions keyed by VALUE — the server-side schema stores them as a
      // sibling map under `descriptions` so they survive round-trips through Jackson.
      if (row.getDescription() != null && !row.getDescription().isBlank() && !value.isEmpty()) {
        descriptions.put(value, row.getDescription().trim());
      }
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
    if (!descriptions.isEmpty()) root.put("descriptions", descriptions);
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
  public static class VocabRow {
    private String key;
    private String value;
    /** Free-text note from the user — only meaningful for ceremony-specific identifiers. */
    private String description = "";

    public VocabRow() {}

    public VocabRow(String key, String value) {
      this.key = key;
      this.value = value;
    }

    public VocabRow(String key, String value, String description) {
      this.key = key;
      this.value = value;
      this.description = description == null ? "" : description;
    }
  }

  /* ------------------------------------------------------------------ */
  /*  Custom-vocab filter & categorisation                              */
  /* ------------------------------------------------------------------ */

  /**
   * Identifiers that Tamarin / X-Men ship with — rows whose VALUE is in this set are hidden
   * from the Custom Vocabulary table so the user sees only the ceremony-specific additions.
   * Mirrors the "Reserved fact names" and "Default semantic roles" sections of the
   * Tamarin Reference sub-tab.
   */
  private static final java.util.Set<String> BUILTIN_VALUES = java.util.Set.of(
      "Send", "Receive", "Forget", "Setup", "Fr", "H", "To",
      "OnlyOnce", "Neq", "Roles", "Hfin",
      "State", "In", "Out", "SndS", "RcvS", "ChanSndS", "ChanRcvS",
      "KU", "KD", "K",
      "~", "$", "!", "#",
      "insec", "conf", "auth", "sec");

  private static boolean isVisibleVocabularyRow(VocabRow row) {
    String key = row.getKey();
    if (key == null) return false;
    if (key.startsWith("actions.core-actions")) {
      return !BUILTIN_VALUES.contains(row.getValue());
    }
    return key.startsWith("actions.")
        || key.startsWith("facts.")
        || key.startsWith("adornments.")
        || key.startsWith("channels.");
  }

  /** Human-readable category derived from a VocabRow's path. Drives the first table column. */
  private static String categoryFor(String key) {
    if (key == null) return "";
    if (key.startsWith("actions.core-actions")) return "Core action";
    if (key.startsWith("actions.send")) return "Send action";
    if (key.startsWith("actions.receive")) return "Receive action";
    if (key.startsWith("actions.forget")) return "Forget action";
    if (key.startsWith("actions.setup")) return "Setup action";
    if (key.startsWith("actions.fresh")) return "Fresh action";
    if (key.startsWith("actions.human-marker")) return "Human marker";
    if (key.startsWith("facts.state")) return "State fact";
    if (key.startsWith("facts.outbound")) return "Outbound channel";
    if (key.startsWith("facts.inbound")) return "Inbound channel";
    if (key.startsWith("facts.fresh")) return "Fresh fact";
    if (key.startsWith("adornments.")) return "Adornment";
    if (key.startsWith("channels.")) return "Channel mode";
    return "";
  }
}

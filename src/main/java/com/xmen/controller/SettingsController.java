package com.xmen.controller;

import com.xmen.config.CeremonyVocabulary;
import com.xmen.config.ThemeCatalog.Theme;
import com.xmen.model.UiPreferences;
import com.xmen.service.*;
import com.xmen.service.TamarinValidationService.ValidationReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST surface for managing user-facing settings:
 *
 * <ul>
 *   <li><b>Vocabulary</b> — get/patch the live {@link CeremonyVocabulary}, import/export as YAML.
 *   <li><b>Themes</b> — list the 15 palettes, query / change the active one.
 *   <li><b>Validation</b> — pre-flight a {@code .spthy} upload against the Tamarin syntax checker.
 *   <li><b>Snapshot</b> — read everything in one call to populate the UI.
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings", description = "Vocabulary, themes, and validation controls")
public class SettingsController {

  @Autowired private VocabularyService vocabularyService;
  @Autowired private ThemeService themeService;
  @Autowired private TamarinValidationService validator;
  @Autowired private SettingsStore settingsStore;
  @Autowired private VocabularyDetector vocabularyDetector;
  @Autowired private VocabularyProfileStore vocabularyProfileStore;

  /* ------------------------------------------------------------------ */
  /*  Unified snapshot                                                  */
  /* ------------------------------------------------------------------ */

  @GetMapping
  @Operation(summary = "Snapshot of every user setting (vocabulary + themes + preferences).")
  public Map<String, Object> snapshot() {
    return settingsStore.snapshot();
  }

  /* ------------------------------------------------------------------ */
  /*  Preferences                                                       */
  /* ------------------------------------------------------------------ */

  @GetMapping("/preferences")
  @Operation(summary = "Read the persisted UI preferences.")
  public UiPreferences getPreferences() {
    return settingsStore.preferences();
  }

  @PostMapping("/preferences")
  @Operation(summary = "Persist the UI preferences. Survives restarts.")
  public UiPreferences updatePreferences(@RequestBody Map<String, Object> body) {
    return settingsStore.updatePreferences(body);
  }

  /* ------------------------------------------------------------------ */
  /*  Vocabulary                                                        */
  /* ------------------------------------------------------------------ */

  @GetMapping("/vocabulary")
  @Operation(summary = "Read the active ceremony vocabulary.")
  public CeremonyVocabulary getVocabulary() {
    return vocabularyService.current();
  }

  @PostMapping("/vocabulary")
  @Operation(summary = "Patch the active vocabulary in place.")
  public CeremonyVocabulary updateVocabulary(@RequestBody Map<String, Object> body)
      throws Exception {
    return vocabularyService.update(body);
  }

  @PostMapping("/vocabulary/reset")
  @Operation(summary = "Restore the built-in vocabulary defaults.")
  public CeremonyVocabulary resetVocabulary() {
    return vocabularyService.resetToDefaults();
  }

  @GetMapping("/vocabulary/export")
  @Operation(summary = "Export the active vocabulary as a YAML file.")
  public ResponseEntity<ByteArrayResource> exportVocabulary() throws Exception {
    byte[] yaml = vocabularyService.exportYaml();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=vocabulary.yaml")
        .contentType(MediaType.parseMediaType("application/x-yaml"))
        .body(new ByteArrayResource(yaml));
  }

  @PostMapping(value = "/vocabulary/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary =
          "Import a YAML vocabulary file. Validates the payload first; only swaps the live "
              + "config if parsing succeeds.")
  public CeremonyVocabulary importVocabulary(@RequestParam("file") MultipartFile file)
      throws Exception {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("vocabulary YAML upload is empty");
    }
    // Validate-then-swap: importYaml itself parses defensively, but we surface a clear
    // error if the document doesn't deserialise to a CeremonyVocabulary at all.
    byte[] bytes = file.getBytes();
    try {
      vocabularyService.exportYaml(); // confirm round-tripper available
      return vocabularyService.importYaml(bytes);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid vocabulary YAML: " + e.getMessage(), e);
    }
  }

  /* ------------------------------------------------------------------ */
  /*  Vocabulary detection (from .spthy)                                */
  /* ------------------------------------------------------------------ */

  @PostMapping(
      value = "/vocabulary/detect",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary =
          "Detect a likely vocabulary from a .spthy file. Detection is best-effort and runs "
              + "even when full grammar validation fails — only an empty/missing upload is "
              + "rejected.")
  public ResponseEntity<?> detectVocabulary(@RequestParam("file") MultipartFile file)
      throws Exception {
    // Run validation purely for logging/feedback — we do NOT block detection on it because
    // many real-world .spthy files use vendor-specific extensions that the bundled ANTLR
    // grammar trips on, yet the regex-based detector can still pull useful vocabulary from
    // them. Only an empty / missing upload is hard-failed.
    if (file == null || file.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(java.util.Map.of("error", "Upload is empty or missing"));
    }
    ValidationReport report = validator.validate(file);
    if (!report.isValid()) {
      log.info(
          "Vocabulary detect: validation flagged the file but detection will still run ({} issues).",
          report.getIssues().size());
    }
    String content = new String(file.getBytes());
    return ResponseEntity.ok(vocabularyDetector.detectFrom(content));
  }

  /* ------------------------------------------------------------------ */
  /*  Saved vocabulary profiles                                         */
  /* ------------------------------------------------------------------ */

  @GetMapping("/vocabulary/profiles")
  @Operation(summary = "List all saved vocabulary profiles by name.")
  public Map<String, Object> listProfiles() {
    return Map.of(
        "profiles", vocabularyProfileStore.list(),
        "protected", java.util.List.copyOf(
            com.xmen.service.VocabularyProfileStore.PROTECTED_PROFILES));
  }

  @PostMapping("/vocabulary/profiles/{name}")
  @Operation(summary = "Save the current live vocabulary under {name}.")
  public Map<String, Object> saveProfile(@PathVariable("name") String name) throws Exception {
    vocabularyProfileStore.save(name);
    return Map.of("saved", name, "profiles", vocabularyProfileStore.list());
  }

  @PostMapping("/vocabulary/profiles/{name}/activate")
  @Operation(summary = "Activate the profile named {name}, replacing the live vocabulary.")
  public CeremonyVocabulary activateProfile(@PathVariable("name") String name) throws Exception {
    return vocabularyProfileStore.activate(name);
  }

  @PostMapping("/vocabulary/profiles/{name}/rename")
  @Operation(summary = "Rename the saved profile named {name}. Protected profiles cannot be renamed.")
  public Map<String, Object> renameProfile(
      @PathVariable("name") String name, @RequestBody RenameProfileRequest body)
      throws Exception {
    String renamed = vocabularyProfileStore.rename(name, body == null ? null : body.name());
    return Map.of("renamed", renamed, "profiles", vocabularyProfileStore.list());
  }

  @DeleteMapping("/vocabulary/profiles/{name}")
  @Operation(summary = "Delete the saved profile named {name}.")
  public Map<String, Object> deleteProfile(@PathVariable("name") String name) throws Exception {
    boolean deleted = vocabularyProfileStore.delete(name);
    return Map.of("deleted", deleted, "profiles", vocabularyProfileStore.list());
  }

  /* ------------------------------------------------------------------ */
  /*  Themes                                                            */
  /* ------------------------------------------------------------------ */

  @GetMapping("/themes")
  @Operation(summary = "List all available themes.")
  public Map<String, Object> listThemes() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("active", themeService.activeId());
    body.put("catalog", themeService.list());
    return body;
  }

  @GetMapping("/themes/active")
  @Operation(summary = "Resolve the currently active theme.")
  public Theme activeTheme() {
    return themeService.active();
  }

  @PostMapping("/themes/active")
  @Operation(summary = "Switch the active theme by id.")
  public Theme setActiveTheme(@RequestBody ThemeSelection selection) {
    String id = selection != null ? selection.id() : null;
    return themeService.setActive(id);
  }

  @GetMapping("/themes/{id}")
  @Operation(summary = "Resolve a theme by id (returns the default if id is unknown).")
  public Theme getTheme(@PathVariable("id") String id) {
    return themeService.resolve(id);
  }

  /* ------------------------------------------------------------------ */
  /*  Validation                                                        */
  /* ------------------------------------------------------------------ */

  @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Pre-flight a .spthy upload against the Tamarin syntax checker.")
  public ResponseEntity<ValidationReport> validate(@RequestParam("file") MultipartFile file) {
    ValidationReport report = validator.validate(file);
    return report.isValid() ? ResponseEntity.ok(report) : ResponseEntity.badRequest().body(report);
  }

  /** Body shape for {@code POST /themes/active}. */
  public record ThemeSelection(String id) {}

  /** Body shape for {@code POST /vocabulary/profiles/{name}/rename}. */
  public record RenameProfileRequest(String name) {}
}

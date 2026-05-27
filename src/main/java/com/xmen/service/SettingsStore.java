package com.xmen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xmen.config.CeremonyVocabulary;
import com.xmen.config.ThemeCatalog;
import com.xmen.model.UiPreferences;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists the user-tweakable parts of X-Men to disk so they survive restarts.
 *
 * <p>State stored:
 *
 * <ul>
 *   <li>The active ceremony vocabulary.
 *   <li>The id of the active theme.
 *   <li>UI preferences (validate-on-upload, animations, etc.).
 * </ul>
 *
 * <p>Storage location: {@code ~/.xmen/settings.json}. The directory and file are created on
 * first write. On startup, if the file exists it is read and applied to the live
 * {@link CeremonyVocabulary} and {@link ThemeCatalog} beans — so the running app picks up
 * exactly what the user last saved.
 *
 * <p>All mutation entry points ({@code VocabularyService.update}, {@code ThemeService.setActive},
 * {@code SettingsController.savePreferences}) call {@link #persist()} so every change hits
 * disk immediately.
 */
@Slf4j
@Service
public class SettingsStore {

  private static final Path STORE_DIR = Paths.get(System.getProperty("user.home"), ".xmen");
  private static final Path STORE_FILE = STORE_DIR.resolve("settings.json");

  private final CeremonyVocabulary vocabulary;
  private final ThemeCatalog themes;
  private final ObjectMapper json;

  private UiPreferences preferences = new UiPreferences();

  @Autowired
  public SettingsStore(CeremonyVocabulary vocabulary, ThemeCatalog themes) {
    this.vocabulary = vocabulary;
    this.themes = themes;
    this.json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  }

  /** Read the on-disk store after Spring has wired the configuration beans. */
  @PostConstruct
  public void loadFromDisk() {
    if (!Files.exists(STORE_FILE)) {
      log.info("No persisted settings at {} — using built-in defaults.", STORE_FILE);
      return;
    }
    try {
      byte[] bytes = Files.readAllBytes(STORE_FILE);
      PersistedSettings snap = json.readValue(bytes, PersistedSettings.class);

      if (snap.vocabulary != null) {
        vocabulary.copyFrom(snap.vocabulary);
        log.info("Applied persisted vocabulary from {}.", STORE_FILE);
      }
      if (snap.activeThemeId != null && !snap.activeThemeId.isBlank()) {
        themes.setDefaultId(snap.activeThemeId);
        log.info("Applied persisted active theme '{}'.", snap.activeThemeId);
      }
      if (snap.preferences != null) {
        this.preferences = snap.preferences;
        log.info("Applied persisted UI preferences.");
      }
    } catch (Exception e) {
      log.warn("Failed to load persisted settings from {}: {}", STORE_FILE, e.getMessage());
    }
  }

  /** Current UI preferences. */
  public UiPreferences preferences() {
    return preferences;
  }

  /** Overwrite UI preferences and persist. */
  public UiPreferences updatePreferences(UiPreferences incoming) {
    if (incoming != null) {
      this.preferences = incoming;
      persist();
    }
    return this.preferences;
  }

  /** Update UI preferences from a free-form map (used by the JavaFX dialog). */
  public UiPreferences updatePreferences(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) return this.preferences;
    UiPreferences incoming = json.convertValue(payload, UiPreferences.class);
    return updatePreferences(incoming);
  }

  /** Persist the current state of vocabulary + active theme + preferences. */
  public synchronized void persist() {
    try {
      Files.createDirectories(STORE_DIR);
      PersistedSettings snap = new PersistedSettings();
      snap.vocabulary = vocabulary;
      snap.activeThemeId = themes.getDefaultId();
      snap.preferences = preferences;
      byte[] bytes = json.writeValueAsBytes(snap);
      Files.write(STORE_FILE, bytes);
      log.debug("Persisted settings to {}.", STORE_FILE);
    } catch (IOException e) {
      log.warn("Failed to persist settings to {}: {}", STORE_FILE, e.getMessage());
    }
  }

  /** Snapshot for the {@code GET /api/settings} endpoint. */
  public Map<String, Object> snapshot() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("vocabulary", vocabulary);
    Map<String, Object> themesBody = new LinkedHashMap<>();
    themesBody.put("active", themes.getDefaultId());
    themesBody.put("catalog", themes.getCatalog());
    body.put("themes", themesBody);
    body.put("preferences", preferences);
    return body;
  }

  /** Wire format on disk. Kept package-public so the JSON layout stays under our control. */
  @Data
  @NoArgsConstructor
  public static class PersistedSettings {
    public CeremonyVocabulary vocabulary;
    public String activeThemeId;
    public UiPreferences preferences;
  }
}

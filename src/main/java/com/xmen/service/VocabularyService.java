package com.xmen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.xmen.config.CeremonyVocabulary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Mediates reads and writes to the live {@link CeremonyVocabulary} bean.
 *
 * <p>Used by the Settings API and the JavaFX settings dialog to inspect the active vocabulary
 * and to import/export it as YAML. All mutations go through this single point so the bean
 * stays consistent.
 */
@Slf4j
@Service
public class VocabularyService {

  private final CeremonyVocabulary vocabulary;
  private final ObjectMapper yamlMapper;
  private final org.springframework.beans.factory.ObjectProvider<SettingsStore> storeProvider;

  @Autowired
  public VocabularyService(
      CeremonyVocabulary vocabulary,
      org.springframework.beans.factory.ObjectProvider<SettingsStore> storeProvider) {
    this.vocabulary = vocabulary;
    this.storeProvider = storeProvider;
    YAMLFactory yaml = new YAMLFactory()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
    this.yamlMapper = new ObjectMapper(yaml);
  }

  /** Hand off to the persistence layer if it's available (it isn't during early bootstrap). */
  private void persistIfPossible() {
    SettingsStore store = storeProvider.getIfAvailable();
    if (store != null) store.persist();
  }

  /** Current snapshot of the live vocabulary. */
  public CeremonyVocabulary current() {
    return vocabulary;
  }

  /** Patch the live vocabulary with the supplied object. Missing fields keep their value. */
  public CeremonyVocabulary update(CeremonyVocabulary patch) {
    if (patch != null) {
      vocabulary.copyFrom(patch);
      log.info("Vocabulary updated via Settings API.");
      persistIfPossible();
    }
    return vocabulary;
  }

  /** Patch via free-form map (used when the UI sends a partial JSON body). */
  public CeremonyVocabulary update(Map<String, Object> payload) throws IOException {
    if (payload == null || payload.isEmpty()) return vocabulary;
    CeremonyVocabulary incoming =
        yamlMapper.convertValue(payload, CeremonyVocabulary.class);
    return update(incoming);
  }

  /** Render the live vocabulary as YAML bytes (for export download). */
  public byte[] exportYaml() throws IOException {
    return yamlMapper.writeValueAsBytes(vocabulary);
  }

  /** Parse YAML bytes and atomically swap the live vocabulary (for import upload). */
  public CeremonyVocabulary importYaml(byte[] yaml) throws IOException {
    CeremonyVocabulary incoming =
        yamlMapper.readValue(new ByteArrayInputStream(yaml), CeremonyVocabulary.class);
    return update(incoming);
  }

  /** Restore the built-in defaults. */
  public CeremonyVocabulary resetToDefaults() {
    vocabulary.copyFrom(new CeremonyVocabulary());
    log.info("Vocabulary reset to defaults.");
    persistIfPossible();
    return vocabulary;
  }
}

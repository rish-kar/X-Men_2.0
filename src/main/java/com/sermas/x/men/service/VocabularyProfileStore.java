package com.sermas.x.men.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sermas.x.men.config.CeremonyVocabulary;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Named profiles of {@link CeremonyVocabulary}.
 *
 * <p>Each profile is a JSON file under {@code ~/.xmen/vocabularies/<name>.json}. The Settings
 * dialog lets the user save the live vocabulary with a custom name, list saved profiles,
 * switch to one (which activates that profile via {@link VocabularyService#update}), and
 * delete profiles. Profile filenames are sanitized so they're always filesystem-safe.
 */
@Slf4j
@Service
public class VocabularyProfileStore {

  private static final Path PROFILES_DIR =
      Paths.get(System.getProperty("user.home"), ".xmen", "vocabularies");

  private final VocabularyService vocabularyService;
  private final ObjectMapper json;

  @Autowired
  public VocabularyProfileStore(VocabularyService vocabularyService) {
    this.vocabularyService = vocabularyService;
    this.json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  }

  @PostConstruct
  public void ensureDirectory() {
    try {
      Files.createDirectories(PROFILES_DIR);
      seedDefaultProfiles();
    } catch (IOException e) {
      log.warn(
          "Could not create vocabulary profiles directory {}: {}", PROFILES_DIR, e.getMessage());
    }
  }

  /**
   * On first run, drop in two ready-to-use profiles named after the bundled examples
   * ({@code Oyster.spthy} and {@code Bank_revised_new.spthy}). Both currently use the same
   * default Tamarin vocabulary — they're shipped so the user can branch from them and edit
   * each independently.
   */
  private void seedDefaultProfiles() {
    seedProfileIfMissing("Oyster");
    seedProfileIfMissing("Bank");
  }

  private void seedProfileIfMissing(String name) {
    Path file = PROFILES_DIR.resolve(name + ".json");
    if (Files.exists(file)) return;
    try {
      CeremonyVocabulary defaults = new CeremonyVocabulary();
      Files.write(file, json.writeValueAsBytes(defaults));
      log.info("Seeded default vocabulary profile '{}'.", name);
    } catch (IOException e) {
      log.warn("Could not seed default profile '{}': {}", name, e.getMessage());
    }
  }

  /** List the names of all stored profiles, sorted alphabetically. */
  public List<String> list() {
    if (!Files.isDirectory(PROFILES_DIR)) return List.of();
    try (Stream<Path> stream = Files.list(PROFILES_DIR)) {
      return stream
          .filter(p -> p.toString().toLowerCase().endsWith(".json"))
          .map(p -> p.getFileName().toString())
          .map(n -> n.substring(0, n.length() - ".json".length()))
          .sorted(Comparator.naturalOrder())
          .collect(Collectors.toList());
    } catch (IOException e) {
      log.warn("Could not list vocabulary profiles: {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  /** Save the live vocabulary under the given name. */
  public void save(String name) throws IOException {
    Path file = pathFor(name);
    Files.createDirectories(file.getParent());
    byte[] bytes = json.writeValueAsBytes(vocabularyService.current());
    Files.write(file, bytes);
    log.info("Saved vocabulary profile '{}' at {}.", name, file);
  }

  /** Load a profile into the live vocabulary. */
  public CeremonyVocabulary activate(String name) throws IOException {
    Path file = pathFor(name);
    if (!Files.exists(file)) throw new IOException("profile not found: " + name);
    byte[] bytes = Files.readAllBytes(file);
    CeremonyVocabulary incoming = json.readValue(bytes, CeremonyVocabulary.class);
    return vocabularyService.update(incoming);
  }

  /** Delete a saved profile. */
  public boolean delete(String name) throws IOException {
    Path file = pathFor(name);
    return Files.deleteIfExists(file);
  }

  /* ------------------------------------------------------------------ */
  /*  Filesystem helpers                                                */
  /* ------------------------------------------------------------------ */

  private Path pathFor(String name) {
    String sanitized = sanitize(name);
    if (sanitized.isBlank()) throw new IllegalArgumentException("profile name is empty");
    return PROFILES_DIR.resolve(sanitized + ".json");
  }

  private String sanitize(String raw) {
    if (raw == null) return "";
    return raw.trim().replaceAll("[^A-Za-z0-9._ -]", "_");
  }
}

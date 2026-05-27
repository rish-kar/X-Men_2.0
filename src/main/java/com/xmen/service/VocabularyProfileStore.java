package com.xmen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xmen.config.CeremonyVocabulary;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  /**
   * Profiles that ship with the app and represent the bundled example ceremonies.
   * They cannot be deleted, and their files are force-rewritten on startup so the user
   * can always rely on them as a known-good baseline.
   */
  public static final Set<String> PROTECTED_PROFILES =
      new LinkedHashSet<>(Arrays.asList("Oyster", "Bank"));

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
   * On every startup, drop in ready-to-use profiles named after the bundled examples
   * ({@code Oyster.spthy}, {@code Bank_revised_new.spthy}).
   *
   * <p>Both are <b>protected</b> and force-rewritten so their files always match
   * what the sample .spthy actually uses. Any previously seeded Library profile is
   * removed to keep the Custom Vocabulary list focused on the supported ceremonies.
   */
  private void seedDefaultProfiles() {
    writeProfile("Oyster", oysterVocabulary(), /* force = */ true);
    writeProfile("Bank", bankVocabulary(), /* force = */ true);
    // Library was previously seeded as an example profile but is no longer
    // supported. Delete any leftover file so it doesn't keep showing up in
    // the Custom Vocabulary picker for users who started on an older build.
    try {
      Files.deleteIfExists(PROFILES_DIR.resolve("Library.json"));
    } catch (IOException e) {
      log.debug("Could not remove legacy Library profile: {}", e.getMessage());
    }
  }

  private void writeProfile(String name, CeremonyVocabulary vocab, boolean force) {
    Path file = PROFILES_DIR.resolve(name + ".json");
    if (!force && Files.exists(file)) return;
    try {
      Files.write(file, json.writeValueAsBytes(vocab));
      log.info("Seeded vocabulary profile '{}' (force={}).", name, force);
    } catch (IOException e) {
      log.warn("Could not seed profile '{}': {}", name, e.getMessage());
    }
  }

  /** Oyster ceremony: SndS/RcvS channels, gate actions. */
  private CeremonyVocabulary oysterVocabulary() {
    CeremonyVocabulary v = new CeremonyVocabulary();
    v.getFacts().setOutboundChannels(new ArrayList<>(List.of("SndS")));
    v.getFacts().setInboundChannels(new ArrayList<>(List.of("RcvS")));
    v.getActions().setCoreActions(new ArrayList<>(List.of(
        "Send", "Receive", "To", "H", "Fr", "Setup", "OnlyOnce", "Neq",
        "Roles", "ChanSndS", "ChanRcvS", "Hfin", "Forget",
        "GateIn", "GateOut", "CommitGid", "Commit")));
    return v;
  }

  /** Bank ceremony: Out/In channels, login-flow actions. */
  private CeremonyVocabulary bankVocabulary() {
    CeremonyVocabulary v = new CeremonyVocabulary();
    v.getFacts().setOutboundChannels(new ArrayList<>(List.of("Out")));
    v.getFacts().setInboundChannels(new ArrayList<>(List.of("In")));
    v.getActions().setCoreActions(new ArrayList<>(List.of(
        "Send", "Receive", "To", "H", "Fr", "Setup", "OnlyOnce", "Neq",
        "Roles", "Hfin", "Forget", "Commit", "Target",
        "U_LoginRequest", "PasswordAttempt", "B_Running",
        "LoginOK", "LoginFail", "LoginSuccess", "LoginFailed", "RevLtk")));
    return v;
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

  /** Delete a saved profile. Protected built-ins (Oyster, Bank) cannot be deleted. */
  public boolean delete(String name) throws IOException {
    if (name != null && PROTECTED_PROFILES.contains(name.trim())) {
      log.info("Refused to delete protected profile '{}'.", name);
      return false;
    }
    Path file = pathFor(name);
    return Files.deleteIfExists(file);
  }

  /** Rename a saved profile. Protected built-ins (Oyster, Bank) cannot be renamed. */
  public String rename(String oldName, String newName) throws IOException {
    String targetName = sanitize(newName);
    if (isProtected(oldName) || isProtected(targetName)) {
      throw new IOException("protected profile cannot be renamed");
    }
    Path source = pathFor(oldName);
    Path target = pathFor(targetName);
    if (!Files.exists(source)) throw new IOException("profile not found: " + oldName);
    if (Files.exists(target)) throw new IOException("profile already exists: " + targetName);
    Files.move(source, target);
    log.info("Renamed vocabulary profile '{}' to '{}'.", oldName, targetName);
    return targetName;
  }

  /** True if {@code name} refers to one of the protected built-in profiles. */
  public boolean isProtected(String name) {
    return name != null
        && PROTECTED_PROFILES.stream().anyMatch(p -> p.equalsIgnoreCase(name.trim()));
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

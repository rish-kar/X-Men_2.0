package com.xmen.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Static bridge from model classes (which are not Spring-managed) to the
 * active {@link CeremonyVocabulary}.
 *
 * <p>{@link com.xmen.model.Rule} and similar POJOs can't be
 * {@code @Autowired}. Without this bridge they would have to hardcode the
 * literal {@code "H"} / {@code "SndS"} / {@code "State"} ... names, which
 * is exactly what prevented X-Men from accepting arbitrary valid Tamarin
 * syntax (e.g. the {@code CoachService.spthy} file whose human role does
 * not carry an {@code H()} marker as its first action).
 *
 * <p>The Spring container instantiates this component once and the
 * {@link #init()} hook publishes the active vocabulary to a static field.
 * Subsequent {@link CeremonyVocabulary#copyFrom} calls from the Settings
 * API mutate the same bean in place, so the holder always reflects the
 * live config without further coordination.
 */
@Component
public class VocabularyHolder {

  private static volatile CeremonyVocabulary active;

  private final CeremonyVocabulary vocabulary;

  @Autowired
  public VocabularyHolder(CeremonyVocabulary vocabulary) {
    this.vocabulary = vocabulary;
  }

  @PostConstruct
  void init() {
    active = vocabulary;
  }

  /** The currently-active vocabulary, or {@code null} before Spring start-up. */
  public static CeremonyVocabulary get() {
    return active;
  }

  /**
   * Test-only seam so unit tests that don't bring up the Spring context can
   * still drive vocabulary-aware code paths.
   */
  public static void setForTesting(CeremonyVocabulary v) {
    active = v;
  }
}

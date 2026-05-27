package com.xmen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UI-level preferences that the desktop client persists across sessions.
 *
 * <p>This object is separate from the protocol-level {@code CeremonyVocabulary} because it
 * controls visual / workflow behaviour, not the meaning of the formal model.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UiPreferences {
  /** Run the Tamarin syntax validator before submitting a file. */
  private boolean validateOnUpload = true;

  /** Play the background video on the main scene. */
  private boolean autoplayVideo = true;

  /** Allow UI transitions and animated effects. */
  private boolean showAnimations = true;

  /** Keep the derivation-tree overlay open after a mutation finishes. */
  private boolean keepDerivationTree = false;
}

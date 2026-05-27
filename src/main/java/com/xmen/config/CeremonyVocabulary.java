package com.xmen.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Runtime-mutable ceremony vocabulary.
 *
 * <p>Backs the values loaded from {@code vocabulary.yaml} under the
 * {@code xmen.vocabulary} prefix. The Settings API ({@code /api/settings/vocabulary}) can
 * mutate this bean at runtime so users can re-target X-Men at a different naming convention
 * without recompiling.
 *
 * <p>The data shape mirrors {@code vocabulary.yaml} 1-to-1 so that import/export round-trips
 * losslessly through Jackson's YAML mapper.
 */
@Component
@ConfigurationProperties(prefix = "xmen.vocabulary")
@Data
@NoArgsConstructor
public class CeremonyVocabulary {

  private Actions actions = new Actions();
  private Facts facts = new Facts();
  private Adornments adornments = new Adornments();
  private Channels channels = new Channels();

  /**
   * Optional user-supplied descriptions for ceremony-specific identifiers (typically the
   * values that appear in {@code actions.core-actions}). Maps the identifier itself
   * (e.g. {@code "LoginOK"}) to a short note like {@code "bank grants the session"}.
   * Persisted with the profile so descriptions survive restarts.
   */
  private Map<String, String> descriptions = new LinkedHashMap<>();

  @Data
  @NoArgsConstructor
  public static class Actions {
    private String send = "Send";
    private String receive = "Receive";
    private String forget = "Forget";

    @JsonProperty("human-marker")
    private String humanMarker = "H";

    private String setup = "Setup";
    private String fresh = "Fr";

    @JsonProperty("core-actions")
    private List<String> coreActions =
        new ArrayList<>(
            List.of(
                "Send",
                "Receive",
                "To",
                "H",
                "Fr",
                "Setup",
                "OnlyOnce",
                "Neq",
                "Roles",
                "ChanSndS",
                "ChanRcvS",
                "Hfin",
                "Forget"));
  }

  @Data
  @NoArgsConstructor
  public static class Facts {
    private List<String> state = new ArrayList<>(List.of("State"));

    @JsonProperty("outbound-channels")
    private List<String> outboundChannels = new ArrayList<>(List.of("Out", "SndS"));

    @JsonProperty("inbound-channels")
    private List<String> inboundChannels = new ArrayList<>(List.of("In", "RcvS"));

    private List<String> fresh = new ArrayList<>(List.of("Fr"));
  }

  @Data
  @NoArgsConstructor
  public static class Adornments {
    @JsonProperty("fresh-prefix")
    private String freshPrefix = "~";

    @JsonProperty("public-prefix")
    private String publicPrefix = "$";

    @JsonProperty("persistent-prefix")
    private String persistentPrefix = "!";
  }

  @Data
  @NoArgsConstructor
  public static class Channels {
    private String insecure = "insec";
    private String confidential = "conf";
    private String authentic = "auth";
    private String secure = "sec";
  }

  /* ------------------------------------------------------------------------------------ */
  /* Convenience predicates – callers should use these instead of doing string compares.  */
  /* ------------------------------------------------------------------------------------ */

  @JsonIgnore
  public boolean isSendAction(String name) {
    return Objects.equals(name, actions.send);
  }

  @JsonIgnore
  public boolean isReceiveAction(String name) {
    return Objects.equals(name, actions.receive);
  }

  @JsonIgnore
  public boolean isForgetAction(String name) {
    return Objects.equals(name, actions.forget);
  }

  @JsonIgnore
  public boolean isHumanMarker(String name) {
    return Objects.equals(name, actions.humanMarker);
  }

  @JsonIgnore
  public boolean isStateFact(String name) {
    return name != null && facts.state.contains(name);
  }

  @JsonIgnore
  public boolean isOutboundChannel(String name) {
    return name != null && facts.outboundChannels.contains(name);
  }

  @JsonIgnore
  public boolean isInboundChannel(String name) {
    return name != null && facts.inboundChannels.contains(name);
  }

  @JsonIgnore
  public boolean isFreshFact(String name) {
    return name != null && facts.fresh.contains(name);
  }

  @JsonIgnore
  public boolean isPersistent(String name) {
    return name != null && name.startsWith(adornments.persistentPrefix);
  }

  /** The set the Forget strategy uses to decide what is *not* an internal cognitive action. */
  @JsonIgnore
  public Set<String> nonInternalActions() {
    return new LinkedHashSet<>(actions.coreActions);
  }

  /** Replace the entire state of this vocabulary (used by the Settings API). */
  public void copyFrom(CeremonyVocabulary other) {
    if (other == null) return;
    this.actions = other.actions != null ? other.actions : new Actions();
    this.facts = other.facts != null ? other.facts : new Facts();
    this.adornments = other.adornments != null ? other.adornments : new Adornments();
    this.channels = other.channels != null ? other.channels : new Channels();
    this.descriptions = other.descriptions != null
        ? new LinkedHashMap<>(other.descriptions)
        : new LinkedHashMap<>();
  }
}

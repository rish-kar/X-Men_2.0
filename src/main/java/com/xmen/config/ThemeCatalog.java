package com.xmen.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Holds the 15 named themes loaded from {@code themes.yaml} plus a pointer to the active one.
 * Designed to be mutated at runtime through the Settings API so users can switch themes from
 * the UI without restarting the application.
 */
@Component
@ConfigurationProperties(prefix = "xmen.themes")
@Data
@NoArgsConstructor
public class ThemeCatalog {

  /** Id of the currently active theme. */
  @JsonProperty("default")
  private String defaultId = "classic";

  /** Full theme catalogue. */
  private List<Theme> catalog = new ArrayList<>();

  /** Look up by id. Returns the default theme if id is null/unknown. */
  public Theme resolve(String id) {
    if (id != null) {
      Optional<Theme> hit = catalog.stream().filter(t -> id.equals(t.getId())).findFirst();
      if (hit.isPresent()) return hit.get();
    }
    return catalog.stream()
        .filter(t -> defaultId.equals(t.getId()))
        .findFirst()
        .orElseGet(() -> catalog.isEmpty() ? new Theme() : catalog.get(0));
  }

  /**
   * A single theme palette. Every colour is a JavaFX-friendly CSS string (hex or rgba()).
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Theme {
    private String id = "classic";
    private String name = "Classic";
    private String accent = "#A56BFF";

    @JsonProperty("accent-soft")
    private String accentSoft = "#D4B4FF";

    private String overlay = "rgba(26, 10, 48, 0.70)";

    @JsonProperty("glass-fill")
    private String glassFill = "rgba(155, 93, 229, 0.22)";

    @JsonProperty("glass-stroke")
    private String glassStroke = "rgba(214, 178, 255, 0.45)";

    private String text = "#F8F2FF";

    @JsonProperty("text-muted")
    private String textMuted = "rgba(238, 222, 255, 0.78)";

    private String shadow = "rgba(10, 0, 30, 0.55)";
  }
}

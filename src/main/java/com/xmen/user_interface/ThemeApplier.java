package com.xmen.user_interface;

import com.xmen.config.ThemeCatalog.Theme;
import javafx.scene.Parent;

/**
 * Translates a {@link Theme} into a JavaFX inline style on the root node.
 *
 * <p>The {@code main-v2.css} stylesheet declares CSS variables (e.g. {@code -accent}) on
 * {@code .x-root}. By setting those variables inline on the root, every selector in the
 * stylesheet that references them updates instantly — no scene rebuild required.
 */
public final class ThemeApplier {

  private ThemeApplier() {}

  public static void apply(Parent root, Theme theme) {
    if (root == null || theme == null) return;
    root.getStyleClass().removeIf(style -> style.startsWith("x-theme-"));
    if (theme.getId() != null && !theme.getId().isBlank()) {
      root.getStyleClass().add("x-theme-" + theme.getId().replaceAll("[^A-Za-z0-9_-]", "-"));
    }
    String style =
        new StringBuilder(256)
            .append("-accent: ").append(theme.getAccent()).append(';')
            .append("-accent-soft: ").append(theme.getAccentSoft()).append(';')
            .append("-overlay: ").append(theme.getOverlay()).append(';')
            .append("-glass-fill: ").append(theme.getGlassFill()).append(';')
            .append("-glass-stroke: ").append(theme.getGlassStroke()).append(';')
            .append("-text: ").append(theme.getText()).append(';')
            .append("-text-muted: ").append(theme.getTextMuted()).append(';')
            .append("-shadow: ").append(theme.getShadow()).append(';')
            .toString();
    root.setStyle(style);
  }
}

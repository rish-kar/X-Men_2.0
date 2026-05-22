package com.sermas.x.men.user_interface;

import com.sermas.x.men.config.ThemeCatalog.Theme;
import java.io.InputStream;
import java.util.Set;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Resolves which logo PNG to display based on the active theme.
 *
 * <p>Themes whose {@code text} colour is dark (e.g. Paper Light, Arctic Ice) need the
 * <strong>Black.png</strong> logo so it stays visible. Dark themes use the
 * <strong>White.png</strong> logo. The decision uses both an id allow-list and a luminance
 * heuristic on the {@code text} colour, so adding a new light-mode theme later still works
 * without code changes.
 */
public final class ThemeLogo {

  private ThemeLogo() {}

  /** Themes whose foreground text is intentionally dark — paired with Black.png. */
  private static final Set<String> LIGHT_THEMES = Set.of("paper-light", "arctic-ice");

  public static ImageView build(Theme theme, double targetWidth) {
    String resource = pickResource(theme);
    try (InputStream is = ThemeLogo.class.getResourceAsStream(resource)) {
      if (is != null) {
        ImageView iv = new ImageView(new Image(is));
        iv.setFitWidth(targetWidth);
        iv.setPreserveRatio(true);
        iv.getStyleClass().add("x-logo-img");
        return iv;
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  /** Update an existing ImageView in place — used when the theme changes at runtime. */
  public static void apply(ImageView iv, Theme theme) {
    if (iv == null) return;
    String resource = pickResource(theme);
    try (InputStream is = ThemeLogo.class.getResourceAsStream(resource)) {
      if (is != null) {
        iv.setImage(new Image(is));
      }
    } catch (Exception ignored) {
    }
  }

  public static boolean isLightTheme(Theme theme) {
    if (theme == null) return false;
    if (theme.getId() != null && LIGHT_THEMES.contains(theme.getId())) return true;
    // Luminance fallback: dark text → light theme.
    return luminance(theme.getText()) < 0.5;
  }

  private static String pickResource(Theme theme) {
    return isLightTheme(theme) ? "/images/Black.png" : "/images/White.png";
  }

  /** Rough 0–1 luminance of a hex/rgba colour string; defaults to 1.0 (assume bright). */
  private static double luminance(String css) {
    if (css == null || css.isBlank()) return 1.0;
    try {
      javafx.scene.paint.Color c = javafx.scene.paint.Color.web(css);
      return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
    } catch (Exception e) {
      return 1.0;
    }
  }
}

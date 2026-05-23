package com.sermas.x.men.user_interface;

import com.sermas.x.men.config.ThemeCatalog.Theme;
import java.io.InputStream;
import java.util.Set;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Resolves which logo PNG to display based on the active theme.
 *
 * <p>The product now ships a single brand mark — {@code SERMAS Classic.png} —
 * that reads on both light and dark backgrounds, so every theme uses the same
 * file. The {@link #isLightTheme(Theme)} helper and {@link #LIGHT_THEMES} set
 * are kept because other UI code (e.g. status-bar colouring) still consults
 * them.
 */
public final class ThemeLogo {

  private ThemeLogo() {}

  /** Themes whose foreground text is intentionally dark. */
  private static final Set<String> LIGHT_THEMES = Set.of("paper-light", "arctic-ice");

  /** Single brand mark used across every theme. */
  private static final String LOGO_RESOURCE = "/images/SERMAS Classic.png";

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
        double requestedWidth = iv.getFitWidth() > 0 ? iv.getFitWidth() * 2 : 0;
        Image base = new Image(is, requestedWidth, 0, true, true);
        iv.setImage(tintLogo(base, theme, 0.38));
      }
    } catch (Exception ignored) {
    }
  }

  private static Image tintLogo(Image base, Theme theme, double amount) {
    if (base == null || theme == null || theme.getAccentSoft() == null) return base;

    try {
      Color tint = Color.web(theme.getAccentSoft());

      int w = (int) Math.round(base.getWidth());
      int h = (int) Math.round(base.getHeight());

      if (w <= 0 || h <= 0) return base;

      WritableImage out = new WritableImage(w, h);
      PixelReader reader = base.getPixelReader();
      PixelWriter writer = out.getPixelWriter();

      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          Color c = reader.getColor(x, y);
          double a = c.getOpacity();

          if (a <= 0.02) {
            writer.setColor(x, y, c);
            continue;
          }

          double mix = amount * a;

          writer.setColor(
                  x,
                  y,
                  new Color(
                          c.getRed() * (1 - mix) + tint.getRed() * mix,
                          c.getGreen() * (1 - mix) + tint.getGreen() * mix,
                          c.getBlue() * (1 - mix) + tint.getBlue() * mix,
                          a));
        }
      }

      return out;
    } catch (Exception e) {
      return base;
    }
  }

  public static boolean isLightTheme(Theme theme) {
    if (theme == null) return false;
    if (theme.getId() != null && LIGHT_THEMES.contains(theme.getId())) return true;
    // Luminance fallback: dark text → light theme.
    return luminance(theme.getText()) < 0.5;
  }

  private static String pickResource(Theme theme) {
    // Theme is ignored — the SERMAS Classic mark is theme-agnostic.
    return LOGO_RESOURCE;
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

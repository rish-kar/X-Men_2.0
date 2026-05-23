package com.sermas.x.men.user_interface;

import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.StrokeType;

/**
 * Crisp SVG-path icons used in the UI. Every icon returns a fresh {@link SVGPath} per call
 * so the same shape can be embedded in multiple parents safely.
 *
 * <p>The icon's stroke can be overridden by CSS through the {@code x-icon-themed} style
 * class — useful when an icon needs to flip colour depending on the active theme.
 */
public final class Icons {

  private Icons() {}

  /** Compact, modern gear glyph — replaces the "⚙" Unicode glyph. */
  public static SVGPath gear(double size, Color stroke) {
    String d =
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 "
            + "1.7 1.7 0 0 0-1 1.5V21a2 2 0 0 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3"
            + "l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 0 "
            + "1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a"
            + "1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 "
            + "1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 "
            + "0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z M12 15 a3 3 0 1 0 0-6 3 3 "
            + "0 0 0 0 6z";
    return strokeIcon(d, size, stroke);
  }

  /** Circled lower-case "i" — replaces the "ℹ" Unicode glyph. */
  public static SVGPath info(double size, Color stroke) {
    String d =
        "M12 22 a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 16 v-4 M12 8 h.01";
    return strokeIcon(d, size, stroke);
  }

  /**
   * Friendly chat-bot face — rounded head, antenna, two eyes. Used as the
   * trigger for the X-Men assistant button so it reads as an AI bot rather
   * than the "info" glyph it used to share with help dialogs.
   */
  public static SVGPath chatBot(double size, Color stroke) {
    String d =
        "M12 3 v2 "
            + "M9 5 h6 a3 3 0 0 1 3 3 v7 a3 3 0 0 1-3 3 h-3 l-3 3 v-3 h-0 a3 3 0 0 1-3-3 v-7 a3 3 0 0 1 3-3 z "
            + "M9.5 11 a1 1 0 1 0 0 0.01 z "
            + "M14.5 11 a1 1 0 1 0 0 0.01 z";
    return strokeIcon(d, size, stroke);
  }

  /** Trash-can icon used to delete chat threads. */
  public static SVGPath trash(double size, Color stroke) {
    String d =
        "M3 6 h18 M8 6 v-2 a2 2 0 0 1 2-2 h4 a2 2 0 0 1 2 2 v2 "
            + "M5 6 l1 14 a2 2 0 0 0 2 2 h8 a2 2 0 0 0 2-2 l1-14 "
            + "M10 11 v6 M14 11 v6";
    return strokeIcon(d, size, stroke);
  }

  /** Download arrow icon. */
  public static SVGPath download(double size, Color stroke) {
    String d =
        "M12 3 v12 M7 10 l5 5 l5-5 M5 19 h14";
    return strokeIcon(d, size, stroke);
  }

  /* ------------------------------------------------------------------ */
  /*  Internal                                                          */
  /* ------------------------------------------------------------------ */

  private static SVGPath strokeIcon(String d, double size, Color stroke) {
    SVGPath p = new SVGPath();
    p.setContent(d);
    p.setFill(Color.TRANSPARENT);
    p.setStroke(stroke);
    p.setStrokeWidth(2.0);
    p.setStrokeType(StrokeType.CENTERED);
    p.setStrokeLineCap(StrokeLineCap.ROUND);
    p.setStrokeLineJoin(StrokeLineJoin.ROUND);
    p.setScaleX(size / 24.0);
    p.setScaleY(size / 24.0);
    p.getStyleClass().add("x-icon-themed");
    return p;
  }
}

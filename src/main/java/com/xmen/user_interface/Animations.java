package com.xmen.user_interface;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Tiny helper for the subtle hover micro-animations used across the UI.
 *
 * <p>Each button registered through {@link #hoverLift(Node, double)} animates its scale up to
 * the supplied factor (e.g. {@code 1.03}) on mouse enter, then back to 1.0 on exit. The
 * animation is short (140 ms) and uses an ease-out curve so it feels snappy.
 */
public final class Animations {

  private static final String HOVER_SCALE_TRANSITION = "xmen.hoverScaleTransition";

  private Animations() {}

  public static void hoverLift(Node node, double scaleUpTo) {
    if (node == null) return;
    node.setOnMouseEntered(e -> scale(node, scaleUpTo));
    node.setOnMouseExited(e -> scale(node, 1.0));
  }

  private static void scale(Node node, double factor) {
    ScaleTransition t = (ScaleTransition) node.getProperties().get(HOVER_SCALE_TRANSITION);
    if (t == null) {
      t = new ScaleTransition(Duration.millis(140), node);
      t.setInterpolator(Interpolator.EASE_OUT);
      node.getProperties().put(HOVER_SCALE_TRANSITION, t);
    }
    t.stop();
    t.setFromX(node.getScaleX());
    t.setFromY(node.getScaleY());
    t.setToX(factor);
    t.setToY(factor);
    t.play();
  }
}

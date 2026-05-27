package com.xmen.model;

import java.util.List;
import java.util.Objects;

public class Derivation {

  private final Message goal;
  private final String rule; // e.g., "Initial", "Pairing", "Decryption", "Projection-Left", ...
  private final List<Derivation> premises;

  public Derivation(Message goal, String rule, List<Derivation> premises) {
    this.goal = goal;
    this.rule = rule;
    this.premises = premises;
  }

  public Message getGoal() {
    return goal;
  }

  public String getRule() {
    return rule;
  }

  public List<Derivation> getPremises() {
    return premises;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Derivation that)) return false;
    return goal.equals(that.goal) && rule.equals(that.rule) && premises.equals(that.premises);
  }

  @Override
  public int hashCode() {
    return Objects.hash(goal, rule, premises);
  }
}

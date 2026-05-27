package com.xmen.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Mutants class represents a mutation in the value of a component. It contains the old value and
 * the new value that will replace it. This class is used to track changes in component values
 * during the mutation process.
 */
@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class Mutants {

  public Value oldValue;
  public Value newValue;

  /**
   * Returns a string representation of the mutation.
   *
   * @return A string describing the mutation, indicating whether it is a replacement or a removal.
   */
  public String toString() {
    return this.oldValue != null
        ? "The old value is "
            + this.oldValue
            + " that will be replaced with "
            + this.newValue.toString()
        : "I will remove the value " + this.newValue.toString();
  }
}

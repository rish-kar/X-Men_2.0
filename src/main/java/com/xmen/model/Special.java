package com.xmen.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;

/**
 * Special class represents a special type of collection that holds a group of Value objects. It
 * provides methods to add values to the group.
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class Special {
  private ArrayList<Value> group;

  /**
   * Adds a Value object to the group.
   *
   * @param var1 the Value object to be added
   */
  public abstract void addValue(Value var1);
}

package com.xmen.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

/**
 * Represents a collection of built-in values. This class extends the Component class and provides
 * methods to manage a list of built-in values.
 */
@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class Builtins extends Component {
  private String name;
  private ArrayList<String> group;

  /**
   * Default constructor for Builtins.
   * Initializes the name and creates an empty list for the group of built-in values.
   */
  public Builtins(String name) {
    this.name = name;
    this.group = new ArrayList<>();
  }

  /**
   * Adds a value to the built-in values group.
   *
   * @param x The value to be added.
   */
  public void addValue(String x) {
    this.group.add(x);
  }

  /**
   * Removes a value from the built-in values group.
   */
  public String toString() {
    String name = "";

    for (int i = 0; i < this.group.size(); ++i) {
      name = name + this.group.get(i);
      if (i < this.group.size() - 1) {
        name = name + ", ";
      }
    }

    return this.name + " : " + name;
  }
}

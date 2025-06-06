package com.sermas.x.men.model;

import java.util.ArrayList;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Nary_app class represents a function with a name and a group of values. It allows adding values
 * to the group and provides a string representation of the function call.
 */
@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
public class Nary_app extends Abs_Value {

  @NonNull private String fname;
  private ArrayList<Value> group;

  /**
   * Constructor for Nary_app.
   *
   * @param x the name of the function
   */
  public void addValue(Value x) {
    this.group.add(x);
  }

  /**
   * Returns a string representation of the Nary_app function call.
   *
   * @return A string in the format "fname(value1 value2 ...)" where fname is the function name and
   */
  public String toString() {
    String name = "";

    for (int i = 0; i < this.group.size(); ++i) {
      name = name + this.group.get(i);
      if (i < this.group.size() - 1) {
        name = name + " ";
      }
    }

    return this.fname + "(" + name + ")";
  }
}

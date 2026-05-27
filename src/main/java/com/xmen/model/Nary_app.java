package com.xmen.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

/**
 * Nary_app class represents a function with a name and a group of values. It allows adding values
 * to the group and provides a string representation of the function call.
 */
@Slf4j
@Getter
@Setter
public class Nary_app extends Abs_Value {

  @NonNull private String fname;
  private ArrayList<Value> group = new ArrayList<>();

  /**
   * Constructor for Nary_app.
   *
   * @param fname the name of the function
   */
  public Nary_app(String fname) {
    this.fname = fname;
    this.group = new ArrayList<>();
  }

  /**
   * Adds a value to the group.
   *
   * @param x the value to add
   */
  public void addValue(Value x) {
    if (this.group == null) {
      this.group = new ArrayList<>();
    }
    this.group.add(x);
  }

  /**
   * Returns a string representation of the Nary_app function call.
   *
   * @return A string in the format "fname(value1,value2,...)" where fname is the function name
   */
  public String toString() {
    if (this.group == null || this.group.isEmpty()) {
      return this.fname + "()";
    }

    StringBuilder name = new StringBuilder();
    for (int i = 0; i < this.group.size(); ++i) {
      name.append(this.group.get(i));
      if (i < this.group.size() - 1) {
        name.append(",");
      }
    }

    return this.fname + "(" + name + ")";
  }
}

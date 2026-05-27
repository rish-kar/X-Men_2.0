package com.xmen.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * PSpecial class represents a special type of collection that holds a group of Value objects. It
 * provides methods to add, remove, and retrieve values, as well as to clone the collection.
 */
@Slf4j
@Getter
@Setter
@NoArgsConstructor
public class PSpecial extends Special implements Cloneable, Comparable {

  public ArrayList<Value> group = new ArrayList();

  /**
   * Adds a Value object to the group.
   *
   * @param x the Value object to be added
   */
  public void addValue(Value x) {
    this.group.add(x);
  }

  /**
   * Number of values in the group.
   *
   * @return the size of the group
   */
  public int numberOfValues() {
    return this.group.size();
  }

  /**
   * Gets the Value object at the specified position in the group.
   *
   * @param position the index of the Value object to retrieve
   */
  public Value getValue(int position) {
    return this.group.get(position);
  }

  /**
   * Returns string representation of the PSpecial object.
   *
   * @return a string in a certain format
   */
  public String toString() {
    if (this.group == null || this.group.isEmpty()) {
      return "<>";
    }

    StringBuilder str = new StringBuilder("<");
    boolean first = true;

    for (Value value : this.group) {
      if (value != null && !value.isRemoved()) {
        if (!first) {
          str.append(",");
        }
        first = false;

        if (value instanceof Variable) {
          String name = value.getName();
          if (name != null && !name.isEmpty()) {
            str.append(name);
          }
        } else {
          String valueStr = value.toString();
          if (valueStr != null && !valueStr.isEmpty()) {
            str.append(valueStr);
          }
        }
      }
    }

    str.append(">");
    return str.toString();
  }

  /**
   * Clones the PSpecial object, creating a new instance with the same group of Value objects. This
   * method ensures that the cloned object has its own copy of the group list, preventing shared
   * references between the original and cloned objects.
   *
   * @return a new PSpecial object that is a clone of the current instance
   */
  public PSpecial clone() {
    try {
      PSpecial p = (PSpecial) super.clone();
      ArrayList<Value> clone = cloneList(this.group);
      p.group = clone;
      return p;
    } catch (CloneNotSupportedException var3) {
      CloneNotSupportedException ex = var3;
      throw new RuntimeException(ex);
    }
  }

  /**
   * Clones the provided list of Value objects, creating a new ArrayList with cloned instances of
   * each Value.
   *
   * @param list the ArrayList of Value objects to be cloned
   * @return a new ArrayList containing cloned Value objects
   */
  private static ArrayList<Value> cloneList(ArrayList<Value> list) {
    ArrayList<Value> clone = new ArrayList(list.size());
    Iterator var2 = list.iterator();

    while (var2.hasNext()) {
      Value item = (Value) var2.next();
      clone.add(item.clone());
    }

    return clone;
  }

  /**
   * Finds a Value object in the group by its name.
   *
   * @param x the Value object to search for
   * @return the Value object if found, or null if not found
   */
  public Value findParameter3(Value x) {
    Iterator var2 = this.group.iterator();

    Object obj;
    do {
      if (!var2.hasNext()) {
        return null;
      }

      obj = var2.next();
    } while (!((Value) obj).getName().equals(x.getName()));

    return (Value) obj;
  }

  /**
   * Finds a PSpecial object in the group by its name.
   *
   * @param x the Value object to search for
   * @return the PSpecial object if found, or null if not found
   */
  public PSpecial findParameter2(Value x) {
    Iterator var2 = this.group.iterator();

    Object obj;
    do {
      if (!var2.hasNext()) {
        return null;
      }

      obj = var2.next();
    } while (!((Value) obj).getName().equals(x.getName()));

    return this;
  }

  /**
   * Compares this PSpecial object with another object for equality.
   *
   * @param o the object to be compared.
   * @return 1 if the objects are equal, 0 otherwise.
   */
  public int compareTo(Object o) {
    boolean equal = true;
    PSpecial oo = (PSpecial) o;
    if (oo.numberOfValues() != this.numberOfValues()) {
      return 0;
    } else {
      for (int x = 0; x < this.group.size(); ++x) {
        if (this.group.get(x).compareTo(oo.getValue(x)) != 1) {
          equal = false;
        }
      }

      if (equal) {
        return 1;
      } else {
        return 0;
      }
    }
  }
}

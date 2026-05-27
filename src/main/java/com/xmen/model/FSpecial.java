package com.xmen.model;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * FSpecial class represents a special type of object that contains a name, a group of values, and a
 * key. It extends the Special class and implements cloning functionality.
 */
@Slf4j
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
public class FSpecial extends Special implements Cloneable {

  @NonNull private String fname;
  // initialize to avoid NPE when adding values
  private ArrayList<Object> group = new ArrayList<>();
  private Abs_Value key;

  /**
   * Returns the group of this FSpecial instance.
   *
   * @return An ArrayList containing the group of values associated with this FSpecial instance.
   */
  @Override
  public ArrayList<Value> getGroup() {
    // Convert internal Object list to a Value list (skip non-Value entries)
    ArrayList<Value> res = new ArrayList<>();
    if (this.group == null) {
      return res;
    }
    for (Object o : this.group) {
      if (o instanceof Value) {
        res.add((Value) o);
      }
    }
    return res;
  }

  /**
   * Sets the group of this FSpecial instance using an ArrayList of Value objects.
   *
   * @param group An ArrayList containing the new group of values to set for this FSpecial instance.
   */
  @Override
  public void setGroup(ArrayList<Value> group) {
    if (group == null) {
      this.group = new ArrayList<>();
      return;
    }
    this.group = new ArrayList<>(group.size());
    this.group.addAll(group);
  }

  /**
   * Adds a special object to the group of this FSpecial instance. The object can be a Variable,
   * PSpecial, or Value.
   *
   * @param x The name of the special object.
   */
  public void addValue(Value x) {
    if (this.group == null) {
      this.group = new ArrayList<>();
    }
    this.group.add(x);
  }

  /**
   * Clones this FSpecial instance, creating a new instance with the same values. This method
   * performs a deep copy of the group list to ensure that the cloned instance does not share
   * references with the original.
   *
   * @return A new FSpecial instance that is a clone of this instance.
   */
  public FSpecial clone() {
    try {
      FSpecial p = (FSpecial) super.clone();
      p.fname = this.fname;
      ArrayList<Object> clone = cloneList(this.group);
      p.group = clone;
      p.key = this.key;
      return p;
    } catch (CloneNotSupportedException var3) {
      CloneNotSupportedException ex = var3;
      throw new RuntimeException(ex);
    }
  }

  /**
   * Clones the list of values in the group of this FSpecial instance. This method creates a new
   * ArrayList containing deep copies of each item in the original list, ensuring that modifications
   * to the cloned list do not affect the original list.
   *
   * @param list The list to clone, which can contain Variable, PSpecial, or Value objects.
   * @return A new ArrayList containing cloned items from the original list.
   */
  private static ArrayList<Object> cloneList(ArrayList<Object> list) {
    if (list == null) {
      return new ArrayList<>();
    }
    ArrayList<Object> clone = new ArrayList(list.size());
    Iterator var2 = list.iterator();

    while (var2.hasNext()) {
      Object item = var2.next();
      if (item instanceof PSpecial) {
        clone.add(((PSpecial) item).clone());
      } else if (item instanceof Variable) {
        clone.add(item);
      } else if (item instanceof Value) {
        clone.add(((Value) item).clone());
      }
    }

    return clone;
  }

  /**
   * Returns a string representation of this FSpecial instance.
   *
   * @return A string that includes the function name, group of values, and key.
   */
  public String toString() {
    String name = "";

    if (this.group == null || this.group.isEmpty()) {
      return "";
    }

    for (int i = 0; i < this.group.size(); ++i) {
      Object c = this.group.get(i);
      if (c instanceof Variable) {
        String x = c.toString();
        if (!x.equals("")) {
          name = name + ((Variable) c).getName();
          if (this.group.size() != 1 && i < this.group.size() - 1) {
            name = name.concat(",");
          }
        }
      } else if (c instanceof Value) {
        if (!((Value) c).isRemoved()) {
          name = name.concat(((Value) c).getName());
          if (this.group.size() != 1 && i < this.group.size() - 1) {
            name = name.concat(",");
          }
        } else if (i == this.group.size() - 1) {
          name = name.substring(0, name.length() - 1);
        }
      } else if (c instanceof PSpecial) {
      }
    }

    if (!name.equals("")) {
      return this.fname + "{" + name + "}" + (this.key != null ? this.key.toString() : "");
    } else {
      return "";
    }
  }
}

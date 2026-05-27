package com.xmen.model;

import java.util.Objects;

/**
 * Atom class represents a simple message consisting of a single string value. It implements the
 * Message interface and provides methods to represent the atom, check equality, and retrieve the
 * value of the atomic entity.
 */
public class Atom implements Message {

  private final String value;

  /**
   * Constructor for Atom.
   *
   * @param value the string value of the atom
   */
  public Atom(String value) {
    this.value = value;
  }

  /**
   * Represents the atom as a string.
   *
   * @return the string representation of the atom
   */
  @Override
  public String represent() {
    return value;
  }

  /**
   * Checks if this atom is equal to another object. Two atoms are considered equal if their values
   * are the same.
   *
   * @param o the object to compare with
   * @return true if the object is an Atom and its value is equal to this atom's value, false
   *     otherwise
   */
  @Override
  public boolean equals(Object o) {
    return (o instanceof Atom) && value.equals(((Atom) o).value);
  }

  /**
   * Generates a hash code for this atom based on its value.
   *
   * @return the hash code of the atom
   */
  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  /**
   * Returns the value of the atom.
   *
   * @return the string value of the atom
   */
  public String getValue() {
    return value;
  }

  /**
   * Returns a string representation of the atom.
   *
   * @return a string in the format "Atom(value)"
   */
  @Override
  public String toString() {
    return "Atom(" + value + ")";
  }
}

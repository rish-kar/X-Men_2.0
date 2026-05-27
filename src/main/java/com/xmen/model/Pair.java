package com.xmen.model;

import java.util.Objects;

/**
 * Represents a pair of messages, allowing for structured data representation. This class implements
 * the Message interface and provides methods to represent the pair, check equality, and retrieve
 * the left and right messages.
 */
public class Pair implements Message {
  private final Message left;
  private final Message right;

  /**
   * Constructor for Pair.
   *
   * @param left the left message
   * @param right the right message
   */
  public Pair(Message left, Message right) {
    this.left = left;
    this.right = right;
  }

  /**
   * Represents the pair as a string in the format "(left, right)".
   *
   * @return a string representation of the pair
   */
  @Override
  public String represent() {
    return "(" + left.represent() + ", " + right.represent() + ")";
  }

  /**
   * Checks if this pair is equal to another object. Two pairs are considered equal if both their
   * left and right messages are equal.
   *
   * @param o the object to compare with
   * @return true if the object is a Pair and both left and right messages are equal, false
   *     otherwise
   */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Pair)) {
      return false;
    }
    Pair p = (Pair) o;
    return left.equals(p.left) && right.equals(p.right);
  }

  /**
   * Generates a hash code for this pair based on the hash codes of its left and right messages.
   *
   * @return the hash code of the pair
   */
  @Override
  public int hashCode() {
    return Objects.hash(left, right);
  }

  /**
   * Returns the left message of the pair.
   *
   * @return the left message
   */
  public Message getLeft() {
    return left;
  }

  /**
   * Returns the right message of the pair.
   *
   * @return the right message
   */
  public Message getRight() {
    return right;
  }

  /**
   * Returns a string representation of the pair in the format "Pair(left, right)".
   *
   * @return a string representation of the pair
   */
  @Override
  public String toString() {
    return "Pair(" + left + ", " + right + ")";
  }
}

package com.xmen.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * PredictiveFunction class represents a predictive function in the system. This class implements
 * the Message interface and provides methods to represent the function, check equality, and
 * retrieve the function's name and arguments.
 */
public class PredictiveFunction implements Message {
  private final String name;
  private final List<Message> args;

  /**
   * Constructor for PredictiveFunction.
   *
   * @param name the name of the predictive function
   * @param args the arguments of the predictive function
   */
  public PredictiveFunction(String name, List<Message> args) {
    this.name = name;
    this.args = args;
  }

  /**
   * Represents the predictive function as a string in the format "name(arg1, arg2, ...)".
   *
   * @return a string representation of the predictive function
   */
  @Override
  public String represent() {
    return name
        + "("
        + args.stream().map(Message::represent).collect(Collectors.joining(", "))
        + ")";
  }

  /**
   * Checks if this predictive function is equal to another object. Two predictive functions are
   * considered equal if their names and arguments are the same.
   *
   * @param o the object to compare with
   * @return true if the object is a PredictiveFunction and both name and args are equal, false
   *     otherwise
   */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PredictiveFunction)) return false;
    PredictiveFunction f = (PredictiveFunction) o;
    return name.equals(f.name) && args.equals(f.args);
  }

  /**
   * Generates a hash code for this predictive function based on its name and arguments.
   *
   * @return the hash code of the predictive function
   */
  @Override
  public int hashCode() {
    return Objects.hash(name, args);
  }

  /**
   * Returns the arguments of the predictive function.
   *
   * @return a list of messages representing the arguments
   */
  public List<Message> getArgs() {
    return args;
  }

  /**
   * Returns the name of the predictive function.
   *
   * @return the name of the predictive function
   */
  public String getName() {
    return name;
  }

  /**
   * Returns a string representation of the predictive function.
   *
   * @return a string in the format "PredictiveFunction(name, args)"
   */
  @Override
  public String toString() {
    return "PredictiveFunction(" + name + args + ")";
  }
}

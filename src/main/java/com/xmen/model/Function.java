package com.xmen.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Function class represents a function with a name and the number of parameters it accepts. It
 * extends the Component class and provides a string representation of the function.
 */
@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class Function extends Component {

  public String name;
  public int numberofParam;

  /**
   * Returns a string representation of the function.
   *
   * @return A string in the format "name/numberofParam" where
   */
  public String toString() {
    return this.name + "/" + this.numberofParam;
  }
}

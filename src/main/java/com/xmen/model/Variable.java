package com.xmen.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Variable entity representing a variable in the system. It extends the Value class and includes
 * special values.
 */
@Getter
@Setter
public class Variable extends Value implements Cloneable {

    private Special values;

    /**
     * Constructor for Variable with name and special values.
     *
     * @param name the name of the variable
     */
    public Variable(String name) {
        super(name);
    }

    /**
     * Get Name method to retrieve the name of the variable.
     *
     * @return the name of the variable
     */
    public String getName() {
        return super.getName();
    }

    /**
     * Set Values method to assign special values to the variable.
     *
     * @param isRemoved indicates if the variable is removed
     */
    public void setRemoved(boolean isRemoved) {
        super.setRemoved(isRemoved);
    }

    /**
     * Clone method to create a copy of the Variable object.
     *
     * @return a new Variable object with the same properties
     */
    public Variable clone() {
        Variable v = new Variable(this.getName());
        Special spec = null;
        if (this.values instanceof PSpecial) {
            spec = ((PSpecial) this.values).clone();
        } else if (this.values instanceof FSpecial) {
            spec = ((FSpecial) this.values).clone();
        }

        v.setValues(spec);
        return v;
    }

    /**
     * Returns the string representation of the variable.
     *
     * @return the string representation of the variable if it is not removed, otherwise an empty
     *     string
     */
    public String toString() {
        if (!this.isRemoved()) {
            String params = "";
            if (this.values instanceof PSpecial) {
                params = this.values.toString();
            } else {
                params = this.values.toString();
            }

            return !params.isEmpty() ? super.getName() + " = " + params : "";
        } else {
            return "";
        }
    }
}

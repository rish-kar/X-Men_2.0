package com.xmen.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/** Value entity representing a value in the system. */
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class Value extends Abs_Value implements Cloneable, Comparable {

    private String name;
    private String tag;
    private boolean isAdded;
    private boolean isRemoved;
    private boolean isModified;
    private boolean inKnowledge;

    /**
     * Constructor for Value.
     *
     * @param name the name of the value
     * @param isMutated indicates if the value is mutated
     * @param isRemoved indicates if the value is removed
     * @param inKnowledge indicates if the value is in knowledge
     */
    public Value(String name, boolean isMutated, boolean isRemoved, boolean inKnowledge) {
        this.name = name;
        this.isAdded = isMutated;
        this.isRemoved = isRemoved;
        this.inKnowledge = inKnowledge;
    }

    /**
     * Constructor for Value with only name.
     *
     * @param name the name of the value
     */
    public Value(String name) {
        this.name = name;
    }

    /** Persistent knowledge indicates that the value is part of the persistent knowledge base. */
    public void persistentKnowledge() {
        this.inKnowledge = true;
    }

    /**
     * Clone method to create a copy of the Value object.
     *
     * @return a new Value object with the same properties
     */
    public Value clone() {
        Value v = new Value(this.name, this.isAdded, this.isRemoved, this.inKnowledge);
        v.setTag(this.tag);
        return v;
    }

    /**
     * Returns the name of the value.
     *
     * @return the name of the value if it is not removed, otherwise an empty string
     */
    public String toString() {
        return !this.isRemoved() ? this.getName() : "";
    }

    /**
     * Returns the hash code of the value.
     *
     * @return the hash code of the value's name
     */
    public int hashCode() {
        return this.toString().hashCode();
    }

    /**
     * Checks if the value is a constant.
     *
     * @return true if the value's name contains a single quote, indicating it is a constant,
     *     otherwise false
     */
    public boolean isConstant() {
        return this.name.contains("'");
    }

    /**
     * Checks if the value is equal to another object.
     *
     * @param obj the object to compare with
     * @return true if the object is a Value and has the same name, otherwise false
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (this.getClass() != obj.getClass()) {
            return false;
        } else {
            Value other = (Value) obj;
            return Objects.equals(this.name, other.name);
        }
    }

    /**
     * Compares this Value object with another object.
     *
     * @param o the object to compare with
     * @return 1 if the names are equal and the other properties match, otherwise 0
     */
    public int compareTo(Object o) {
        Value oo = (Value) o;
        if (this.name.equals(oo.getName())) {
            if (this.isAdded == oo.isAdded()) {
                if (this.isRemoved == oo.isRemoved()) {
                    return this.inKnowledge == oo.isInKnowledge() ? 1 : 0;
                } else {
                    return 0;
                }
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }
}

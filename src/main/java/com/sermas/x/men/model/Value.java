package com.sermas.x.men.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public class Value extends Abs_Value implements Cloneable, Comparable {
    private String name;
    private String tag;
    private boolean isAdded;
    private boolean isRemoved;
    private boolean isModified;
    private boolean inKnowledge;

    public Value(String name) {
        this.name = name;
    }

    public Value(String name, boolean isMutated, boolean isRemoved, boolean inKnowledge) {
        this.name = name;
        this.isAdded = isMutated;
        this.isRemoved = isRemoved;
        this.inKnowledge = inKnowledge;
    }

    public void persistentKnowledge() {
        this.inKnowledge = true;
    }

    public void normalKnowledge() {
        this.inKnowledge = false;
    }

    public Value clone() {
        Value v = new Value(this.name, this.isAdded, this.isRemoved, this.inKnowledge);
        v.setTag(this.tag);
        return v;
    }

    public String toString() {
        return !this.isRemoved() ? this.getName() : "";
    }

    public int hashCode() {
        return this.toString().hashCode();
    }

    public boolean isConstant() {
        return this.name.contains("'");
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (this.getClass() != obj.getClass()) {
            return false;
        } else {
            Value other = (Value)obj;
            return Objects.equals(this.name, other.name);
        }
    }

    public boolean myEquals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (this.getClass() != obj.getClass()) {
            return false;
        } else {
            Value other = (Value)obj;
            return Objects.equals(this.name, other.name) ? true : Objects.equals(this.name.replaceAll("[^a-zA-Z]", ""), other.name.replaceAll("[^a-zA-Z]", ""));
        }
    }

    public int compareTo(Object o) {
        Value oo = (Value)o;
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
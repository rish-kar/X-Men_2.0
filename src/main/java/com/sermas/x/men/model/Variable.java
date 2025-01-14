package com.sermas.x.men.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Variable extends Value implements Cloneable {
    private Special values;

    public Variable(String name) {
        super(name);
    }

    public String getName() {
        return super.getName();
    }

    public void setRemoved(boolean isRemoved) {
        super.setRemoved(isRemoved);
    }

    public Variable clone() {
        Variable v = new Variable(this.getName());
        Special spec = null;
        if (this.values instanceof PSpecial) {
            spec = ((PSpecial)this.values).clone();
        } else if (this.values instanceof FSpecial) {
            spec = ((FSpecial)this.values).clone();
        }

        v.setValues((Special)spec);
        return v;
    }

    public String toString() {
        if (!this.isRemoved()) {
            String params = "";
            if (this.values instanceof PSpecial) {
                params = ((PSpecial)this.values).toString();
            } else {
                params = ((FSpecial)this.values).toString();
            }

            return !params.isEmpty() ? super.getName() + " = " + params : "";
        } else {
            return "";
        }
    }
}
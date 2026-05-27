package com.xmen.model;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Represents a fact in the system. A fact consists of a name, type, parameters, and flags
 * indicating whether it has been added, modified, or removed.
 */
@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class Fact implements Cloneable, Comparable {

    @NonNull public String f_name;
    public TypeFact type;

    @NonNull public ArrayList<Object> parametes;
    public boolean isRemoved;
    public boolean isAdded;
    public boolean isModified;

    /**
     * Default constructor for Fact. Initializes the fact with a name and an empty list of parameters.
     *
     * @param fName The name of the fact.
     */
    public Fact(String fName) {
        this.f_name = fName;
        this.parametes = new ArrayList<>();
    }

    /**
     * Returns parameters of the fact.
     *
     * @return An ArrayList containing the parameters of the fact.
     */
    public ArrayList<Object> getParameters() {
        return this.parametes;
    }

    /**
     * Sets the parameters of the fact using an ArrayList.
     *
     * @param x An ArrayList containing the new parameters to set.
     */
    public void setArrayListParameters(ArrayList<Object> x) {
        ArrayList<Object> v = new ArrayList<>();
        v.addAll(x);
        this.parametes.clear();
        this.parametes.addAll(v);
    }

    /**
     * Sets a single parameter at a specified index.
     *
     * @param x The parameter to set, which can be a Value or PSpecial object.
     * @param index The index at which to set the parameter.
     * @throws CloneNotSupportedException If cloning of the parameter fails.
     */
    public void setSingleParameter(Object x, int index) throws CloneNotSupportedException {
        if (x instanceof Value) {
            Value v =
                    new Value(
                            ((Value) x).getName(),
                            ((Value) x).isAdded(),
                            ((Value) x).isRemoved(),
                            ((Value) x).isInKnowledge());
            v.setTag(((Value) x).getTag());
            this.parametes.set(index, v);
        } else if (x instanceof PSpecial value) {
            this.parametes.set(index, value.clone());
        }
    }

    /**
     * Returns the name of the fact.
     *
     * @return The name of the fact.
     */
    public String toString() {
        if (!this.isRemoved) {
            String str = this.f_name + "(";

            for (int po = 0; po < this.parametes.size(); ++po) {
                Object c;
                if (po < this.parametes.size() - 1) {
                    c = this.parametes.get(po);
                    if (c instanceof Variable) {
                        str = str.concat(((Variable) c).getName() + ",");
                    } else {
                        str = str.concat(this.parametes.get(po).toString() + ",");
                    }
                } else {
                    c = this.parametes.get(po);
                    if (c instanceof Variable) {
                        str = str.concat(((Variable) c).getName());
                    } else {
                        str = str.concat(this.parametes.get(po).toString());
                    }
                }
            }

            str = str.concat(")");
            return str;
        } else {
            return "";
        }
    }

    /**
     * Clones the Fact object, creating a deep copy of its parameters.
     *
     * @return A new Fact object that is a clone of this one.
     */
    public Fact clone() {
        try {
            Fact p = (Fact) super.clone();
            ArrayList<Object> clone1 = cloneList(this.parametes);
            p.parametes = clone1;
            return p;
        } catch (CloneNotSupportedException var3) {
            CloneNotSupportedException ex = var3;
            throw new RuntimeException(ex);
        }
    }

    /**
     * Clones the list of parameters, creating a deep copy of each item.
     *
     * @param list The list to clone.
     * @return A new ArrayList containing cloned items.
     */
    private static ArrayList<Object> cloneList(ArrayList<Object> list) {
        ArrayList<Object> clone = new ArrayList<>(list.size());
        Iterator<Object> var2 = list.iterator();

        while (var2.hasNext()) {
            Object item = var2.next();
            if (item instanceof Variable) {
                clone.add(((Variable) item).clone());
            } else if (item instanceof Value) {
                clone.add(((Value) item).clone());
            } else if (item instanceof PSpecial) {
                clone.add(((PSpecial) item).clone());
            }
        }

        return clone;
    }

    /**
     * Finds a fact using a specific value.
     *
     * @param v The value to search for in the parameters.
     * @return The Fact object if found, otherwise null.
     */
    public Fact findFactUsingValue(Value v) {
        Iterator<Object> var2 = this.parametes.iterator();

        while (var2.hasNext()) {
            Object x = var2.next();
            if (x instanceof Value) {
                if (((Value) x).getName().equals(v.getName())) {
                    return this;
                }
            } else if (x instanceof PSpecial && ((PSpecial) x).findParameter2(v) != null) {
                return this;
            }
        }

        return null;
    }

    /**
     * Finds a value in the parameters of the fact.
     *
     * @param v The value to search for.
     * @return The Value object if found, otherwise null.
     */
    public Value findValue(Value v) {
        Iterator<Object> var2 = this.parametes.iterator();

        while (var2.hasNext()) {
            Object x = var2.next();
            if (x instanceof Value) {
                if (((Value) x).getName().equals(v.getName())) {
                    return (Value) x;
                }
            } else if (x instanceof PSpecial) {
                Value xx = ((PSpecial) x).findParameter3(v);
                if (xx != null) {
                    return xx;
                }
            }
        }

        return null;
    }

    /**
     * Returns the parameter at a specified position.
     *
     * @param position The index of the parameter to retrieve.
     * @return The parameter at the specified position, or null if the position is out of bounds.
     */
    public Object getParameter(int position) {
        return position > this.parametes.size() ? null : this.parametes.get(position);
    }

    /** Checks if the fact has been removed. */
    public void setRemoved(boolean isRemoved) {
        this.isRemoved = isRemoved;
        Iterator<Object> var2 = this.parametes.iterator();

        while (true) {
            while (var2.hasNext()) {
                Object x = var2.next();
                if (x instanceof Value) {
                    ((Value) x).setRemoved(true);
                } else {
                    Iterator<Value> var4 = ((PSpecial) x).getGroup().iterator();

                    while (var4.hasNext()) {
                        Value xx = var4.next();
                        xx.setRemoved(true);
                    }
                }
            }

            return;
        }
    }

    /**
     * Checks if the fact has been added.
     *
     * @return true if the fact is added, false otherwise.
     */
    public boolean isAdded() {
        return this.isAdded;
    }

    /**
     * Compares this Fact object with another Fact object.
     *
     * @param o the object to be compared.
     * @return 1 if the two Fact objects are equal, 0 otherwise.
     */
    public int compareTo(Object o) {
        Fact oo = (Fact) o;
        boolean equal = true;
        if (this.f_name.equals(oo.getF_name())) {
            if (this.type == oo.getType()) {
                if (this.isAdded == oo.isAdded()) {
                    if (this.isRemoved == oo.isRemoved()) {
                        for (int x = 0; x < this.parametes.size(); ++x) {
                            Object o1 = this.parametes.get(x);
                            Object o2 = oo.parametes.get(x);
                            if (o1 instanceof Value && o2 instanceof Value) {
                                if (((Value) o1).compareTo(o2) != 1) {
                                    equal = false;
                                    break;
                                }
                            } else if (o1 instanceof PSpecial && o2 instanceof PSpecial) {
                                if (((PSpecial) o1).compareTo(o2) != 1) {
                                    equal = false;
                                    break;
                                }
                            } else {
                                equal = false;
                            }
                        }
                    } else {
                        equal = false;
                    }
                } else {
                    equal = false;
                }
            } else {
                equal = false;
            }
        } else {
            equal = false;
        }

        return equal ? 1 : 0;
    }
}
package com.sermas.x.men.model;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;

@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class Fact implements Cloneable, Comparable {

    @NonNull
    public String f_name;
    public TypeFact type;

    @NonNull
    public ArrayList<Object> parametes;
    public boolean isRemoved;
    public boolean isAdded;
    public boolean isModified;

    public Fact(String f_name) {
        this.f_name = f_name;
        this.parametes = new ArrayList<>();
    }

    public ArrayList<Object> getParameters() {
        return this.parametes;
    }

    public void setArrayListParameters(ArrayList x) {
        ArrayList v = new ArrayList();
        v.addAll(x);
        this.parametes.clear();
        this.parametes.addAll(v);
    }

    public void setSingleParameter(Object x, int index) throws CloneNotSupportedException {
        if (x instanceof Value) {
            Value v = new Value(((Value)x).getName(), ((Value)x).isAdded(), ((Value)x).isRemoved(), ((Value)x).isInKnowledge());
            v.setTag(((Value)x).getTag());
            this.parametes.set(index, v);
        } else if (x instanceof PSpecial) {
            PSpecial value = (PSpecial)x;
            this.parametes.set(index, value.clone());
        }

    }

    public String toString() {
        if (!this.isRemoved) {
            String str = this.f_name + "(";

            for(int po = 0; po < this.parametes.size(); ++po) {
                Object c;
                if (po < this.parametes.size() - 1) {
                    c = this.parametes.get(po);
                    if (c instanceof Variable) {
                        str = str.concat(((Variable)c).getName() + ",");
                    } else {
                        str = str.concat(this.parametes.get(po).toString() + ",");
                    }
                } else {
                    c = this.parametes.get(po);
                    if (c instanceof Variable) {
                        str = str.concat(((Variable)c).getName());
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

    public Fact clone() {
        try {
            Fact p = (Fact)super.clone();
            ArrayList<Object> clone1 = cloneList(this.parametes);
            p.parametes = clone1;
            return p;
        } catch (CloneNotSupportedException var3) {
            CloneNotSupportedException ex = var3;
            throw new RuntimeException(ex);
        }
    }

    private static ArrayList<Object> cloneList(ArrayList<Object> list) {
        ArrayList<Object> clone = new ArrayList(list.size());
        Iterator var2 = list.iterator();

        while(var2.hasNext()) {
            Object item = var2.next();
            if (item instanceof Variable) {
//                clone.add(item);
                clone.add(((Variable) item).clone());
            } else if (item instanceof Value) {
                clone.add(((Value)item).clone());
            } else if (item instanceof PSpecial) {
                clone.add(((PSpecial)item).clone());
            }
        }

        return clone;
    }

    public Fact findFactUsingValue(Value v) {
        Iterator var2 = this.parametes.iterator();

        while(var2.hasNext()) {
            Object x = var2.next();
            if (x instanceof Value) {
                if (((Value)x).getName().equals(v.getName())) {
                    return this;
                }
            } else if (x instanceof PSpecial && ((PSpecial)x).findParameter2(v) != null) {
                return this;
            }
        }

        return null;
    }

    public Value findValue(Value v) {
        Iterator var2 = this.parametes.iterator();

        while(var2.hasNext()) {
            Object x = var2.next();
            if (x instanceof Value) {
                if (((Value)x).getName().equals(v.getName())) {
                    return (Value)x;
                }
            } else if (x instanceof PSpecial) {
                Value xx = ((PSpecial)x).findParameter3(v);
                if (xx != null) {
                    return xx;
                }
            }
        }

        return null;
    }

    public void setRemovedFactFound(Value v) {
        Iterator var2 = this.parametes.iterator();

        while(var2.hasNext()) {
            Object x = var2.next();
            if (x instanceof Value) {
                if (((Value)x).getName().equals(v.getName())) {
                    this.isRemoved = true;
                }
            } else if (x instanceof PSpecial && ((PSpecial)x).findParameter2(v) != null) {
                this.isRemoved = true;
            }
        }

    }

    public Object getParameter(int position) {
        return position > this.parametes.size() ? null : this.parametes.get(position);
    }

    public void setRemoved(boolean isRemoved) {
        this.isRemoved = isRemoved;
        Iterator var2 = this.parametes.iterator();

        while(true) {
            while(var2.hasNext()) {
                Object x = var2.next();
                if (x instanceof Value) {
                    ((Value)x).setRemoved(true);
                } else {
                    Iterator var4 = ((PSpecial)x).getGroup().iterator();

                    while(var4.hasNext()) {
                        Value xx = (Value)var4.next();
                        xx.setRemoved(true);
                    }
                }
            }

            return;
        }
    }

    public boolean isAdded() {
        return this.isAdded;
    }

    public void setValueRemoved(Value value) {
        Iterator var2 = this.parametes.iterator();

        while(var2.hasNext()) {
            Object x = var2.next();
            if (x instanceof Value) {
                if (((Value)x).getName().equals(value.getName()) && !((Value)x).isInKnowledge()) {
                    ((Value)x).setRemoved(true);
                }
            } else if (x instanceof PSpecial) {
                Value val = ((PSpecial)x).findParameter3(value);
                if (val != null && !val.isInKnowledge()) {
                    val.setRemoved(true);
                }
            }
        }

    }

    public int compareTo(Object o) {
        Fact oo = (Fact)o;
        boolean equal = true;
        if (this.f_name.equals(oo.getF_name())) {
            if (this.type == oo.getType()) {
                if (this.isAdded == oo.isAdded()) {
                    if (this.isRemoved == oo.isRemoved()) {
                        for(int x = 0; x < this.parametes.size(); ++x) {
                            Object o1 = this.parametes.get(x);
                            Object o2 = oo.parametes.get(x);
                            if (o1 instanceof Value && o2 instanceof Value) {
                                if (((Value)o1).compareTo(o2) != 1) {
                                    equal = false;
                                    break;
                                }
                            } else if (o1 instanceof PSpecial && o2 instanceof PSpecial) {
                                if (((PSpecial)o1).compareTo(o2) != 1) {
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

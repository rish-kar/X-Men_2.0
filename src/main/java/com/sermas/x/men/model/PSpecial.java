package com.sermas.x.men.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;

@Slf4j
@Getter
@Setter
@NoArgsConstructor
public class PSpecial extends Special implements Cloneable, Comparable {

    public ArrayList<Value> group = new ArrayList();

    public void addValue(Value x) {
        this.group.add(x);
    }

    public int numberOfValues() {
        return this.group.size();
    }

    public void removeSpecificElement(Value x) {
        Iterator var2 = this.group.iterator();

        while(var2.hasNext()) {
            Object obj = var2.next();
            if (((Value)obj).getName().equals(x.getName())) {
                this.group.remove(obj);
                break;
            }
        }

    }

    public void removeElement(int position) {
        this.group.remove(position);
    }

    public Value getValue(int position) {
        return (Value)this.group.get(position);
    }

    public String toString() {
        String str = "<";

        for(int po = 0; po < this.group.size(); ++po) {
            if (!((Value)this.group.get(po)).isRemoved()) {
                Object c = this.group.get(po);
                if (c instanceof Variable) {
                    String x = c.toString();
                    if (!x.equals("")) {
                        str = str + ((Variable)c).getName();
                    }

                    if (this.group.size() != 1 && po < this.group.size() - 1 && !x.equals("")) {
                        str = str.concat(",");
                    }
                } else {
                    str = str.concat(((Value)this.group.get(po)).toString());
                    if (this.group.size() != 1 && po < this.group.size() - 1) {
                        str = str.concat(",");
                    }
                }
            } else if (po == this.group.size() - 1) {
                str = str.substring(0, str.length() - 1);
            }
        }

        if (!str.isEmpty()) {
            str = str.concat(">");
        }

        return str;
    }

    public PSpecial clone() {
        try {
            PSpecial p = (PSpecial)super.clone();
            ArrayList<Value> clone = cloneList(this.group);
            p.group = clone;
            return p;
        } catch (CloneNotSupportedException var3) {
            CloneNotSupportedException ex = var3;
            throw new RuntimeException(ex);
        }
    }

    private static ArrayList<Value> cloneList(ArrayList<Value> list) {
        ArrayList<Value> clone = new ArrayList(list.size());
        Iterator var2 = list.iterator();

        while(var2.hasNext()) {
            Value item = (Value)var2.next();
            clone.add(item.clone());
        }

        return clone;
    }

    public boolean findParameter(Value x) {
        boolean found = false;
        Iterator var3 = this.group.iterator();

        while(var3.hasNext()) {
            Object obj = var3.next();
            if (((Value)obj).getName().equals(x.getName())) {
                found = true;
                break;
            }
        }

        return found;
    }

    public Value findParameter3(Value x) {
        Iterator var2 = this.group.iterator();

        Object obj;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            obj = var2.next();
        } while(!((Value)obj).getName().equals(x.getName()));

        return (Value)obj;
    }

    public PSpecial findParameter2(Value x) {
        Iterator var2 = this.group.iterator();

        Object obj;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            obj = var2.next();
        } while(!((Value)obj).getName().equals(x.getName()));

        return this;
    }

    public void setValue(int x, Value v) {
        this.group.set(x, v);
    }

    public int compareTo(Object o) {
        boolean equal = true;
        PSpecial oo = (PSpecial)o;
        if (oo.numberOfValues() != this.numberOfValues()) {
            return 0;
        } else {
            for(int x = 0; x < this.group.size(); ++x) {
                if (((Value)this.group.get(x)).compareTo(oo.getValue(x)) != 1) {
                    equal = false;
                }
            }

            if (equal) {
                return 1;
            } else {
                return 0;
            }
        }
    }
}
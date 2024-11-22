package com.sermas.x.men.model;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;

@Slf4j
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
public class FSpecial extends Special implements Cloneable {
    @NonNull
    private String fname;
    private ArrayList<Object> group;
    private Abs_Value key;

    @Override
    public ArrayList<Value> getGroup() {
        return this.getGroup();
    }

    @Override
    public void setGroup(ArrayList<Value> group) {
        this.setGroup(group);
    }

    public void addValue(Value x) {
        this.group.add(x);
    }

    public FSpecial clone() {
        try {
            FSpecial p = (FSpecial)super.clone();
            p.fname = this.fname;
            ArrayList<Object> clone = cloneList(this.group);
            p.group = clone;
            p.key = this.key;
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
            if (item instanceof PSpecial) {
                clone.add(((PSpecial)item).clone());
            } else if (item instanceof Variable) {
                clone.add(item);
            } else if (item instanceof Value) {
                clone.add(((Value)item).clone());
            }
        }

        return clone;
    }

    public String toString() {
        String name = "";

        for(int i = 0; i < this.group.size(); ++i) {
            Object c = this.group.get(i);
            if (c instanceof Variable) {
                String x = c.toString();
                if (!x.equals("")) {
                    name = name + ((Variable)c).getName();
                    if (this.group.size() != 1 && i < this.group.size() - 1) {
                        name = name.concat(",");
                    }
                }
            } else if (c instanceof Value) {
                if (!((Value)c).isRemoved()) {
                    name = name.concat(((Value)c).getName());
                    if (this.group.size() != 1 && i < this.group.size() - 1) {
                        name = name.concat(",");
                    }
                } else if (i == this.group.size() - 1) {
                    name = name.substring(0, name.length() - 1);
                }
            } else if (c instanceof PSpecial) {
            }
        }

        if (!name.equals("")) {
            return this.fname + "{" + name + "}" + this.key.toString();
        } else {
            return "";
        }
    }
}


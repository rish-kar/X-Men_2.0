package com.sermas.x.men.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
public class Nary_app extends Abs_Value {

    @NonNull
    private String fname;
    private ArrayList<Value> group;

    public void addValue(Value x) {
        this.group.add(x);
    }

    public String toString() {
        String name = "";

        for(int i = 0; i < this.group.size(); ++i) {
            name = name + this.group.get(i);
            if (i < this.group.size() - 1) {
                name = name + " ";
            }
        }

        return this.fname + "(" + name + ")";
    }
}
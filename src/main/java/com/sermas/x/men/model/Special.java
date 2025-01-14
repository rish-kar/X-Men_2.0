package com.sermas.x.men.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
@NoArgsConstructor
public abstract class Special {
    private ArrayList<Value> group;

    public abstract void addValue(Value var1);
}

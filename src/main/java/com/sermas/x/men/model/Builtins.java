package com.sermas.x.men.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class Builtins extends Component {
    private String name;
    private ArrayList<String> group;

    public Builtins(String name) {
        this.name = name;
        this.group = new ArrayList();
    }

    public void addValue(String x) {
        this.group.add(x);
    }

    public String toString() {
        String name = "";

        for(int i = 0; i < this.group.size(); ++i) {
            name = name + (String)this.group.get(i);
            if (i < this.group.size() - 1) {
                name = name + ", ";
            }
        }

        return this.name + " : " + name;
    }
}

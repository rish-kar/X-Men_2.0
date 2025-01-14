package com.sermas.x.men.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class Function extends Component {

    // Variables
    public String name;
    public int numberofParam;

    public String toString() {
        return this.name + "/" + this.numberofParam;
    }
}
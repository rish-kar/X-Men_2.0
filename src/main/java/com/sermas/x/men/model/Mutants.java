package com.sermas.x.men.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
public class Mutants {

    public Value oldValue;
    public Value newValue;

    public String toString() {
        return this.oldValue != null ? "The old value is " + this.oldValue.toString() + " that will be replaced with " + this.newValue.toString() : "I will remove the value " + this.newValue.toString();
    }
}
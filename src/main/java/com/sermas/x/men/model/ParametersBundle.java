package com.sermas.x.men.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;

@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class ParametersBundle {

    String fileName;
    ArrayList<Rule> theory;
    ArrayList<ArrayList> collections;
    ArrayList<Function> functions;
    ArrayList<Builtins> builtins;
    Boolean modelWithTags = false;
}

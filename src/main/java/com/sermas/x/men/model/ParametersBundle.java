package com.sermas.x.men.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
    ArrayList<Value> roles;
    Boolean modelWithTags = false;
    Flags flags;

    // New field to store additional content
    private Map<String, String> extraContent = new HashMap<>();

    // New methods to handle additional content
    public void addExtraContent(String key, String value) {
        this.extraContent.put(key, value);
    }

    public String getExtraContent(String key) {
        return this.extraContent.getOrDefault(key, "");
    }
}

package com.sermas.x.men.service;

import com.sermas.x.men.model.Rule;

import java.util.ArrayList;

public interface MutationStrategy {

    void applyMutation(ArrayList<Rule> rules);
}
package com.sermas.x.men.service;

import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Rule;

import java.util.ArrayList;

public interface MutationStrategy {

    ParametersBundle applyMutation(Rule rule, ArrayList<Rule> rules, ParametersBundle parametersBundle);
}
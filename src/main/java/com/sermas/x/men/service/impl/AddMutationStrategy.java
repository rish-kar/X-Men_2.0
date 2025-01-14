package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.MutationStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class AddMutationStrategy implements MutationStrategy {
    @Override
    public ParametersBundle applyMutation(Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {
        // Implement skip send mutation logic
        return  new ParametersBundle();
    }
}
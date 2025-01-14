package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.MutationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@Slf4j
public class SkipSendReceiveMutationStrategy implements MutationStrategy {
    @Override
    public ParametersBundle applyMutation(Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {
        // Implement skip send mutation logic
        return new ParametersBundle();
    }
}
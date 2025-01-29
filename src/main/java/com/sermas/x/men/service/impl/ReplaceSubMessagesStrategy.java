package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.*;
import com.sermas.x.men.service.MutationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class ReplaceSubMessagesStrategy implements MutationStrategy {

    @Autowired
    ReplaceMutationService replaceMutationService;

    @Override
    public ParametersBundle applyMutation(Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {
        parametersBundle = replaceMutationService.replaceMutation(originalRule, rules, parametersBundle);
        parametersBundle.getFlags().setSwitchFlag(true);
        return parametersBundle;
    }
}
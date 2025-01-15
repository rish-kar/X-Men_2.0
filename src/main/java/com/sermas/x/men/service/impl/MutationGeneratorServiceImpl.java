package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.MutationGeneratorService;
import com.sermas.x.men.service.MutationStrategy;
import com.sermas.x.men.service.MutationStrategyFactory;
import com.sermas.x.men.utilities.MutatedFileGenerator;
import com.sermas.x.men.utilities.UtilityFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Set;

/**
 * MutationGeneratorServiceImpl class.
 */
@Service
@Slf4j
public class MutationGeneratorServiceImpl implements MutationGeneratorService {

    @Autowired
    MutationStrategyFactory mutationStrategyFactory;

    @Autowired
    MutatedFileGenerator mutatedFileGenerator;

    @Autowired
    UtilityFunctions utilityFunctions;


    /**
     * Generate mutation based on the mutation set
     *
     * @param rules Rules
     * @param mutationSet Set of Mutations
     * @return Modified Rules
     */
    @Override
    public ArrayList<Rule> generateMutation(ArrayList<Rule> rules, Set<Mutations> mutationSet, ParametersBundle parametersBundle) {

        for (Mutations mutation : mutationSet) {
            MutationStrategy strategy = mutationStrategyFactory.getStrategy(mutation);
            if (strategy != null) {
                ArrayList<Rule> human_rules = extractHumanRules(rules);
                ArrayList<Rule> cloned_rules = utilityFunctions.cloneRules(human_rules);
                for(Rule rule : cloned_rules) {
                    parametersBundle = strategy.applyMutation(rule, rules, parametersBundle);
                }

            }
        }
        mutatedFileGenerator.saveFiles(parametersBundle);
        return rules;
    }

    /**
     * Extract human rules from the rules
     *
     * @param rules Rules
     * @return Human Rules
     */
    private ArrayList<Rule> extractHumanRules(ArrayList<Rule> rules) {
        ArrayList<Rule> human_rules = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule.isHuman()) {
                human_rules.add(rule);
            }
        }
        return human_rules;
    }

}

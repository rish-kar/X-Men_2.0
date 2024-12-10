package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.MutationGeneratorService;
import com.sermas.x.men.service.MutationStrategy;
import com.sermas.x.men.service.MutationStrategyFactory;
import com.sermas.x.men.utilities.MutatedFileGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class MutationGeneratorServiceImpl implements MutationGeneratorService {

    @Autowired
    MutationStrategyFactory mutationStrategyFactory;

    @Autowired
    MutatedFileGenerator mutatedFileGenerator;

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
                for(Rule rule : rules) {
                    parametersBundle = strategy.applyMutation(rule, rules, parametersBundle);
                }

            }
        }
//        parametersBundle.setCollections(new ArrayList<>(List.of(rules)));
        mutatedFileGenerator.saveFiles(parametersBundle);
        return rules;
    }
}

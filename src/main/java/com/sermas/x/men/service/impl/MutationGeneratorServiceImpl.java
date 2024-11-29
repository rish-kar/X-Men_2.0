package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.MutationGeneratorService;
import com.sermas.x.men.service.MutationStrategy;
import com.sermas.x.men.service.MutationStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Set;

@Service
@Slf4j
public class MutationGeneratorServiceImpl implements MutationGeneratorService {

    @Autowired
    MutationStrategyFactory mutationStrategyFactory;

    /**
     * Generate mutation based on the mutation set
     *
     * @param rules Rules
     * @param mutationSet Set of Mutations
     * @return Modified Rules
     */
    @Override
    public ArrayList<Rule> generateMutation(ArrayList<Rule> rules, Set<Mutations> mutationSet) {
        for (Mutations mutation : mutationSet) {
            MutationStrategy strategy = MutationStrategyFactory.getStrategy(mutation);
            if (strategy != null) {
                strategy.applyMutation(rules);
            }
        }
        return rules;
    }
}

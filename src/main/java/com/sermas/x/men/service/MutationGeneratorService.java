package com.sermas.x.men.service;

import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.model.Rule;

import java.util.ArrayList;
import java.util.Set;

public interface MutationGeneratorService {

    /**
     * Generate mutation.
     *
     * @param rules The set of rules to mutate.
     * @return The mutated set of rules.
     */
    ArrayList<Rule> generateMutation(ArrayList<Rule> rules, Set<Mutations> mutations);
}

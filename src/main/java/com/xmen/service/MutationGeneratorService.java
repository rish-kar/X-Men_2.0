package com.xmen.service;

import com.xmen.model.Mutations;
import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import java.util.ArrayList;
import java.util.Set;

/**
 * MutationGeneratorService interface. This service is responsible for generating mutations based on
 * a set of rules and a set of mutations.
 */
public interface MutationGeneratorService {

  /**
   * Generate mutation.
   *
   * @param rules The set of rules to mutate.
   */
  void generateMutation(
      ArrayList<Rule> rules, Set<Mutations> mutations, ParametersBundle parametersBundle);

  /**
   * Extracts human-readable rules from a set of rules.
   *
   * @param rules The set of rules to extract human-readable rules from.
   * @return An ArrayList of human-readable rules extracted from the input rules.
   */
  ArrayList<Rule> extractHumanRules(ArrayList<Rule> rules);
}

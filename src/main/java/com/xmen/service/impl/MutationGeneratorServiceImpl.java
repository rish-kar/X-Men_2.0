package com.xmen.service.impl;

import com.xmen.model.Mutations;
import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import com.xmen.service.MutationGeneratorService;
import com.xmen.service.MutationStrategy;
import com.xmen.service.MutationStrategyFactory;
import com.xmen.utilities.MutatedFileGenerator;
import com.xmen.utilities.UtilityFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Set;

/** MutationGeneratorServiceImpl class. */
@Service
@Slf4j
public class MutationGeneratorServiceImpl implements MutationGeneratorService {

  @Autowired MutationStrategyFactory mutationStrategyFactory;

  @Autowired MutatedFileGenerator mutatedFileGenerator;

  @Autowired UtilityFunctions utilityFunctions;

  /**
   * Generate mutation based on the mutation set.
   *
   * @param rules Rules
   * @param mutationSet Set of Mutations
   */
  @Override
  public void generateMutation(
          ArrayList<Rule> rules, Set<Mutations> mutationSet, ParametersBundle parametersBundle) {

    for (Mutations mutation : mutationSet) {
      MutationStrategy strategy = mutationStrategyFactory.getStrategy(mutation);
      if (strategy != null) {
        ArrayList<Rule> humanRules = extractHumanRules(rules);
        ArrayList<Rule> clonedRules = utilityFunctions.cloneRules(humanRules);
        for (Rule rule : clonedRules) {
          parametersBundle = strategy.applyMutation(rule, rules, parametersBundle);
        }
      }
    }
    mutatedFileGenerator.saveFiles(parametersBundle);
  }

  /**
   * Extract human rules from the rules.
   *
   * @param rules Rules
   * @return Human Rules
   */
  public ArrayList<Rule> extractHumanRules(ArrayList<Rule> rules) {
    ArrayList<Rule> humanRules = new ArrayList<>();
    for (Rule rule : rules) {
      if (rule.isHuman()) {
        humanRules.add(rule);
      }
    }
    return humanRules;
  }
}

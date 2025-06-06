package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.MutationGeneratorService;
import com.sermas.x.men.service.MutationStrategy;
import com.sermas.x.men.service.MutationStrategyFactory;
import com.sermas.x.men.utilities.MutatedFileGenerator;
import com.sermas.x.men.utilities.UtilityFunctions;
import java.util.ArrayList;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
  private ArrayList<Rule> extractHumanRules(ArrayList<Rule> rules) {
    ArrayList<Rule> humanRules = new ArrayList<>();
    for (Rule rule : rules) {
      if (rule.isHuman()) {
        humanRules.add(rule);
      }
    }
    return humanRules;
  }
}

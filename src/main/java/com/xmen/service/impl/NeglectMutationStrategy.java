package com.xmen.service.impl;

import com.xmen.model.Fact;
import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import com.xmen.model.Type;
import com.xmen.service.MutationStrategy;
import com.xmen.utilities.UtilityFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NeglectMutationStrategy implements the "neglect" mutation as provided: for a rule, it keeps the
 * first action and selects subsets of the remaining actions, creating mutated variants per subset.
 * For each variant, it clones the theory, adds the mutated rule, and stores it into collections.
 */
@Component
@Slf4j
public class NeglectMutationStrategy implements MutationStrategy {

  @Autowired private UtilityFunctions utilityFunctions;

  @Override
  public ParametersBundle applyMutation(
      Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {
    try {
      if (originalRule == null || originalRule.getActions() == null) {
        return parametersBundle;
      }

      // Skip if mutations have already been generated (neglect should only process once)
      if (!parametersBundle.getCollections().isEmpty()) {
        return parametersBundle;
      }

      ArrayList<Fact> actions = new ArrayList<>(originalRule.getActions());

      // Skip rules with 1 or fewer actions (nothing to neglect beyond the first)
      if (actions.size() <= 1) {
        return parametersBundle;
      }

      // Ensure base theory is set before cloning
      parametersBundle.setTheory(new ArrayList<>(rules));

      // Build index set for actions from index 1 to end (keep index 0 always)
      Set<Integer> indexSet = new HashSet<>();
      for (int i = 1; i < actions.size(); i++) {
        indexSet.add(i);
      }

      // Generate power set of indices
      Set<Set<Integer>> powerSet = utilityFunctions.powerSet(indexSet);

      // Convert each non-empty subset to a sorted list of indices (preserve deterministic order)
      ArrayList<List<Integer>> subsets = new ArrayList<>();
      for (Set<Integer> subset : powerSet) {
        if (subset == null || subset.isEmpty()) {
          continue;
        }
        List<Integer> sorted = subset.stream().sorted().collect(Collectors.toList());
        subsets.add(sorted);
      }

      // For each subset, build a mutated rule retaining the first action + chosen subset actions
      for (List<Integer> sub : subsets) {
        // Clone the base rules directly (not from parametersBundle to avoid contamination)
        ArrayList<Rule> theoryClone = new ArrayList<>();
        for (Rule r : rules) {
          theoryClone.add(r.clone());
        }

        // Now clone the rule from the original (not from the cloned theory)
        Rule mutated = originalRule.clone();

        // Build new actions list: first action + selected subset in ascending index order
        ArrayList<Fact> newActions = new ArrayList<>();
        // Always keep the first action
        newActions.add(actions.get(0).clone());
        for (Integer idx : sub) {
          if (idx != null && idx >= 0 && idx < actions.size()) {
            newActions.add(actions.get(idx).clone());
          }
        }

        // Replace actions on the mutated rule (keep preconditions and postconditions intact)
        mutated.getActions().clear();
        mutated.getActions().addAll(newActions);
        mutated.setRule_name(originalRule.getRule_name() + "_M");
        mutated.setTypo(Type.MUTATED);

        theoryClone.add(mutated);
        parametersBundle.getCollections().add(theoryClone);
      }

    } catch (Exception e) {
      log.error("Neglect mutation failed for rule '{}': ",
          (originalRule != null ? originalRule.getRule_name() : "null"), e);
    }

    return parametersBundle;
  }
}

package com.xmen.service;

import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import java.util.ArrayList;

/**
 * MutationStrategy interface. This interface defines the contract for mutation strategies that
 * apply mutations to rules.
 */
public interface MutationStrategy {

  /**
   * Apply mutation to the given rule.
   *
   * @param rule The rule to mutate.
   * @param rules The list of rules to consider for mutation.
   * @param parametersBundle The parameters bundle containing additional information for mutation.
   * @return Updated parameters bundle after applying the mutation.
   */
  ParametersBundle applyMutation(
      Rule rule, ArrayList<Rule> rules, ParametersBundle parametersBundle);
}

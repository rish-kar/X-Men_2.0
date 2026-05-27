package com.xmen.service.impl;

import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import com.xmen.service.MutationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * ReplaceTypeStrategy class. This strategy replaces types in the rules. This strategy is used to
 * mutate rules by replacing their types with other types.
 */
@Service
@Slf4j
public class ReplaceTypeStrategy implements MutationStrategy {

  @Autowired ReplaceMutationService replaceMutationService;

  /**
   * Apply mutation to the original rule by replacing types.
   *
   * @param originalRule Original rule to be mutated
   * @param rules List of rules to consider for mutation
   * @param parametersBundle Parameters bundle containing additional information for mutation
   * @return Updated parameters bundle after applying the mutation
   */
  @Override
  public ParametersBundle applyMutation(
          Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {
    parametersBundle =
        replaceMutationService.replaceMutation(originalRule, rules, parametersBundle);
    parametersBundle.getFlags().setSwitchFlag(true);
    return parametersBundle;
  }
}

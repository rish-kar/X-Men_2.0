package com.xmen.service.impl;

import com.xmen.model.Fact;
import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import com.xmen.model.Variable;
import com.xmen.service.MutationStrategy;
import com.xmen.utilities.RulesModifier;
import com.xmen.utilities.UtilityFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * SkipSendMutationStrategy class. This strategy skips the "Snd" postcondition fact in the original
 * rule, mutates the rule, and modifies the theory accordingly. It is used to mutate rules by
 * skipping the send action.
 */
@Service
@Slf4j
public class SkipSendMutationStrategy implements MutationStrategy {

  private final UtilityFunctions utilityFunctions;
  private final RulesModifier rulesModifier;

  /**
   * SkipSendMutationStrategy constructor.
   *
   * @param utilityFunctions Utility functions
   * @param rulesModifier Rules modifier
   */
  @Autowired
  public SkipSendMutationStrategy(
      @Lazy UtilityFunctions utilityFunctions, RulesModifier rulesModifier) {
    this.utilityFunctions = utilityFunctions;
    this.rulesModifier = rulesModifier;
  }

  /**
   * Apply the SkipSend mutation strategy to the original rule.
   *
   * @param originalRule The original rule to be mutated
   * @param rules The list of rules in the theory
   * @param parametersBundle The parameters bundle containing the theory and other parameters
   * @return The parameters bundle containing the mutated theory and other parameters
   */
  @Override
  public ParametersBundle applyMutation(
          Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {
    log.debug("Applying SkipSendMutationStrategy");
    // Check if the original rule has a postcondition fact named "Snd"
    if (originalRule.getPostconditionFactByMatchingName("Snd") != null) {
      // Clone the theory to create a new list of rules
      // parametersBundle.getTheory().clear();
      parametersBundle.setTheory(new ArrayList<>(rules));
      ArrayList<Rule> clonedTheory = utilityFunctions.cloneModel(parametersBundle);
      // Clone the original rule to create a mutated rule
      Rule mutatedRule = originalRule.clone();

      // Set the name and type of the mutated rule
      mutatedRule.setRule_name(originalRule.getRule_name() + "_M");
      mutatedRule.setTypo(com.xmen.model.Type.MUTATED);

      // Remove the "Snd" postcondition fact from the mutated rule
      mutatedRule.getPostconditionFactByMatchingName("Snd").setRemoved(true);

      // Check if the "Snd" postcondition fact has a variable as its parameter
      if (mutatedRule.getPostconditionFactByMatchingName("Snd").getParameter(2)
          instanceof Variable) {
        Fact preconditionRcv = originalRule.getPreconditionFactByMatchingName("Rcv");
        Fact postconditionSnd = originalRule.getPostconditionFactByMatchingName("Snd");

        ArrayList<String> postSndVariables = null;
        ArrayList<String> preRcvVariables = null;

        // Extract variables from the "Snd" postcondition fact
        if (postconditionSnd != null && postconditionSnd.getParameter(2) instanceof Variable) {
          postSndVariables = utilityFunctions.loop((Variable) postconditionSnd.getParameter(2));
        }

        // Extract variables from the "Rcv" precondition fact
        if (preconditionRcv != null && preconditionRcv.getParameter(2) instanceof Variable) {
          preRcvVariables = utilityFunctions.loop((Variable) preconditionRcv.getParameter(2));
        }

        // Remove matching variables from the lists
        if (postSndVariables != null && preRcvVariables != null) {
          for (String postVar : postSndVariables) {
            preRcvVariables.removeIf(preVar -> preVar.equals(postVar));
          }
        }

        // Mark variables as removed in the mutated rule
        for (Variable variable : mutatedRule.getVariables()) {
          if (postSndVariables != null && postSndVariables.contains(variable.getName())) {
            variable.setRemoved(true);
          }
        }
      }

      // Remove actions related to "Snd" or "To" in the mutated rule
      for (Fact action : mutatedRule.getActions()) {
        if (action.getF_name().equals("Snd") || action.getF_name().equals("To")) {
          action.setRemoved(true);
        }
      }

      // Remove actions that use removed variables
      for (Fact action : mutatedRule.getActions()) {
        for (Variable variable : mutatedRule.getVariables()) {
          if (variable.isRemoved() && utilityFunctions.actionsCheck(action, variable.getName())) {
            action.setRemoved(true);
            break;
          }
        }
      }

      // Add the mutated rule to the cloned theory
      clonedTheory.add(mutatedRule);
      log.debug("Mutated rule added: {}", mutatedRule);

      // Perform additional processing on the cloned theory
      parametersBundle = rulesModifier.rulesModifier(clonedTheory, false, null, parametersBundle);
      // TODO: Check for variable removal which is commented
      // Return the modified theory
      // return clonedTheory;
    } else {
      // Log a warning if the original rule does not have a "Snd" postcondition fact
      log.warn("The original rule does not have a 'Snd' postcondition fact.");
      // return rules;
    }
    return parametersBundle;
  }
}

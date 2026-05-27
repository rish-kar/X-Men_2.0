package com.xmen.service.impl;

import com.xmen.model.Fact;
import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import com.xmen.service.MutationStrategy;
import com.xmen.utilities.RulesModifier;
import com.xmen.utilities.UtilityFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/** SkipReceiveMutationStrategy class. */
@Service
@Slf4j
public class SkipReceiveSendMutationStrategy implements MutationStrategy {

  private final UtilityFunctions utilityFunctions;
  private final RulesModifier rulesModifier;

  /**
   * SkipReceiveSendMutationStrategy constructor.
   *
   * @param utilityFunctions Utility functions
   * @param rulesModifier Rules modifier
   */
  @Autowired
  public SkipReceiveSendMutationStrategy(
      @Lazy UtilityFunctions utilityFunctions, RulesModifier rulesModifier) {
    this.utilityFunctions = utilityFunctions;
    this.rulesModifier = rulesModifier;
  }

  /**
   * Apply the SkipReceiveSend mutation strategy to the original rule.
   *
   * @param originalRule The original rule to be mutated
   * @param rules The list of rules in the theory
   * @param parametersBundle The parameters bundle containing the theory and other parameters
   * @return The parameters bundle containing the mutated theory and other parameters
   */
  @Override
  public ParametersBundle applyMutation(
      Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {

    // Validate input parameters
    if (originalRule == null || rules == null) {
      log.error("Input parameters 'rule' or 'theory' cannot be null.");
      throw new IllegalArgumentException("Input parameters 'rule' or 'theory' cannot be null.");
    }

    try {
      // Check if the rule contains the required precondition and postcondition facts
      if (originalRule.getPreconditionFactByMatchingName("Rcv") != null
          && originalRule.getPostconditionFactByMatchingName("Snd") != null) {

        // Clone the theory to avoid modifying the original list
        ArrayList<Rule> clonedTheory = utilityFunctions.cloneModel(parametersBundle);

        // Clone the input rule to create a mutated version
        Rule mutatedRule = originalRule.clone();

        // Update the rule name and set its type to MUTATED
        mutatedRule.setRule_name(originalRule.getRule_name() + "_M");
        mutatedRule.setTypo(com.xmen.model.Type.MUTATED);
        mutatedRule.getPreconditionFactByMatchingName("Rcv").setRemoved(true);
        mutatedRule.getPostconditionFactByMatchingName("Snd").setRemoved(true);

        for (Fact f : mutatedRule.getActions()) {
          if (f.getF_name().equals("Snd")
              || f.getF_name().equals("To")
              || f.getF_name().equals("Rcv")
              || f.getF_name().equals("From")) {
            f.setRemoved(true);
          }
        }

        // Update the "State" postcondition if applicable
        Fact preconditionState = mutatedRule.getPreconditionFactByMatchingName("State");
        if (mutatedRule.getPostconditionFactByMatchingName("State") != null) {
          mutatedRule
              .getPostconditions()
              .set(
                  0,
                  utilityFunctions.buildNewState(
                      mutatedRule.getPreconditions(), preconditionState));
        }

        // Log the details of the mutated rule
        log.debug("Mutated Rule: " + mutatedRule);

        // Add the mutated rule to the cloned theory
        clonedTheory.add(mutatedRule);

        // Perform further operations on the updated theory
        return rulesModifier.rulesModifier(clonedTheory, false, null, parametersBundle);
      } else {
        // Log a warning if the required facts are missing
        log.warn(
            "The rule does not contain the required precondition 'Rcv' or postcondition 'Snd'.");
      }
    } catch (Exception e) {
      // Log any unexpected exceptions during execution
      log.error("An error occurred during rule mutation.", e);
      throw new RuntimeException("An error occurred during rule mutation.", e);
    }

    log.debug(
        "Returning ParametersBundle without any mutation. \n Original Theory: "
            + parametersBundle.getTheory().toString());
    // Return the original theory if no mutation is performed
    return parametersBundle;
  }
}

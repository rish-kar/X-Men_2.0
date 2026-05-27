package com.xmen.service.impl;

import com.xmen.model.*;
import com.xmen.service.MutationStrategy;
import com.xmen.utilities.RulesModifier;
import com.xmen.utilities.UtilityFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SkipReceiveMutationStrategy class.
 */
@Service
@Slf4j
public class SkipReceiveMutationStrategy implements MutationStrategy {

  private final UtilityFunctions utilityFunctions;
  private final RulesModifier rulesModifier;

  /**
   * SkipReceiveMutationStrategy constructor.
   *
   * @param utilityFunctions Utility functions
   * @param rulesModifier Rules modifier
   */
  @Autowired
  public SkipReceiveMutationStrategy(
      @Lazy UtilityFunctions utilityFunctions, RulesModifier rulesModifier) {
    this.utilityFunctions = utilityFunctions;
    this.rulesModifier = rulesModifier;
  }

  /**
   * Apply the SkipReceive mutation strategy to the original rule.
   *
   * @param originalRule The original rule to be mutated
   * @param rules The list of rules in the theory
   * @param parametersBundle The parameters bundle containing the theory and other parameters
   * @return The parameters bundle containing the mutated theory and other parameters
   */
  @Override
  public ParametersBundle applyMutation(
      Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {
    ParametersBundle parametersBundle1 = new ParametersBundle();
    parametersBundle1.setTheory(rules);
    Fact rcvPreconditionFact = originalRule.getPreconditionFactByMatchingName("Rcv");
    if (rcvPreconditionFact == null) {
      return parametersBundle;
    }

    ArrayList<Rule> clonedTheory = utilityFunctions.cloneModel(parametersBundle1);

    Rule mutatedRule = originalRule.clone();
    mutatedRule.setRule_name(originalRule.getRule_name() + "_M");
    mutatedRule.setTypo(com.xmen.model.Type.MUTATED);

    // Remove Rcv precondition
    Fact mutatedRcvFact = mutatedRule.getPreconditionFactByMatchingName("Rcv");
    mutatedRcvFact.setRemoved(true);

    // Remove Rcv and From actions
    removeActionsByNames(mutatedRule, "Rcv", "From");

    // Gather modifications
    ArrayList<Mutants> modifications = gatherModifications(mutatedRcvFact);

    // If no modifications, just return after adding the rule
    if (modifications.isEmpty()) {
      clonedTheory.add(mutatedRule);
      return rulesModifier.rulesModifier(clonedTheory, false, null, parametersBundle);
    }

    // Rebuild state if needed
    rebuildStateIfPresent(mutatedRule);

    // Precompute a set of mutated value names not in knowledge
    Set<String> mutatedValueNames = new HashSet<>();
    for (Mutants m : modifications) {
      if (!m.getNewValue().isInKnowledge()) {
        mutatedValueNames.add(m.getNewValue().getName());
      }
    }

    // Handle "Snd" postcondition parameters removal
    Fact sndPostcondition = mutatedRule.getPostconditionFactByMatchingName("Snd");
    if (sndPostcondition != null && !mutatedValueNames.isEmpty()) {
      removeMatchingValuesFromFact(sndPostcondition, mutatedValueNames);
    }

    // Handle "Snd" and "To" actions removal
    removeActionsIfContain(modifications, mutatedRule, mutatedValueNames, "Snd", "To");

    // Add mutated rule and process
    clonedTheory.add(mutatedRule);
    return rulesModifier.rulesModifier(clonedTheory, false, null, parametersBundle);
  }

  /**
   * Remove actions by names.
   *
   * @param rule Rule to remove actions from
   * @param namesToRemove Names of actions to remove
   */
  private void removeActionsByNames(Rule rule, String... namesToRemove) {
    Set<String> removeSet = new HashSet<>(Arrays.asList(namesToRemove));
    for (Fact action : rule.getActions()) {
      if (removeSet.contains(action.getF_name())) {
        action.setRemoved(true);
      }
    }
  }

  /**
   * Gather modifications from a fact.
   *
   * @param rcvFact Fact to gather modifications from
   * @return List of modifications
   */
  private ArrayList<Mutants> gatherModifications(Fact rcvFact) {
    ArrayList<Mutants> modifications = new ArrayList<>();
    // Parameter 0 should be a Value
    Object param0 = rcvFact.getParameter(0);
    if (param0 instanceof Value) {
      modifications.add(new Mutants(null, (Value) param0));
    }

    Object param2 = rcvFact.getParameter(2);
    if (param2 instanceof Value) {
      modifications.add(new Mutants(null, (Value) param2));
    } else if (param2 instanceof PSpecial special) {
        for (int i = 0; i < special.getGroup().size(); i++) {
        modifications.add(new Mutants(null, special.getValue(i)));
      }
    }
    // Variables or other types can be handled if needed
    return modifications;
  }

  /**
   * Rebuild state if present.
   *
   * @param mutatedRule Mutated rule to rebuild state for
   */
  private void rebuildStateIfPresent(Rule mutatedRule) {
    Fact statePrecondition = mutatedRule.getPreconditionFactByMatchingName("State");
    Fact statePostcondition = mutatedRule.getPostconditionFactByMatchingName("State");
    if (statePostcondition != null) {
      mutatedRule
          .getPostconditions()
          .set(
              0, utilityFunctions.buildNewState(mutatedRule.getPreconditions(), statePrecondition));
    }
  }

  /**
   * Remove matching values from a fact.
   *
   * @param fact Fact to remove values from
   * @param valueNames Names of values to remove
   */
  private void removeMatchingValuesFromFact(Fact fact, Set<String> valueNames) {
    for (Object param : fact.getParameters()) {
      if (param instanceof Value val) {
          if (valueNames.contains(val.getName())) {
          val.setRemoved(true);
        }
      } else if (param instanceof PSpecial special) {
          for (int s = 0; s < special.numberOfValues(); s++) {
          Value currentValue = special.getValue(s);
          if (valueNames.contains(currentValue.getName())) {
            currentValue.setRemoved(true);
          }
        }
      }
    }
  }

  /**
   * Remove actions if they contain certain values.
   *
   * @param modifications List of modifications
   * @param rule Rule to remove actions from
   * @param valueNames Names of values to check for
   * @param actionNames Action names to remove
   */
  private void removeActionsIfContain(
      ArrayList<Mutants> modifications, Rule rule, Set<String> valueNames, String... actionNames) {
    Set<String> actionNamesSet = new HashSet<>(Arrays.asList(actionNames));
    for (Fact action : rule.getActions()) {
      if (actionNamesSet.contains(action.getF_name())) {
        for (int i = 0; i < modifications.size(); i++) {
          Mutants mutants = modifications.get(i);

          boolean removeAction = false;
          for (Object param : action.getParameters()) {
            if (param instanceof Value val) {
                if (valueNames.contains(val.getName())
                  && !val.isInKnowledge()
                  && !modifications.get(i).getNewValue().isInKnowledge()) {
                removeAction = true;
                break;
              }
            }
          }
          if (removeAction) {
            action.setRemoved(true);
          }
        }
      }
    }
  }
}

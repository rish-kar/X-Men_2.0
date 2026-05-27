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

/** The SkipSendReceiveMutationStrategy class. */
@Service
@Slf4j
public class SkipSendReceiveMutationStrategy implements MutationStrategy {

  private final UtilityFunctions utilityFunctions;
  private final RulesModifier rulesModifier;

  /**
   * Constructor for SkipSendReceiveMutationStrategy.
   *
   * @param utilityFunctions The utility functions
   * @param rulesModifier The rules modifier
   */
  @Autowired
  public SkipSendReceiveMutationStrategy(
      @Lazy UtilityFunctions utilityFunctions, RulesModifier rulesModifier) {
    this.utilityFunctions = utilityFunctions;
    this.rulesModifier = rulesModifier;
  }

  /**
   * Applies the mutation strategy.
   *
   * @param originalRule The original rule
   * @param rules The list of rules
   * @param parametersBundle The parameters bundle
   * @return The parameters bundle object
   */
  @Override
  public ParametersBundle applyMutation(
      Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {
    try {
      if (originalRule.getPostconditionFactByMatchingName("Snd") == null) {

        log.debug(
            "No mutation needed if 'Snd' postcondition is absent for Skip Receive "
                    + "Mutation Strategy");
        return parametersBundle; // No mutation needed if "Snd" postcondition is absent
      }

      ArrayList<Rule> nextRules = findNextRules(originalRule, rules);

      for (Rule arrivingRule : nextRules) {
        parametersBundle.setTheory(rules);
        ArrayList<Rule> theoryClone = utilityFunctions.cloneModel(parametersBundle);

        // Mutate the original rule
        Rule mutatedRule = mutateOriginalRule(originalRule);
        theoryClone.add(mutatedRule);
        parametersBundle =
            rulesModifier.rulesModifier(theoryClone, true, arrivingRule, parametersBundle);

        // Mutate the arriving rule
        mutateArrivingRule(arrivingRule, theoryClone, originalRule);

        // Add mutated arriving rule to the clone
        theoryClone.add(arrivingRule);

        log.debug("Final Mutated Arriving Rule: {}", arrivingRule);

        // Apply the modified rules
        parametersBundle = rulesModifier.rulesModifier(theoryClone, false, null, parametersBundle);
      }
    } catch (Exception e) {
      log.error("Error during mutation process: ", e);
    }

    return parametersBundle;
  }

  /**
   * Finds the next rules based on the original rule.
   *
   * @param originalRule The original rule
   * @param rules The list of rules
   * @return The list of next rules
   */
  private ArrayList<Rule> findNextRules(Rule originalRule, ArrayList<Rule> rules) {
    ArrayList<Rule> nextRules = new ArrayList<>();

    for (Rule tempRule : rules) {
      if (tempRule.getPreconditionFactByMatchingName("Rcv") != null) {
        Fact postState = originalRule.getPostconditionFactByMatchingName("State");

        String agentName = ((Value) postState.getParameter(0)).getName();
        int state =
            Integer.parseInt(
                ((Value) postState.getParameter(1)).getName().replaceAll("[^0-9]", ""));

        Rule nextRule = tempRule.findNextRule(agentName, state);
        if (nextRule != null) {
          nextRule = nextRule.clone();
          nextRules.add(nextRule);
        }
      }
    }
    return nextRules;
  }

  /**
   * Mutates the original rule.
   *
   * @param originalRule The original rule to be mutated
   * @return The mutated rule
   */
  private Rule mutateOriginalRule(Rule originalRule) {
    Rule mutatedRule = originalRule.clone();
    mutatedRule.setRule_name(mutatedRule.getRule_name() + "_M");
    mutatedRule.setTypo(com.xmen.model.Type.MUTATED);

    // Remove "Snd" postcondition
    Fact sndPostcondition = mutatedRule.getPostconditionFactByMatchingName("Snd");
    if (sndPostcondition != null) {
      sndPostcondition.setRemoved(true);
    }

    // Remove "Snd" and "To" actions
    for (Fact fact : mutatedRule.getActions()) {
      if (fact.getF_name().equals("Snd") || fact.getF_name().equals("To")) {
        fact.setRemoved(true);
      }
    }

    log.debug("Mutated Original Rule: {}", mutatedRule);
    return mutatedRule;
  }

  /**
   * Mutates the arriving rule.
   *
   * @param arrivingRule The arriving rule to be mutated
   * @param theoryClone The clone of the theory
   * @param originalRule The original rule
   */
  private void mutateArrivingRule(
      Rule arrivingRule, ArrayList<Rule> theoryClone, Rule originalRule) {
    arrivingRule.setRule_name(arrivingRule.getRule_name() + "_M");
    arrivingRule.setTypo(com.xmen.model.Type.MUTATED);

    // Update state precondition
    Fact preState = arrivingRule.getPreconditionFactByMatchingName("State");
    Value agent = (Value) preState.getParameter(0);
    int stateIndex =
        Integer.parseInt(
            ((Value) preState.getParameter(1)).getName().replaceAll("[^a-zA-Z0-9]", ""));
    Fact previousState =
        utilityFunctions.previousRuleFact(theoryClone, agent.getName(), stateIndex);

    if (previousState != null) {
      previousState.setType(TypeFact.PRE);
      arrivingRule.getPreconditions().set(0, previousState.clone());
    }

    // Remove "Rcv" precondition and associated actions
    arrivingRule.getPreconditionFactByMatchingName("Rcv").setRemoved(true);

    for (Fact fact : arrivingRule.getActions()) {
      if (fact.getF_name().equals("Rcv") || fact.getF_name().equals("From")) {
        fact.setRemoved(true);
      }
    }

    // Update state postcondition
    Fact preStateFact = arrivingRule.getPreconditionFactByMatchingName("State");
    if (arrivingRule.getPostconditionFactByMatchingName("State") != null) {
      arrivingRule
          .getPostconditions()
          .set(0, utilityFunctions.buildNewState(originalRule.getPreconditions(), preStateFact));
    }

    // Handle modifications and update send postconditions
    handleModifications(arrivingRule);
  }

  /**
   * Handles modifications in the arriving rule.
   *
   * @param arrivingRule The arriving rule to be mutated
   */
  private void handleModifications(Rule arrivingRule) {
    ArrayList<Mutants> modifications = new ArrayList<>();
    Fact rcvFact = arrivingRule.getPreconditionFactByMatchingName("Rcv");
    Object receivedValue = rcvFact.getParameter(2);

    if (receivedValue instanceof Value && ((Value) receivedValue).isRemoved()) {
      modifications.add(new Mutants(null, (Value) receivedValue));
    } else if (receivedValue instanceof PSpecial specialValue) {
        for (Value value : specialValue.getGroup()) {
        if (value.isRemoved()) {
          modifications.add(new Mutants(null, value));
        }
      }
    }

    // Update "Snd" postconditions based on modifications
    Fact postSend = arrivingRule.getPostconditionFactByMatchingName("Snd");
    if (postSend != null) {
      // Loop over each Mutant
      for (Mutants mutant : modifications) {

        // Loop over each parameter in the postcondition Fact
        for (Object param : postSend.getParameters()) {

          // Case 1: The parameter is a direct Value
          if (param instanceof Value value) {
            // Compare name and check knowledge
            if (value.getName().equals(mutant.getNewValue().getName()) && !value.isInKnowledge()) {

              // Remove the Value and also remove the entire postSend Fact
              value.setRemoved(true);
              postSend.setRemoved(true);
            }
          } else if (param instanceof PSpecial special) {
            // Case 2: The parameter is a PSpecial (i.e., it can contain multiple Values)
            // Loop through all the Values inside the PSpecial
            for (int i = 0; i < special.numberOfValues(); i++) {
              Value innerValue = special.getValue(i);

              // Compare name and check knowledge
              if (innerValue.getName().equals(mutant.getNewValue().getName())
                  && !innerValue.isInKnowledge()) {

                // Remove only the matching Value
                innerValue.setRemoved(true);
                // Notice here we do NOT remove postSend itself,
                // to keep the same behavior as your first snippet
              }
            }
          }
        }
      }
    }

    // Remove send actions if applicable
    // Iterate through each action in the arriving rule
    for (Fact action : arrivingRule.getActions()) {
      // Check if the action is "Snd" or "To"
      if (action.getF_name().equals("Snd") || action.getF_name().equals("To")) {
        // Iterate through each modification
        for (int i = 0; i < modifications.size(); i++) {
          Mutants mutant = modifications.get(i);
          // Iterate through each parameter of the action
          for (Object parameter : action.getParameters()) {
            Value value = (Value) parameter;
            // Check if the value matches the mutant's new value and is not in knowledge
            if (value.getName().equals(mutant.getNewValue().getName())
                && !value.isInKnowledge()
                && !mutant.getNewValue().isInKnowledge()) {
              // Mark the action as removed
              action.setRemoved(true);
            }
          }
        }
      }
    }
  }
}

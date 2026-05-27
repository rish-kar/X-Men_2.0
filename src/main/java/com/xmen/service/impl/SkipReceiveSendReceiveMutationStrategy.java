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

/** SkipReceiveSendReceiveMutationStrategy class. */
@Service
@Slf4j
public class SkipReceiveSendReceiveMutationStrategy implements MutationStrategy {

  private final UtilityFunctions utilityFunctions;
  private final RulesModifier rulesModifier;

  /**
   * SkipReceiveSendReceiveMutationStrategy constructor.
   *
   * @param utilityFunctions Utility functions
   * @param rulesModifier Rules modifier
   */
  @Autowired
  public SkipReceiveSendReceiveMutationStrategy(
      @Lazy UtilityFunctions utilityFunctions, RulesModifier rulesModifier) {
    this.utilityFunctions = utilityFunctions;
    this.rulesModifier = rulesModifier;
  }

  /**
   * Apply the SkipReceiveSendReceive mutation strategy to the original rule.
   *
   * @param originalRule The original rule to be mutated
   * @param rules The list of rules in the theory
   * @param parametersBundle The parameters bundle containing the theory and other parameters
   * @return The parameters bundle containing the mutated theory and other parameters
   */
  @Override
  public ParametersBundle applyMutation(
      Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {
    log.debug("Starting SKIP_RECEIVE_SEND_RECEIVE mutation...");

    ArrayList<Rule> mutatedTheory = new ArrayList<>();
    Rule nextHumanRule;

    try {
      // Step 1: Check if the original rule has both Rcv and Snd conditions
      if (originalRule.getPreconditionFactByMatchingName("Rcv") != null
          && originalRule.getPostconditionFactByMatchingName("Snd") != null) {

        // Step 2: Identify the next rules related to human interactions
        for (Rule candidateRule : rules) {
          if (candidateRule.getPreconditionFactByMatchingName("Rcv") != null) {

            Fact postAgentState = originalRule.getPostconditionFactByMatchingName("State");
            String agentName = ((Value) postAgentState.getParameter(0)).getName();
            int state =
                Integer.parseInt(
                    ((Value) postAgentState.getParameter(1)).getName().replaceAll("[^0-9]", ""));

            nextHumanRule = candidateRule.findNextRule(agentName, state);

            if (nextHumanRule != null) {
              nextHumanRule = nextHumanRule.clone();
              mutatedTheory.add(nextHumanRule);
            }
          }
        }

        // Step 3: Apply mutations to the rules
        for (Rule arrivingRule : mutatedTheory) {

          ArrayList<Rule> localTheoryClone = utilityFunctions.cloneModel(parametersBundle);
          Rule mutatedRule = originalRule.clone();

          // Mark the mutated rule with appropriate naming and type
          mutatedRule.setRule_name(mutatedRule.getRule_name() + "_M");
          mutatedRule.setTypo(com.xmen.model.Type.MUTATED);
          mutatedRule.getPreconditionFactByMatchingName("Rcv").setRemoved(true);

          Fact preState = mutatedRule.getPreconditionFactByMatchingName("State");
          if (mutatedRule.getPostconditionFactByMatchingName("State") != null) {
            mutatedRule
                .getPostconditions()
                .set(0, utilityFunctions.buildNewState(mutatedRule.getPreconditions(), preState));
          }

          mutatedRule.getPostconditionFactByMatchingName("Snd").setRemoved(true);

          // Remove specific actions related to Snd, Rcv, To, and From
          for (Fact action : mutatedRule.getActions()) {
            if (action.getF_name().equals("Snd")
                || action.getF_name().equals("To")
                || action.getF_name().equals("Rcv")
                || action.getF_name().equals("From")) {
              action.setRemoved(true);
            }
          }

          log.debug("Mutated rule created: {}", mutatedRule);
          localTheoryClone.add(mutatedRule);
          rulesModifier.rulesModifier(localTheoryClone, true, arrivingRule, parametersBundle);

          // Step 4: Apply mutations to the arriving rule
          arrivingRule.setRule_name(arrivingRule.getRule_name() + "_M");
          arrivingRule.setTypo(com.xmen.model.Type.MUTATED);

          Fact preAgentState = arrivingRule.getPreconditionFactByMatchingName("State");
          Value agentName = (Value) preAgentState.getParameter(0);
          Value stateNumber = (Value) preAgentState.getParameter(1);
          int state = Integer.parseInt(stateNumber.getName().replaceAll("[^0-9]", ""));

          Fact previousStateFact =
              utilityFunctions.previousRuleFact(localTheoryClone, agentName.getName(), state);
          if (previousStateFact != null) {
            previousStateFact.setType(TypeFact.PRE);
            arrivingRule.getPreconditions().set(0, previousStateFact.clone());
          }

          arrivingRule.getPreconditionFactByMatchingName("Rcv").setRemoved(true);

          for (Fact action : arrivingRule.getActions()) {
            if (action.getF_name().equals("Rcv") || action.getF_name().equals("From")) {
              action.setRemoved(true);
            }
          }

          Fact preStateCondition = arrivingRule.getPreconditionFactByMatchingName("State");
          if (arrivingRule.getPostconditionFactByMatchingName("State") != null) {
            arrivingRule
                .getPostconditions()
                .set(
                    0,
                    utilityFunctions.buildNewState(
                        originalRule.getPreconditions(), preStateCondition));
          }

          // Handle mutants in the receive precondition
          ArrayList<Mutants> modifications = new ArrayList<>();
          Object receiveParam =
              arrivingRule.getPreconditionFactByMatchingName("Rcv").getParameter(2);

          if (receiveParam instanceof Value) {
            if (((Value) receiveParam).isRemoved()) {
              modifications.add(new Mutants(null, (Value) receiveParam));
            }
          } else if (receiveParam instanceof PSpecial specialGroup) {
              for (int i = 0; i < specialGroup.getGroup().size(); i++) {
              if (specialGroup.getValue(i).isRemoved()) {
                modifications.add(new Mutants(null, specialGroup.getValue(i)));
              }
            }
          }

          // Handle Snd postcondition mutations
          Fact postSndCondition = arrivingRule.getPostconditionFactByMatchingName("Snd");
          for (Mutants mutant : modifications) {
            if (postSndCondition != null) {
              for (int i = 0; i < postSndCondition.getParameters().size(); i++) {
                Object param = postSndCondition.getParameter(i);

                if (param instanceof Value value) {
                    if (value.getName().equals(mutant.getNewValue().getName())
                      && !value.isInKnowledge()) {
                    value.setRemoved(true);
                    postSndCondition.setRemoved(true);
                  }
                } else if (param instanceof PSpecial special) {
                    for (int j = 0; j < special.numberOfValues(); j++) {
                    if (special.getValue(j).getName().equals(mutant.getNewValue().getName())
                        && !special.getValue(j).isInKnowledge()) {
                      special.getValue(j).setRemoved(true);
                    }
                  }
                }
              }
            }
          }

          log.debug("Arriving rule mutated: {}", arrivingRule);
          localTheoryClone.add(arrivingRule);
          return rulesModifier.rulesModifier(localTheoryClone, false, null, parametersBundle);
        }
      }

    } catch (Exception e) {
      log.error("An error occurred during the mutation process: ", e);
    }

    log.debug("Mutation process completed.");
    return parametersBundle;
  }
}

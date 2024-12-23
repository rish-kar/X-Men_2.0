package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.*;
import com.sermas.x.men.service.MutationStrategy;
import com.sermas.x.men.utilities.RulesModifier;
import com.sermas.x.men.utilities.UtilityFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;

@Service
@Slf4j
public class SkipSendReceiveMutationStrategy implements MutationStrategy {

    private final UtilityFunctions utilityFunctions;
    private final RulesModifier rulesModifier;

    @Autowired
    public SkipSendReceiveMutationStrategy(@Lazy UtilityFunctions utilityFunctions, RulesModifier rulesModifier) {
        this.utilityFunctions = utilityFunctions;
        this.rulesModifier = rulesModifier;
    }


    @Override
    public ParametersBundle applyMutation(Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {
        try {
            if (originalRule.getPostconditionFactByMatchingName("Snd") == null) {
                return parametersBundle; // No mutation needed if "Snd" postcondition is absent
            }

            ArrayList<Rule> nextRules = findNextRules(originalRule, rules);

            for (Rule arrivingRule : nextRules) {
                ArrayList<Rule> theoryClone = utilityFunctions.cloneModel(parametersBundle);

                // Mutate the original rule
                Rule mutatedRule = mutateOriginalRule(originalRule);
                theoryClone.add(mutatedRule);

                // Mutate the arriving rule
                mutateArrivingRule(arrivingRule, theoryClone, originalRule);

                // Add mutated arriving rule to the clone
                theoryClone.add(arrivingRule);

                log.info("Final Mutated Arriving Rule: {}", arrivingRule);

                // Apply the modified rules
                parametersBundle = rulesModifier.rulesModifier(theoryClone, false, null, parametersBundle);
            }
        } catch (Exception e) {
            log.error("Error during mutation process: ", e);
        }

        return parametersBundle;
    }

    private ArrayList<Rule> findNextRules(Rule originalRule, ArrayList<Rule> rules) {
        ArrayList<Rule> nextRules = new ArrayList<>();
        Fact postState = originalRule.getPostconditionFactByMatchingName("State");

        String agentName = ((Value) postState.getParameter(0)).getName();
        int state = Integer.parseInt(((Value) postState.getParameter(1)).getName().replaceAll("[^0-9]", ""));

        for (Rule tempRule : rules) {
            if (tempRule.getPreconditionFactByMatchingName("Rcv") != null) {
                Rule nextRule = tempRule.findNextRule(agentName, state);
                if (nextRule != null) {
                    nextRules.add(nextRule.clone());
                }
            }
        }

        return nextRules;
    }

    private Rule mutateOriginalRule(Rule originalRule) {
        Rule mutatedRule = originalRule.clone();
        mutatedRule.setRule_name(mutatedRule.getRule_name() + "_M");
        mutatedRule.setTypo(com.sermas.x.men.model.Type.MUTATED);

        // Remove "Snd" postcondition
        Fact sndPostcondition = mutatedRule.getPostconditionFactByMatchingName("Snd");
        if (sndPostcondition != null) {
            sndPostcondition.setRemoved(true);
        }

        // Remove "Snd" and "To" actions
        mutatedRule.getActions().removeIf(action -> action.getF_name().equals("Snd") || action.getF_name().equals("To"));

        log.info("Mutated Original Rule: {}", mutatedRule);
        return mutatedRule;
    }

    private void mutateArrivingRule(Rule arrivingRule, ArrayList<Rule> theoryClone, Rule originalRule) {
        arrivingRule.setRule_name(arrivingRule.getRule_name() + "_M");
        arrivingRule.setTypo(com.sermas.x.men.model.Type.MUTATED);

        // Update state precondition
        Fact preState = arrivingRule.getPreconditionFactByMatchingName("State");
        Value agent = (Value) preState.getParameter(0);
        int stateIndex = Integer.parseInt(((Value) preState.getParameter(1)).getName().replaceAll("[^a-zA-Z0-9]", ""));
        Fact previousState = utilityFunctions.previousRuleFact(theoryClone, agent.getName(), stateIndex);

        if (previousState != null) {
            previousState.setType(TypeFact.PRE);
            arrivingRule.getPreconditions().set(0, previousState.clone());
        }

        // Remove "Rcv" precondition and associated actions
        arrivingRule.getPreconditionFactByMatchingName("Rcv").setRemoved(true);
        arrivingRule.getActions().removeIf(action -> action.getF_name().equals("Rcv") || action.getF_name().equals("From"));

        // Update state postcondition
        Fact preStateFact = arrivingRule.getPreconditionFactByMatchingName("State");
        if (arrivingRule.getPostconditionFactByMatchingName("State") != null) {
            arrivingRule.getPostconditions().set(0, utilityFunctions.buildNewState(originalRule.getPreconditions(), preStateFact));
        }

        // Handle modifications and update send postconditions
        handleModifications(arrivingRule);
    }

    private void handleModifications(Rule arrivingRule) {
        ArrayList<Mutants> modifications = new ArrayList<>();
        Fact rcvFact = arrivingRule.getPreconditionFactByMatchingName("Rcv");
        Object receivedValue = rcvFact.getParameter(2);

        if (receivedValue instanceof Value && ((Value) receivedValue).isRemoved()) {
            modifications.add(new Mutants(null, (Value) receivedValue));
        } else if (receivedValue instanceof PSpecial) {
            PSpecial specialValue = (PSpecial) receivedValue;
            for (Value value : specialValue.getGroup()) {
                if (value.isRemoved()) {
                    modifications.add(new Mutants(null, value));
                }
            }
        }

        // Update "Snd" postconditions based on modifications
        Fact postSend = arrivingRule.getPostconditionFactByMatchingName("Snd");
        if (postSend != null) {
            for (Mutants mutant : modifications) {
                postSend.getParameters().forEach(param -> {
                    if (param instanceof Value value && value.getName().equals(mutant.getNewValue().getName())) {
                        value.setRemoved(true);
                        postSend.setRemoved(true);
                    }
                });
            }
        }

        // Remove send actions if applicable
        arrivingRule.getActions().removeIf(action ->
                action.getF_name().equals("Snd") || action.getF_name().equals("To")
        );
    }


}
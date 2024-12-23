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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class SkipReceiveMutationStrategy implements MutationStrategy {

    private final UtilityFunctions utilityFunctions;
    private final RulesModifier rulesModifier;

    @Autowired
    public SkipReceiveMutationStrategy(@Lazy UtilityFunctions utilityFunctions, RulesModifier rulesModifier) {
        this.utilityFunctions = utilityFunctions;
        this.rulesModifier = rulesModifier;
    }


    @Override
    public ParametersBundle applyMutation(Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {
        ParametersBundle parametersBundle1 = new ParametersBundle();
        parametersBundle1.setTheory(rules);
        Fact rcvPreconditionFact = originalRule.getPreconditionFactByMatchingName("Rcv");
        if (rcvPreconditionFact == null) {
            return parametersBundle;
        }

        ArrayList<Rule> clonedTheory = utilityFunctions.cloneModel(parametersBundle1);

        Rule mutatedRule = originalRule.clone();
        mutatedRule.setRule_name(originalRule.getRule_name() + "_M");
        mutatedRule.setTypo(com.sermas.x.men.model.Type.MUTATED);

        // Remove Rcv precondition
        Fact mutatedRcvFact = mutatedRule.getPreconditionFactByMatchingName("Rcv");
        mutatedRcvFact.setRemoved(true);

        // Remove Rcv and From actions
        removeActionsByNames(mutatedRule, "Rcv", "From");

        // Gather modifications
        ArrayList<Mutants> modifications = gatherModifications(mutatedRcvFact);

        // If no modifications, just return after adding the rule (if that's appropriate for your logic)
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

// Example helper methods

    private void removeActionsByNames(Rule rule, String... namesToRemove) {
        Set<String> removeSet = new HashSet<>(Arrays.asList(namesToRemove));
        for (Fact action : rule.getActions()) {
            if (removeSet.contains(action.getF_name())) {
                action.setRemoved(true);
            }
        }
    }

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
        } else if (param2 instanceof PSpecial) {
            PSpecial special = (PSpecial) param2;
            for (int i = 0; i < special.getGroup().size(); i++) {
                modifications.add(new Mutants(null, (Value) special.getValue(i)));
            }
        }
        // Variables or other types can be handled if needed
        return modifications;
    }

    private void rebuildStateIfPresent(Rule mutatedRule) {
        Fact statePrecondition = mutatedRule.getPreconditionFactByMatchingName("State");
        Fact statePostcondition = mutatedRule.getPostconditionFactByMatchingName("State");
        if (statePostcondition != null) {
            mutatedRule.getPostconditions().set(0,
                    utilityFunctions.buildNewState(mutatedRule.getPreconditions(), statePrecondition));
        }
    }

    private void removeMatchingValuesFromFact(Fact fact, Set<String> valueNames) {
        for (Object param : fact.getParameters()) {
            if (param instanceof Value) {
                Value val = (Value) param;
                if (valueNames.contains(val.getName())) {
                    val.setRemoved(true);
                }
            } else if (param instanceof PSpecial) {
                PSpecial special = (PSpecial) param;
                for (int s = 0; s < special.numberOfValues(); s++) {
                    Value currentValue = special.getValue(s);
                    if (valueNames.contains(currentValue.getName())) {
                        currentValue.setRemoved(true);
                    }
                }
            }

        }
    }

    private void removeActionsIfContain(ArrayList<Mutants> modifications, Rule rule, Set<String> valueNames, String... actionNames) {
        Set<String> actionNamesSet = new HashSet<>(Arrays.asList(actionNames));
        for (Fact action : rule.getActions()) {
            if (actionNamesSet.contains(action.getF_name())) {
                for (int i = 0; i < modifications.size(); i++) {
                    Mutants mutants = modifications.get(i);

                    boolean removeAction = false;
                    for (Object param : action.getParameters()) {
                        if (param instanceof Value) {
                            Value val = (Value) param;
                            if (valueNames.contains(val.getName()) && !val.isInKnowledge() && !modifications.get(i).getNewValue().isInKnowledge()) {
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
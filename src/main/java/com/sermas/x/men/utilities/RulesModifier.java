package com.sermas.x.men.utilities;

import com.sermas.x.men.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class RulesModifier {

    @Autowired
    UtilityFunctions utilityFunctions;

    public ParametersBundle rulesModifier(ArrayList<Rule> modelRules, boolean enable, Rule anticipatedEndRule, ParametersBundle parametersBundle) {
        ArrayList<Mutants> modifications = new ArrayList<>();
        Rule currentRule = utilityFunctions.find(modelRules);
        Rule ruleToBeModified = currentRule.getNext();

        try {
            if (ruleToBeModified != null) {
                ruleToBeModified = currentRule.getNext().clone();
            }
            currentRule.setNext(ruleToBeModified);

            while (ruleToBeModified != null) {
                // Check if processing should stop based on the anticipated end rule
                if (enable && ruleToBeModified.getRule_name().equals(anticipatedEndRule.getRule_name())) {
                    break;
                }

                // Mark the rule as mutated
                ruleToBeModified.setRule_name(ruleToBeModified.getRule_name() + "_M");
                ruleToBeModified.setTypo(com.sermas.x.men.model.Type.MUTATED);
                Fact postSendFact = currentRule.getPostconditionFactByMatchingName("Snd");

                if (postSendFact.isRemoved() && postSendFact != null) {
                    modifications.clear();
                    ruleToBeModified.getPreconditionFactByMatchingName("Rcv").setRemoved(true);

                    // Save information removed from the Rcv
                    Object value = postSendFact.getParameter(2);

                    if (value instanceof Variable) {
                        handleVariableModification(ruleToBeModified, (Variable) value, modifications);
                    } else if (value instanceof Value) {
                        handleValueModification(ruleToBeModified, (Value) value, modifications);
                    } else if (value instanceof PSpecial) {
                        handlePSpecialModification(ruleToBeModified, (PSpecial) value, modifications);
                    }
                } else {
                    modifications.clear();
                    Object value = postSendFact.getParameter(2);

                    if (value instanceof Variable) {
                        // TODO: Handle Variable modification
                    } else if (value instanceof Value) {
                        handleValueModification((Value) value, modifications);
                    } else if (value instanceof PSpecial) {
                        handlePSpecialModification((PSpecial) value, modifications);
                    }
                }

                // Modify preconditions
                modifyPreconditions(ruleToBeModified, modelRules, modifications);

                // Modify actions
                modifyActions(ruleToBeModified, modifications);

                // Modify postconditions
                modifyPostconditions(ruleToBeModified, modifications);

                log.info("\n" + ruleToBeModified.toString() + "\n");
                modelRules.add(ruleToBeModified);

                currentRule = ruleToBeModified;
                ruleToBeModified = ruleToBeModified.getNext();

                if (ruleToBeModified != null) {
                    ruleToBeModified = ruleToBeModified.clone();
                }
            }

            if (!enable) {
                parametersBundle.setCollections(new ArrayList<>(List.of(modelRules)));
            }
        } catch (Exception e) {
            log.error("Error occurred while processing rules: ", e);
        }
        return parametersBundle;
    }

    private void handleVariableModification(Rule ruleToBeModified, Variable variable, ArrayList<Mutants> modifications) {
        Fact preReceiveFact = ruleToBeModified.getPreconditionFactByMatchingName("Rcv");
        Fact postSendFact = ruleToBeModified.getPostconditionFactByMatchingName("Snd");

        ArrayList<String> postSendVariables = null;
        ArrayList<String> preReceiveVariables = null;

        if (preReceiveFact != null) {
            if (preReceiveFact.getParameter(2) instanceof Variable) {
                preReceiveVariables = utilityFunctions.loop((Variable) preReceiveFact.getParameter(2));
            }
        }

        for (Variable var : ruleToBeModified.getVariables()) {
            for (String varName : preReceiveVariables) {
                if (!varName.equals("")) {
                    if (var.getName().equals(varName)) {
                        ruleToBeModified.getVariables().get(ruleToBeModified.getVariables().indexOf(var)).setRemoved(true);
                    }
                }
            }
        }

        for (Variable var : ruleToBeModified.getVariables()) {
            if (!var.isRemoved()) {
                Special specialValues = var.getValues();
                if (specialValues instanceof PSpecial) {
                    for (Object obj : ((PSpecial) specialValues).getGroup()) {
                        if (obj instanceof Variable) {
                            var.setRemoved(true);
                        }
                    }
                } else if (specialValues instanceof FSpecial) {
                    for (Object obj : ((FSpecial) specialValues).getGroup()) {
                        if (obj instanceof Variable) {
                            var.setRemoved(true);
                        }
                    }
                }
            }
        }
    }

    private void handleValueModification(Rule ruleToBeModified, Value value, ArrayList<Mutants> modifications) {
        Mutants mutant = new Mutants(null, (Value) ruleToBeModified.getPreconditionFactByMatchingName("Rcv").getParameter(0));
        modifications.add(mutant);
        if (value.isRemoved()) {
            Mutants removedMutant = new Mutants(null, value);
            modifications.add(removedMutant);
        }
    }

    private void handleValueModification(Value value, ArrayList<Mutants> modifications) {
        if (value.isRemoved()) {
            Mutants removedMutant = new Mutants(null, value);
            modifications.add(removedMutant);
        }
    }

    private void handlePSpecialModification(Rule ruleToBeModified, PSpecial pSpecial, ArrayList<Mutants> modifications) {
        Mutants mutant = new Mutants(null, (Value) ruleToBeModified.getPreconditionFactByMatchingName("Rcv").getParameter(0));
        modifications.add(mutant);
        for (int i = 0; i < pSpecial.getGroup().size(); i++) {
            if (pSpecial.getValue(i).isRemoved()) {
                Mutants removedMutant = new Mutants(null, (Value) pSpecial.getValue(i));
                modifications.add(removedMutant);
            }
        }
    }

    private void handlePSpecialModification(PSpecial pSpecial, ArrayList<Mutants> modifications) {
        for (int i = 0; i < pSpecial.getGroup().size(); i++) {
            if (pSpecial.getValue(i).isRemoved()) {
                Mutants removedMutant = new Mutants(null, (Value) pSpecial.getValue(i));
                modifications.add(removedMutant);
            }
        }
    }

    private void modifyPreconditions(Rule ruleToBeModified, ArrayList<Rule> modelRules, ArrayList<Mutants> modifications) {
        Fact preAgentState = ruleToBeModified.getPreconditionFactByMatchingName("State");
        Fact preReceiveFact = ruleToBeModified.getPreconditionFactByMatchingName("Rcv");

        Value agentName = (Value) preAgentState.getParameter(0);
        Value stateNumber = (Value) preAgentState.getParameter(1);
        int state = Integer.parseInt(stateNumber.getName().replaceAll("[^a-zA-Z0-9]", ""));
        Fact sameAgentPrecondition = utilityFunctions.previousRuleFact(modelRules, agentName.getName(), state);
        if (sameAgentPrecondition != null) {
            sameAgentPrecondition.setType(TypeFact.PRE);
            ruleToBeModified.getPreconditions().set(0, sameAgentPrecondition.clone());
        }

        if (!preReceiveFact.isRemoved()) {
            for (Mutants mutant : modifications) {
                if (preReceiveFact != null) {
                    for (int i = 0; i < preReceiveFact.getParameters().size(); i++) {
                        Object param = preReceiveFact.getParameter(i);

                        if (param instanceof Value) {
                            if (((Value) param).getName().equals(mutant.getNewValue().getName()) && !((Value) param).isInKnowledge()) {
                                ((Value) param).setRemoved(true);
                            }
                        } else if (param instanceof PSpecial) {
                            for (int j = 0; j < ((PSpecial) param).numberOfValues(); j++) {
                                if (((PSpecial) param).getValue(j).getName().equals(mutant.getNewValue().getName()) && !((PSpecial) param).getValue(j).isInKnowledge()) {
                                    ((PSpecial) param).getValue(j).setRemoved(true);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void modifyActions(Rule ruleToBeModified, ArrayList<Mutants> modifications) {
        ArrayList<Fact> actionsToBeModified = ruleToBeModified.getActions();
        for (Mutants mutant : modifications) {
            for (Fact action : actionsToBeModified) {
                Value value = action.findValue(mutant.getNewValue());
                if (value != null && !value.isInKnowledge()) {
                    action.setRemoved(true);
                }
            }
        }

        for (Fact action : ruleToBeModified.getActions()) {
            for (Variable variable : ruleToBeModified.getVariables()) {
                String variableName = variable.getName();
                if (variable.isRemoved()) {
                    if (utilityFunctions.actionsCheck(action, variableName)) {
                        action.setRemoved(true);
                        break;
                    }
                }
            }
        }
    }


    private void modifyPostconditions(Rule ruleToBeModified, ArrayList<Mutants> modifications) {
        if (ruleToBeModified.getPostconditionFactByMatchingName("State") != null) {
            ruleToBeModified.getPostconditions().set(0, utilityFunctions.buildNewState(ruleToBeModified.getPreconditions(), ruleToBeModified.getPreconditionFactByMatchingName("State")));
        }

        Fact postSendFact = ruleToBeModified.getPostconditionFactByMatchingName("Snd");
        if (postSendFact != null) {
            if (postSendFact.getParameter(2) instanceof Variable) {
                for (Variable variable : ruleToBeModified.getVariables()) {
                    String name = ((Variable) postSendFact.getParameter(2)).getName();
                    if (name.equals(variable.getName())) {
                        postSendFact.setRemoved(true);
                    }
                }
            } else if (postSendFact.getParameter(2) instanceof PSpecial || (postSendFact.getParameter(2) instanceof Value)) {
                for (Mutants mutant : modifications) {
                    if (postSendFact != null) {
                        for (int i = 0; i < postSendFact.getParameters().size(); i++) {
                            Object param = postSendFact.getParameter(i);

                            if (param instanceof Value) {
                                if (((Value) param).getName().equals(mutant.getNewValue().getName()) && !((Value) param).isInKnowledge()) {
                                    ((Value) param).setRemoved(true);
                                    postSendFact.setRemoved(true);
                                }
                            } else if (param instanceof PSpecial) {
                                for (int j = 0; j < ((PSpecial) param).numberOfValues(); j++) {
                                    if (((PSpecial) param).getValue(j).getName().equals(mutant.getNewValue().getName()) && !((PSpecial) param).getValue(j).isInKnowledge()) {
                                        ((PSpecial) param).getValue(j).setRemoved(true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

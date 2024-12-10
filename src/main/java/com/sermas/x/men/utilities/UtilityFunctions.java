package com.sermas.x.men.utilities;

import com.sermas.x.men.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class UtilityFunctions {

    private final FileHandler fileHandler;

    @Autowired
    public UtilityFunctions(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }

    /**
     * Clones the given list of rules and arranges the theory.
     *
     * @param originalTheory The original list of rules to clone.
     * @return The cloned and arranged list of rules.
     */
    public ArrayList<Rule> cloneModel(ArrayList<Rule> originalTheory) {
        ArrayList<Rule> clonedTheory = new ArrayList<>();
        try {
            // Clone each rule in the original theory
            for (Rule rule : originalTheory) {
                clonedTheory.add(rule.clone());
            }
            // Arrange the cloned theory
            fileHandler.arrangeTheory(clonedTheory);
            log.info("Successfully cloned and arranged the theory.");
        } catch (Exception e) {
            log.error("Error occurred while cloning and arranging the theory: ", e);
        }
        return clonedTheory;
    }


    /**
     * Recursively collects the names of variables from the given variable and its nested variables.
     *
     * @param variable The variable to start collecting names from.
     * @return A list of variable names.
     */
    public ArrayList<String> loop(Variable variable) {
        ArrayList<String> variableNames = new ArrayList<>();
        try {
            // Get the special values associated with the variable
            Special specialValues = variable.getValues();

            // Add the name of the current variable to the list
            variableNames.add(variable.getName());

            // Check if the special values are of type PSpecial
            if (specialValues instanceof PSpecial) {
                // Iterate through the group of objects in PSpecial
                for (Object obj : ((PSpecial) specialValues).getGroup()) {
                    // If the object is a variable, add its name and recursively collect its nested variable names
                    if (obj instanceof Variable) {
                        variableNames.add(((Variable) obj).getName());
                        loop((Variable) obj, variableNames);
                    }
                }
            } else if (specialValues instanceof FSpecial) {
                // Check if the special values are of type FSpecial
                // Iterate through the group of objects in FSpecial
                for (Object obj : ((FSpecial) specialValues).getGroup()) {
                    // If the object is a variable, add its name and recursively collect its nested variable names
                    if (obj instanceof Variable) {
                        variableNames.add(((Variable) obj).getName());
                        loop((Variable) obj, variableNames);
                    }
                }
            }
        } catch (Exception e) {
            // Log any exceptions that occur during the process
            log.error("Error occurred while collecting variable names: ", e);
        }
        // Return the list of collected variable names
        return variableNames;
    }

    /**
     * Helper method to recursively collect names of nested variables.
     *
     * @param variable The variable to start collecting names from.
     * @param variableNames The list to store collected variable names.
     */
    private void loop(Variable variable, ArrayList<String> variableNames) {
        try {
            // Get the special values associated with the variable
            Special specialValues = variable.getValues();

            // Check if the special values are of type PSpecial
            if (specialValues instanceof PSpecial) {
                // Iterate through the group of objects in PSpecial
                for (Object obj : ((PSpecial) specialValues).getGroup()) {
                    // If the object is a variable, add its name and recursively collect its nested variable names
                    if (obj instanceof Variable) {
                        variableNames.add(((Variable) obj).getName());
                        loop((Variable) obj, variableNames);
                    }
                }
            } else if (specialValues instanceof FSpecial) {
                // Check if the special values are of type FSpecial
                // Iterate through the group of objects in FSpecial
                for (Object obj : ((FSpecial) specialValues).getGroup()) {
                    // If the object is a variable, add its name and recursively collect its nested variable names
                    if (obj instanceof Variable) {
                        variableNames.add(((Variable) obj).getName());
                        loop((Variable) obj, variableNames);
                    }
                }
            }
        } catch (Exception e) {
            // Log any exceptions that occur during the process
            log.error("Error occurred while collecting nested variable names: ", e);
        }
    }

    /**
     * Checks if the given variable is present in the parameters of the given fact.
     *
     * @param fact The fact to check.
     * @param variableName The name of the variable to look for.
     * @return true if the variable is found, false otherwise.
     */
    public boolean actionsCheck(Fact fact, String variableName) {
        ArrayList<Object> parameters = fact.getParameters();

        try {
            // Iterate through each parameter in the fact
            for (Object parameter : parameters) {
                if (parameter instanceof Value) {
                    Value value = (Value) parameter;
                    // Check if the variable name matches
                    if (value.getName().equals(variableName)) {
                        return true;
                    }

                    // Check if the variable name is part of a larger string
                    if (isVariableInString(value.getName(), variableName)) {
                        return true;
                    }

                } else if (parameter instanceof PSpecial) {
                    // Check nested values in PSpecial
                    for (Object nestedValue : ((PSpecial) parameter).getGroup()) {
                        if (nestedValue instanceof Value) {
                            if (((Value) nestedValue).getName().equals(variableName)) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log any exceptions that occur during the process
            log.error("Error occurred while checking actions: ", e);
        }

        // Return false if the variable is not found
        return false;
    }

    /**
     * Helper method to check if a variable name is part of a larger string.
     *
     * @param text The text to search within.
     * @param variableName The variable name to look for.
     * @return true if the variable name is found, false otherwise.
     */
    private boolean isVariableInString(String text, String variableName) {
        String[] patterns = {
                variableName + "[,]",
                "[(]" + variableName,
                variableName + "[)]"
        };

        for (String pattern : patterns) {
            Pattern compiledPattern = Pattern.compile(pattern);
            Matcher matcher = compiledPattern.matcher(text);
            if (matcher.find()) {
                return true;
            }
        }

        return false;
    }


    /**
     * Finds and returns the last mutated rule in the given list of rules.
     *
     * @param rules The list of rules to search through.
     * @return The last mutated rule found, or null if no mutated rule is found.
     */
    public Rule find(ArrayList<Rule> rules) {
        Rule lastMutatedRule = null;

        try {
            // Iterate through the list of rules
            for (Rule rule : rules) {
                // Check if the rule is mutated
                if (rule.getTypo() == com.sermas.x.men.model.Type.MUTATED) {
                    lastMutatedRule = rule;
                }
            }
        } catch (Exception e) {
            // Log any exceptions that occur during the search
            log.error("An error occurred while finding the last mutated rule: ", e);
        }

        // Return the last mutated rule found, or null if none was found
        return lastMutatedRule;
    }


    /**
     * Finds the previous rule fact for a given agent and step.
     *
     * @param theory The list of rules to search through.
     * @param agentName The name of the agent.
     * @param step The step number to match.
     * @return The matching fact, or null if not found.
     */
    public Fact previousRuleFact(ArrayList<Rule> theory, String agentName, int step) {
        Fact agentStateFound = null;
        String stepString = "'" + step + "'";

        try {
            // Iterate through the list of rules
            for (Rule rule : theory) {
                // Get the postcondition fact matching the given agent name and step
                Fact agentState = rule.getPostconditionFactByMatchingNames("State", agentName, stepString);
                if (agentState != null) {
                    agentStateFound = agentState;
                }
            }
        } catch (Exception e) {
            // Log any exceptions that occur during the search
            log.error("An error occurred while finding the previous rule fact: ", e);
        }

        // Return the found agent state or null if not found
        return agentStateFound;
    }

    /**
     * Constructs a new state fact for an agent based on the given preconditions and the current state.
     *
     * @param preconditions The list of preconditions to process.
     * @param currentState The current state fact of the agent.
     * @return The newly constructed state fact.
     */
    public Fact buildNewState(ArrayList<Fact> preconditions, Fact currentState) {
        Fact newState = new Fact("State");
        newState.setType(TypeFact.POST);

        try {
            // Extract agent name and current state number
            Value agentName = (Value) currentState.getParameter(0);
            Value currentStateNumber = (Value) currentState.getParameter(1);
            Value newStateNumber = currentStateNumber.clone();
            int stateNumber = Integer.parseInt(currentStateNumber.getName().replaceAll("[^0-9]", ""));

            // Increment state number
            stateNumber++;
            String stateString = "'" + stateNumber + "'";
            newStateNumber.setName(stateString);

            // Add agent name and new state number to the new state fact
            newState.getParameters().add(agentName);
            newState.getParameters().add(newStateNumber);

            // Clone the knowledge from the current state
            PSpecial currentKnowledge = (PSpecial) currentState.getParameter(2);
            PSpecial newKnowledge = currentKnowledge.clone();

            // Process each precondition to update the knowledge
            for (int i = 1; i < preconditions.size(); i++) {
                Fact precondition = preconditions.get(i);

                if (precondition.getF_name().startsWith("Rcv") && !precondition.isRemoved()) {
                    Object receivedValue = precondition.getParameter(2);
                    if (receivedValue instanceof PSpecial) {
                        PSpecial receivedValues = (PSpecial) receivedValue;
                        for (Value value : receivedValues.getGroup()) {
                            if (!value.isRemoved() && !value.isConstant()) {
                                value.persistentKnowledge();
                                newKnowledge.addValue(value);
                            }
                        }
                    } else if (receivedValue instanceof Variable) {
                        Variable receivedVariable = (Variable) receivedValue;
                        ArrayList<Value> values = new ArrayList<>();
                        exploreVariable(values, receivedVariable);
                        for (Value value : values) {
                            if (!value.isRemoved() && !value.isConstant()) {
                                value.persistentKnowledge();
                                newKnowledge.addValue(value);
                            }
                        }
                    }
                } else if (precondition.getF_name().startsWith("Fr")) {
                    Value value = (Value) precondition.getParameter(0);
                    value.persistentKnowledge();
                    newKnowledge.addValue(value);
                }
            }

            // Remove duplicate values from the knowledge
            ArrayList<Value> uniqueValues = new ArrayList<>(new LinkedHashSet<>(newKnowledge.getGroup()));
            newKnowledge.getGroup().clear();
            newKnowledge.getGroup().addAll(uniqueValues);

            // Add the updated knowledge to the new state fact
            newState.getParameters().add(newKnowledge);

        } catch (Exception e) {
            log.error("Error while building new state: ", e);
        }

        // Return the newly constructed state fact
        return newState;
    }

    /**
     * Recursively explores a variable and adds its values to the provided list.
     *
     * @param valuesList The list to which the values will be added.
     * @param variable The variable to explore.
     */
    public void exploreVariable(ArrayList<Value> valuesList, Variable variable) {
        String variableName = variable.getName();
        Special specialValues = variable.getValues();

        try {
            // Check if the special values are of type PSpecial
            if (specialValues instanceof PSpecial) {
                for (Object value : ((PSpecial) specialValues).getGroup()) {
                    if (value instanceof Variable) {
                        // Recursively explore nested variables
                        exploreVariable(valuesList, (Variable) value);
                    } else {
                        // Add the value to the list
                        valuesList.add((Value) value);
                    }
                }
            } else if (specialValues instanceof FSpecial) {
                // Check if the special values are of type FSpecial
                for (Object value : ((FSpecial) specialValues).getGroup()) {
                    if (value instanceof Variable) {
                        // Recursively explore nested variables
                        exploreVariable(valuesList, (Variable) value);
                    } else {
                        // Add the value to the list
                        valuesList.add((Value) value);
                    }
                }
            }
        } catch (Exception e) {
            // Log any exceptions that occur during the exploration
            log.error("Error while exploring variable '{}': ", variableName, e);
        }
    }
}


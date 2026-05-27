package com.xmen.utilities;

import com.xmen.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** UtilityFunctions class provides various utility methods for manipulating and processing. */
@Component
@Slf4j
public class UtilityFunctions {

  private final FileHandler fileHandler;

  /**
   * UtilityFunctions constructor.
   *
   * @param fileHandler FileHandler instance for file operations
   */
  @Autowired
  public UtilityFunctions(FileHandler fileHandler) {
    this.fileHandler = fileHandler;
  }

  /**
   * Clones the given list of rules and arranges the theory.
   *
   * @param parametersBundle The parameters bundle containing the theory to clone.
   * @return The cloned and arranged list of rules.
   */
  public ArrayList<Rule> cloneModel(ParametersBundle parametersBundle) {
    ArrayList<Rule> clonedTheory = new ArrayList<>();
    ArrayList<Rule> originalTheory = parametersBundle.getTheory();
    try {
      // Clone each rule in the original theory
      for (Rule rule : originalTheory) {
        clonedTheory.add(rule.clone());
      }
      parametersBundle.setTheory(new ArrayList<>(clonedTheory));
      // Arrange the cloned theory
      parametersBundle = fileHandler.arrangeTheory(parametersBundle);
      log.debug("Successfully cloned and arranged the theory.");
    } catch (Exception e) {
      log.error("Error occurred while cloning and arranging the theory: ", e);
    }
    return parametersBundle.getTheory();
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
        for (Object obj : specialValues.getGroup()) {
          // If the object is a variable, add its name and recursively collect its nested variable
          // names
          if (obj instanceof Variable) {
            variableNames.add(((Variable) obj).getName());
            loop((Variable) obj, variableNames);
          }
        }
      } else if (specialValues instanceof FSpecial) {
        // Check if the special values are of type FSpecial
        // Iterate through the group of objects in FSpecial
        for (Object obj : specialValues.getGroup()) {
          // If the object is a variable, add its name and recursively collect its nested variable
          // names
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
        for (Object obj : specialValues.getGroup()) {
          // If the object is a variable, add its name and recursively collect its nested variable
          // names
          if (obj instanceof Variable) {
            variableNames.add(((Variable) obj).getName());
            loop((Variable) obj, variableNames);
          }
        }
      } else if (specialValues instanceof FSpecial) {
        // Check if the special values are of type FSpecial
        // Iterate through the group of objects in FSpecial
        for (Object obj : specialValues.getGroup()) {
          // If the object is a variable, add its name and recursively collect its nested variable
          // names
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
        if (parameter instanceof Value value) {
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
    String[] patterns = {variableName + "[,]", "[(]" + variableName, variableName + "[)]"};

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
   * Constructs a new state fact for an agent based on the given preconditions and the current
   * state.
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
          if (receivedValue instanceof PSpecial receivedValues) {
            for (Value value : receivedValues.getGroup()) {
              if (!value.isRemoved() && !value.isConstant()) {
                value.persistentKnowledge();
                newKnowledge.addValue(value);
              }
            }
          } else if (receivedValue instanceof Variable receivedVariable) {
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
        for (Object value : specialValues.getGroup()) {
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
        for (Object value : specialValues.getGroup()) {
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

  /**
   * Clones a list of rules.
   *
   * @param rules The list of rules to be cloned.
   * @return A new list containing clones of the original rules.
   */
  public ArrayList<Rule> cloneRules(ArrayList<Rule> rules) {
    ArrayList<Rule> clonedRules = new ArrayList<>();

    try {
      // Iterate through each rule in the original list
      for (Rule rule : rules) {
        // Clone the rule and add it to the new list
        clonedRules.add(rule.clone());
      }
    } catch (Exception e) {
      // Log any exceptions that occur during the cloning process
      log.error("Error while cloning rules: ", e);
    }

    // Return the new list of cloned rules
    return clonedRules;
  }

  /**
   * Returns the power set of the given set. The power set is the set of all possible subsets
   * (including the empty set and the set itself).
   *
   * @param originalSet the set from which to generate all subsets
   * @param <T> the type of elements in the set
   * @return the power set of the given set
   */
  public <T> Set<Set<T>> powerSet(Set<T> originalSet) {
    // This will hold our final collection of subsets
    Set<Set<T>> result = new HashSet<>();

    // Base case: if the original set is empty, its power set contains only the empty set
    if (originalSet.isEmpty()) {
      result.add(new HashSet<>()); // add an empty subset
      return result;
    }

    // Convert to a list to easily extract one element (the "firstElement")
    List<T> elementList = new ArrayList<>(originalSet);

    // "firstElement" is the head of our set, "remainder" are all other elements
    T firstElement = elementList.get(0);
    Set<T> remainder = new HashSet<>(elementList.subList(1, elementList.size()));

    // Recursively build the power set of the remainder
    Set<Set<T>> remainderPowerSet = powerSet(remainder);

    // For each subset in the remainder's power set, create two subsets:
    // 1. The subset itself (without the firstElement).
    // 2. A copy of the subset that includes the firstElement.
    for (Set<T> subset : remainderPowerSet) {
      // Subset #1 (unchanged)
      result.add(subset);

      // Subset #2 (includes firstElement)
      Set<T> subsetWithFirst = new HashSet<>(subset);
      subsetWithFirst.add(firstElement);
      result.add(subsetWithFirst);
    }

    return result;
  }

  /**
   * Finds a rule in the given list by its name.
   *
   * @param rules The list of rules to search through.
   * @param ruleName The name of the rule to find.
   * @return The rule with the specified name, or null if not found.
   */
  public Rule find(List<Rule> rules, String ruleName) {
    for (Rule rule : rules) {
      if (rule.getRule_name().equals(ruleName)) {
        return rule;
      }
    }
    return null;
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
        if (rule.getTypo() == com.xmen.model.Type.MUTATED) {
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
   * Checks whether the provided {@code value} matches any {@code function} based on the old/new
   * values found in the given {@code mutants} object. If a match is found (i.e., the value name
   * starts with the function name and the parameter matches the old value in {@code mutants}), this
   * method clones the new value, updates its name, and returns it. Otherwise, returns {@code null}.
   *
   * @param functions the list of {@link Function} objects to check against
   * @param value the {@link Value} whose name is evaluated
   * @param mutants contains old and new values for substitution
   * @return a cloned {@link Value} with updated name if a match is found, otherwise {@code null}
   */
  public Value checkFunctionReplacement(List<Function> functions, Value value, Mutants mutants) {
    if (functions == null || functions.isEmpty()) {
      return null;
    }
    // Loop through all functions to find a match in the value's name
    for (Function function : functions) {
      String functionName = function.getName().replaceAll("[^a-zA-Z]", "");
      String originalName = value.getName();
      // If the value's name starts with the function name, extract what's after it
      if (originalName.startsWith(functionName)) {
        String extractedParam = originalName.replace(functionName, "").replaceAll("[^a-zA-Z]", "");
        String oldParamName = mutants.getOldValue().getName().replaceAll("[^a-zA-Z]", "");
        // Check if extracted param matches the old value in mutants
        if (extractedParam.equals(oldParamName)) {
          // Build new name, e.g. fun(...) + newValue
          String newName = functionName + "(" + mutants.getNewValue().getName() + ")";
          // Clone the new value and update its name
          Value updatedValue = mutants.getNewValue().clone();
          updatedValue.setName(newName);
          return updatedValue;
        }
      }
    }
    // No matching function replacement found
    return null;
  }

  // Forget Mutation Logic
  /**
   * Removes from stateKnowledge any Message whose "unparsed" string exactly matches one of the
   * forget strings in parametersBundle.getForgetMutationSet().
   *
   * <p>For example: if "bal($oyster)" is in the forget set for "H_2", and stateKnowledge["H_2"]
   * contains a PredictiveFunction(bal [ Atom($oyster) ]), we unparse that to "bal($oyster)" and
   * remove it.
   *
   * @param stateKnowledge The map ruleName -> set of messages from
   *     extractStateKnowledgeFromRules(...)
   * @param forgetMutationSet The map ruleName -> set of raw strings from parseForgetMutations(...)
   */
  public void applyForgetSetsToStateKnowledge(
      Map<String, Set<Message>> stateKnowledge,
      Map<String, LinkedHashSet<String>> forgetMutationSet) {
    for (Map.Entry<String, Set<Message>> entry : stateKnowledge.entrySet()) {
      String ruleName = entry.getKey();
      Set<Message> messages = entry.getValue();

      // Only do something if there's a forget set for this ruleName
      if (!forgetMutationSet.containsKey(ruleName)) {
        continue;
      }

      // For clarity:
      Set<String> forgetStrings = forgetMutationSet.get(ruleName);

      // Build a new set that excludes any message matching a forgetString
      Set<Message> filtered = new LinkedHashSet<>();
      for (Message msg : messages) {
        String unparsed = unparseMessage(msg);
        // If the unparsed string is NOT in the forget set, we keep it
        if (!forgetStrings.contains(unparsed)) {
          filtered.add(msg);
        }
      }
      // Overwrite with the filtered set
      entry.setValue(filtered);
    }
  }

  /**
   * Recursively "unparses" the Message back to a string in the same format your parser recognizes.
   * Examples: Atom("bal($oyster)") -> "bal($oyster)" PredictiveFunction("bal", [Atom("$oyster")])
   * -> "bal($oyster)" Pair(Atom("foo"), Atom("bar")) -> "(foo,bar)" Encrypt(...) -> e.g.
   * "{...}_{...}" Adjust the syntax to match your existing parse logic.
   *
   * @param msg The Message to unparse.
   * @return A string representation of the Message.
   */
  private String unparseMessage(Message msg) {
    if (msg instanceof Atom atom) {
      return atom.getValue();
    } else if (msg instanceof Pair pair) {
      // (left,right)
      return "(" + unparseMessage(pair.getLeft()) + ", " + unparseMessage(pair.getRight()) + ")";
    } else if (msg instanceof PredictiveFunction pf) {
      // e.g. bal($oyster)
      String args =
          pf.getArgs().stream().map(this::unparseMessage).collect(Collectors.joining(", "));
      return pf.getName() + "(" + args + ")";
    } else if (msg instanceof Encrypt encrypt) {
      // {msg}_{key}
      return "{"
          + unparseMessage(encrypt.getMsg())
          + "}_{"
          + unparseMessage(encrypt.getKey())
          + "}";
    }
    // Default fallback
    return msg.represent();
  }
}

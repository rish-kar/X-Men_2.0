package com.xmen.service.impl;

import com.xmen.model.*;
import com.xmen.utilities.UtilityFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ReplaceMutationService class. This service handles the replacement of mutations in rules and
 * theories, including type and submessage replacements. It provides methods to extract parameters,
 * handle type replacements, handle submessage replacements, and propagate changes throughout the
 * theory.
 */
@Component
@Slf4j
@Service
public class ReplaceMutationService {

  @Autowired private UtilityFunctions utilityFunctions;

  /**
   * Replaces mutations in the given rule and updates the theory.
   *
   * @param rule The rule to mutate.
   * @param theory The original theory that may be cloned or updated with mutations.
   * @return The updated theory with newly applied mutations.
   */
  public ParametersBundle replaceMutation(
      Rule rule, ArrayList<Rule> theory, ParametersBundle parametersBundle) {

    // Clone the original theory to preserve it for future iterations
    ArrayList<Rule> theoryClone = new ArrayList<>(theory.size());
    for (Rule r : theory) {
      theoryClone.add(r.clone());
    }

    // Step 1: Extract parameters from the "Snd" postcondition of the rule
    ArrayList<Value> parameters = extractSndParameters(rule);

    if (parametersBundle.getFlags().isReplaceType()) {
      // Step 2: Handle type replacements if enabled
      handleTypeReplacements(rule, theoryClone, parameters, parametersBundle);
    } else if (parametersBundle.getFlags().isReplaceSubmessages()) {
      // Step 3: Otherwise, handle sub message replacements if enabled
      handleSubmessageReplacements(rule, theoryClone, parameters, parametersBundle);
    }

    // Return the updated theory after applying all potential mutations
    return parametersBundle;
  }

  /**
   * Extracts any relevant parameters from the "Snd" postcondition of the given rule.
   *
   * @param rule The rule from which to extract parameters.
   * @return A list of extracted parameters.
   */
  private ArrayList<Value> extractSndParameters(Rule rule) {
    ArrayList<Value> extractedParameters = new ArrayList<>();
    try {
      Fact postSendOut = rule.getPostconditionFactByMatchingName("Snd");
      if (postSendOut != null) {
        Object sendParameter = postSendOut.getParameter(2);

        // If the parameter is a PSpecial, copy its list of Values.
        if (sendParameter instanceof PSpecial) {
          extractedParameters = ((PSpecial) sendParameter).getGroup();
        } else if (sendParameter instanceof Variable message) {
          // If the parameter is a Variable, explore/resolve it into a list of Values.

            utilityFunctions.exploreVariable(extractedParameters, message);
        } else if (sendParameter instanceof Value) {
          // Otherwise, if it is a single Value, just add it directly.

          extractedParameters.add((Value) sendParameter);
        }
      }
    } catch (Exception e) {
      log.error("Error extracting 'Snd' parameters: ", e);
    }
    return extractedParameters;
  }

  /**
   * Handles the process of replacing tags within the given rule and theory, based on the parameters
   * extracted from the "Snd" postcondition.
   *
   * @param rule The rule to mutate.
   * @param theory The original theory that might be cloned or updated.
   * @param parameters The parameters extracted from the "Snd" postcondition.
   */
  private void handleTypeReplacements(
      Rule rule,
      ArrayList<Rule> theory,
      ArrayList<Value> parameters,
      ParametersBundle parametersBundle) {
    try {
      // Attempt to retrieve the "State" fact from the postcondition
      Fact stateInPost = rule.getPostconditionFactByMatchingName("State");
      if (stateInPost == null) {
        return; // No state to mutate
      }

      // Extract knowledge from the "State" postcondition
      ArrayList<Value> knowledgeInPost = ((PSpecial) stateInPost.getParameter(2)).getGroup();

      // Determine if we have possible replacements to make
      boolean possibleReplacements = checkPotentialReplacement(knowledgeInPost, parameters);
      if (!possibleReplacements) {
        return;
      }

      // Build the list of mutants (changes) we might apply
      ArrayList<Mutants> changesToApply = buildPotentialReplacement(knowledgeInPost, parameters);

      // Apply each potential mutant
      for (Mutants mutant : changesToApply) {
        // Clone the entire theory to isolate changes
        ArrayList<Rule> theoryClone = utilityFunctions.cloneModel(parametersBundle);

        // Determine whether to clone or find the rule from the cloned theory
        Rule clonedRule =
            shouldCloneRule(parametersBundle)
                ? rule.clone()
                : utilityFunctions.find(theoryClone, rule.getRule_name());

        // Mark the cloned rule as mutated
        clonedRule.setRule_name(rule.getRule_name() + "_M");
        clonedRule.setTypo(com.xmen.model.Type.MUTATED);

        // Update all actions in the cloned rule
        updateActionsWithMutants(clonedRule, mutant, parametersBundle);

        // Update the "Snd" postcondition in the cloned rule
        updateSndPostcondition(clonedRule, mutant, parametersBundle);

        // Add the cloned rule back to the theory clone if needed
        if (shouldCloneRule(parametersBundle)) {
          theoryClone.add(clonedRule);
        }

        // Propagate the changes throughout the cloned theory
        propagationReplace(theoryClone, mutant, parametersBundle);
        parametersBundle.setTheory(theory);
      }
    } catch (Exception e) {
      log.error("Error during type replacement process: ", e);
    }
  }

  /**
   * Handles the process of replacing submessages within the given rule and theory, based on the
   * parameters extracted from the "Snd" postcondition.
   *
   * @param rule The rule to mutate.
   * @param theory The original theory that might be cloned or updated.
   * @param parameters The parameters extracted from the "Snd" postcondition.
   */
  private void handleSubmessageReplacements(
      Rule rule,
      ArrayList<Rule> theory,
      ArrayList<Value> parameters,
      ParametersBundle parametersBundle) {
    try {
      // Generate all possible sub-parameter permutations (via a power set of indices)
      ArrayList<String> permutations = generatePermutations(parameters);

      // For each permutation string, build a new sub message and clone theory/rule
      for (String permutation : permutations) {
        ArrayList<Rule> theoryClone =
            new ArrayList<>(utilityFunctions.cloneModel(parametersBundle));

        // Build a list of Values to send based on the current permutation
        ArrayList<Value> newContent =
            new ArrayList<>(generateArrayofValuetoSend(permutation, parameters));

        // Decide whether to clone the rule or fetch it from the cloned theory
        Rule clonedRule =
            shouldCloneRule(parametersBundle)
                ? rule.clone()
                : utilityFunctions.find(theoryClone, rule.getRule_name());

        clonedRule.setRule_name(clonedRule.getRule_name() + "_M");
        clonedRule.setTypo(com.xmen.model.Type.MUTATED);

        // Update the "Snd" post condition with the new sub message content
        Fact sendOut = clonedRule.getPostconditionFactByMatchingName("Snd");
        ArrayList<Value> newContentClone = new ArrayList<>(newContent);
        if (sendOut != null) {
          buildMessageToSend(sendOut, newContent);
        }

        // Optionally add the mutated rule to the cloned theory
        if (shouldCloneRule(parametersBundle)) {
          theoryClone.add(clonedRule);
        }

        // Propagate the changes of the submessages throughout the cloned theory
        propagationReplaceSubmessages(theoryClone, newContentClone, parametersBundle);
        parametersBundle.setTheory(theory);
      }
    } catch (Exception e) {
      log.error("Error during sub-message replacement process: ", e);
    }
  }

  /**
   * Checks whether the rule should be cloned and added to the theory or just found in the clone.
   * Improves readability of the repeated condition check.
   *
   * @return true if the rule should be cloned, false otherwise.
   */
  private boolean shouldCloneRule(ParametersBundle parametersBundle) {
    return (parametersBundle.getFlags().isCombineAddReplace()
            && parametersBundle.getFlags().isSwitchFlag())
        || parametersBundle.getFlags().isCombineAddReplaceOnly();
  }

  /**
   * Updates all actions within the given rule using the provided mutant.
   *
   * @param clonedRule The rule whose actions will be updated.
   * @param mutant The mutation details (old/new values, etc.).
   */
  private void updateActionsWithMutants(
      Rule clonedRule, Mutants mutant, ParametersBundle parametersBundle) {
    for (Fact action : clonedRule.getActions()) {
      if (action.getParameters().isEmpty()) {
        continue; // Nothing to update
      }

      // Replace or update each parameter
      for (Object param : action.getParameters()) {
        if (param instanceof Value paramValue) {

            // Direct substitution if it matches the old value
          if (mutant.getOldValue().equals(paramValue)) {
            int index = action.getParameters().indexOf(paramValue);
            action.getParameters().set(index, mutant.getNewValue());
          } else {
            // Otherwise, check if there's a functional replacement

            Value potentialValue =
                utilityFunctions.checkFunctionReplacement(
                    parametersBundle.getFunctions(), paramValue, mutant);
            if (potentialValue != null) {
              potentialValue.setTag(paramValue.getTag());
              int index = action.getParameters().indexOf(paramValue);
              action.getParameters().set(index, potentialValue);
            }
          }
        }
      }
    }
  }

  /**
   * Updates the "Snd" postcondition in the cloned rule with new mutation values.
   *
   * @param clonedRule The rule whose "Snd" postcondition will be updated.
   * @param mutant The mutation details (old/new values, etc.).
   */
  private void updateSndPostcondition(
      Rule clonedRule, Mutants mutant, ParametersBundle parametersBundle) {
    Fact outPost = clonedRule.getPostconditionFactByMatchingName("Snd");
    if (outPost == null) {
      return;
    }

    Object valuesSent = outPost.getParameter(2);

    // If "Snd" carries a PSpecial, update each Value in its group
    if (valuesSent instanceof PSpecial pspecial) {
        for (int i = 0; i < pspecial.getGroup().size(); i++) {
        Value oldValue = pspecial.getValue(i);

        // Direct substitution if it matches the old value
        if (mutant.getOldValue().equals(oldValue)) {
          pspecial.getGroup().set(i, mutant.getNewValue());
        } else {
          // Otherwise, check for functional replacement
          Value potentialValue =
              utilityFunctions.checkFunctionReplacement(
                  parametersBundle.getFunctions(), oldValue, mutant);
          if (potentialValue != null) {
            potentialValue.setTag(oldValue.getTag());
            pspecial.getGroup().set(i, potentialValue);
          }
        }
      }
    } else if (valuesSent instanceof Variable) {
      // TODO: Handle Variable case if needed
    } else if (valuesSent instanceof Value sentValue) {
      // If "Snd" is a single Value, do a direct substitution or functional replacement

        if (mutant.getOldValue().equals(sentValue)) {
        outPost.getParameters().set(2, mutant.getNewValue());
      } else {
        Value potentialValue =
            utilityFunctions.checkFunctionReplacement(
                parametersBundle.getFunctions(), sentValue, mutant);
        if (potentialValue != null) {
          potentialValue.setTag(sentValue.getTag());
          outPost.getParameters().set(2, potentialValue);
        }
      }
    }
  }

  /**
   * Generates all permutation strings based on the size of the parameters list (via power set of
   * indices).
   *
   * @param parameters The parameters for which to generate permutations.
   * @return A list of permutation strings, each representing a subset of parameter indices.
   */
  private ArrayList<String> generatePermutations(ArrayList<Value> parameters) {
    ArrayList<String> permutations = new ArrayList<>();
    try {
        List<List<Integer>> allSubsets = lexPowerSet(parameters.size());
        for (List<Integer> subset : allSubsets) {
            StringBuilder subsetString = new StringBuilder();
            for (Integer index : subset) {
                subsetString.append(index);
            }
            if (!subsetString.toString().isEmpty()) {
                permutations.add(subsetString.toString());
            }
        }
    } catch (Exception e) {
        log.error("Error generating permutations: ", e);
    }
    return permutations;
  }

  /**
   * Checks if there is at least one pair of values (one from knowledgeList and one from sentList)
   * that share the same non-null type (tag) but have different names.
   *
   * @param knowledgeList the list of Value objects representing knowledge in the postcondition
   * @param sentList the list of Value objects that were sent
   * @return true if a matching type (tag) with different names is found; false otherwise
   */
  private boolean checkPotentialReplacement(List<Value> knowledgeList, List<Value> sentList) {
    for (Value knowledgeValue : knowledgeList) {
      for (Value sentValue : sentList) {
        if (knowledgeValue.getTag() != null
            && sentValue.getTag() != null
            && knowledgeValue.getTag().equals(sentValue.getTag())
            && !knowledgeValue.getName().equals(sentValue.getName())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Builds a list of potential Mutants by comparing values from knowledgeList and sentList. A
   * Mutant is created whenever two values have the same non-null tag but different names.
   *
   * @param knowledgeList the list of Value objects representing knowledge in the postcondition
   * @param sentList the list of Value objects that were sent
   * @return a list of Mutants representing potential replacements
   */
  private ArrayList<Mutants> buildPotentialReplacement(
      List<Value> knowledgeList, List<Value> sentList) {
    ArrayList<Mutants> mutantsList = new ArrayList<>();
    for (Value knowledgeValue : knowledgeList) {
      for (Value sentValue : sentList) {
        if (knowledgeValue.getTag() != null
            && sentValue.getTag() != null
            && knowledgeValue.getTag().equals(sentValue.getTag())
            && !knowledgeValue.getName().equals(sentValue.getName())) {
          mutantsList.add(new Mutants(sentValue, knowledgeValue));
        }
      }
    }
    return mutantsList;
  }

  /**
   * Propagates the replacement of values in the given theory based on the provided mutants.
   *
   * @param theory The list of rules representing the theory.
   * @param mutants The mutants containing the old and new values for replacement.
   */
  public void propagationReplace(
      ArrayList<Rule> theory, Mutants mutants, ParametersBundle parametersBundle) {
    try {
      // Find the initial rule to be modified
      Rule initialRule = utilityFunctions.find(theory);
      Rule ruleToBeModified = initialRule.getNext();

      // Clone the rule if necessary
      if (ruleToBeModified != null
          && (parametersBundle.getFlags().isCombineAddReplace()
                  && parametersBundle.getFlags().isSwitchFlag()
              || parametersBundle.getFlags().isCombineAddReplaceOnly())) {
        ruleToBeModified = initialRule.getNext().clone();
      }
      initialRule.setNext(ruleToBeModified);

      // Iterate through the rules and apply replacements
      while (ruleToBeModified != null) {
        ruleToBeModified.setRule_name(ruleToBeModified.getRule_name() + "_M");
        ruleToBeModified.setTypo(com.xmen.model.Type.MUTATED);

        // Modify the precondition
        Fact preReceive = ruleToBeModified.getPreconditionFactByMatchingName("Rcv");
        if (preReceive != null) {
          Object valuesReceived = preReceive.getParameter(2);
          if (valuesReceived instanceof PSpecial) {
            for (int i = 0; i < ((PSpecial) valuesReceived).getGroup().size(); i++) {
              Value value = ((PSpecial) valuesReceived).getValue(i);
              if (mutants.getOldValue().equals(value)) {
                ((PSpecial) valuesReceived).getGroup().set(i, mutants.getNewValue());
              } else {
                Value potentialValue =
                    utilityFunctions.checkFunctionReplacement(
                        parametersBundle.getFunctions(), value, mutants);
                if (potentialValue != null) {
                  potentialValue.setTag(value.getTag());
                  ((PSpecial) valuesReceived).getGroup().set(i, potentialValue);
                }
              }
            }
          } else if (valuesReceived instanceof Variable) {
            // TODO: Handle Variable case
          } else if (valuesReceived instanceof Value) {
            if (mutants.getOldValue().equals(valuesReceived)) {
              preReceive.getParameters().set(2, mutants.getNewValue());
            }
          }
        }

        // Modify the actions
        for (Fact action : ruleToBeModified.getActions()) {
          if (!action.getParameters().isEmpty()) {
            for (Object parameter : action.getParameters()) {
              if (parameter instanceof Value) {
                if (mutants.getOldValue().equals(parameter)) {
                  action
                      .getParameters()
                      .set(action.getParameters().indexOf(parameter), mutants.getNewValue());
                } else {
                  Value potentialValue =
                      utilityFunctions.checkFunctionReplacement(
                          parametersBundle.getFunctions(), (Value) parameter, mutants);
                  if (potentialValue != null) {
                    potentialValue.setTag(((Value) parameter).getTag());
                    action
                        .getParameters()
                        .set(action.getParameters().indexOf(parameter), potentialValue);
                  }
                }
              }
            }
          }
        }

        // Modify the postcondition
        if (!ruleToBeModified.isHuman()) {
          Fact postAgentState = ruleToBeModified.getPostconditionFactByMatchingName("State");
          if (postAgentState != null) {
            PSpecial knowledge = (PSpecial) postAgentState.getParameter(2);
            for (int i = 0; i < knowledge.getGroup().size(); i++) {
              Value value = knowledge.getValue(i);
              if (mutants.getOldValue().equals(value)) {
                knowledge.getGroup().set(i, mutants.getNewValue());
              } else {
                Value potentialValue =
                    utilityFunctions.checkFunctionReplacement(
                        parametersBundle.getFunctions(), value, mutants);
                if (potentialValue != null) {
                  potentialValue.setTag(value.getTag());
                  knowledge.getGroup().set(i, potentialValue);
                }
              }
            }
          }
        }

        Fact postSend = ruleToBeModified.getPostconditionFactByMatchingName("Snd");
        if (postSend != null) {
          Object valuesSent = postSend.getParameter(2);
          if (valuesSent instanceof PSpecial) {
            for (int i = 0; i < ((PSpecial) valuesSent).getGroup().size(); i++) {
              Value value = ((PSpecial) valuesSent).getValue(i);
              if (mutants.getOldValue().equals(value)) {
                ((PSpecial) valuesSent).getGroup().set(i, mutants.getNewValue());
              } else {
                Value potentialValue =
                    utilityFunctions.checkFunctionReplacement(
                        parametersBundle.getFunctions(), value, mutants);
                if (potentialValue != null) {
                  potentialValue.setTag(value.getTag());
                  ((PSpecial) valuesSent).getGroup().set(i, potentialValue);
                }
              }
            }
          } else if (valuesSent instanceof Value) {
            if (mutants.getOldValue().equals(valuesSent)) {
              postSend.getParameters().set(2, mutants.getNewValue());
            } else {
              Value potentialValue =
                  utilityFunctions.checkFunctionReplacement(
                      parametersBundle.getFunctions(), (Value) valuesSent, mutants);
              if (potentialValue != null) {
                potentialValue.setTag(((Value) valuesSent).getTag());
                postSend.getParameters().set(2, potentialValue);
              }
            }
          }
        }

        // Add the modified rule to the theory if necessary
        if (parametersBundle.getFlags().isCombineAddReplace()
                && parametersBundle.getFlags().isSwitchFlag()
            || parametersBundle.getFlags().isCombineAddReplaceOnly()) {
          theory.add(ruleToBeModified);
        }

        // Move to the next rule
        ruleToBeModified = ruleToBeModified.getNext();
        if (ruleToBeModified != null
            && (parametersBundle.getFlags().isCombineAddReplace()
                    && parametersBundle.getFlags().isSwitchFlag()
                || parametersBundle.getFlags().isCombineAddReplaceOnly())) {
          ruleToBeModified = ruleToBeModified.clone();
        }
      }

      // Add the modified theory to the collections
      parametersBundle.getCollections().add(theory);
    } catch (Exception e) {
      log.error("Error during propagation replacement process: ", e);
    }
  }

  /**
   * Generates a list of {@link Value} objects based on the given sequence of indices.
   *
   * @param sequence A string where each character represents an index.
   * @param inputValues A list of {@link Value} objects.
   * @return A new list of {@link Value} objects corresponding to the indices.
   */
  private List<Value> generateArrayofValuetoSend(String sequence, List<Value> inputValues) {
    List<Value> result = new ArrayList<>();
    for (char c : sequence.toCharArray()) {
      result.add(inputValues.get(Character.getNumericValue(c)));
    }
    return result;
  }

  /**
   * Builds a new message to send by modifying the given post-send fact with the provided values.
   *
   * @param postSendFact The fact representing the post-send condition.
   * @param valuesToSend The list of values to be sent.
   * @return The modified post-send fact.
   */
  private Fact buildMessageToSend(Fact postSendFact, ArrayList<Value> valuesToSend) {
    try {
      // Get the original message from the post-send fact
      Object originalMessage = postSendFact.getParameter(2);

      // Handle the case where the original message is a PSpecial
      if (originalMessage instanceof PSpecial originalPspecial) {
          ArrayList<Value> originalValues = originalPspecial.getGroup();
        updateValues(originalValues, valuesToSend);
      } else if (originalMessage instanceof Variable originalVariable) {
        // Handle the case where the original message is a Variable

          Special originalSpecial = originalVariable.getValues();
        if (originalSpecial instanceof PSpecial originalPspecial) {
            ArrayList<Value> originalValues = originalPspecial.getGroup();
          updateValues(originalValues, valuesToSend);
        } else if (originalSpecial instanceof FSpecial) {
          recursiveVariable(originalVariable, valuesToSend);
        }
      } else if (originalMessage instanceof Value) {
        // Handle the case where the original message is a single Value

        updateSingleValue((Value) originalMessage, valuesToSend);
      }
    } catch (Exception e) {
      log.error("Error while building message to send: ", e);
    }
    return postSendFact;
  }

  /**
   * Updates the original values with the new values to be sent.
   *
   * @param originalValues The original values in the message.
   * @param valuesToSend The new values to be sent.
   */
  private void updateValues(ArrayList<Value> originalValues, ArrayList<Value> valuesToSend) {
    int i = 0;
    while (i < originalValues.size() && !valuesToSend.isEmpty()) {
      Value originalValue = originalValues.get(i);
      Value newValue = valuesToSend.get(0);
      if (!originalValue.getName().equals(newValue.getName())) {
        originalValue.setRemoved(true);
      } else {
        valuesToSend.remove(0);
      }
      i++;
    }
    if (valuesToSend.isEmpty() && i < originalValues.size()) {
      for (int j = i; j < originalValues.size(); j++) {
        Object remainingValue = originalValues.get(j);
        if (remainingValue instanceof Variable) {
          recursiveVariable((Variable) remainingValue, valuesToSend);
        } else if (remainingValue instanceof Value) {
          ((Value) remainingValue).setRemoved(true);
        }
      }
    }
  }

  /**
   * Updates a single original value with the new values to be sent.
   *
   * @param originalValue The original value in the message.
   * @param valuesToSend The new values to be sent.
   */
  private void updateSingleValue(Value originalValue, ArrayList<Value> valuesToSend) {
    while (!valuesToSend.isEmpty()) {
      Value newValue = valuesToSend.get(0);
      if (!originalValue.getName().equals(newValue.getName())) {
        originalValue.setRemoved(true);
      } else {
        valuesToSend.remove(0);
      }
    }
  }

  /**
   * Recursively updates the values in the given variable based on the provided sequence.
   *
   * @param variable The variable containing the values to be updated.
   * @param valuesToUpdate The list of values to update.
   */
  private void recursiveVariable(Variable variable, ArrayList<Value> valuesToUpdate) {
    try {
      // Check if the variable contains a functional special (FSpecial) value
      if (variable.getValues() instanceof FSpecial functionalSpecial) {
          ArrayList<Value> group = functionalSpecial.getGroup();
        int i = 0;
        // Iterate through the group and update values
        for (; i < group.size() && !valuesToUpdate.isEmpty(); i++) {
          Object element = group.get(i);
          if (element instanceof Variable) {
            recursiveVariable((Variable) element, valuesToUpdate);
          } else if (element instanceof Value originalValue) {
              Value newValue = valuesToUpdate.get(0);
            if (!originalValue.getName().equals(newValue.getName())) {
              originalValue.setRemoved(true);
            } else {
              valuesToUpdate.remove(0);
            }
          }
        }
        // Mark remaining values as removed if the sequence is empty
        if (valuesToUpdate.isEmpty() && i < group.size()) {
          for (int j = i; j < group.size(); j++) {
            Object remainingElement = group.get(j);
            if (remainingElement instanceof Variable) {
              recursiveVariable((Variable) remainingElement, valuesToUpdate);
            } else if (remainingElement instanceof Value) {
              ((Value) remainingElement).setRemoved(true);
            }
          }
        }
      } else if (variable.getValues() instanceof PSpecial positionalSpecial) {
        // Check if the variable contains a positional special (PSpecial) value

          ArrayList<Value> group = positionalSpecial.getGroup();
        int i = 0;
        // Iterate through the group and update values
        for (; i < group.size() && !valuesToUpdate.isEmpty(); i++) {
          Value originalValue = group.get(i);
          Value newValue = valuesToUpdate.get(0);
          if (!originalValue.getName().equals(newValue.getName())) {
            originalValue.setRemoved(true);
          } else {
            valuesToUpdate.remove(0);
          }
        }
        // Mark remaining values as removed if the sequence is empty
        if (valuesToUpdate.isEmpty() && i < group.size()) {
          for (int j = i; j < group.size(); j++) {
            Object remainingElement = group.get(j);
            if (remainingElement instanceof Variable) {
              recursiveVariable((Variable) remainingElement, valuesToUpdate);
            } else if (remainingElement instanceof Value) {
              ((Value) remainingElement).setRemoved(true);
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("Error while recursively updating variable: ", e);
    }
  }

  /**
   * Propagates and replaces submessages in the given theory based on the specified content.
   *
   * <p>This method locates a {@link Rule} in the provided {@code theory} list, modifies subsequent
   * rules, adjusts their preconditions and postconditions (e.g., "State", "Rcv", "Snd"), and
   * optionally adds those modified rules back into the theory. It also updates a global {@code
   * collections} structure with the final state of the theory.
   *
   * @param theory the list of {@link Rule} objects to be processed
   * @param contentClone a list of {@link Value} objects used for matching and removing operations
   */
  private void propagationReplaceSubmessages(
      ArrayList<Rule> theory, ArrayList<Value> contentClone, ParametersBundle parametersBundle) {
    try {
      // 1) Find the initial rule from the theory.
      Rule foundRule = utilityFunctions.find(theory);

      // 2) Retrieve the next rule to modify. If conditions are met, clone it.
      Rule ruleToModify = foundRule.getNext();
      if (ruleToModify != null
          && (parametersBundle.getFlags().isCombineAddReplace()
                  && parametersBundle.getFlags().isSwitchFlag()
              || parametersBundle.getFlags().isCombineAddReplaceOnly())) {
        ruleToModify = foundRule.getNext().clone();
      }
      // 3) Link the modified/cloned rule back to the found rule.
      foundRule.setNext(ruleToModify);

      // 4) Process each subsequent rule as long as it exists.
      while (ruleToModify != null) {
        // Clone the content list so each rule's changes start with the same base data.
        @SuppressWarnings("unchecked")
        ArrayList<Value> localContentClone = (ArrayList<Value>) contentClone.clone();

        // Mark rule as mutated for identification.
        ruleToModify.setRule_name(ruleToModify.getRule_name() + "_M");
        ruleToModify.setTypo(com.xmen.model.Type.MUTATED);

        /*
         * ==========================
         * PRECONDITION MODIFICATIONS
         * ==========================
         */
        // (A) Handle the "State" fact in the preconditions
        Fact preAgentState = ruleToModify.getPreconditionFactByMatchingName("State");

        if (preAgentState != null) {
          Value agentName = (Value) preAgentState.getParameter(0);
          Value stateNumberVal = (Value) preAgentState.getParameter(1);
          int parsedStateNumber =
              Integer.parseInt(stateNumberVal.getName().replaceAll("[^a-zA-Z0-9]", ""));
          Fact previousAgentFact =
              utilityFunctions.previousRuleFact(theory, agentName.getName(), parsedStateNumber);

          if (previousAgentFact != null) {
            previousAgentFact.setType(TypeFact.PRE);
            // Replace the old "State" fact with a cloned version of the newly found fact.
            ruleToModify.getPreconditions().set(0, previousAgentFact.clone());

            // Maintain consistency between "State" and "Rcv"
            ensureConsistency(
                ruleToModify.getPreconditionFactByMatchingName("State"),
                ruleToModify.getPreconditionFactByMatchingName("Rcv"));
          }
        }

        // (B) Handle the "Rcv" fact in the preconditions
        Fact preRcv = ruleToModify.getPreconditionFactByMatchingName("Rcv");
        ArrayList<Value> removedValues = new ArrayList<>();

        if (preRcv != null) {
          Object rcvParameter = preRcv.getParameter(2);
          ArrayList<Value> rcvValues = new ArrayList<>();

          if (rcvParameter instanceof PSpecial) {
            rcvValues = ((PSpecial) rcvParameter).getGroup();
          } else if (rcvParameter instanceof Variable) {
            utilityFunctions.exploreVariable(rcvValues, (Variable) rcvParameter);
          } else if (rcvParameter instanceof Value) {
            rcvValues.add((Value) rcvParameter);
          }

          int i = 0;
          int j = 0;
          while (i < contentClone.size() && j < rcvValues.size()) {
            String cloneName = contentClone.get(i).getName().replaceAll("[^a-zA-Z]", "");
            String rcvName = rcvValues.get(j).getName().replaceAll("[^a-zA-Z]", "");
            if (cloneName.equals(rcvName)) {
              i++;
              j++;
            } else {
              removedValues.add(rcvValues.get(j).clone());
              rcvValues.get(j).setRemoved(true);
              j++;
            }
          }
          for (int h = j; h < rcvValues.size(); h++) {
            rcvValues.get(h).setRemoved(true);
          }
        }

        /*
         * ===========================
         * POSTCONDITION MODIFICATIONS
         * ===========================
         */
        // (A) Update the "State" fact in the postconditions to build a new state
        Fact postState = ruleToModify.getPostconditionFactByMatchingName("State");
        if (postState != null) {
          Fact newState =
              utilityFunctions.buildNewState(ruleToModify.getPreconditions(), preAgentState);
          ruleToModify.getPostconditions().set(0, newState);
        }

        // (B) Handle the "Snd" fact in the postconditions
        Fact postSnd = ruleToModify.getPostconditionFactByMatchingName("Snd");
        if (postSnd != null) {
          Object valuesSent = postSnd.getParameter(2);
          ArrayList<Value> sentValues = new ArrayList<>();

          if (valuesSent instanceof PSpecial) {
            sentValues = ((PSpecial) valuesSent).getGroup();
          } else if (valuesSent instanceof Variable) {
            utilityFunctions.exploreVariable(sentValues, (Variable) valuesSent);
          } else if (valuesSent instanceof Value) {
            sentValues.add((Value) valuesSent);
          }

          Fact statePostFact = ruleToModify.getPostconditionFactByMatchingName("State");
          if (statePostFact != null && statePostFact.getParameter(2) instanceof PSpecial) {
            ArrayList<Value> knowledge = ((PSpecial) statePostFact.getParameter(2)).getGroup();
            for (int i = 0; i < sentValues.size(); i++) {
              if (!sentValues.get(i).isConstant()) {
                boolean found = false;
                for (Value kn : knowledge) {
                  if (sentValues.get(i).getName().replaceAll("[^a-zA-Z]", "")
                      .equals(kn.getName().replaceAll("[^a-zA-Z]", ""))) {
                    found = true;
                    break;
                  }
                }
                if (!found) {
                  sentValues.get(i).setRemoved(true);
                }
              }
            }
          }

          contentClone.clear();
          for (Value v : sentValues) {
            if (!v.isRemoved()) {
              contentClone.add(v);
            }
          }
        }

        // Decide whether to add the modified rule back to the theory
        if ((parametersBundle.getFlags().isCombineAddReplace()
                && parametersBundle.getFlags().isSwitchFlag())
            || parametersBundle.getFlags().isCombineAddReplaceOnly()) {
          theory.add(ruleToModify);
        }

        // Move to the next rule
        ruleToModify = ruleToModify.getNext();
        // If conditions are met, clone again for further modifications
        if (ruleToModify != null
            && (parametersBundle.getFlags().isCombineAddReplace()
                    && parametersBundle.getFlags().isSwitchFlag()
                || parametersBundle.getFlags().isCombineAddReplaceOnly())) {
          ruleToModify = ruleToModify.clone();
        }
      }

      // Add the updated theory to global collections
      parametersBundle.getCollections().add(theory);

    } catch (Exception e) {
      // Handle any unexpected errors
      System.err.println("Error in propagationReplaceSubmessages: " + e.getMessage());
      throw e; // Rethrow or handle as needed
    }
  }

  /**
   * Ensures consistency between the agent in the state fact and the agent in the receive fact.
   *
   * @param stateFact The fact representing the state.
   * @param receiveFact The fact representing the received message.
   */
  private void ensureConsistency(Fact stateFact, Fact receiveFact) {
    try {
      // Extract the agent from the state fact
      Value stateAgent = (Value) stateFact.getParameter(0);

      // Extract the agent who received the message from the receive fact
      Value receivingAgent = (Value) receiveFact.getParameter(1);

      // Check if the agents are the same
      if (!stateAgent.getName().equals(receivingAgent.getName())) {
        // Update the state agent's name to match the receiving agent's name
        stateAgent.setName(receivingAgent.getName());
      }
    } catch (Exception e) {
      log.error("Error ensuring consistency between state and receive facts: ", e);
    }
  }

  private List<List<Integer>> lexPowerSet(int n) {
    List<List<Integer>> result = new ArrayList<>();
    int powerSetSize = 1 << n;
    for (int i = 1; i < powerSetSize; i++) { // skip empty set
        List<Integer> subset = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            if ((i & (1 << j)) != 0) {
                subset.add(j);
            }
        }
        result.add(subset);
    }
    // Sort by size, then lexicographically
    result.sort(Comparator.<List<Integer>>comparingInt(List::size).thenComparing(a -> a.toString()));
    return result;
  }
}

package com.xmen.service.impl;

import com.xmen.model.*;
import com.xmen.service.MutationStrategy;
import com.xmen.utilities.UtilityFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * AddMutationStrategy is a concrete implementation of the MutationStrategy interface. This strategy
 * applies a mutation to a given rule by cloning it, modifying its postconditions, and generating
 * new rules based on the original.
 */
@Component
@Slf4j
public class AddMutationStrategy implements MutationStrategy {

  @Autowired UtilityFunctions utilityFunctions;

  /**
   * Applies a mutation to the given rule by cloning it, modifying its postconditions, and
   * generating new rules based on the original.
   *
   * @param originalRule the rule to mutate
   * @param rules the list of rules in the theory
   * @param parametersBundle the parameters bundle containing the theory and roles
   * @return the updated parameters bundle with the mutated rules added
   */
  @Override
  public ParametersBundle applyMutation(
      Rule originalRule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {

    // Basic null checks.
    if (originalRule == null || rules == null) {
      log.warn("Either 'originalRule' or 'theoryClone' is null. Cannot perform mutation.");
      log.warn("File Details: {}", this.getClass());
      return parametersBundle;
    }

    try {
      // Clone the original rule to start the mutation process
      Rule mutatedRule = originalRule.clone();
      log.debug("Cloned rule '{}' for mutation.", originalRule.getRule_name());

      // Identify the "State" Fact in the postconditions of the original rule
      Fact statePostcondition = originalRule.getPostconditionFactByMatchingName("State");
      if (statePostcondition == null) {
        // If there's no "State" in the postconditions, no mutation is performed
        log.debug(
            "No 'State' postcondition found in rule '{}'. Skipping mutation.",
            originalRule.getRule_name());
        log.debug("File Details: {}", this.getClass());
        return parametersBundle;
      }

      // Extract relevant data from the original rule
      Fact rcvPrecondition = originalRule.getPreconditionFactByMatchingName("Rcv");
      Fact sndPostcondition = originalRule.getPostconditionFactByMatchingName("Snd");
      ArrayList<Fact> actionsClone = mutatedRule.getActions();
      ArrayList<Fact> postconditionsClone = originalRule.getPostconditions();

      // Clear actions and postconditions from the mutated rule to reconstruct them
      mutatedRule.getActions().clear();
      mutatedRule.getPostconditions().clear();

      // Adjust rule name and type to indicate it is mutated
      mutatedRule.setRule_name(originalRule.getRule_name() + "_M");
      mutatedRule.setTypo(Type.MUTATED);

      // Associate this mutation back to the original
      originalRule.setAssociateMutation(mutatedRule);

      // Check which variables are no longer needed and remove them
      removeUnusedVariables(mutatedRule, rcvPrecondition, sndPostcondition);

      // Re-inject only the Rcv actions from the original rule into the mutated rule
      reInjectRcvActions(mutatedRule, actionsClone);

      // Extract knowledge from the "State" Fact
      ArrayList<Value> knowledgeContent =
          extractKnowledgeAndAddStatePostcondition(mutatedRule, postconditionsClone);

      // Build permutations of knowledge content
      ArrayList<String> permutations = buildPermutations(knowledgeContent);

      // Construct the base "Snd" Fact with an empty receiver to clone for each permutation
      Fact baseSendFact = buildBaseSendFact(originalRule);

      // Build messages to send from permutations
      ArrayList<Fact> messagesToSend =
          buildMessagesFromPermutations(baseSendFact, permutations, knowledgeContent);

      // For each role in the environment, create mutated send rules and handle arrival mutations
      processRolesAndMutations(
          originalRule, mutatedRule, messagesToSend, statePostcondition, parametersBundle);

    } catch (Exception e) {
      // Catch-all to ensure we log any unexpected issues
      log.error(
          "Error while adding mutation for rule '{}': ",
          (originalRule != null ? originalRule.getRule_name() : "null"),
          e);
    }

    return parametersBundle;
  }

  /**
   * Removes unused variables from the mutated rule by comparing "Rcv" preconditions and "Snd"
   * postconditions from the original rule.
   *
   * @param mutatedRule the rule to mutate
   * @param rcvPrecondition the "Rcv" precondition Fact from the original rule
   * @param sndPostcondition the "Snd" postcondition Fact from the original rule
   */
  private void removeUnusedVariables(
      Rule mutatedRule, Fact rcvPrecondition, Fact sndPostcondition) {
    try {
      // Prepare lists of variable names from Rcv (pre) and Snd (post)
      ArrayList<String> rcvVarNames = extractVariableNames(rcvPrecondition, 2);
      ArrayList<String> sndVarNames = extractVariableNames(sndPostcondition, 2);

      // Mark matching variable names as empty
      if ((sndVarNames != null && rcvVarNames != null)
          && (!sndVarNames.isEmpty() && !rcvVarNames.isEmpty())) {
        for (String postVar : sndVarNames) {
          for (String preVar : rcvVarNames) {
            if (postVar.equals(preVar)) {
              sndVarNames.set(sndVarNames.indexOf(postVar), "");
              break;
            }
          }
        }
      }

      // Remove those variables from the mutated rule's variable list
      for (Variable variable : mutatedRule.getVariables()) {
        if (variable == null) {
          continue;
        }
        if (sndVarNames != null) {
          for (String varName : sndVarNames) {
            if (!varName.isEmpty() && variable.getName().equals(varName)) {
              mutatedRule.getVariables().set(mutatedRule.getVariables().indexOf(variable), null);
              break;
            }
          }
        }
      }

      // Remove any null placeholders left behind
      mutatedRule.getVariables().removeAll(Collections.singleton(null));

    } catch (Exception ex) {
      log.error("Failed to remove unused variables in the mutated rule.", ex);
    }
  }

  /**
   * Extracts variable names from a specific parameter index of a Fact if that parameter is a
   * Variable.
   *
   * @param fact the Fact from which to extract variable names
   * @param parameterIndex the index of the parameter to check
   */
  private ArrayList<String> extractVariableNames(Fact fact, int parameterIndex) {
    if (fact == null || fact.getParameter(parameterIndex) == null) {
      log.warn("Fact or parameter is null. Cannot extract variable names.");
      log.debug("File Details: {}", this.getClass());
      return null;
    }
    Object param = fact.getParameter(parameterIndex);
    if (param instanceof Variable) {
      // 'loop' is assumed to be a method in your code that returns ArrayList<String>
      return utilityFunctions.loop((Variable) param);
    }
    return null;
  }

  /**
   * Re-injects only Rcv actions from the original actions clone into the mutated rule.
   *
   * @param mutatedRule the rule to mutate
   * @param originalActionsClone the original actions list to extract Rcv actions from
   */
  private void reInjectRcvActions(Rule mutatedRule, ArrayList<Fact> originalActionsClone) {
    for (Fact action : originalActionsClone) {
      if ("Rcv".equals(action.getF_name())) {
        mutatedRule.getActions().add(action);
      }
    }
  }

  /**
   * Finds the 'State' Fact in postconditions, adds it to the mutatedRule, and returns the PSpecial
   * knowledge content.
   *
   * @param mutatedRule the rule being mutated
   * @param postconditionsClone the cloned postconditions from the original rule
   */
  private ArrayList<Value> extractKnowledgeAndAddStatePostcondition(
      Rule mutatedRule, ArrayList<Fact> postconditionsClone) {
    PSpecial knowledge = null;
    ArrayList<Value> knowledgeList = new ArrayList<>();
    for (Fact fact : postconditionsClone) {
      if ("State".equals(fact.getF_name())) {
        mutatedRule.getPostconditions().add(fact);
        knowledge = (PSpecial) fact.getParameter(2);
        knowledgeList.addAll(knowledge.getGroup());
        break;
      }
    }
    return knowledgeList;
  }

  /**
   * Builds all possible permutations (as string indices) from the knowledge content.
   *
   * @param knowledgeContent the list of Value objects representing knowledge
   */
  private ArrayList<String> buildPermutations(ArrayList<Value> knowledgeContent) {
    ArrayList<String> permutationList = new ArrayList<>();
    if (knowledgeContent == null) {
      return permutationList;
    }

    // Create an "alphabet" of indices
    StringBuilder alphabetBuilder = new StringBuilder();
    Set<Integer> indexSet = new HashSet<>();

    // Build set of indices
    for (int i = 0; i < knowledgeContent.size(); i++) {
      alphabetBuilder.append(i);
      indexSet.add(i);
    }

    // Generate power set of the index set
    Set<Set<Integer>> powerSetOfIndices = utilityFunctions.powerSet(indexSet);

    // Convert each non-empty subset into a concatenated string
    for (Set<Integer> subset : powerSetOfIndices) {
      if (!subset.isEmpty()) {
        StringBuilder subsetStr = new StringBuilder();
        for (Integer idx : subset) {
          subsetStr.append(idx);
        }
        permutationList.add(subsetStr.toString());
      }
    }

    return permutationList;
  }

  /**
   * Creates a base "Snd" Fact using the rule's postcondition Snd as a template. Sets the receiver
   * to "empty".
   *
   * @param originalRule the original rule containing the Snd postcondition
   */
  private Fact buildBaseSendFact(Rule originalRule) {
    Fact postSnd = originalRule.getPostconditionFactByMatchingName("Snd");
    if (postSnd == null) {
      return null;
    }

    Fact newSendFact = new Fact(postSnd.getF_name());
    // Copy sender
    newSendFact.getParameters().add(postSnd.getParameter(0));
    // Set receiver to "empty"
    newSendFact.getParameters().add(new Value("empty"));

    return newSendFact;
  }

  /**
   * Given a base "Snd" Fact with "empty" receiver, build multiple messages using each permutation
   * of knowledge content.
   *
   * @param baseSendFact the base "Snd" Fact to clone for each permutation
   * @param permutations the list of string permutations representing indices
   * @param knowledgeContent the list of Value objects representing knowledge content
   * @return a list of mutated "Snd" Facts for each permutation
   */
  private ArrayList<Fact> buildMessagesFromPermutations(
      Fact baseSendFact, ArrayList<String> permutations, ArrayList<Value> knowledgeContent) {
    ArrayList<Fact> messages = new ArrayList<>();
    if (baseSendFact == null || knowledgeContent == null) {
      return messages;
    }

    for (String permutation : permutations) {
      // Clone the base fact for each permutation
      Fact clonedSend = baseSendFact.clone();
      PSpecial specialParam = new PSpecial();

      // Collect knowledge values by index
      for (int i = 0; i < permutation.length(); i++) {
        int index = Character.getNumericValue(permutation.charAt(i));
        specialParam.addValue(knowledgeContent.get(index));
      }

      // Add the PSpecial content as the third parameter
      clonedSend.getParameters().add(specialParam);
      messages.add(clonedSend);
    }
    return messages;
  }

  /**
   * For each role (agent), create the mutated send rule, then find the matching arrival rules for
   * that receiving agent, and mutate those as well.
   *
   * @param originalRule the original rule being mutated
   * @param mutatedRule the cloned and modified rule
   * @param messagesToSend the list of messages to send
   * @param statePostcondition the "State" postcondition Fact from the original rule
   * @param parametersBundle the parameters bundle containing the theory and roles
   */
  private void processRolesAndMutations(
      Rule originalRule,
      Rule mutatedRule,
      ArrayList<Fact> messagesToSend,
      Fact statePostcondition,
      ParametersBundle parametersBundle) {

    // Identify the "human agent" from the 'State' fact
    Value humanAgentValue = (Value) statePostcondition.getParameter(0);

    // For each possible message to send, mutate and add to the theory
    for (Fact messageToSend : messagesToSend) {
      for (Value role : parametersBundle.getRoles()) {
        ArrayList<Rule> preservedTheory = new ArrayList<>(parametersBundle.getTheory());
        // Clone the original theory so each scenario is separate
        ArrayList<Rule> theoryClone2 = utilityFunctions.cloneModel(parametersBundle);
        // Re-clone the mutated rule for each scenario
        Rule scenarioMutatedRule = mutatedRule.clone();

        if (!role.getName()
            .replaceAll("[^a-zA-Z0-9]", "")
            .equalsIgnoreCase(humanAgentValue.getName().replaceAll("[^a-zA-Z0-9]", ""))) {
          // Set the chosen role as the receiver
          Value name = role.clone();
          messageToSend.setType(TypeFact.POST);
          messageToSend.getParameters().set(1, name);

          // Add this new "Snd" fact to the mutated rule
          scenarioMutatedRule.getPostconditions().add(messageToSend);

          // Convert that same message to "Snd" actions in the "Actions" list
          addSndActionsForMessage(scenarioMutatedRule, messageToSend);

          // Log and store the mutated rule with the new message
          log.debug("Mutated send rule created:\n{}", scenarioMutatedRule);
          theoryClone2.add(scenarioMutatedRule);

          // Next, find potential arrival rules for this receiver agent and mutate them
          mutateArrivalRules(
              theoryClone2, scenarioMutatedRule, messageToSend, role, parametersBundle);
          parametersBundle.setTheory(preservedTheory);
          // Store the entire mutated theory in 'collections' if needed
          // Will be done inside mutateArrivalRules when final sets are done
        }
      }
    }
  }

  /**
   * Adds corresponding "Snd" actions for the third parameter of the message (PSpecial or Value).
   *
   * @param scenarioMutatedRule the rule being mutated
   * @param mutatedMessage the message Fact to process
   */
  private void addSndActionsForMessage(Rule scenarioMutatedRule, Fact mutatedMessage) {
    // Extract parameters
    Value senderParam = (Value) mutatedMessage.getParameter(0);
    Value receiverParam = (Value) mutatedMessage.getParameter(1);
    Object contentObj = mutatedMessage.getParameter(2);

    // If content is a PSpecial, add an action for each Value
    if (contentObj instanceof PSpecial) {
      for (Value contentValue : ((PSpecial) contentObj).getGroup()) {
        Fact sndAction = new Fact("Snd");
        sndAction.getParameters().add(senderParam);
        // Add the tag if present; otherwise, add 'noTag'
        if (contentValue.getTag() != null && !contentValue.getTag().isEmpty()) {
          sndAction.getParameters().add(new Value(contentValue.getTag()));
        } else {
          sndAction.getParameters().add(new Value("noTag"));
        }
        sndAction.getParameters().add(contentValue);
        scenarioMutatedRule.addAction(sndAction);
      }
    } else if (contentObj instanceof Variable) {
      // No specific action in the original snippet
    } else if (contentObj instanceof Value) {
      Fact sndAction = new Fact("Snd");
      sndAction.getParameters().add(senderParam);
      // Add the tag
      Value tag = new Value(((Value) contentObj).getTag());
      sndAction.getParameters().add(tag);
      sndAction.getParameters().add(contentObj);
      scenarioMutatedRule.addAction(sndAction);
    }
  }

  /**
   * Finds arrival rules for the specified 'receiverAgent' in the theory and mutates them to handle
   * the new "Rcv" message. Recursively mutates the chain of 'next' rules as well.
   *
   * @param theoryClone the cloned theory to mutate
   * @param scenarioMutatedRule the mutated rule with the new "Snd" message
   * @param messageToSend the "Snd" message Fact to process
   * @param receiverAgent the agent receiving the message
   * @param parametersBundle the parameters bundle containing the theory and roles
   */
  private void mutateArrivalRules(
      ArrayList<Rule> theoryClone,
      Rule scenarioMutatedRule,
      Fact messageToSend,
      Value receiverAgent,
      ParametersBundle parametersBundle) {

    // Extract the agent name from the 'receiverAgent'
    String receiverName = receiverAgent.getName();

    // Identify all rules that have a matching "State" for the agent in their preconditions
    ArrayList<Rule> arrivalRules = new ArrayList<>();
    for (Rule ruleCandidate : theoryClone) {
      Fact stateFact = ruleCandidate.getPreconditionFactByMatchingName("State");
      if (stateFact != null) {
        Value agentValue = (Value) stateFact.getParameter(0);
        if (agentValue.getName().equalsIgnoreCase(receiverName)) {
          arrivalRules.add(ruleCandidate);
        }
      }
    }

    // For each arrival rule, create a mutated version that includes receiving the new message
    for (Rule arrivalRule : arrivalRules) {
      ArrayList<Rule> localTheoryClone = utilityFunctions.cloneModel(parametersBundle);

      Rule mutatedArrivalRule = arrivalRule.clone();
      mutatedArrivalRule.setRule_name(arrivalRule.getRule_name() + "_M");

      // Convert the "Snd" message into "Rcv" precondition
      Fact rcvMessage = buildRcvMessage(messageToSend);
      // If the arrival rule has an existing Rcv precondition, replace it; otherwise, add it
      Fact existingRcv = mutatedArrivalRule.getPreconditionFactByMatchingName("Rcv");
      if (existingRcv != null) {
        mutatedArrivalRule.getPreconditions().set(1, rcvMessage);
      } else {
        mutatedArrivalRule.addPrecondition(rcvMessage);
      }

      // Remove or mark old Rcv/Snd/Commit actions as removed
      for (Fact action : mutatedArrivalRule.getActions()) {
        if (action.getF_name().equals("Rcv")
            || action.getF_name().equals("Snd")
            || action.getF_name().startsWith("Commit")) {
          action.setRemoved(true);
        }
      }

      // Add new "Rcv" actions
      addRcvActionsForMessage(mutatedArrivalRule, rcvMessage);

      // Rebuild the post-state of the mutated arrival rule
      rebuildPostState(mutatedArrivalRule);

      // Log and store the mutated arrival rule
      log.debug("Mutated arrival rule:\n{}", mutatedArrivalRule);
      localTheoryClone.add(mutatedArrivalRule);

      // Now mutate subsequent rules (the "next" states) in the chain
      mutateNextRules(localTheoryClone, mutatedArrivalRule);

      // Finally, add the fully mutated local theory to the global 'collections' if needed
      parametersBundle.getCollections().add(localTheoryClone);
    }
  }

  /**
   * Converts a "Snd" message Fact into an "Rcv" Fact for arrival rules.
   *
   * @param sndMessage the original "Snd" message Fact
   * @return a new "Rcv" Fact with the same parameters but modified name and type
   */
  private Fact buildRcvMessage(Fact sndMessage) {
    Fact rcvMessage = sndMessage.clone();
    rcvMessage.setF_name(rcvMessage.getF_name().replace("Snd", "Rcv"));
    rcvMessage.setType(TypeFact.PRE);
    return rcvMessage;
  }

  /**
   * Adds corresponding "Rcv" actions to the arrival rule for the third parameter of the message
   * (PSpecial or Value).
   *
   * @param arrivalRule the rule being mutated
   * @param rcvMessage the "Rcv" message Fact to process
   */
  private void addRcvActionsForMessage(Rule arrivalRule, Fact rcvMessage) {
    Value sender = (Value) rcvMessage.getParameter(0);
    Value receiver = (Value) rcvMessage.getParameter(1);
    Object contentObj = rcvMessage.getParameter(2);

    if (contentObj instanceof PSpecial) {
      for (Value val : ((PSpecial) contentObj).getGroup()) {
        Fact rcvAction = new Fact("Rcv");
        rcvAction.getParameters().add(receiver);
        rcvAction.getParameters().add(sender);
        rcvAction.getParameters().add(val);
        arrivalRule.addAction(rcvAction);
      }
    } else if (contentObj instanceof Variable) {
      // No specific logic was in the original snippet
    } else {
      Fact rcvAction = new Fact("Rcv");
      rcvAction.getParameters().add(receiver);
      rcvAction.getParameters().add(sender);
      rcvAction.getParameters().add(contentObj);
      arrivalRule.addAction(rcvAction);
    }
  }

  /**
   * Rebuilds the first postcondition as a new "State" derived from the preconditions. Removes any
   * old 'Snd' postconditions if found.
   *
   * @param arrivalRule the rule being mutated
   */
  private void rebuildPostState(Rule arrivalRule) {
    Fact preAgentState = arrivalRule.getPreconditionFactByMatchingName("State");
    if (preAgentState == null) {
      return; // Nothing to do if no state in preconditions
    }

    // Overwrite the first postcondition with a new state
    Fact newStateFact =
        utilityFunctions.buildNewState(arrivalRule.getPreconditions(), preAgentState);
    arrivalRule.getPostconditions().set(0, newStateFact);

    // If there's an old "Snd" postcondition, mark it removed
    Fact oldSndPost = arrivalRule.getPostconditionFactByMatchingName("Snd");
    if (oldSndPost != null) {
      oldSndPost.setRemoved(true);
    }
  }

  /**
   * Recursively mutates the "next" rules in the chain by updating pre- and post-states accordingly.
   *
   * @param localTheoryClone the cloned theory to mutate
   * @param mutatedArrivalRule the mutated arrival rule to process
   */
  private void mutateNextRules(ArrayList<Rule> localTheoryClone, Rule mutatedArrivalRule) {
    Fact stateFact = mutatedArrivalRule.getPostconditionFactByMatchingName("State");
    if (stateFact == null) {
      return;
    }

    // Extract agent and state number
    Value agentName = (Value) stateFact.getParameter(0);
    Value stateNumber = (Value) stateFact.getParameter(1);
    int stateIndex = Integer.parseInt(stateNumber.getName().replaceAll("[^a-zA-Z0-9]", ""));

    // Retrieve the next rule for (agentName, stateIndex)
    Rule next = nextRule(localTheoryClone, agentName.getName(), stateIndex);
    Fact newPreState = stateFact.clone();

    // Walk forward through the chain of "next" rules
    while (next != null) {
      next.setRule_name(next.getRule_name() + "_M");

      // Overwrite the pre-state of the "next" rule
      newPreState.setType(TypeFact.PRE);
      next.getPreconditions().set(0, newPreState);

      // Build a new post-state for that "next" rule
      Fact newPostState = utilityFunctions.buildNewState(next.getPreconditions(), newPreState);
      next.getPostconditions().set(0, newPostState);

      log.debug("Mutated next rule in chain:\n{}", next);

      // Get next agent & state for the next iteration
      Value agentValue = (Value) newPreState.getParameter(0);
      Value stateVal = (Value) newPreState.getParameter(1);
      int nextStateIndex = Integer.parseInt(stateVal.getName().replaceAll("[^a-zA-Z0-9]", "")) + 1;

      // Fetch the next rule in line
      next = nextRule(localTheoryClone, agentValue.getName(), nextStateIndex);
      if (next != null) {
        newPreState = next.getPostconditionFactByMatchingName("State").clone();
      }
    }
  }

  /**
   * Returns the next Rule in the chain for the given agent and state.
   *
   * @param theory the list of rules in the theory
   * @param agentName the name of the agent to match
   * @param stateIndex the index of the state to match
   * @return the next Rule that matches the agent and state, or null if not found
   */
  private Rule nextRule(ArrayList<Rule> theory, String agentName, int stateIndex) {
    // Stub logic for example only
    for (Rule r : theory) {

      // Skip mutated rules so we only follow the original chain
      if (r.getTypo() == Type.MUTATED) {
        continue;
      }

      Fact statePre = r.getPreconditionFactByMatchingName("State");
      if (statePre != null) {
        Value name = (Value) statePre.getParameter(0);
        Value num = (Value) statePre.getParameter(1);
        if (name.getName().equalsIgnoreCase(agentName)
            && num.getName().equals(String.valueOf(stateIndex))) {
          return r;
        }
      }
    }
    return null;
  }
}

package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.*;
import com.sermas.x.men.service.*;
import com.sermas.x.men.utilities.UtilityFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.sermas.x.men.utilities.*;

import java.util.*;

/**
 * ForgetMutationStrategy class. This class implements the mutation strategy for forgetting values
 * in rules. It handles the mutation process by removing forgotten values from rules, choosing
 * replacements, and propagating the mutations through the theory.
 */
@Slf4j
@Service
public class ForgetMutationStrategy implements MutationStrategy {

  @Autowired private DerivationCheckService derivationCheckService;
  @Autowired private SetupKnowledgeExtractor setupKnowledgeExtractor;
  @Autowired private DerivationService derivationService;
  @Autowired private UtilityFunctions utilityFunctions;

  /**
   * Applies the mutation strategy to a given rule.
   *
   * @param rule The rule to mutate.
   * @param rules The list of rules to consider for mutation.
   * @param parametersBundle The parameters bundle containing additional information for mutation.
   * @return The updated parameters bundle after applying the mutation.
   */
  @Override
  public ParametersBundle applyMutation(
      Rule rule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {

    // Setup knowledge extraction
    Map<String, String> setup = parametersBundle.getExistingSetupKnowledge();
    if (setup == null || setup.isEmpty()) {
      setup = setupKnowledgeExtractor.processProtocolModel(rules);
      parametersBundle.setExistingSetupKnowledge(setup);
    }

    ArrayList<Rule> theoryClone = deepCloneTheory(rules);

    // Forget mutation set for this rule
    Set<String> forgetSet = parametersBundle.getForgetMutationSet().get(rule.getRule_name());
    if (forgetSet == null || forgetSet.isEmpty()) return parametersBundle;

    // Handle the case where no forget values are provided
    for (String forgotten : forgetSet) {

      Message target = derivationCheckService.extractTargetFromRule(rule);
      Set<Message> knowledge = derivationCheckService.extractKnowledge(parametersBundle);

      log.info("Checking if target '{}' is derivable from current knowledge...", target);
      boolean derivable = derivationCheckService.isDerivable(target, knowledge);
      if (derivable) {
        log.info(
            "Target '{}' IS derivable from knowledge. Skipping mutation for forgotten value '{}'.",
            target,
            forgotten);
        continue;
      } else {
        log.info(
            "Target '{}' is NOT derivable from knowledge. Proceeding with mutation for forgotten value '{}'.",
            target,
            forgotten);
      }

      Rule startRule = findRuleByName(theoryClone, rule.getRule_name());
      if (startRule == null) continue;

      startRule.setRule_name(startRule.getRule_name() + "_M");
      startRule.setTypo(Type.MUTATED);

      // Remove Forget mutation from post-conditions
      removeForgetMutation(startRule, forgotten);

      // Choose a replacement value for the forgotten value
      String replacement = chooseReplacement(forgotten, setup);

      // Replace forgotten value with a new one pulled out from the knowledge
      replaceValue(
          startRule,
          forgotten,
          replacement,
          /* mutatePreState */ false,
          /* mutateRcvS     */ false);

      // Propagate the mutation through the theory
      propagateMutation(theoryClone, startRule, forgotten, replacement, parametersBundle);
    }

    parametersBundle.getCollections().add(theoryClone);
    return parametersBundle;
  }

  /**
   * Propagates the mutation through the theory.
   *
   * @param theory the theory to mutate
   * @param startRule the rule where the mutation starts
   * @param forgotten the value to forget
   * @param replacement the value to replace the forgotten value with
   * @param bundle the parameters bundle containing additional information for mutation
   */
  private void propagateMutation(
      ArrayList<Rule> theory,
      Rule startRule,
      String forgotten,
      String replacement,
      ParametersBundle bundle) {

    boolean seenStart = false;

    for (Rule r : theory) {

      // Skip rules that are not mutated or are not the start rule
      if (!seenStart) {
        seenStart = r.getRule_name().equals(startRule.getRule_name());
        continue;
      }

      // Set the rule name and type for mutation
      r.setRule_name(r.getRule_name() + "_M");
      r.setTypo(Type.MUTATED);

      replaceValue(r, forgotten, replacement, /* mutatePreState */ true, /* mutateRcvS     */ true);

      if (checkValueReceived(r, forgotten)) {
        restoreWhenReceived(r, forgotten, replacement);
        bundle.getForgetMutationSet().values().forEach(set -> set.remove(forgotten));
        break; // hard stop
      }

      replaceValue(r, forgotten, replacement, /* mutatePreState */ true, /* mutateRcvS     */ true);

      // Check if the forgotten value is received in this rule
      if (checkValueReceived(r, forgotten)) {
        restoreWhenReceived(r, forgotten, replacement);
        bundle.getForgetMutationSet().values().forEach(s -> s.remove(forgotten));
        break; // halt propagation
      }
    }
  }

  /**
   * Removes the Forget mutation from the rule's postconditions and updates the State fact.
   *
   * @param rule the rule from which to remove the Forget mutation
   * @param forgotten the value that was forgotten
   */
  private void removeForgetMutation(Rule rule, String forgotten) {

    rule.getPostconditions()
        .removeIf(f -> "Forget".equals(f.getF_name()) && f.getParameters().contains(forgotten));

    rule.getPostconditions().stream()
        .filter(f -> "State".equals(f.getF_name()))
        .forEach(f -> removeInFact(f, forgotten));
  }

  /**
   * Replaces the forgotten value with a replacement value in the rule's facts.
   *
   * @param rule the rule in which to replace the value
   * @param forgotten the value to be forgotten
   * @param replacement the value to replace the forgotten value with
   * @param mutatePreState whether to mutate preconditions
   * @param mutateRcvS whether to mutate the RcvS fact
   */
  private void replaceValue(
      Rule rule, String forgotten, String replacement, boolean mutatePreState, boolean mutateRcvS) {

    rule.getPostconditions().forEach(f -> replaceInFact(f, forgotten, replacement));

    rule.getActions()
        .forEach(
            f -> {
              if (!"Receive".equals(f.getF_name())) {
                replaceInFact(f, forgotten, replacement);
              }
            });

    if (mutatePreState) {
      rule.getPreconditions()
          .forEach(
              f -> {
                if (!"RcvS".equals(f.getF_name()) || mutateRcvS) {
                  replaceInFact(f, forgotten, replacement);
                }
              });
    }
  }

  /**
   * Restores the forgotten value in the State and SndS facts when it is received.
   *
   * @param rule the rule in which to restore the forgotten value
   * @param forgotten the value that was forgotten
   * @param replacement the value that replaced the forgotten value
   */
  private void restoreWhenReceived(Rule rule, String forgotten, String replacement) {

    rule.getPostconditions().stream()
        .filter(f -> "State".equals(f.getF_name()))
        .map(
            f ->
                f.getParameters().stream()
                    .filter(p -> p instanceof PSpecial)
                    .map(p -> (PSpecial) p)
                    .findFirst()
                    .orElse(null))
        .filter(Objects::nonNull)
        .forEach(
            ps -> {
              ps.getGroup().removeIf(v -> replacement.equals(v.getName()));
              if (ps.getGroup().stream().noneMatch(v -> forgotten.equals(v.getName()))) {
                ps.getGroup().add(new Value(forgotten));
              }
            });

    rule.getPostconditions().stream()
        .filter(f -> "SndS".equals(f.getF_name()))
        .forEach(f -> replaceInFact(f, replacement, forgotten));
  }

  /**
   * Checks if the forgotten value is received in the rule's preconditions or actions.
   *
   * @param rule the rule to check
   * @param forgotten the value that was forgotten
   * @return true if the forgotten value is received, false otherwise
   */
  private boolean checkValueReceived(Rule rule, String forgotten) {

    boolean inRcvS =
        rule.getPreconditions().stream()
            .filter(f -> "RcvS".equals(f.getF_name()))
            .flatMap(f -> f.getParameters().stream())
            .filter(p -> p instanceof PSpecial)
            .map(p -> (PSpecial) p)
            .flatMap(ps -> ps.getGroup().stream())
            .anyMatch(v -> forgotten.equals(v.getName()));

    boolean inReceive =
        rule.getActions().stream()
            .filter(f -> "Receive".equals(f.getF_name()))
            .flatMap(f -> f.getParameters().stream())
            .anyMatch(p -> p instanceof String s && s.equals(forgotten));

    return inRcvS || inReceive;
  }

  /**
   * Removes the forgotten value from the parameters of a fact.
   *
   * @param fact the fact from which to remove the forgotten value
   * @param forgotten the value that was forgotten
   */
  private void removeInFact(Fact fact, String forgotten) {

    fact.getParameters()
        .forEach(
            p -> {
              if (p instanceof PSpecial ps) {
                ps.getGroup().removeIf(v -> forgotten.equals(v.getName()));
              }
            });
  }

  /**
   * Replaces the forgotten value with a replacement value in a fact.
   *
   * @param fact the fact in which to replace the value
   * @param forgotten the value that was forgotten
   * @param replacement the value to replace the forgotten value with
   */
  private void replaceInFact(Fact fact, String forgotten, String replacement) {

    List<Object> params = fact.getParameters();

    for (Object param : params) {
      if (param instanceof PSpecial) {
        deepReplaceInPSpecial((PSpecial) param, forgotten, replacement);
      }
    }
  }

  /**
   * Recursively replaces the forgotten value in a PSpecial object.
   *
   * @param ps the PSpecial object in which to replace the value
   * @param forgotten the value that was forgotten
   * @param replacement the value to replace the forgotten value with
   */
  private void deepReplaceInPSpecial(PSpecial ps, String forgotten, String replacement) {

    List<Value> group = ps.getGroup();

    for (int i = 0; i < group.size(); i++) {
      Object e = group.get(i);

      if (e instanceof PSpecial) {
        deepReplaceInPSpecial((PSpecial) e, forgotten, replacement);

      } else if (e instanceof Value) {
        Value v = (Value) e;
        if (forgotten.equals(v.getName())) v.setName(replacement);

      } else if (e instanceof String) {
        if (forgotten.equals(e)) group.set(i, new Value(replacement));
      }
    }
  }

  /**
   * Deep clones the theory of rules.
   *
   * @param src the source rules to clone
   * @return a new ArrayList containing cloned rules
   */
  private ArrayList<Rule> deepCloneTheory(ArrayList<Rule> src) {
    ArrayList<Rule> out = new ArrayList<>();
    src.forEach(r -> out.add(r.clone()));
    return out;
  }

  /**
   * Finds a rule by its name in the list of rules.
   *
   * @param rules the list of rules to search
   * @param name the name of the rule to find
   * @return the Rule object if found, null otherwise
   */
  private Rule findRuleByName(ArrayList<Rule> rules, String name) {
    return rules.stream().filter(r -> name.equals(r.getRule_name())).findFirst().orElse(null);
  }

  /**
   * Chooses a replacement value for the forgotten value based on the setup knowledge.
   *
   * @param forgotten the value that was forgotten
   * @param setup the setup knowledge map containing values and their types
   * @return the replacement value for the forgotten value
   */
  private String chooseReplacement(String forgotten, Map<String, String> setup) {
    String type = setup.get(forgotten);
    return setup.entrySet().stream()
        .filter(e -> e.getValue().equals(type) && !e.getKey().equals(forgotten))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(forgotten + "_rep");
  }
}

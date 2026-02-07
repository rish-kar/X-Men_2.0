package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.*;
import com.sermas.x.men.service.*;
import com.sermas.x.men.utilities.*;
import com.sermas.x.men.utilities.UtilityFunctions;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    // Ensure original rules are in collections for knowledge extraction
    if (parametersBundle.getCollections() == null) {
      parametersBundle.setCollections(new ArrayList<>());
    }
    // Store original rules temporarily for knowledge extraction
    ArrayList<ArrayList> tempCollections = new ArrayList<>();
    tempCollections.add(rules);
    ParametersBundle knowledgeBundle = new ParametersBundle();
    knowledgeBundle.setCollections(tempCollections);
    knowledgeBundle.setForgetMutationSet(parametersBundle.getForgetMutationSet());

    // Handle the case where no forget values are provided
    for (String forgottenOriginal : forgetSet) {
      String forgotten = canonicalize(forgottenOriginal);

      // Set the current rule name in parametersBundle for knowledge extraction
      knowledgeBundle.addExtraContent("currentRuleName", rule.getRule_name());

      Message target = derivationCheckService.extractTargetFromRule(rule);
      Set<Message> knowledge = derivationCheckService.extractKnowledge(knowledgeBundle);

      log.info("Checking if target '{}' is derivable from current knowledge...", target);
      boolean derivable = derivationCheckService.isDerivable(target, knowledge, parametersBundle.getDerivationType(), parametersBundle.getDerivationDepth());
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

      // Remove Forget mutation from actions
      removeForgetMutation(startRule, forgotten);

      // Choose a replacement value of same type
      String replacement = chooseReplacement(forgotten, setup);

      // Apply replacement in the mutated rule (not touching State)
      replaceValue(
          startRule,
          forgotten,
          replacement, /* mutatePreState */
          false, /* mutateRcvS */
          false, /* includeStatePost */
          false);

      // Propagate to subsequent non-human rules and adjust access decision if needed
      propagateMutation(theoryClone, startRule, forgotten, replacement, setup);
    }

    parametersBundle.getCollections().add(theoryClone);
    return parametersBundle;
  }

  // Normalize names like "~p1" -> "p1"
  private String canonicalize(String name) {
    return name != null && name.startsWith("~") ? name.substring(1) : name;
  }

  /**
   * Propagates the mutation through the theory.
   *
   * @param theory the theory to mutate
   * @param startRule the rule where the mutation starts
   * @param forgotten the value to forget
   * @param replacement the value to replace the forgotten value with
   * @param setup the setup knowledge map containing values and their types
   */
  private void propagateMutation(
      ArrayList<Rule> theory,
      Rule startRule,
      String forgotten,
      String replacement,
      Map<String, String> setup) {

    int startIndex = -1;
    for (int i = 0; i < theory.size(); i++) {
      if (theory.get(i).getRule_name().equals(startRule.getRule_name())) {
        startIndex = i;
        break;
      }
    }
    if (startIndex < 0) return;

    for (int i = startIndex + 1; i < theory.size(); i++) {
      Rule r = theory.get(i);

      r.setRule_name(r.getRule_name() + "_M");
      r.setTypo(Type.MUTATED);

      if (r.isHuman()) {
        continue;
      }

      // In subsequent non-human rules, also update State facts
      replaceValue(
          r,
          forgotten,
          replacement, /* mutatePreState */
          true, /* mutateRcvS */
          true, /* includeStatePost */
          true);

      adjustAccessDecision(theory, i, r, replacement, setup);
    }
  }

  // Flip access=Denied->Granted in current rule (Send/SndS) and in the next rule's matching RcvS
  private void adjustAccessDecision(
      ArrayList<Rule> theory,
      int currentIndex,
      Rule rule,
      String replacement,
      Map<String, String> setup) {
    try {
      String type = setup.get(replacement);
      if (!"password".equalsIgnoreCase(type)) return;

      boolean receivedReplacement =
          rule.getPreconditions().stream()
              .filter(f -> "RcvS".equals(f.getF_name()))
              .flatMap(f -> f.getParameters().stream())
              .filter(p -> p instanceof PSpecial)
              .map(p -> (PSpecial) p)
              .flatMap(ps -> ps.getGroup().stream())
              .anyMatch(v -> replacement.equals(v.getName()));

      if (!receivedReplacement) return;

      // Flip any Send(_, 'access', 'Denied') -> 'Granted'
      for (Fact act : rule.getActions()) {
        if (!"Send".equals(act.getF_name()) || act.getParameters().size() < 3) continue;
        Object a1 = act.getParameters().get(1), a2 = act.getParameters().get(2);
        if (a1 instanceof Value va1
            && "'access'".equals(va1.getName())
            && a2 instanceof Value va2
            && "'Denied'".equals(va2.getName())) {
          va2.setName("'Granted'");
        }
      }

      // Flip any SndS(_,_, <'access'>, <'Denied'>) -> 'Granted' (robust to quotes/HTML)
      for (Fact post : rule.getPostconditions()) {
        if (!"SndS".equals(post.getF_name())) continue;
        Object values = post.getParameters().get(2);
        if (values instanceof PSpecial vp) {
          for (Value v : vp.getGroup()) {
            String n = normalizeAlpha(v.getName());
            if ("denied".equalsIgnoreCase(n)) {
              v.setName("'Granted'");
            }
          }
        } else if (values instanceof Value v) {
          String n = normalizeAlpha(v.getName());
          if ("denied".equalsIgnoreCase(n)) {
            v.setName("'Granted'");
          }
        }
      }

      // Propagation logic: Also update the next rule's RcvS to receive 'Granted' instead of
      // 'Denied'
      if (currentIndex + 1 < theory.size()) {
        Rule nextRule = theory.get(currentIndex + 1);
        for (Fact pre : nextRule.getPreconditions()) {
          if (!"RcvS".equals(pre.getF_name())) continue;
          // Handle both parameter structures
          for (int paramIndex = 0; paramIndex < pre.getParameters().size(); paramIndex++) {
            Object param = pre.getParameters().get(paramIndex);
            if (param instanceof PSpecial ps) {
              // Check if this PSpecial contains access-related values
              boolean hasAccess =
                  ps.getGroup().stream().anyMatch(v -> "'access'".equals(v.getName()));
              if (hasAccess) {
                // Find the corresponding values parameter
                if (paramIndex + 1 < pre.getParameters().size()) {
                  Object valuesParam = pre.getParameters().get(paramIndex + 1);
                  if (valuesParam instanceof PSpecial vp) {
                    vp.getGroup().stream()
                        .filter(v -> "'Denied'".equals(v.getName()))
                        .forEach(v -> v.setName("'Granted'"));
                  }
                }
              } else {
                // Direct replacement in the same PSpecial group
                ps.getGroup().stream()
                    .filter(v -> "'Denied'".equals(v.getName()))
                    .forEach(v -> v.setName("'Granted'"));
              }
            }
          }
        }

        // Update actions in next rule to handle 'Granted'
        for (Fact act : nextRule.getActions()) {
          if ("Receive".equals(act.getF_name()) || "Commit".equals(act.getF_name())) {
            for (Object param : act.getParameters()) {
              if (param instanceof Value v && "'Denied'".equals(v.getName())) {
                v.setName("'Granted'");
              }
            }
          }
        }

        // Update State postconditions to store 'Granted' instead of 'Denied'
        for (Fact post : nextRule.getPostconditions()) {
          if ("State".equals(post.getF_name()) && post.getParameters().size() >= 3) {
            Object stateData = post.getParameters().get(2);
            if (stateData instanceof PSpecial ps) {
              ps.getGroup().stream()
                  .filter(v -> "'Denied'".equals(v.getName()))
                  .forEach(v -> v.setName("'Granted'"));
            }
          }
        }
      }

    } catch (Exception e) {
      log.warn("adjustAccessDecision failed: {}", e.getMessage());
    }
  }

  private String normalizeAlpha(String s) {
    if (s == null) return null;
    // Drop common HTML tokens first, then non-letters
    String t =
        s.replace("&apos;", "").replace("&quot;", "").replace("apos", "").replace("quot", "");
    return t.replaceAll("[^A-Za-z]", "");
  }

  /**
   * Removes the Forget mutation from the rule's postconditions and updates the State fact.
   *
   * @param rule the rule from which to remove the Forget mutation
   * @param forgotten the value that was forgotten
   */
  private void removeForgetMutation(Rule rule, String forgotten) {

    // Remove Forget(...) from actions to keep generated syntax valid
    rule.getActions().removeIf(f -> "Forget".equals(f.getF_name()) && containsParam(f, forgotten));

    // Do not alter State knowledge
  }

  // Compare canonical names inside Value and PSpecial
  private boolean containsParam(Fact fact, String name) {
    for (Object p : fact.getParameters()) {
      if (p instanceof Value v && canonicalize(v.getName()).equals(name)) return true;
      if (p instanceof PSpecial ps) {
        for (Value v : ps.getGroup()) if (canonicalize(v.getName()).equals(name)) return true;
      }
    }
    return false;
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
      Rule rule,
      String forgotten,
      String replacement,
      boolean mutatePreState,
      boolean mutateRcvS,
      boolean includeStatePost) {

    // Replace in postconditions; include State only when requested
    rule.getPostconditions()
        .forEach(
            f -> {
              if (includeStatePost || !"State".equals(f.getF_name())) {
                replaceInFact(f, forgotten, replacement);
              }
            });

    // Replace in all actions, including Receive
    rule.getActions().forEach(f -> replaceInFact(f, forgotten, replacement));

    if (mutatePreState) {
      rule.getPreconditions()
          .forEach(
              f -> {
                if (!"RcvS".equals(f.getF_name()) || mutateRcvS)
                  replaceInFact(f, forgotten, replacement);
              });
    }
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
    for (Object param : fact.getParameters()) {
      if (param instanceof PSpecial) {
        deepReplaceInPSpecial((PSpecial) param, forgotten, replacement);
      } else if (param instanceof Value v) {
        if (canonicalize(v.getName()).equals(forgotten)) v.setName(replacement);
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
    for (Value v : ps.getGroup()) {
      if (canonicalize(v.getName()).equals(forgotten)) v.setName(replacement);
    }
  }

  /**
   * Adjusts the access decision in the rule based on the replacement value.
   *
   * @param rule the rule in which to adjust the access decision
   * @param replacement the value that replaced the forgotten value
   * @param setup the setup knowledge map containing values and their types
   */
  private void adjustAccessDecision(Rule rule, String replacement, Map<String, String> setup) {
    try {
      String type = setup.get(replacement);

      boolean receivedReplacement =
          rule.getPreconditions().stream()
              .filter(f -> "RcvS".equals(f.getF_name()))
              .flatMap(f -> f.getParameters().stream())
              .filter(p -> p instanceof PSpecial)
              .map(p -> (PSpecial) p)
              .flatMap(ps -> ps.getGroup().stream())
              .anyMatch(v -> replacement.equals(v.getName()));

      // Flip any Send(_, 'access', 'Denied') -> 'Granted'
      for (Fact act : rule.getActions()) {
        if (!"Send".equals(act.getF_name()) || act.getParameters().size() < 3) continue;
        Object a1 = act.getParameters().get(1), a2 = act.getParameters().get(2);
        if (a1 instanceof Value va1
            && "access".equals(va1.getName())
            && a2 instanceof Value va2
            && "Denied".equals(va2.getName())) {
          va2.setName("'Granted'");
        }
      }

      // Flip any SndS(_,_, <'access'>, <'Denied'>) -> 'Granted'
      for (Fact post : rule.getPostconditions()) {
        if (!"SndS".equals(post.getF_name()) || post.getParameters().size() < 4) continue;
        Object labels = post.getParameters().get(2);
        Object values = post.getParameters().get(3);
        if (labels instanceof PSpecial lp && values instanceof PSpecial vp) {
          boolean hasAccess = lp.getGroup().stream().anyMatch(v -> v.getName().contains("access"));
          if (hasAccess) {
            vp.getGroup()
                .forEach(
                    v -> {
                      if (v.getName().contains("Denied")) v.setName("'Granted'");
                    });
          }
        }
      }

      for (Fact pre : rule.getPreconditions()) {
        if (!"RcvS".equals(pre.getF_name()) || pre.getParameters().size() < 4) continue;
        Object lbl = pre.getParameters().get(2);
        Object val = pre.getParameters().get(3);
        if (lbl instanceof PSpecial lp && val instanceof PSpecial vp) {
          boolean hasAccessPre =
              lp.getGroup().stream().anyMatch(v -> v.getName().contains("access"));
          if (hasAccessPre) {
            vp.getGroup()
                .forEach(
                    v -> {
                      if (v.getName().contains("Denied")) v.setName("'Granted'");
                    });
          }
        }
      }
    } catch (Exception e) {
      log.warn("adjustAccessDecision failed: {}", e.getMessage());
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
        .filter(e -> Objects.equals(e.getValue(), type) && !Objects.equals(e.getKey(), forgotten))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(forgotten + "_rep");
  }
}

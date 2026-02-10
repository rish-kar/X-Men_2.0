package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.*;
import com.sermas.x.men.service.*;
import com.sermas.x.men.service.forget.*;
import com.sermas.x.men.service.forget.ForgetContext.BlockingMode;
import com.sermas.x.men.utilities.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ForgetMutationStrategy implements Algorithm 1 from the paper:
 * - Maintains K (knowledge) as monotonic (never shrinks)
 * - Tracks Forget set separately
 * - Checks for unblocked derivations before mutating
 * - Generates variants by replacing blocked hypotheses
 * - Removes send + matching receive when no variants exist
 *
 * Supports three blocking cases:
 * - Case 1 (weak): h blocked if h ∈ Forget
 * - Case 2 (medium): h blocked if derivable from Forget via pairing rules only
 * - Case 3 (strong): h blocked if derivable from Forget via all DY rules
 */
@Slf4j
@Service
public class ForgetMutationStrategy implements MutationStrategy {

  @Autowired private DerivationCheckService derivationCheckService;
  @Autowired private SetupKnowledgeExtractor setupKnowledgeExtractor;
  @Autowired private DerivationService derivationService;
  @Autowired private UtilityFunctions utilityFunctions;
  @Autowired private ForgetDerivationChecker forgetDerivationChecker;
  @Autowired private BlockingChecker blockingChecker;
  @Autowired private ReplacementComputer replacementComputer;

  // Default blocking mode - can be configured
  private static final BlockingMode DEFAULT_BLOCKING_MODE = BlockingMode.CASE1_WEAK;
  private static final int MAX_VARIANTS_PER_RULE = 10;

  /**
   * Applies the forget mutation strategy according to Algorithm 1.
   *
   * Algorithm 1 (per-derivation processing):
   * 1. For each derivation π of m2 from K':
   *    - Compute Hyp_π (hypotheses used in π)
   *    - Compute blocked_π = { h ∈ Hyp_π : h is blocked by Forget' }
   *    - If blocked_π is empty → unblocked derivation exists, stop (keep send unchanged)
   *    - Else compute replacement sets R_h for each h ∈ blocked_π
   *    - If any R_h is empty → skip this derivation (cannot repair)
   *    - Else generate variants via cartesian product and accumulate
   * 2. After all derivations:
   *    - If accumulated variants is empty → remove send + matching receive
   *    - Else produce one mutant per variant (up to MAX_VARIANTS)
   *
   * @param rule The rule to mutate.
   * @param rules The list of rules to consider for mutation.
   * @param parametersBundle The parameters bundle containing additional information for mutation.
   * @return The updated parameters bundle after applying the mutation.
   */
  @Override
  public ParametersBundle applyMutation(
      Rule rule, ArrayList<Rule> rules, ParametersBundle parametersBundle) {

    // Configure derivation checker with user-defined functions
    // This prevents exponential blow-up by treating user-defined functions as opaque
    if (parametersBundle.getFunctions() != null && !parametersBundle.getFunctions().isEmpty()) {
      forgetDerivationChecker.configureUserDefinedFunctions(parametersBundle.getFunctions());
    }

    // Setup knowledge extraction (type mapping)
    Map<String, String> setup = parametersBundle.getExistingSetupKnowledge();
    if (setup == null || setup.isEmpty()) {
      setup = setupKnowledgeExtractor.processProtocolModel(rules);
      parametersBundle.setExistingSetupKnowledge(setup);
    }

    // Get forget targets for this rule
    Set<String> forgetSet = parametersBundle.getForgetMutationSet().get(rule.getRule_name());
    if (forgetSet == null || forgetSet.isEmpty()) {
      return parametersBundle;
    }

    // Prepare knowledge bundle for extraction
    if (parametersBundle.getCollections() == null) {
      parametersBundle.setCollections(new ArrayList<>());
    }
    ArrayList<ArrayList> tempCollections = new ArrayList<>();
    tempCollections.add(rules);
    ParametersBundle knowledgeBundle = new ParametersBundle();
    knowledgeBundle.setCollections(tempCollections);
    knowledgeBundle.setForgetMutationSet(parametersBundle.getForgetMutationSet());
    knowledgeBundle.addExtraContent("currentRuleName", rule.getRule_name());

    // Extract full knowledge K (without removing forgotten items - per Algorithm 1)
    Set<Message> fullKnowledge = derivationCheckService.extractKnowledgeWithoutForgetRemoval(knowledgeBundle);

    // Build ForgetContext with K and Forget set
    ForgetContext ctx = buildForgetContext(fullKnowledge, forgetSet, setup);

    log.info("Algorithm 1 - Processing rule {} with {} forget targets",
             rule.getRule_name(), forgetSet.size());
    log.info("Knowledge K: {}", ctx.getKnowledge().stream().map(Message::represent).toList());
    log.info("Forget set: {}", ctx.getForgetSet().stream().map(Message::represent).toList());
    log.info("Blocking mode: {}", ctx.getBlockingMode());

    // Extract target message (m2 - the send message)
    Message target = derivationCheckService.extractTargetFromRule(rule);
    if (target == null) {
      log.warn("No target message found for rule {}", rule.getRule_name());
      return parametersBundle;
    }

    log.info("Target message m2: {}", target.represent());

    // Get ALL derivations for the target
    Set<Derivation> allDerivations = forgetDerivationChecker.getAllDerivations(target, ctx.getKnowledge());

    // FIX 1: Per Algorithm 1, if Π is empty, we treat this as "no unblocked derivation and no variants"
    // Therefore we MUST delete send + matching receive (not just remove Forget and return unchanged)
    if (allDerivations.isEmpty()) {
      log.warn("No derivations found for target {} - per Algorithm 1, must delete send + matching receive", target.represent());
      ArrayList<Rule> theoryClone = deepCloneTheory(rules);
      Rule startRule = findRuleByName(theoryClone, rule.getRule_name());
      if (startRule != null) {
        // Remove send and matching receive per Algorithm 1
        removeSendAndMatchingReceive(theoryClone, startRule, target);

        // Remove Forget actions
        for (String forgotten : forgetSet) {
          removeForgetMutation(startRule, canonicalize(forgotten));
        }

        startRule.setRule_name(startRule.getRule_name() + "_M");
        startRule.setTypo(Type.MUTATED);
      }
      parametersBundle.getCollections().add(theoryClone);
      return parametersBundle;
    }

    log.info("Found {} derivations for target {}", allDerivations.size(), target.represent());

    // ===== Algorithm 1: Per-derivation processing =====
    List<VariantInfo> accumulatedVariants = new ArrayList<>();
    boolean foundUnblockedDerivation = false;

    for (Derivation derivation : allDerivations) {
      // 1. Compute hypotheses for this derivation
      Set<Message> hypotheses = blockingChecker.extractHypotheses(derivation);
      log.debug("Derivation hypotheses: {}", hypotheses.stream().map(Message::represent).toList());

      // 2. Compute blocked hypotheses for THIS derivation only
      Set<Message> blockedHypotheses = new LinkedHashSet<>();
      for (Message h : hypotheses) {
        if (blockingChecker.isBlocked(h, ctx)) {
          blockedHypotheses.add(h);
        }
      }

      // 3. If no blocked hypotheses → unblocked derivation exists!
      if (blockedHypotheses.isEmpty()) {
        log.info("Found unblocked derivation for target {} - skipping mutation", target.represent());
        foundUnblockedDerivation = true;
        break;
      }

      log.debug("Derivation has {} blocked hypotheses: {}",
               blockedHypotheses.size(),
               blockedHypotheses.stream().map(Message::represent).toList());

      // 4. Compute replacement sets for THIS derivation's blocked hypotheses
      Map<Message, Set<Message>> blockedToReplacements = new LinkedHashMap<>();
      boolean anyEmptyReplacementSet = false;

      for (Message blocked : blockedHypotheses) {
        Set<Message> replacements = replacementComputer.computeReplacementSet(blocked, ctx);
        blockedToReplacements.put(blocked, replacements);

        if (replacements.isEmpty()) {
          log.debug("Empty replacement set for blocked hypothesis {} - skipping this derivation",
                   blocked.represent());
          anyEmptyReplacementSet = true;
          break; // This derivation cannot be repaired
        }
      }

      // 5. If any replacement set is empty, skip this derivation (but continue to next)
      if (anyEmptyReplacementSet) {
        continue;
      }

      // 6. Generate variants for this derivation via cartesian product
      List<Message> variants = replacementComputer.generateVariants(target, blockedToReplacements);

      for (Message variant : variants) {
        if (accumulatedVariants.size() >= MAX_VARIANTS_PER_RULE) {
          log.info("Reached maximum variants limit ({})", MAX_VARIANTS_PER_RULE);
          break;
        }
        accumulatedVariants.add(new VariantInfo(variant, blockedToReplacements));
      }

      if (accumulatedVariants.size() >= MAX_VARIANTS_PER_RULE) {
        break;
      }
    }

    // ===== Post-processing based on Algorithm 1 results =====

    if (foundUnblockedDerivation) {
      // Unblocked derivation exists - keep send unchanged, just remove Forget action
      ArrayList<Rule> theoryClone = deepCloneTheory(rules);
      Rule startRule = findRuleByName(theoryClone, rule.getRule_name());
      if (startRule != null) {
        for (String forgotten : forgetSet) {
          removeForgetMutation(startRule, canonicalize(forgotten));
        }
      }
      parametersBundle.getCollections().add(theoryClone);
      return parametersBundle;
    }

    if (accumulatedVariants.isEmpty()) {
      // No variants from any derivation - remove send and matching receive
      log.info("No variants possible from any derivation - removing send and matching receive");
      ArrayList<Rule> theoryClone = deepCloneTheory(rules);
      Rule startRule = findRuleByName(theoryClone, rule.getRule_name());
      if (startRule != null) {
        removeSendAndMatchingReceive(theoryClone, startRule, target);
        for (String forgotten : forgetSet) {
          removeForgetMutation(startRule, canonicalize(forgotten));
        }
        startRule.setRule_name(startRule.getRule_name() + "_M");
        startRule.setTypo(Type.MUTATED);
      }
      parametersBundle.getCollections().add(theoryClone);
      return parametersBundle;
    }

    // Generate one mutant per variant (up to MAX_VARIANTS)
    log.info("Generating {} mutants from accumulated variants", accumulatedVariants.size());

    for (int i = 0; i < accumulatedVariants.size(); i++) {
      VariantInfo variantInfo = accumulatedVariants.get(i);
      ArrayList<Rule> theoryClone = deepCloneTheory(rules);
      Rule startRule = findRuleByName(theoryClone, rule.getRule_name());

      if (startRule == null) {
        log.error("Could not find rule {} in theory clone", rule.getRule_name());
        continue;
      }

      // Apply the variant by replacing the target in send facts
      applyVariantToRule(startRule, target, variantInfo.variant, variantInfo.blockedToReplacements);

      String suffix = accumulatedVariants.size() > 1 ? "_M" + i : "_M";
      startRule.setRule_name(startRule.getRule_name() + suffix);
      startRule.setTypo(Type.MUTATED);

      // Remove Forget actions
      for (String forgotten : forgetSet) {
        removeForgetMutation(startRule, canonicalize(forgotten));
      }

      // Propagate changes to subsequent rules
      propagateMutationWithVariant(theoryClone, startRule, variantInfo.blockedToReplacements, setup);

      parametersBundle.getCollections().add(theoryClone);
    }

    return parametersBundle;
  }

  /**
   * Helper class to store variant information with its replacement map.
   */
  private static class VariantInfo {
    final Message variant;
    final Map<Message, Set<Message>> blockedToReplacements;

    VariantInfo(Message variant, Map<Message, Set<Message>> blockedToReplacements) {
      this.variant = variant;
      this.blockedToReplacements = blockedToReplacements;
    }
  }

  /**
   * Builds a ForgetContext from the extracted knowledge, forget set strings, and type map.
   */
  private ForgetContext buildForgetContext(Set<Message> knowledge, Set<String> forgetStrings,
                                           Map<String, String> typeMap) {
    Set<Message> forgetMessages = new LinkedHashSet<>();

    // Convert forget strings to Messages
    for (String forgottenStr : forgetStrings) {
      String canonical = canonicalize(forgottenStr);
      // Try to find matching message in knowledge
      Message found = findMessageByRepresentation(knowledge, canonical);
      if (found != null) {
        forgetMessages.add(found);
      } else {
        // Create as Atom if not found
        forgetMessages.add(new Atom(canonical));
      }
    }

    return new ForgetContext(knowledge, forgetMessages, DEFAULT_BLOCKING_MODE, typeMap);
  }

  /**
   * Finds a message in a set by its string representation.
   */
  private Message findMessageByRepresentation(Set<Message> messages, String repr) {
    for (Message m : messages) {
      String mRepr = m.represent();
      // Check direct match or with ~ prefix
      if (mRepr.equals(repr) || mRepr.equals("~" + repr) ||
          ("~" + mRepr).equals(repr) || mRepr.replace("~", "").equals(repr)) {
        return m;
      }
    }
    return null;
  }

  /**
   * Applies a variant message to the rule by replacing the target in send facts.
   *
   * FIX B: Now properly uses the variant message structure. The variant contains
   * the substituted message with blocked hypotheses replaced. We extract the
   * substitutions from comparing originalTarget to variant and apply them.
   */
  private void applyVariantToRule(Rule rule, Message originalTarget, Message variant,
                                  Map<Message, Set<Message>> blockedToReplacements) {
    // Build substitution from blocked to the specific replacement used in this variant
    // We determine the actual substitution by traversing the variant structure
    Map<String, String> stringSubstitution = buildSubstitutionFromVariant(
        originalTarget, variant, blockedToReplacements);

    // Apply substitutions to postconditions (SndS facts)
    for (Fact post : rule.getPostconditions()) {
      if ("SndS".equals(post.getF_name())) {
        applyStringSubstitutionToFact(post, stringSubstitution);
      }
    }

    // Apply to actions (Send facts)
    for (Fact action : rule.getActions()) {
      if ("Send".equals(action.getF_name())) {
        applyStringSubstitutionToFact(action, stringSubstitution);
      }
    }
  }

  /**
   * Builds a substitution map by comparing the original message to the variant.
   * This handles the case where different variants have different replacement choices.
   */
  private Map<String, String> buildSubstitutionFromVariant(
      Message original, Message variant, Map<Message, Set<Message>> blockedToReplacements) {

    Map<String, String> substitution = new HashMap<>();

    // If original and variant are the same, no substitution needed
    if (original == null || variant == null) {
      return substitution;
    }

    if (original.equals(variant)) {
      return substitution;
    }

    // Compare structures to find substitutions
    findSubstitutionsRecursive(original, variant, substitution);

    // Also add explicit blocked -> replacement mappings for any that weren't found structurally
    // This handles cases where the variant was constructed by ReplacementComputer
    for (Map.Entry<Message, Set<Message>> entry : blockedToReplacements.entrySet()) {
      Message blocked = entry.getKey();
      String blockedRepr = blocked.represent();

      // If this blocked term appears in variant differently, find the replacement
      if (!substitution.containsKey(blockedRepr) && !substitution.containsKey(canonicalize(blockedRepr))) {
        // Check if variant contains any of the replacements for this blocked term
        for (Message replacement : entry.getValue()) {
          String replacementRepr = replacement.represent();
          if (variant.represent().contains(replacementRepr)) {
            substitution.put(blockedRepr, replacementRepr);
            substitution.put(canonicalize(blockedRepr), replacementRepr);
            break;
          }
        }
      }
    }

    return substitution;
  }

  /**
   * Recursively finds substitutions by comparing message structures.
   *
   * FIX 3: Do NOT recurse into function arguments. If a function like bal($oyster)
   * differs from bal($ccard), we record the ENTIRE function substitution, not the
   * inner argument. This ensures we only replace blocked hypotheses, not their
   * occurrences inside other terms.
   */
  private void findSubstitutionsRecursive(Message original, Message variant, Map<String, String> substitution) {
    if (original == null || variant == null) return;

    // If they're equal, no substitution at this level
    if (original.equals(variant)) return;

    // If they're different atoms, this is a substitution
    if (original instanceof Atom && variant instanceof Atom) {
      substitution.put(original.represent(), variant.represent());
      substitution.put(canonicalize(original.represent()), variant.represent());
      return;
    }

    // If both are pairs, recurse into components (pairs are structural containers)
    if (original instanceof Pair op && variant instanceof Pair vp) {
      findSubstitutionsRecursive(op.getLeft(), vp.getLeft(), substitution);
      findSubstitutionsRecursive(op.getRight(), vp.getRight(), substitution);
      return;
    }

    // If both are encryptions, recurse (encryptions are structural)
    if (original instanceof Encrypt oe && variant instanceof Encrypt ve) {
      findSubstitutionsRecursive(oe.getMsg(), ve.getMsg(), substitution);
      findSubstitutionsRecursive(oe.getKey(), ve.getKey(), substitution);
      return;
    }

    // FIX 3: For functions, do NOT recurse into arguments!
    // If the functions differ, record the ENTIRE function as a substitution.
    // This is correct per Algorithm 1: we only replace blocked hypotheses,
    // and if bal($oyster) is not blocked, it should remain unchanged.
    if (original instanceof PredictiveFunction && variant instanceof PredictiveFunction) {
      // The entire function changed - record as whole substitution
      substitution.put(original.represent(), variant.represent());
      substitution.put(canonicalize(original.represent()), variant.represent());
      return;
    }

    // Different types - record as substitution
    substitution.put(original.represent(), variant.represent());
    substitution.put(canonicalize(original.represent()), variant.represent());
  }

  /**
   * Applies string substitutions to a fact's parameters.
   */
  private void applyStringSubstitutionToFact(Fact fact, Map<String, String> substitution) {
    for (Object param : fact.getParameters()) {
      if (param instanceof PSpecial ps) {
        for (Value v : ps.getGroup()) {
          String vRepr = v.getName();
          for (Map.Entry<String, String> sub : substitution.entrySet()) {
            if (vRepr.equals(sub.getKey()) || vRepr.equals("~" + sub.getKey()) ||
                canonicalize(vRepr).equals(canonicalize(sub.getKey()))) {
              v.setName(sub.getValue());
              break;
            }
          }
        }
      } else if (param instanceof Value v) {
        String vRepr = v.getName();
        for (Map.Entry<String, String> sub : substitution.entrySet()) {
          if (vRepr.equals(sub.getKey()) || vRepr.equals("~" + sub.getKey()) ||
              canonicalize(vRepr).equals(canonicalize(sub.getKey()))) {
            v.setName(sub.getValue());
            break;
          }
        }
      }
    }
  }

  /**
   * Removes send facts from the rule and ONLY the matching receive facts from subsequent rules.
   * Per Algorithm 1: if no variant exists, remove the send action and the MATCHING receive.
   *
   * Matching is defined by: sender + receiver + label + message payload
   * NOT all receives - only the specific one that would have consumed this send.
   *
   * @param theory The theory (list of rules) to modify
   * @param currentRule The rule where send is being removed
   * @param targetMessage The target message being sent (used for matching)
   */
  private void removeSendAndMatchingReceive(ArrayList<Rule> theory, Rule currentRule, Message targetMessage) {
    // Collect send message patterns to match (with full detail for precise matching)
    Set<SendPattern> sendPatterns = new LinkedHashSet<>();

    // Remove SndS postconditions and collect their full patterns
    currentRule.getPostconditions().removeIf(f -> {
      if ("SndS".equals(f.getF_name())) {
        SendPattern pattern = extractFullSendPattern(f);
        if (pattern != null) {
          sendPatterns.add(pattern);
          log.info("Removing SndS: {}", pattern);
        }
        return true;
      }
      return false;
    });

    // Remove ONLY the matching Send actions (not all Send actions)
    currentRule.getActions().removeIf(f -> {
      if ("Send".equals(f.getF_name())) {
        // Check if this Send action's payload matches our target
        String actionPayload = extractActionPayload(f);
        if (actionPayload != null && targetMessage != null) {
          String targetStr = targetMessage.represent();
          // Match if the action payload contains or matches the target
          return actionPayload.contains(canonicalize(targetStr)) ||
                 canonicalize(actionPayload).contains(canonicalize(targetStr));
        }
        return true; // Remove if we can't determine
      }
      return false;
    });

    if (sendPatterns.isEmpty()) {
      log.warn("No send patterns extracted - nothing to match for receive removal");
      return;
    }

    // Find the index of current rule
    int currentIndex = -1;
    for (int i = 0; i < theory.size(); i++) {
      if (theory.get(i).getRule_name().equals(currentRule.getRule_name())) {
        currentIndex = i;
        break;
      }
    }

    if (currentIndex < 0) return;

    // Only look at subsequent rules for matching receives
    for (int i = currentIndex + 1; i < theory.size(); i++) {
      Rule nextRule = theory.get(i);
      boolean removedAnything = false;

      // Remove ONLY matching RcvS preconditions
      removedAnything |= nextRule.getPreconditions().removeIf(f -> {
        if ("RcvS".equals(f.getF_name())) {
          SendPattern recvPattern = extractFullSendPattern(f);
          if (recvPattern != null) {
            // Check if this receive matches any of our removed sends
            for (SendPattern sendPat : sendPatterns) {
              if (patternsMatch(sendPat, recvPattern)) {
                log.info("Removing matching RcvS in rule {}: {}", nextRule.getRule_name(), recvPattern);
                return true;
              }
            }
          }
        }
        return false;
      });

      // Remove ONLY the matching Receive actions (based on payload, not all Receive actions)
      removedAnything |= nextRule.getActions().removeIf(f -> {
        if ("Receive".equals(f.getF_name())) {
          String actionPayload = extractActionPayload(f);
          if (actionPayload != null && targetMessage != null) {
            String targetStr = targetMessage.represent();
            // Only remove if payload matches
            boolean matches = actionPayload.contains(canonicalize(targetStr)) ||
                             canonicalize(actionPayload).contains(canonicalize(targetStr));
            if (matches) {
              log.info("Removing matching Receive action in rule {}: {}", nextRule.getRule_name(), actionPayload);
            }
            return matches;
          }
        }
        return false;
      });

      // Only mark as mutated if we actually removed something
      if (removedAnything && !nextRule.getRule_name().endsWith("_M")) {
        nextRule.setRule_name(nextRule.getRule_name() + "_M");
        nextRule.setTypo(Type.MUTATED);
      }
    }
  }

  /**
   * Pattern class for matching sends and receives with full detail.
   */
  private static class SendPattern {
    String sender;
    String receiver;
    String labels;
    String values;

    @Override
    public String toString() {
      return sender + "->" + receiver + " [" + labels + "] (" + values + ")";
    }
  }

  /**
   * Extracts a full pattern from SndS/RcvS fact for precise matching.
   */
  private SendPattern extractFullSendPattern(Fact fact) {
    if (fact.getParameters().size() < 4) {
      return null;
    }
    SendPattern pattern = new SendPattern();
    pattern.sender = paramToString(fact.getParameters().get(0));
    pattern.receiver = paramToString(fact.getParameters().get(1));
    pattern.labels = paramToString(fact.getParameters().get(2));
    pattern.values = paramToString(fact.getParameters().get(3));
    return pattern;
  }

  /**
   * Checks if two patterns match (for send/receive matching).
   * Requires: same sender, same receiver, same labels, same values.
   */
  private boolean patternsMatch(SendPattern send, SendPattern recv) {
    // Sender and receiver must match
    if (!canonicalize(send.sender).equals(canonicalize(recv.sender))) return false;
    if (!canonicalize(send.receiver).equals(canonicalize(recv.receiver))) return false;

    // Labels should match (or be compatible)
    if (!labelsMatch(send.labels, recv.labels)) return false;

    // Values should match (or be compatible)
    if (!valuesMatch(send.values, recv.values)) return false;

    return true;
  }

  /**
   * Checks if labels match (exact or compatible).
   */
  private boolean labelsMatch(String sendLabels, String recvLabels) {
    String s = canonicalize(sendLabels).replace("<", "").replace(">", "").replace("'", "");
    String r = canonicalize(recvLabels).replace("<", "").replace(">", "").replace("'", "");
    return s.equals(r);
  }

  /**
   * Checks if values match (exact or compatible).
   */
  private boolean valuesMatch(String sendValues, String recvValues) {
    String s = canonicalize(sendValues).replace("<", "").replace(">", "").replace("~", "");
    String r = canonicalize(recvValues).replace("<", "").replace(">", "").replace("~", "");
    return s.equals(r) || s.contains(r) || r.contains(s);
  }

  /**
   * Converts a parameter to string representation.
   */
  private String paramToString(Object param) {
    if (param instanceof Value v) {
      return v.getName();
    } else if (param instanceof PSpecial ps) {
      StringBuilder sb = new StringBuilder("<");
      for (int i = 0; i < ps.getGroup().size(); i++) {
        if (i > 0) sb.append(",");
        sb.append(ps.getGroup().get(i).getName());
      }
      sb.append(">");
      return sb.toString();
    }
    return String.valueOf(param);
  }

  /**
   * Extracts payload from a Send/Receive action fact.
   */
  private String extractActionPayload(Fact actionFact) {
    // Send/Receive typically have: (principal, label, value) or similar
    List<Object> params = actionFact.getParameters();
    if (params == null || params.isEmpty()) return null;

    // The value/payload is typically the last parameter
    Object lastParam = params.get(params.size() - 1);
    return paramToString(lastParam);
  }

  /**
   * Propagates mutation with variant replacements to subsequent rules.
   */
  private void propagateMutationWithVariant(ArrayList<Rule> theory, Rule startRule,
                                            Map<Message, Set<Message>> blockedToReplacements,
                                            Map<String, String> setup) {
    // Build string-based substitution
    Map<String, String> substitution = new HashMap<>();
    for (Map.Entry<Message, Set<Message>> entry : blockedToReplacements.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        Message blocked = entry.getKey();
        Message replacement = entry.getValue().iterator().next();
        substitution.put(canonicalize(blocked.represent()), replacement.represent());
      }
    }

    if (substitution.isEmpty()) return;

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

      if (!r.getRule_name().endsWith("_M")) {
        r.setRule_name(r.getRule_name() + "_M");
        r.setTypo(Type.MUTATED);
      }

      if (r.isHuman()) continue;

      // Apply substitutions
      for (Map.Entry<String, String> sub : substitution.entrySet()) {
        replaceValue(r, sub.getKey(), sub.getValue(), true, true, true);
      }

      // Adjust access decisions if needed
      for (String replacement : substitution.values()) {
        adjustAccessDecision(theory, i, r, replacement, setup);
      }
    }
  }

  /**
   * Legacy mutation logic for backwards compatibility.
   */
  private void applyLegacyMutation(Rule startRule, ArrayList<Rule> theoryClone,
                                   Set<String> forgetSet, Map<String, String> setup) {
    startRule.setRule_name(startRule.getRule_name() + "_M");
    startRule.setTypo(Type.MUTATED);

    for (String forgottenOriginal : forgetSet) {
      String forgotten = canonicalize(forgottenOriginal);

      removeForgetMutation(startRule, forgotten);
      String replacement = chooseReplacement(forgotten, setup);

      replaceValue(startRule, forgotten, replacement, false, false, false);
      propagateMutation(theoryClone, startRule, forgotten, replacement, setup);
    }
  }

  // Normalize names like "~p1" -> "p1"
  private String canonicalize(String name) {
    return name != null && name.startsWith("~") ? name.substring(1) : name;
  }

  /**
   * Propagates the mutation through the theory.
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

      replaceValue(r, forgotten, replacement, true, true, true);
      adjustAccessDecision(theory, i, r, replacement, setup);
    }
  }

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

      if (currentIndex + 1 < theory.size()) {
        Rule nextRule = theory.get(currentIndex + 1);
        for (Fact pre : nextRule.getPreconditions()) {
          if (!"RcvS".equals(pre.getF_name())) continue;
          for (int paramIndex = 0; paramIndex < pre.getParameters().size(); paramIndex++) {
            Object param = pre.getParameters().get(paramIndex);
            if (param instanceof PSpecial ps) {
              boolean hasAccess =
                  ps.getGroup().stream().anyMatch(v -> "'access'".equals(v.getName()));
              if (hasAccess) {
                if (paramIndex + 1 < pre.getParameters().size()) {
                  Object valuesParam = pre.getParameters().get(paramIndex + 1);
                  if (valuesParam instanceof PSpecial vp) {
                    vp.getGroup().stream()
                        .filter(v -> "'Denied'".equals(v.getName()))
                        .forEach(v -> v.setName("'Granted'"));
                  }
                }
              } else {
                ps.getGroup().stream()
                    .filter(v -> "'Denied'".equals(v.getName()))
                    .forEach(v -> v.setName("'Granted'"));
              }
            }
          }
        }

        for (Fact act : nextRule.getActions()) {
          if ("Receive".equals(act.getF_name()) || "Commit".equals(act.getF_name())) {
            for (Object param : act.getParameters()) {
              if (param instanceof Value v && "'Denied'".equals(v.getName())) {
                v.setName("'Granted'");
              }
            }
          }
        }

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
    String t =
        s.replace("&apos;", "").replace("&quot;", "").replace("apos", "").replace("quot", "");
    return t.replaceAll("[^A-Za-z]", "");
  }

  private void removeForgetMutation(Rule rule, String forgotten) {
    rule.getActions().removeIf(f -> "Forget".equals(f.getF_name()) && containsParam(f, forgotten));
  }

  private boolean containsParam(Fact fact, String name) {
    for (Object p : fact.getParameters()) {
      if (p instanceof Value v && canonicalize(v.getName()).equals(name)) return true;
      if (p instanceof PSpecial ps) {
        for (Value v : ps.getGroup()) if (canonicalize(v.getName()).equals(name)) return true;
      }
    }
    return false;
  }

  private void replaceValue(
      Rule rule,
      String forgotten,
      String replacement,
      boolean mutatePreState,
      boolean mutateRcvS,
      boolean includeStatePost) {

    rule.getPostconditions()
        .forEach(
            f -> {
              if (includeStatePost || !"State".equals(f.getF_name())) {
                replaceInFact(f, forgotten, replacement);
              }
            });

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

  private void replaceInFact(Fact fact, String forgotten, String replacement) {
    for (Object param : fact.getParameters()) {
      if (param instanceof PSpecial) {
        deepReplaceInPSpecial((PSpecial) param, forgotten, replacement);
      } else if (param instanceof Value v) {
        if (canonicalize(v.getName()).equals(forgotten)) v.setName(replacement);
      }
    }
  }

  private void deepReplaceInPSpecial(PSpecial ps, String forgotten, String replacement) {
    for (Value v : ps.getGroup()) {
      if (canonicalize(v.getName()).equals(forgotten)) v.setName(replacement);
    }
  }

  private ArrayList<Rule> deepCloneTheory(ArrayList<Rule> src) {
    ArrayList<Rule> out = new ArrayList<>();
    src.forEach(r -> out.add(r.clone()));
    return out;
  }

  private Rule findRuleByName(ArrayList<Rule> rules, String name) {
    return rules.stream().filter(r -> name.equals(r.getRule_name())).findFirst().orElse(null);
  }

  private String chooseReplacement(String forgotten, Map<String, String> setup) {
    String type = setup.get(forgotten);
    return setup.entrySet().stream()
        .filter(e -> Objects.equals(e.getValue(), type) && !Objects.equals(e.getKey(), forgotten))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(forgotten + "_rep");
  }
}

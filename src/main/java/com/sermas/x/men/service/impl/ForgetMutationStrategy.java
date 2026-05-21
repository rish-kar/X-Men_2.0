package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.*;
import com.sermas.x.men.service.DerivationCheckService;
import com.sermas.x.men.service.DerivationService;
import com.sermas.x.men.service.MutationStrategy;
import com.sermas.x.men.service.forget.BlockingChecker;
import com.sermas.x.men.service.forget.ForgetContext;
import com.sermas.x.men.service.forget.ForgetContext.BlockingMode;
import com.sermas.x.men.service.forget.ForgetDerivationChecker;
import com.sermas.x.men.service.forget.ReplacementComputer;
import com.sermas.x.men.utilities.SetupKnowledgeExtractor;
import com.sermas.x.men.utilities.UtilityFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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

  private Set<String> nonInternalActions = new HashSet<>(Set.of(
      "Send", "Receive", "To", "H", "Fr", "Setup", "OnlyOnce", "Neq", "Roles",
      "ChanSndS", "ChanRcvS", "Hfin", "Forget"));

  public Set<String> getNonInternalActions() {
    return Collections.unmodifiableSet(nonInternalActions);
  }

  public void setNonInternalActions(Set<String> newSet) {
    this.nonInternalActions = new HashSet<>(newSet);
  }

  public void addWitnessActions(Collection<String> witnessNames) {
    if (witnessNames != null) this.nonInternalActions.addAll(witnessNames);
  }

  @Autowired private DerivationCheckService derivationCheckService;
  @Autowired private SetupKnowledgeExtractor setupKnowledgeExtractor;
  @Autowired private DerivationService derivationService;
  @Autowired private UtilityFunctions utilityFunctions;
  @Autowired private ForgetDerivationChecker forgetDerivationChecker;
  @Autowired private BlockingChecker blockingChecker;
  @Autowired private ReplacementComputer replacementComputer;

  // Default blocking mode - can be configured
  private BlockingMode blockingMode = BlockingMode.CASE1_WEAK;
  private int maxVariantsPerRule = 10;

  public int getMaxVariantsPerRule() {
    return maxVariantsPerRule;
  }

  public void setMaxVariantsPerRule(int maxVariantsPerRule) {
    this.maxVariantsPerRule = maxVariantsPerRule;
  }

  public BlockingMode getBlockingMode() {
    return blockingMode;
  }

  public void setBlockingMode(BlockingMode blockingMode) {
    this.blockingMode = blockingMode;
  }

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

    // Prepare knowledge bundle for extraction
    if (parametersBundle.getCollections() == null) {
      parametersBundle.setCollections(new ArrayList<>());
    }
    ArrayList<ArrayList<Rule>> tempCollections = new ArrayList<>();
    tempCollections.add(rules);
    ParametersBundle knowledgeBundle = new ParametersBundle();
    knowledgeBundle.setCollections(tempCollections);
    knowledgeBundle.setForgetMutationSet(parametersBundle.getForgetMutationSet());
    knowledgeBundle.addExtraContent("currentRuleName", rule.getRule_name());

    // Initialize or reuse transition-aware context
    ForgetContext ctx = parametersBundle.getForgetContext();
    if (ctx == null) {
      Set<Message> fullKnowledge =
          derivationCheckService.extractKnowledgeWithoutForgetRemoval(knowledgeBundle);
      ctx = new ForgetContext(fullKnowledge, Collections.emptySet(), blockingMode, setup);
      parametersBundle.setForgetContext(ctx);
    } else if (ctx.getTypeMap().isEmpty()) {
      ctx.setTypeMap(setup);
    }

    // Update context for this transition
    Set<String> forgetSet = parametersBundle.getForgetMutationSet().get(rule.getRule_name());
    Message receivedMessage = extractReceivedMessage(rule);

    if (forgetSet == null || forgetSet.isEmpty()) {
      ctx.updateForTransition(receivedMessage, null);
      parametersBundle.setForgetContext(ctx);
      return parametersBundle;
    }

    List<Message> forgottenMessages = new ArrayList<>();
    for (String forgotten : forgetSet) {
      Message forgottenMessage = resolveForgetMessage(ctx.getKnowledge(), forgotten);
      forgottenMessages.add(forgottenMessage);
      ctx.updateForTransition(receivedMessage, forgottenMessage);
    }
    parametersBundle.setForgetContext(ctx);

    log.info("Algorithm 1 - Processing rule {} with {} forget targets",
             rule.getRule_name(), forgetSet.size());
    log.info("Knowledge K: {}", ctx.getKnowledge().stream().map(Message::represent).toList());
    log.info("Forget set: {}", ctx.getForgetSet().stream().map(Message::represent).toList());
    log.info("Blocking mode: {}", ctx.getBlockingMode());

    Set<String> actionsToNeglect = new LinkedHashSet<>();
    if (rule.isHuman()) {
      for (Message forgottenMessage : forgottenMessages) {
        List<Fact> internalActions =
            isMessageUsedInInternalAction(rule, forgottenMessage, nonInternalActions);
        if (!internalActions.isEmpty()) {
          log.info(
              "Forget triggers Neglect on rule {} - removing {} internal action(s) using forgotten message {}",
              rule.getRule_name(), internalActions.size(), forgottenMessage.represent());
          for (Fact action : internalActions) {
            actionsToNeglect.add(factSignature(action));
          }
        }
      }
    }

    // Extract target message (m2 - the send message)
    Message target = derivationCheckService.extractTargetFromRule(rule);
    if (target == null) {
      log.warn("No target message found for rule {}", rule.getRule_name());
      return parametersBundle;
    }

    log.info("Target message m2: {}", target.represent());

    // Get ALL derivations for the target
    Set<Derivation> allDerivations = forgetDerivationChecker.getAllDerivations(target, ctx.getKnowledge());

    // Per Algorithm 1, if Π is empty the algorithm yields "skip the send". However, in
    // ceremonies where the send payload contains user-defined functions (e.g. k(nH,nB))
    // or principal names that are not present in the extracted K, the DY engine cannot
    // reconstruct the target even though the agent is clearly able to send it. In that
    // case we fall back to a textual substitution: if each forgotten message has at
    // least one type-compatible replacement, we substitute it inside the send payload
    // and continue the ceremony (this is exactly the variant branch of Algorithm 1,
    // expressed at the textual level of the rule).
    if (allDerivations.isEmpty()) {
      log.warn("No derivations found for target {} - falling back to textual substitution",
               target.represent());

      Map<String, String> textualSub = new LinkedHashMap<>();
      for (Message forgottenMessage : forgottenMessages) {
        Set<Message> reps = replacementComputer.computeReplacementSet(forgottenMessage, ctx);
        if (reps.isEmpty()) {
          textualSub.clear();
          break; // a forgotten term has no replacement -> genuine skip
        }
        String fromName = canonicalize(forgottenMessage.represent());
        String toName = canonicalize(reps.iterator().next().represent());
        textualSub.put(fromName, toName);
      }

      ArrayList<Rule> theoryClone = deepCloneTheory(rules);
      Rule startRule = findRuleByName(theoryClone, rule.getRule_name());
      if (startRule != null) {
        if (!textualSub.isEmpty()) {
          log.info("Applying textual substitution {} to rule {}",
                   textualSub, startRule.getRule_name());
          // Substitute in every action except the Forget itself (we drop Forget below).
          // Witness actions like PasswordAttempt that reference the forgotten term
          // should mirror what the human actually attempted, i.e. the replacement.
          for (Fact action : startRule.getActions()) {
            if (!"Forget".equals(action.getF_name())) {
              applyStringSubstitutionToFact(action, textualSub);
            }
          }
          // Substitute in non-State postconditions (Out, SndS). State is monotonic
          // and must still contain both the original and the replacement.
          for (Fact post : startRule.getPostconditions()) {
            if (!"State".equals(post.getF_name())) {
              applyStringSubstitutionToFact(post, textualSub);
            }
          }
        } else {
          // No replacement available. Per Algorithm 1: skip the send, and
          // conditionally trigger Neglect for any internal action that uses the
          // forgotten term (line 7-8: "If m is used in a, trigger Neglect").
          log.info("No replacements available - removing send and matching receive,"
                   + " neglecting internal actions using the forgotten term");
          removeSendAndMatchingReceive(theoryClone, startRule, target);
          removeNeglectedActions(startRule, actionsToNeglect);
        }

        // Remove Forget actions
        for (String forgotten : forgetSet) {
          removeForgetMutation(startRule, canonicalize(forgotten));
        }

        // Mark the rule as mutated and tag it with the _M suffix so the output
        // makes the mutation visible at the rule-name level.
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
        if (accumulatedVariants.size() >= maxVariantsPerRule) {
          parametersBundle.setVariantsTruncated(true);
          log.warn("Reached maximum variants per rule ({}) for rule {} - truncating",
              maxVariantsPerRule, rule.getRule_name());
          break;
        }
        accumulatedVariants.add(new VariantInfo(variant, blockedToReplacements));
      }

      if (accumulatedVariants.size() >= maxVariantsPerRule) {
        if (!parametersBundle.isVariantsTruncated()) {
          parametersBundle.setVariantsTruncated(true);
          log.warn("Reached maximum variants per rule ({}) for rule {} - truncating",
              maxVariantsPerRule, rule.getRule_name());
        }
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
        removeNeglectedActions(startRule, actionsToNeglect);
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
        removeNeglectedActions(startRule, actionsToNeglect);
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

      removeNeglectedActions(startRule, actionsToNeglect);

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

    return new ForgetContext(knowledge, forgetMessages, blockingMode, typeMap);
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

    // Always include the direct blocked -> chosen-replacement mappings, so that
    // when the original send payload is stored as a single Value string
    // (e.g. "senc(<$Human,pw1>,k(nH,nB))"), the canonical name of the blocked
    // hypothesis can still be textually substituted inside it.
    for (Map.Entry<Message, Set<Message>> entry : blockedToReplacements.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
      Message blocked = entry.getKey();
      Message replacement = entry.getValue().iterator().next();
      String fromName = canonicalize(blocked.represent());
      String toName = canonicalize(replacement.represent());
      stringSubstitution.putIfAbsent(fromName, toName);
    }

    // Apply substitutions to postconditions (SndS and Out facts)
    for (Fact post : rule.getPostconditions()) {
      String fname = post.getF_name();
      if ("SndS".equals(fname) || "Out".equals(fname)) {
        applyStringSubstitutionToFact(post, stringSubstitution);
      }
    }

    // Apply to actions (Send facts and internal actions that mention the forgotten term)
    for (Fact action : rule.getActions()) {
      String fname = action.getF_name();
      if ("Send".equals(fname)) {
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
          substituteValueName(v, substitution);
        }
      } else if (param instanceof Value v) {
        substituteValueName(v, substitution);
      }
    }
  }

  /**
   * Rewrites a Value's name by substituting the canonical form of each substitution key
   * with its replacement, using a word-boundary regex so occurrences inside complex
   * payloads such as "senc(<$Human,pw1>,k(nH,nB))" are rewritten without affecting
   * unrelated tokens (e.g. "pw10").
   */
  private void substituteValueName(Value v, Map<String, String> substitution) {
    if (v == null || v.getName() == null) return;
    String original = v.getName();
    String result = original;
    for (Map.Entry<String, String> sub : substitution.entrySet()) {
      String from = canonicalize(sub.getKey());
      String to = canonicalize(sub.getValue());
      if (from == null || from.isEmpty() || from.equals(to)) continue;
      String quoted = java.util.regex.Pattern.quote(from);
      // Treat $, ~ and alphanumerics/underscore as word characters so that
      // tokens like "$Human", "~pw1" and "pw1" are matched as whole units.
      String regex = "(?<![A-Za-z0-9_$~])" + quoted + "(?![A-Za-z0-9_$])";
      result = result.replaceAll(regex, java.util.regex.Matcher.quoteReplacement(to));
    }
    if (!result.equals(original)) {
      v.setName(result);
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
    Set<String> sentPayloadKeys = new LinkedHashSet<>();

    // Remove SndS postconditions and collect their full patterns
    currentRule.getPostconditions().removeIf(f -> {
      if ("SndS".equals(f.getF_name())) {
        SendPattern pattern = extractFullSendPattern(f);
        if (pattern != null) {
          sendPatterns.add(pattern);
          if (pattern.values != null) sentPayloadKeys.add(canonicalize(pattern.values));
          log.info("Removing SndS: {}", pattern);
        }
        return true;
      }
      return false;
    });

    // Remove Out postconditions whose payload matches the target (In/Out style rules)
    currentRule.getPostconditions().removeIf(f -> {
      if ("Out".equals(f.getF_name())) {
        String payload = extractOutPayload(f);
        if (payload != null && targetMessage != null) {
          String targetStr = canonicalize(targetMessage.represent());
          if (canonicalize(payload).contains(targetStr)) {
            log.info("Removing Out matching target: {}", payload);
            sentPayloadKeys.add(canonicalize(payload));
            return true;
          }
        }
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

    if (sendPatterns.isEmpty() && sentPayloadKeys.isEmpty()) {
      log.warn("No send patterns or Out payloads extracted - nothing to match for receive removal");
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

      // Remove ONLY matching In preconditions (In/Out style rules)
      removedAnything |= nextRule.getPreconditions().removeIf(f -> {
        if ("In".equals(f.getF_name())) {
          List<Object> params = f.getParameters();
          if (params != null && !params.isEmpty()) {
            String payload = String.valueOf(params.get(params.size() - 1));
            String key = canonicalize(payload);
            for (String sentKey : sentPayloadKeys) {
              if (sentKey != null && (sentKey.contains(key) || key.contains(sentKey))) {
                log.info("Removing matching In in rule {}: {}", nextRule.getRule_name(), payload);
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

  private String extractOutPayload(Fact f) {
    if (f == null || f.getParameters() == null || f.getParameters().isEmpty()) return "";
    return String.valueOf(f.getParameters().get(f.getParameters().size() - 1));
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
    }
  }

  private void removeForgetMutation(Rule rule, String forgotten) {
    rule.getActions().removeIf(f -> "Forget".equals(f.getF_name()) && containsParam(f, forgotten));
  }

  private boolean containsParam(Fact fact, String name) {
    for (Object p : fact.getParameters()) {
      if (paramContainsName(p, name)) return true;
    }
    return false;
  }

  private boolean paramContainsName(Object param, String name) {
    if (param instanceof Value v) {
      return canonicalize(v.getName()).equals(name);
    }
    if (param instanceof PSpecial ps) {
      for (Value v : ps.getGroup()) {
        if (canonicalize(v.getName()).equals(name)) return true;
      }
      return canonicalize(ps.toString()).contains(name);
    }
    if (param instanceof FSpecial fs) {
      for (Value v : fs.getGroup()) {
        if (canonicalize(v.getName()).equals(name)) return true;
      }
      return canonicalize(fs.toString()).contains(name);
    }
    if (param instanceof Nary_app na) {
      for (Value v : na.getGroup()) {
        if (canonicalize(v.getName()).equals(name)) return true;
      }
      return canonicalize(na.toString()).contains(name);
    }
    if (param != null) {
      return canonicalize(param.toString()).contains(name);
    }
    return false;
  }

  private List<Fact> isMessageUsedInInternalAction(
      Rule rule, Message forgotten, Set<String> nonInternalActionNames) {
    List<Fact> matches = new ArrayList<>();
    if (rule == null || forgotten == null || rule.getActions() == null) {
      return matches;
    }
    String forgottenName = canonicalize(forgotten.represent());
    for (Fact action : rule.getActions()) {
      if (action == null || nonInternalActionNames.contains(action.getF_name())) {
        continue;
      }
      if (containsParam(action, forgottenName)) {
        matches.add(action);
      }
    }
    return matches;
  }

  private void removeNeglectedActions(Rule rule, Set<String> actionSignatures) {
    if (rule == null || actionSignatures == null || actionSignatures.isEmpty()) {
      return;
    }
    rule.getActions().removeIf(action -> actionSignatures.contains(factSignature(action)));
  }

  private String factSignature(Fact fact) {
    if (fact == null) return "";
    StringBuilder signature = new StringBuilder(fact.getF_name()).append("|");
    for (Object param : fact.getParameters()) {
      signature.append(String.valueOf(param)).append(",");
    }
    return signature.toString();
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

  private Message resolveForgetMessage(Set<Message> knowledge, String forgottenStr) {
    String canonical = canonicalize(forgottenStr);
    Message found = findMessageByRepresentation(knowledge, canonical);
    if (found != null) {
      return found;
    }
    return new Atom(canonical);
  }

  private Message extractReceivedMessage(Rule rule) {
    if (rule == null || rule.getPreconditions() == null) {
      return null;
    }
    for (Fact fact : rule.getPreconditions()) {
      if ("RcvS".equals(fact.getF_name())) {
        List<Object> params = fact.getParameters();
        if (params != null && params.size() >= 4) {
          Object valuesParam = params.get(params.size() - 1);
          String paramStr = payloadToString(valuesParam).trim();
          Message received = parseTargetParam(paramStr);
          if (received != null) {
            log.debug("Extracted received message from RcvS: {}", received.represent());
            return received;
          }
        }
      }
    }
    for (Fact pre : rule.getPreconditions()) {
      if ("In".equals(pre.getF_name())) {
        List<Object> params = pre.getParameters();
        if (params != null && !params.isEmpty()) {
          Object payloadParam = params.get(params.size() - 1);
          String paramStr = String.valueOf(payloadParam).trim();
          try {
            Message m = parseStringToMessage(paramStr);
            if (m != null) {
              log.debug("Extracted received message from In: {}", m.represent());
              return m;
            }
          } catch (Throwable t) {
            // fall through to Atom fallback
          }
          return new Atom(paramStr);
        }
      }
    }
    return null;
  }

  private String payloadToString(Object obj) {
    if (obj instanceof Value v) {
      return v.getName();
    }
    return String.valueOf(obj);
  }

  private Message parseTargetParam(String param) {
    String trimmed = param.trim();
    if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
      String inner = trimmed.substring(1, trimmed.length() - 1);
      List<String> parts = splitTopLevelCommas(inner);
      return buildNestedPair(parts);
    }
    return parseStringToMessage(trimmed);
  }

  private Message buildNestedPair(List<String> elements) {
    if (elements.isEmpty()) return null;
    if (elements.size() == 1) return parseStringToMessage(elements.get(0).trim());
    Message first = parseStringToMessage(elements.get(0).trim());
    Message rest = buildNestedPair(elements.subList(1, elements.size()));
    return new Pair(first, rest);
  }

  private Message parseStringToMessage(String raw) {
    String str = raw.trim();

    if (str.startsWith("<") && str.endsWith(">")) {
      String inner = str.substring(1, str.length() - 1).trim();
      if (inner.isEmpty()) return new Atom(str);
      List<String> parts = splitTopLevelCommas(inner);
      return buildNestedPair(parts);
    }

    if (str.startsWith("{") && str.contains("}_")) return parseEncryption(str);
    if (str.startsWith("(") && str.endsWith(")")) return parsePair(str);
    if (str.contains("(") && str.endsWith(")")) return parseFunction(str);
    return new Atom(str);
  }

  private Encrypt parseEncryption(String str) {
    String s = str.trim();
    if (!s.startsWith("{") || !s.endsWith("}")) {
      return new Encrypt(new Atom(str), new Atom("UNKNOWN_KEY"));
    }
    int sep = s.indexOf("}_");
    if (sep < 0) {
      return new Encrypt(new Atom(str), new Atom("UNKNOWN_KEY"));
    }
    String msgPart = s.substring(1, sep).trim();
    int keyStart = sep + 2;
    if (keyStart >= s.length() || s.charAt(keyStart) != '{') {
      return new Encrypt(new Atom(str), new Atom("UNKNOWN_KEY"));
    }
    int depth = 0, i = keyStart;
    for (; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '{') depth++;
      else if (c == '}') {
        depth--;
        if (depth == 0) {
          i++;
          break;
        }
      }
    }
    String keyPart = s.substring(keyStart + 1, i - 1).trim();
    Message msg = parseStringToMessage(msgPart);
    Message key = parseStringToMessage(keyPart);
    return new Encrypt(msg, key);
  }

  private Message parsePair(String str) {
    String inner = str.substring(1, str.length() - 1).trim();
    int commaPos = findTopLevelComma(inner);
    if (commaPos < 0) return parseStringToMessage(inner);
    String left = inner.substring(0, commaPos).trim();
    String right = inner.substring(commaPos + 1).trim();
    return new Pair(parseStringToMessage(left), parseStringToMessage(right));
  }

  private Message parseFunction(String str) {
    int idx = str.indexOf('(');
    String name = str.substring(0, idx).trim();
    String inside = str.substring(idx + 1, str.length() - 1).trim();
    if (inside.isEmpty()) return new Atom(name);
    List<String> parts = splitTopLevelCommas(inside);
    List<Message> args = new ArrayList<>();
    for (String p : parts) args.add(parseStringToMessage(p));
    return new PredictiveFunction(name, args);
  }

  private int findTopLevelComma(String s) {
    int depth = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '(' || c == '<') depth++;
      else if (c == ')' || c == '>') depth--;
      else if (c == ',' && depth == 0) return i;
    }
    return -1;
  }

  private List<String> splitTopLevelCommas(String s) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int last = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '(' || c == '<') depth++;
      else if (c == ')' || c == '>') depth--;
      else if (c == ',' && depth == 0) {
        parts.add(s.substring(last, i).trim());
        last = i + 1;
      }
    }
    parts.add(s.substring(last).trim());
    return parts;
  }

}

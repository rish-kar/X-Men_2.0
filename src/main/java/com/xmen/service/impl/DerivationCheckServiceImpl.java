package com.xmen.service.impl;

import com.xmen.config.CeremonyVocabulary;
import com.xmen.model.*;
import com.xmen.service.DerivationCheckService;
import com.xmen.service.DerivationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * DerivationCheckServiceImpl implements the DerivationCheckService interface, providing methods to
 * check if a target message is derivable from a set of knowledge messages, extract target messages
 * from rules, and extract knowledge from parameters bundles and rules.
 */
@Service
@Slf4j
public class DerivationCheckServiceImpl implements DerivationCheckService {

  private final DerivationService hybridDerivationService;
  private final DerivationService javaDerivationService;

  @Autowired(required = false)
  private CeremonyVocabulary vocabulary;

  private static final Set<String> RULES_TO_SKIP =
      Set.of("humansetup", "Setup", "ChanSndS", "ChanRcvS");

  private boolean isOutboundFact(String name) {
    if (name == null) return false;
    if (vocabulary != null) return vocabulary.isOutboundChannel(name);
    return "Out".equals(name) || "SndS".equals(name);
  }

  private boolean isInboundFact(String name) {
    if (name == null) return false;
    if (vocabulary != null) return vocabulary.isInboundChannel(name);
    return "In".equals(name) || "RcvS".equals(name);
  }

  private boolean isStateFact(String name) {
    if (name == null) return false;
    if (vocabulary != null) return vocabulary.isStateFact(name);
    return "State".equals(name);
  }

  /**
   * Constructor for DerivationCheckServiceImpl.
   *
   * @param hybridDerivationService hybrid facade that may proxy to Haskell
   * @param javaDerivationService pure Java derivation implementation
   */
  public DerivationCheckServiceImpl(
      HybridDerivationService hybridDerivationService,
      DerivationServiceImpl javaDerivationService) {
    this.hybridDerivationService = hybridDerivationService;
    this.javaDerivationService = javaDerivationService;
  }

  private DerivationService resolveDerivationService() {
    return DerivationModeContext.isHaskellEnabled()
        ? hybridDerivationService
        : javaDerivationService;
  }

  /**
   * Checks if a target message can be derived from a set of knowledge messages.
   *
   * @param target the target message to check
   * @param knowledge the set of knowledge messages
   * @return true if the target can be derived from the knowledge, false otherwise
   */
  @Override
  public boolean isDerivable(
      Message target, Set<Message> knowledge, String derivationType, int derivationDepth) {
    // Dolev–Yao style backward derivation
    Set<Derivation> derivations = new HashSet<>();

    // Handle null or empty derivationType by defaulting to LIMITED
    if (derivationType == null || derivationType.isEmpty()) {
      derivationType = String.valueOf(DerivationType.LIMITED);
      log.debug("derivationType was null, defaulting to LIMITED");
    }

    if (derivationType.equalsIgnoreCase(String.valueOf(DerivationType.LIMITED))) {
      Set<String> derivationsStrings =
          resolveDerivationService().deriveLimited(target, knowledge, 5);
      resolveDerivationService().printDerivationTree(target, knowledge, 5);
      return !derivationsStrings.isEmpty();
    } else if (derivationType.equalsIgnoreCase(String.valueOf(DerivationType.DEPTH_SPECIFIED))) {
      derivations = resolveDerivationService().deriveToDepth(target, knowledge, derivationDepth);
    } else {
      derivations = resolveDerivationService().deriveToInfinity(target, knowledge);
    }
    return !derivations.isEmpty();
  }

  /**
   * Extracts the target message (m2 - the send payload) from a rule.
   *
   * FIX G: Prioritize SndS facts for target extraction since m2 per the paper
   * is the send message in the transition. Fall back to last postcondition only
   * if no SndS fact is found.
   *
   * @param rule the rule from which to extract the target message
   * @return the target message extracted from the rule, or null if unavailable
   */
  @Override
  public Message extractTargetFromRule(Rule rule) {
    List<Fact> posts = rule.getPostconditions();
    if (posts == null || posts.isEmpty()) return null;

    // Preferred shape: outbound fact carrying the full
    // (sender, receiver, labels, values) tuple — the values (last parameter)
    // are the send payload = m2. The fact name is resolved from the active
    // vocabulary so files using "Send", "ChanSndS", etc. are also recognised.
    for (Fact fact : posts) {
      if (isOutboundFact(fact.getF_name())) {
        List<Object> params = fact.getParameters();
        if (params != null && params.size() >= 4) {
          Object valuesParam = params.get(params.size() - 1);
          String paramStr = payloadToString(valuesParam).trim();
          Message target = parseTargetParam(paramStr);
          if (target != null) {
            log.debug("Extracted target from {}: {}", fact.getF_name(), target.represent());
            return target;
          }
        }
      }
    }

    // Fallback shape: single-payload outbound fact (e.g. native Tamarin Out(...)).
    for (Fact fact : posts) {
      if (isOutboundFact(fact.getF_name())) {
        List<Object> params = fact.getParameters();
        if (params != null && !params.isEmpty() && params.size() < 4) {
          Object payloadParam = params.get(params.size() - 1);
          String paramStr = payloadToString(payloadParam).trim();
          Message target = parseTargetParam(paramStr);
          if (target != null) {
            log.debug("Extracted target from {}: {}", fact.getF_name(), target.represent());
            return target;
          }
        }
      }
    }

    // No SndS / Out — there is no outgoing message m2 in this transition.
    // Per paper Algorithm 1 (Sec. IV.C), only lines 7-8 apply in this case
    // (Neglect of an internal action that uses the forgotten message). Do
    // NOT fall back to the State postcondition: that conflates the human's
    // monotonic knowledge with an "outgoing message", which sends the
    // algorithm into the textual-substitution branch and silently rewrites
    // witness actions.
    return null;
  }

  // Convert a Fact parameter to its plain string form; unwrap Value when present
  private String payloadToString(Object obj) {
    if (obj instanceof Value v) {
      return v.getName();
    }
    return String.valueOf(obj);
  }

  /**
   * Parses the target parameter from a string representation.
   *
   * @param param the string representation of the target parameter
   * @return a Message object representing the target parameter
   */
  private Message parseTargetParam(String param) {
    param = param.trim();
    if (param.startsWith("<") && param.endsWith(">")) {
      param = param.substring(1, param.length() - 1);
      List<String> parts = splitTopLevelCommas(param);
      return buildNestedPair(parts);
    } else {
      return parseStringToMessage(param);
    }
  }

  // Note: <a,b,c> becomes Pair(a, Pair(b, c)) to align with binary pair constructor rules.
  /**
   * Builds a nested pair of messages from a list of string elements.
   *
   * @param elements the list of string elements to build the nested pair from
   * @return a Message object representing the nested pair
   */
  private Message buildNestedPair(List<String> elements) {
    if (elements.isEmpty()) return null;
    if (elements.size() == 1) return parseStringToMessage(elements.get(0).trim());
    Message first = parseStringToMessage(elements.get(0).trim());
    Message rest = buildNestedPair(elements.subList(1, elements.size()));
    return new Pair(first, rest);
  }

  /**
   * Extracts knowledge messages from the PARAMETERS BUNDLE by: 1) Locating the CURRENT rule 2)
   * Extracting the values inside the POSTCONDITION State that corresponds to the same State
   * variable(s) present in the precondition (e.g., $User) 3) Applying Forget(~x) by removing x from
   * the knowledge
   *
   * <p>This matches the requirement to use the rule's own postcondition as the basis of knowledge.
   */
  @Override
  public Set<Message> extractKnowledge(ParametersBundle parametersBundle) {
    Set<Message> knowledge = new LinkedHashSet<>();

    if (parametersBundle.getCollections() == null || parametersBundle.getCollections().isEmpty()) {
      log.warn("No collections found in parametersBundle");
      return knowledge;
    }

    @SuppressWarnings("unchecked")
    List<Rule> allRules = (List<Rule>) parametersBundle.getCollections().get(0);

    // Get the current rule name from extra content
    String currentRuleName = parametersBundle.getExtraContent("currentRuleName");
    if (currentRuleName == null || currentRuleName.isEmpty()) {
      log.warn("No current rule name found in parametersBundle");
      return knowledge;
    }

    // Find current rule
    Rule currentRule = null;
    for (Rule r : allRules) {
      if (currentRuleName.equals(r.getRule_name())) {
        currentRule = r;
        break;
      }
    }
    if (currentRule == null) {
      log.warn("Current rule not found: {}", currentRuleName);
      return knowledge;
    }

    if (RULES_TO_SKIP.contains(currentRule.getRule_name())) {
      log.info("Skipping knowledge extraction for rule: {}", currentRule.getRule_name());
      return knowledge;
    }

    log.info("Current rule: {}", currentRuleName);

    // Identify the State variable names used in the PRECONDITION (e.g., $User)
    Set<String> preconditionStateVars = new LinkedHashSet<>();
    for (Fact pre : currentRule.getPreconditions()) {
      if ("State".equals(pre.getF_name()) && !pre.getParameters().isEmpty()) {
        preconditionStateVars.add(payloadToString(pre.getParameters().get(0)));
      }
    }

    // Extract knowledge from the POSTCONDITION State that matches any of those vars
    String extractedStateRaw = null;
    for (Fact post : currentRule.getPostconditions()) {
      if ("State".equals(post.getF_name()) && post.getParameters().size() >= 3) {
        String postStateVar = payloadToString(post.getParameters().get(0));
        if (preconditionStateVars.isEmpty() || preconditionStateVars.contains(postStateVar)) {
          Object stateData = post.getParameters().get(post.getParameters().size() - 1);
          extractedStateRaw = payloadToString(stateData);
          break; // take the first matching State in postconditions
        }
      }
    }
    if (extractedStateRaw == null) {
      // Fallback: if not found, try ANY State in postconditions
      for (Fact post : currentRule.getPostconditions()) {
        if ("State".equals(post.getF_name()) && post.getParameters().size() >= 3) {
          Object stateData = post.getParameters().get(post.getParameters().size() - 1);
          extractedStateRaw = payloadToString(stateData);
          break;
        }
      }
    }

    if (extractedStateRaw != null) {
      log.info("Current rule State POSTCONDITION data: {}", extractedStateRaw);
      knowledge.addAll(parseStateParam(extractedStateRaw));
    } else {
      log.warn("No State postcondition found to extract knowledge for rule: {}", currentRuleName);
    }

    log.info("Knowledge BEFORE Forget: {}", knowledge.stream().map(Message::represent).toList());

    // Apply Forget mutation: remove canonicalized values
    Set<String> forgetSet = parametersBundle.getForgetMutationSet().get(currentRuleName);
    if (forgetSet != null && !forgetSet.isEmpty()) {
      for (String forgotten : forgetSet) {
        String canonicalForgotten = forgotten.startsWith("~") ? forgotten.substring(1) : forgotten;
        log.info("Applying Forget on value: {}", canonicalForgotten);
        knowledge.removeIf(
            msg ->
                (msg instanceof Atom a)
                    && (a.getValue().startsWith("~") ? a.getValue().substring(1) : a.getValue())
                        .equals(canonicalForgotten));
      }
    }

    log.info("Knowledge AFTER Forget: {}", knowledge.stream().map(Message::represent).toList());

    return knowledge;
  }

  /**
   * Extracts knowledge messages from a rule.
   *
   * @param rule the rule from which to extract knowledge messages
   * @return a set of knowledge messages extracted from the rule
   */
  @Override
  public Set<Message> extractKnowledgeFromRule(Rule rule) {
    if (rule == null || RULES_TO_SKIP.contains(rule.getRule_name())) return Collections.emptySet();
    // Reuse logic: simulate parameters bundle containing only this rule
    ParametersBundle pb = new ParametersBundle();
    ArrayList<ArrayList<Rule>> col = new ArrayList<>();
    ArrayList<Rule> single = new ArrayList<>();
    single.add(rule);
    col.add(single);
    pb.setCollections(col);
    pb.addExtraContent("currentRuleName", rule.getRule_name());
    // Provide empty forget set to avoid removal
    pb.setForgetMutationSet(new HashMap<>());
    return extractKnowledge(pb);
  }

  /**
   * Extracts state knowledge from a list of rules.
   *
   * @param humanRules the list of rules from which to extract state knowledge
   * @return a map where the key is the state ID and the value is a set of knowledge messages
   */
  @Override
  public Map<String, Set<Message>> extractStateKnowledgeFromRules(List<Rule> humanRules) {
    Map<String, Set<Message>> out = new LinkedHashMap<>();
    if (humanRules == null) return out;
    for (Rule r : humanRules) {
      if (RULES_TO_SKIP.contains(r.getRule_name())) continue;
      String id = extractStateIdFromRule(r);
      if (id != null) out.put(id, extractKnowledgeFromRule(r));
    }
    return out;
  }

  /**
   * Extracts the state ID from a rule.
   *
   * @param rule the rule from which to extract the state ID
   * @return the state ID extracted from the rule, or null if unavailable
   */
  @Override
  public String extractStateIdFromRule(Rule rule) {
    if (rule == null) return null;
    // Heuristic: first State postcondition numeric/string second parameter is state id
    for (Fact post : rule.getPostconditions()) {
      if ("State".equals(post.getF_name()) && post.getParameters().size() >= 2) {
        return payloadToString(post.getParameters().get(1));
      }
    }
    return null;
  }

  /**
   * Extracts knowledge messages from the parameters bundle WITHOUT removing forgotten items.
   * This implements the paper's requirement that K is monotonic (never shrinks).
   * Forget items are tracked separately in ForgetContext.
   *
   * FIX 2: Monotonic knowledge includes:
   * - Everything from the PRECONDITION State (what was known before)
   * - Everything from the POSTCONDITION State (what is known after)
   * - Everything received (from RcvS preconditions)
   * This ensures K_{i+1} ⊇ K_i per the paper.
   *
   * @param parametersBundle the parameters bundle from which to extract knowledge
   * @return a set of knowledge messages (full K, not K minus Forget)
   */
  @Override
  public Set<Message> extractKnowledgeWithoutForgetRemoval(ParametersBundle parametersBundle) {
    Set<Message> knowledge = new LinkedHashSet<>();

    if (parametersBundle.getCollections() == null || parametersBundle.getCollections().isEmpty()) {
      log.warn("No collections found in parametersBundle");
      return knowledge;
    }

    @SuppressWarnings("unchecked")
    List<Rule> allRules = (List<Rule>) parametersBundle.getCollections().get(0);

    String currentRuleName = parametersBundle.getExtraContent("currentRuleName");
    if (currentRuleName == null || currentRuleName.isEmpty()) {
      log.warn("No current rule name found in parametersBundle");
      return knowledge;
    }

    Rule currentRule = null;
    for (Rule r : allRules) {
      if (currentRuleName.equals(r.getRule_name())) {
        currentRule = r;
        break;
      }
    }
    if (currentRule == null) {
      log.warn("Current rule not found: {}", currentRuleName);
      return knowledge;
    }

    if (RULES_TO_SKIP.contains(currentRule.getRule_name())) {
      log.info("Skipping knowledge extraction for rule: {}", currentRule.getRule_name());
      return knowledge;
    }

    log.info("Extracting MONOTONIC knowledge for rule: {}", currentRuleName);

    // FIX 2: Extract from PRECONDITION State first (what was already known)
    for (Fact pre : currentRule.getPreconditions()) {
      if (isStateFact(pre.getF_name()) && pre.getParameters().size() >= 3) {
        Object stateData = pre.getParameters().get(pre.getParameters().size() - 1);
        String stateRaw = payloadToString(stateData);
        log.info("Precondition State data: {}", stateRaw);
        knowledge.addAll(parseStateParam(stateRaw));
      }
    }

    // FIX 2: Extract from POSTCONDITION State (what is known after transition)
    for (Fact post : currentRule.getPostconditions()) {
      if (isStateFact(post.getF_name()) && post.getParameters().size() >= 3) {
        Object stateData = post.getParameters().get(post.getParameters().size() - 1);
        String stateRaw = payloadToString(stateData);
        log.info("Postcondition State data: {}", stateRaw);
        knowledge.addAll(parseStateParam(stateRaw));
      }
    }

    // FIX 2: Extract from RcvS preconditions (received messages)
    for (Fact pre : currentRule.getPreconditions()) {
      if (isInboundFact(pre.getF_name()) && pre.getParameters().size() >= 4) {
        // RcvS(sender, receiver, labels, values) - extract the values (last param)
        Object valuesData = pre.getParameters().get(pre.getParameters().size() - 1);
        String valuesRaw = payloadToString(valuesData);
        log.info("RcvS received data: {}", valuesRaw);
        knowledge.addAll(parseStateParam(valuesRaw));
      }
    }

    // DO NOT remove forgotten items - K is monotonic per Algorithm 1
    log.info("Full MONOTONIC knowledge K: {}",
             knowledge.stream().map(Message::represent).toList());

    return knowledge;
  }

  /**
   * Parses a raw string into a Message object according to Dolev-Yao Storage.
   *
   * @param raw the raw string to parse
   * @return a Message object representing the parsed string
   */
  private Message parseStringToMessage(String raw) {
    String str = raw.trim();

    //  Support Tamarin tuple syntax: <a,b,c> => Pair(a, Pair(b, c))
    // This is needed so State(...) can contribute Pair terms to knowledge (enabling Projection
    // trees).
    if (str.startsWith("<") && str.endsWith(">")) {
      String inner = str.substring(1, str.length() - 1).trim();
      if (inner.isEmpty()) return new Atom(str);
      List<String> parts = splitTopLevelCommas(inner);
      return buildNestedPair(parts);
    }

    if (str.startsWith("{") && str.contains("}_")) return parseEncryption(str);
    if (str.startsWith("(") && str.endsWith(")")) return parsePair(str);
    if (str.contains("(") && str.endsWith(")")) return parseFunction(str);
    // default => Atom
    return new Atom(str);
  }

  // Robustly parse {M}_{K}, with balanced braces in key
  /**
   * Parses a string representation of an encrypted message into an Encrypt object.
   *
   * @param str the string representation of the encrypted message
   * @return an Encrypt object representing the parsed message
   */
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
    int keyStart = sep + 2; // should be at '{' of key
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

  /**
   * Parses a string representation of a pair into a Pair object.
   *
   * @param str the string representation of the pair
   * @return a Pair object representing the parsed pair
   */
  private Message parsePair(String str) {
    // strip outer parentheses => (A, B)
    str = str.substring(1, str.length() - 1).trim();
    int commaPos = findTopLevelComma(str);
    if (commaPos < 0) return parseStringToMessage(str);
    String left = str.substring(0, commaPos).trim();
    String right = str.substring(commaPos + 1).trim();
    return new Pair(parseStringToMessage(left), parseStringToMessage(right));
  }

  /** Parses a string representation of a function into a Message. */
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

  /** Finds a top-level comma in a string (commas not buried in parentheses). */
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

  /** Splits a string by top-level commas, ignoring nested parentheses/angles. */
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

  /** Parses a State payload string like "<$uid, p1, p2, ~nh>" into atomic messages. */
  private Set<Message> parseStateParam(String payload) {
    Set<Message> out = new LinkedHashSet<>();
    String s = payload.trim();
    if (s.startsWith("<") && s.endsWith(">")) s = s.substring(1, s.length() - 1);
    for (String part : splitTopLevelCommas(s)) {
      if (!part.isBlank()) out.add(parseStringToMessage(part.trim()));
    }
    return out;
  }
}

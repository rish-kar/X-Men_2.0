package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.*;
import com.sermas.x.men.service.DerivationCheckService;
import com.sermas.x.men.service.DerivationService;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * DerivationCheckServiceImpl implements the DerivationCheckService interface, providing methods to
 * check if a target message is derivable from a set of knowledge messages, extract target messages
 * from rules, and extract knowledge from parameters bundles and rules.
 */
@Service
@Slf4j
public class DerivationCheckServiceImpl implements DerivationCheckService {

  private final DerivationService derivationService;

  private static final Set<String> RULES_TO_SKIP =
      Set.of("humansetup", "Setup", "ChanSndS", "ChanRcvS");

  /**
   * Constructor for DerivationCheckServiceImpl.
   *
   * @param derivationService the DerivationService to use for derivation operations
   */
  public DerivationCheckServiceImpl(DerivationService derivationService) {
    this.derivationService = derivationService;
  }

  /**
   * Checks if a target message can be derived from a set of knowledge messages.
   *
   * @param target the target message to check
   * @param knowledge the set of knowledge messages
   * @return true if the target can be derived from the knowledge, false otherwise
   */
  @Override
  public boolean isDerivable(Message target, Set<Message> knowledge) {
    // Dolev–Yao style backward derivation
    Set<String> derivations = derivationService.derive(target, knowledge, 5);
    derivationService.printDerivationTree(target, knowledge, 5);
    return !derivations.isEmpty();
  }

  /**
   * Extracts the target message from a rule by taking the last postcondition's last parameter.
   *
   * Example: if postconditions contain ... , TSnd(H,I,'response',<~p1,~nb,~nh>) then we pick
   * the TSnd fact (as it's last) and its last parameter <~p1,~nb,~nh> and parse that as target.
   * No fact names are hardcoded.
   *
   * @param rule the rule from which to extract the target message
   * @return the target message extracted from the rule, or null if unavailable
   */
  @Override
  public Message extractTargetFromRule(Rule rule) {
    List<Fact> posts = rule.getPostconditions();
    if (posts == null || posts.isEmpty()) return null;

    Fact lastFact = posts.get(posts.size() - 1);
    List<Object> params = lastFact.getParameters();
    if (params == null || params.isEmpty()) return null;

    Object lastParam = params.get(params.size() - 1);
    String paramStr = payloadToString(lastParam).trim();
    return parseTargetParam(paramStr);
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
   * Extracts knowledge messages from the PARAMETERS BUNDLE by:
   * 1) Locating the CURRENT rule
   * 2) Extracting the values inside the POSTCONDITION State that corresponds to the same State
   *    variable(s) present in the precondition (e.g., $User)
   * 3) Applying Forget(~x) by removing x from the knowledge
   *
   * This matches the requirement to use the rule's own postcondition as the basis of knowledge.
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
        knowledge.removeIf(msg -> (msg instanceof Atom a)
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
    ArrayList<ArrayList> col = new ArrayList<>();
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
   * Parses a raw string into a Message object according to Dolev-Yao Storage.
   *
   * @param raw the raw string to parse
   * @return a Message object representing the parsed string
   */
  private Message parseStringToMessage(String raw) {
    String str = raw.trim();

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
        if (depth == 0) { i++; break; }
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

  /**
   * Parses a string representation of a function into a Message.
   */
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

  /**
   * Finds a top-level comma in a string (commas not buried in parentheses).
   */
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

  /**
   * Splits a string by top-level commas, ignoring nested parentheses/angles.
   */
  private List<String> splitTopLevelCommas(String s) {
    List<String> parts = new ArrayList<>();
    int depth = 0; int last = 0;
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

  /**
   * Parses a State payload string like "<$uid, p1, p2, ~nh>" into atomic messages.
   */
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

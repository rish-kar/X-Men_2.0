package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.*;
import com.sermas.x.men.service.DerivationCheckService;
import com.sermas.x.men.service.DerivationService;
import java.util.*;
import java.util.stream.Collectors;
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

  /**
   * Builds a nested pair of messages from a list of string elements.
   *
   * @param elements the list of string elements to build the nested pair from
   * @return a Message object representing the nested pair
   */
  private Message buildNestedPair(List<String> elements) {
    if (elements.isEmpty()) {
      return null;
    }
    if (elements.size() == 1) {
      return parseStringToMessage(elements.get(0).trim());
    }

    Message first = parseStringToMessage(elements.get(0).trim());
    Message rest = buildNestedPair(elements.subList(1, elements.size()));
    return new Pair(first, rest);
  }

  /**
   * Extracts knowledge messages from a parameters bundle.
   *
   * @param parametersBundle the parameters bundle from which to extract knowledge
   * @return a set of knowledge messages extracted from the parameters bundle
   */
  @Override
  public Set<Message> extractKnowledge(ParametersBundle parametersBundle) {
    Set<Message> knowledge = new HashSet<>();

    if (parametersBundle.getCollections() == null || parametersBundle.getCollections().isEmpty()) {
      // Optionally log a warning here
      return knowledge;
    }

    @SuppressWarnings("unchecked")
    List<Rule> allRules = (List<Rule>) parametersBundle.getCollections().get(0);

    for (Rule rule : allRules) {
      // skip setup or channel rules
      if (RULES_TO_SKIP.contains(rule.getRule_name())) {
        continue;
      }

      // For each Fact in the negative label (actions), if the fact name is "Send" or "RcvS",
      // we parse the last parameter as known.
      for (Fact action : rule.getActions()) {
        String fname = action.getF_name();
        if ("Send".equals(fname) || "RcvS".equals(fname)) {
          List<Object> params = action.getParameters();
          if (!params.isEmpty()) {
            Object lastParam = params.get(params.size() - 1);
            if (lastParam instanceof Value val) {
              knowledge.add(parseStringToMessage(val.getName()));
            } else {
              knowledge.add(new Atom(lastParam.toString()));
            }
          }
        }
      }
    }
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

    if (str.startsWith("{") && str.contains("}_")) {
      // e.g. {M}_{K}
      return parseEncryption(str);
    } else if (str.startsWith("(") && str.endsWith(")")) {
      // e.g. (x, y)
      return parsePair(str);
    } else if (str.contains("(") && str.endsWith(")")) {
      // e.g. bal(x)
      return parseFunction(str);
    }
    // default => Atom
    return new Atom(str);
  }

  /**
   * Parses a string representation of an encrypted message into an Encrypt object.
   *
   * @param str the string representation of the encrypted message
   * @return an Encrypt object representing the parsed message
   */
  private Encrypt parseEncryption(String str) {
    // naive parse: e.g. { (x,y) }_{ k }
    int underscorePos = str.lastIndexOf("}_");
    if (underscorePos < 0) {
      return new Encrypt(new Atom(str), new Atom("UNKNOWN_KEY"));
    }
    String msgPart = str.substring(1, underscorePos); // skip leading '{'
    String keyPart =
        str.substring(underscorePos + 2, str.length() - 1); // skip '_{' and trailing '}'
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
    if (commaPos < 0) {
      // single chunk => treat as Atom
      return new Atom(str);
    }
    String leftStr = str.substring(0, commaPos).trim();
    String rightStr = str.substring(commaPos + 1).trim();
    Message left = parseStringToMessage(leftStr);
    Message right = parseStringToMessage(rightStr);
    return new Pair(left, right);
  }

  /**
   * Parses a string representation of a predictive function into a PredictiveFunction object.
   *
   * @param str the string representation of the predictive function
   * @return a PredictiveFunction object representing the parsed function
   */
  private PredictiveFunction parseFunction(String str) {
    // e.g. location($bookingqrcode), bal($oyster), etc.
    int parenIndex = str.indexOf('(');
    String fname = str.substring(0, parenIndex).trim();
    String inside = str.substring(parenIndex + 1, str.length() - 1).trim();
    List<String> argStrs = splitTopLevelCommas(inside);
    List<Message> args =
        argStrs.stream().map(this::parseStringToMessage).collect(Collectors.toList());
    return new PredictiveFunction(fname, args);
  }

  /**
   * Finds the position of the first top-level comma in a string, ignoring nested parentheses.
   *
   * @param s the string to search for a top-level comma
   * @return the index of the first top-level comma, or -1 if none is found
   */
  private int findTopLevelComma(String s) {
    int depth = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Splits a string by top-level commas, ignoring nested parentheses.
   *
   * @param s the string to split
   * @return a list of strings split by top-level commas
   */
  private List<String> splitTopLevelCommas(String s) {
    List<String> result = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        result.add(s.substring(start, i).trim());
        start = i + 1;
      }
    }
    if (start < s.length()) {
      result.add(s.substring(start).trim());
    }
    return result;
  }

  /**
   * Extracts knowledge messages from a rule.
   *
   * @param rule the rule from which to extract knowledge
   * @return a set of knowledge messages extracted from the rule
   */
  @Override
  public Set<Message> extractKnowledgeFromRule(Rule rule) {
    Set<Message> knowledge = new HashSet<>();

    if (RULES_TO_SKIP.contains(rule.getRule_name())) {
      return knowledge; // Empty set for rules to skip
    }

    for (Fact action : rule.getActions()) {
      String fname = action.getF_name();
      if ("Send".equals(fname) || "RcvS".equals(fname)) {
        List<Object> params = action.getParameters();
        if (!params.isEmpty()) {
          Object lastParam = params.get(params.size() - 1);
          if (lastParam instanceof Value val) {
            knowledge.add(parseStringToMessage(val.getName()));
          } else {
            knowledge.add(new Atom(lastParam.toString()));
          }
        }
      }
    }
    return knowledge;
  }

  /**
   * Extracts state knowledge from a list of human-readable rules.
   *
   * @param humanRules the list of human-readable rules
   * @return a map where keys are state IDs and values are sets of messages representing the state
   *     knowledge
   */
  @Override
  public Map<String, Set<Message>> extractStateKnowledgeFromRules(List<Rule> humanRules) {
    Map<String, Set<Message>> stateKnowledgeMap =
        new LinkedHashMap<>(); // Maintain insertion order explicitly

    for (Rule rule : humanRules) {
      String ruleName = rule.getRule_name();

      // Only Human rules
      if (ruleName.startsWith("H_")) {
        for (Fact fact : rule.getPostconditions()) {
          if ("State".equals(fact.getF_name())) {
            List<Object> parameters = fact.getParameters();
            if (parameters.size() >= 3) {
              //                            String stateId =
              // parameters.get(1).toString().replace("'", "").trim();
              Object lastParam = parameters.get(parameters.size() - 1);

              // Special check if state is empty: []
              if (lastParam.toString().equals("[]")) {
                stateKnowledgeMap.put(ruleName, new LinkedHashSet<>()); // explicitly empty
              } else {
                Set<Message> knowledge = parseStateParam(lastParam.toString());
                stateKnowledgeMap.put(ruleName, knowledge);
              }
            }
          }
        }
      }
    }
    return stateKnowledgeMap;
  }

  /**
   * Parses the state parameter from a string representation into a set of Message objects.
   *
   * @param paramString the string representation of the state parameter
   * @return a set of Message objects representing the parsed state parameter
   */
  private Set<Message> parseStateParam(String paramString) {
    Set<Message> knowledge = new LinkedHashSet<>();

    // Remove angle brackets if present
    paramString = paramString.trim();
    if (paramString.startsWith("<") && paramString.endsWith(">")) {
      paramString = paramString.substring(1, paramString.length() - 1);
    }

    // Split parameters carefully, respecting nested parentheses
    List<String> params = splitTopLevelCommas(paramString);

    for (String param : params) {
      knowledge.add(parseStringToMessage(param.trim()));
    }

    return knowledge;
  }

  /**
   * Extracts the state ID from a rule.
   *
   * @param rule the rule from which to extract the state ID
   * @return the state ID extracted from the rule
   */
  @Override
  public String extractStateIdFromRule(Rule rule) {
    for (Fact postcondition : rule.getPostconditions()) {
      if ("State".equals(postcondition.getF_name())) {
        List<Object> parameters = postcondition.getParameters();
        if (parameters.size() >= 2) {
          return parameters.get(1).toString().replace("'", "").trim();
        }
      }
    }
    return null; // If no state identifier found explicitly
  }
}

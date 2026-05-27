package com.xmen.utilities;

import com.xmen.model.Rule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class for extracting protocol values from Tamarin models. */
@Slf4j
@Component
public class SetupKnowledgeExtractor {

  /**
   * Extracts the principal (e.g. $Human) from the Setup rule
   *
   * @param rules List of rules from the protocol
   * @return The principal identifier or null if not found
   */
  public String extractPrincipalFromSetupRule(List<Rule> rules) {
    for (Rule rule : rules) {
      if (rule.getRule_name().equals("Setup")) {
        // Extract the value inside Setup() in the action facts
        // Fix: Update regex to properly capture the entire principal value
        Pattern setupPattern = Pattern.compile("--\\s*\\[[^\\[\\]]*?Setup\\((\\$[A-Za-z0-9_]+|'[A-Za-z0-9_]+'|[A-Za-z0-9_]+)\\)[^\\]]*\\]");
        Matcher setupMatcher = setupPattern.matcher(rule.toString());

        if (setupMatcher.find()) {
          String principal = setupMatcher.group(1);
          log.info("Extracted principal from Setup rule: {}", principal);
          return principal;
        }
      }
    }
    log.warn("No principal found in Setup rule");
    return null;
  }

  /**
   * Extracts type declarations related to the principal from rules like humansetup
   *
   * @param rules List of rules from the protocol
   * @param principal The principal identifier (e.g. $Human)
   * @return Map of values and their types, preserving order of insertion
   */
  public Map<String, String> extractPrincipalTypeValues(List<Rule> rules, String principal) {
    if (principal == null) {
      log.warn("Principal is null, will try to extract all type values");
    }

    // Using LinkedHashMap to preserve insertion order
    Map<String, String> valueTypeMap = new LinkedHashMap<>();

    // Look for the humansetup rule and extract !Type declarations
    for (Rule rule : rules) {
      if (!"humansetup".equalsIgnoreCase(rule.getRule_name())) {
        continue;
      }

      String ruleContent = rule.toString();

      // Pattern to match !Type declarations
      // Accept $var, ~var, 'constant', fun(args), nested pairs like <x,y>, or plain identifiers
      Pattern typePattern;
      if (principal != null) {
        typePattern = Pattern.compile(
                "!Type\\(" +
                        Pattern.quote(principal) +
                        "\\s*,\\s*'([^']+)'\\s*,\\s*" +
                        "([^)]+)" +
                        "\\)"
        );
      } else {
        // If no principal, match any !Type declaration
        typePattern = Pattern.compile(
                "!Type\\(" +
                        "([^,]+)" +
                        "\\s*,\\s*'([^']+)'\\s*,\\s*" +
                        "([^)]+)" +
                        "\\)"
        );
      }

      Matcher typeMatcher = typePattern.matcher(ruleContent);

      while (typeMatcher.find()) {
        String type;
        String value;
        if (principal != null) {
          type = typeMatcher.group(1).trim(); // e.g., 'card', 'balance', 'pw', 'msg'
          value = typeMatcher.group(2).trim(); // e.g., $oyster, bal($oyster), senc(m1,m2)
        } else {
          type = typeMatcher.group(2).trim();
          value = typeMatcher.group(3).trim();
        }

        valueTypeMap.put(value, type);
        log.debug("Found type declaration: {} -> {}", value, type);
      }

      // Also look for !Cred declarations (used in some protocols like TrialCase)
      // Pattern: !Cred('B','User1',senc(m1,m2))
      Pattern credPattern = Pattern.compile(
              "!Cred\\(" +
                      "([^,]+)" +  // First param (agent)
                      "\\s*,\\s*" +
                      "([^,]+)" +  // Second param (username/identifier)
                      "\\s*,\\s*" +
                      "([^)]+)" +  // Third param (password/value)
                      "\\)"
      );
      Matcher credMatcher = credPattern.matcher(ruleContent);
      while (credMatcher.find()) {
        String identifier = credMatcher.group(2).trim(); // e.g., 'User1'
        String credential = credMatcher.group(3).trim(); // e.g., senc(m1,m2)

        // Map the credential to a 'pw' type (password)
        valueTypeMap.put(credential, "pw");
        log.debug("Found credential declaration: {} -> pw", credential);
      }

      // Process only the first matching humansetup rule
      break;
    }

    // If nothing found in humansetup, try Setup rule for State-based knowledge
    if (valueTypeMap.isEmpty()) {
      for (Rule rule : rules) {
        if (!"Setup".equalsIgnoreCase(rule.getRule_name())) {
          continue;
        }

        String ruleContent = rule.toString();

        // Extract values from State postconditions
        // Pattern: State('H','1',<val1,val2,...>)
        Pattern statePattern = Pattern.compile("State\\([^)]*,\\s*<([^>]+)>\\s*\\)");
        Matcher stateMatcher = statePattern.matcher(ruleContent);

        while (stateMatcher.find()) {
          String values = stateMatcher.group(1);
          String[] parts = splitTopLevel(values);
          for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
              // Try to determine the type based on pattern
              String type = inferType(part);
              valueTypeMap.put(part, type);
              log.debug("Inferred type from State: {} -> {}", part, type);
            }
          }
        }
        break;
      }
    }

    if (valueTypeMap.isEmpty()) {
      log.warn("No type values found for principal: {}", principal);
    } else {
      log.info("Extracted {} type values for principal {}", valueTypeMap.size(), principal);
    }

    return valueTypeMap;
  }

  /**
   * Infers the type of a value based on its pattern
   */
  private String inferType(String value) {
    if (value.startsWith("'") && value.endsWith("'")) {
      return "username"; // Quoted values are usually usernames/constants
    } else if (value.startsWith("senc(") || value.startsWith("aenc(")) {
      return "pw"; // Encryption typically wraps passwords
    } else if (value.startsWith("<") || value.contains(",")) {
      return "pw"; // Pairs might be passwords
    } else if (value.startsWith("~")) {
      return "nonce"; // Tilde prefix indicates fresh nonces
    } else if (value.matches("m\\d+")) {
      return "msg"; // Variables like m1, m2 are messages
    } else {
      return "unknown";
    }
  }

  /**
   * Splits a string by commas at the top level (not inside nested brackets)
   */
  private String[] splitTopLevel(String input) {
    java.util.List<String> result = new java.util.ArrayList<>();
    int depth = 0;
    StringBuilder current = new StringBuilder();

    for (char c : input.toCharArray()) {
      if (c == '<' || c == '(' || c == '{') {
        depth++;
        current.append(c);
      } else if (c == '>' || c == ')' || c == '}') {
        depth--;
        current.append(c);
      } else if (c == ',' && depth == 0) {
        result.add(current.toString().trim());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }

    if (current.length() > 0) {
      result.add(current.toString().trim());
    }

    return result.toArray(new String[0]);
  }

  /**
   * Process a protocol model file to extract all relevant values
   *
   * @param rules List of rules from the protocol
   * @return Map of values and their types for the principal in the Setup rule
   */
  public Map<String, String> processProtocolModel(List<Rule> rules) {
    // Extract the principal from Setup rule
    String principal = extractPrincipalFromSetupRule(rules);

    // Extract type values for the principal
    return extractPrincipalTypeValues(rules, principal);
  }
}

package com.sermas.x.men.utilities;

import com.sermas.x.men.model.Rule;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
        Pattern setupPattern = Pattern.compile("--\\s*\\[[^\\[\\]]*?Setup\\((\\$[A-Za-z0-9_]+)\\)[^\\]]*\\]");
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
      log.warn("Principal is null, cannot extract type values");
      return Collections.emptyMap();
    }

    // Using LinkedHashMap to preserve insertion order
    Map<String, String> valueTypeMap = new LinkedHashMap<>();

    // Look for only the humansetup rule and extract !Type declarations
    for (Rule rule : rules) {
      if (!"humansetup".equalsIgnoreCase(rule.getRule_name())) {
        continue;
      }

      String ruleContent = rule.toString();

      // Accept $var, ~var, fun(args), or plain identifiers
      Pattern typePattern = Pattern.compile(
              "!Type\\(" +
                      Pattern.quote(principal) +
                      "\\s*,\\s*'([^']+)'\\s*,\\s*" +
                      "(\\$[A-Za-z0-9_]+|~[A-Za-z0-9_]+|[A-Za-z0-9_]+\\([^)]*\\)|[A-Za-z0-9_]+)" +
                      "\\)"
      );
    
      Matcher typeMatcher = typePattern.matcher(ruleContent);

      while (typeMatcher.find()) {
        String type = typeMatcher.group(1); // e.g., 'card', 'balance'
        String value = typeMatcher.group(2); // e.g., $oyster, bal($oyster)

        valueTypeMap.put(value, type);
        log.debug("Found type declaration: {} -> {}", value, type);
      }

      // Process only the first matching humansetup rule
      break;
    }

    if (valueTypeMap.isEmpty()) {
      log.warn("No type values found for principal: {}", principal);
    } else {
      log.info("Extracted {} type values for principal {}", valueTypeMap.size(), principal);
    }

    return valueTypeMap;
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

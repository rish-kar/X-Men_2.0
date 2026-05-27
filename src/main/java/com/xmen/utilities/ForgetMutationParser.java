package com.xmen.utilities;

import com.xmen.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ForgetMutationParser class is responsible for parsing rules to extract Forget facts. It processes
 * each rule to find Forget(...) facts, extracts the values, and stores them in a LinkedHashSet.
 */
@Component
@Slf4j
public class ForgetMutationParser {

  // Pattern to match rule names: rule RuleName:
  private static final Pattern RULE_PATTERN = Pattern.compile("rule\\s+(\\w+)\\s*:");

  // Pattern to match Forget facts: Forget(value)
  private static final Pattern FORGET_PATTERN = Pattern.compile("Forget\\s*\\(([^)]+)\\)");

  /**
   * Parses Forget mutations from a list of rules and adds them to the provided ParametersBundle.
   * First tries grammar-based parsing, then falls back to regex-based parsing if needed.
   *
   * @param rules The list of rules to parse.
   * @param parametersBundle The ParametersBundle to add Forget mutations to.
   * @param rawFileContent The raw file content for regex fallback (can be null).
   * @return The updated ParametersBundle with Forget mutations.
   */
  public static ParametersBundle parseForgetMutations(
      List<Rule> rules, ParametersBundle parametersBundle, String rawFileContent) {

    boolean foundForget = false;

    // First try grammar-based parsing
    for (Rule rule : rules) {
      if (rule.isHuman()) {
        LinkedHashSet<String> forgetSet = new LinkedHashSet<>();

        List<Fact> actions = rule.getActions();
        Iterator<Fact> iterator = actions.iterator();
        while (iterator.hasNext()) {
          Fact fact = iterator.next();
          if ("Forget".equals(fact.getF_name())) {
            if (fact.getParameters() != null && !fact.getParameters().isEmpty()) {
              for (Object param : fact.getParameters()) {
                String forgetVal = extractParameterValue(param);
                if (forgetVal != null && !forgetVal.isEmpty()) {
                  forgetSet.add(forgetVal);
                  log.debug("Extracted Forget value: {}", forgetVal);
                }
              }
            }
            iterator.remove();
          }
        }
        if (!forgetSet.isEmpty()) {
          parametersBundle.getForgetMutationSet().put(rule.getRule_name(), forgetSet);
          log.info("Found Forget values for rule {}: {}", rule.getRule_name(), forgetSet);
          foundForget = true;
        }
      }
    }

    // If no Forget found via grammar parsing and we have raw content, try regex fallback
    if (!foundForget && rawFileContent != null && !rawFileContent.isEmpty()) {
      log.info("Grammar-based parsing found no Forget facts, trying regex fallback");
      foundForget = parseForgetWithRegex(rawFileContent, parametersBundle);
    }

    if (!foundForget && parametersBundle.getForgetMutationSet().isEmpty()) {
      throw new IllegalArgumentException(
          "Forget function not found in any rule in the given input file.");
    }

    return parametersBundle;
  }

  /**
   * Legacy method for backward compatibility.
   */
  public static ParametersBundle parseForgetMutations(
      List<Rule> rules, ParametersBundle parametersBundle) {
    return parseForgetMutations(rules, parametersBundle, null);
  }

  /**
   * Parses Forget facts using regex from raw file content.
   * This is a fallback when grammar-based parsing fails due to complex syntax.
   */
  private static boolean parseForgetWithRegex(String content, ParametersBundle parametersBundle) {
    boolean foundAny = false;
    String currentRule = null;

    // Split by lines and process
    String[] lines = content.split("\\r?\\n");
    for (String line : lines) {
      // Check for rule start
      Matcher ruleMatcher = RULE_PATTERN.matcher(line);
      if (ruleMatcher.find()) {
        currentRule = ruleMatcher.group(1);
        log.debug("Found rule via regex: {}", currentRule);
      }

      // Check for Forget fact
      Matcher forgetMatcher = FORGET_PATTERN.matcher(line);
      while (forgetMatcher.find()) {
        String forgetValue = forgetMatcher.group(1).trim();
        if (forgetValue != null && !forgetValue.isEmpty()) {
          String ruleName = currentRule != null ? currentRule : "UnknownRule";

          // Get or create the forgetSet for this rule
          LinkedHashSet<String> forgetSet = parametersBundle.getForgetMutationSet()
              .computeIfAbsent(ruleName, k -> new LinkedHashSet<>());
          forgetSet.add(forgetValue);

          log.info("Found Forget value via regex in rule {}: {}", ruleName, forgetValue);
          foundAny = true;
        }
      }
    }

    return foundAny;
  }

  /**
   * Extracts the string value from a parameter of any supported type.
   * Handles Value, FSpecial, PSpecial, Nary_app, and String types.
   *
   * @param param The parameter object to extract the value from.
   * @return The string representation of the value, or null if not supported.
   */
  private static String extractParameterValue(Object param) {
    if (param == null) {
      return null;
    }

    if (param instanceof Value) {
      return ((Value) param).getName();
    } else if (param instanceof FSpecial) {
      // FSpecial represents function applications like senc{x}k
      return param.toString();
    } else if (param instanceof PSpecial) {
      // PSpecial represents tuple/group like <x, y>
      return param.toString();
    } else if (param instanceof Nary_app) {
      // Nary_app represents function calls like func(args)
      return param.toString();
    } else if (param instanceof String) {
      return (String) param;
    } else {
      // Fallback: use toString() representation
      log.debug("Unknown parameter type in Forget: {}, using toString()", param.getClass().getSimpleName());
      return param.toString();
    }
  }
}

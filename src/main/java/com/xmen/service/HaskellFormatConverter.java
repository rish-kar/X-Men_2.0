package com.xmen.service;

import com.xmen.model.Fact;
import com.xmen.model.Rule;
import com.xmen.model.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts SPTHY protocol rules into Haskell derivation service format.
 *
 * The Haskell service expects a format based on:
 * - Initial knowledge (ik0): public constants, agent names, public keys
 * - Messages (m1, m2, ...): protocol message exchanges
 * - Goal term (m6): what the intruder is trying to derive
 *
 * This converter maps our parsed SPTHY rules to that format.
 */
@Service
@Slf4j
public class HaskellFormatConverter {

  /**
   * Converts parsed SPTHY rules into Haskell-compatible format.
   *
   * Format expected by Haskell service:
   * INITIAL_KNOWLEDGE: agent1, agent2, pub(agent1), pub(agent2), ...
   * MESSAGE_1: aenc(pub(receiver), <nonce, sender>)
   * MESSAGE_2: ...
   * GOAL: h(nonce1, nonce2)
   *
   * @param rules Parsed SPTHY protocol rules
   * @param theoryName Name of the protocol theory
   * @return Formatted string for Haskell service
   */
  public String convertToHaskellFormat(ArrayList<Rule> rules, String theoryName) {
    log.info("Converting {} rules from theory '{}' to Haskell format", rules.size(), theoryName);

    StringBuilder haskellInput = new StringBuilder();

    // Header
    haskellInput.append("PROTOCOL: ").append(theoryName).append("\n\n");

    // Extract initial knowledge from setup/humansetup rules
    String initialKnowledge = extractInitialKnowledge(rules);
    haskellInput.append("INITIAL_KNOWLEDGE:\n").append(initialKnowledge).append("\n\n");

    // Extract protocol messages from rules with Snd/Rcv facts
    List<String> messages = extractProtocolMessages(rules);
    haskellInput.append("MESSAGES:\n");
    for (int i = 0; i < messages.size(); i++) {
      haskellInput.append("  M").append(i + 1).append(": ").append(messages.get(i)).append("\n");
    }
    haskellInput.append("\n");

    // Extract goal from lemmas or final state
    String goal = extractGoal(rules);
    haskellInput.append("GOAL: ").append(goal).append("\n");

    String result = haskellInput.toString();
    log.debug("Converted to Haskell format:\n{}", result);
    return result;
  }

  /**
   * Extracts initial intruder knowledge from setup rules.
   * Maps to: agent names, public keys, public constants
   */
  private String extractInitialKnowledge(ArrayList<Rule> rules) {
    StringBuilder ik = new StringBuilder();

    // Find setup or humansetup rule
    Rule setupRule = rules.stream()
        .filter(r -> r.getRule_name().toLowerCase().contains("setup"))
        .findFirst()
        .orElse(null);

    if (setupRule != null) {
      // Extract public constants from postconditions
      for (Fact fact : setupRule.getPostconditions()) {
        String factName = fact.getF_name();

        // Extract agent names and roles
        if (factName.equals("!Type") || factName.equals("State") || factName.equals("!Wallet")) {
          for (Object param : fact.getParameters()) {
            if (param instanceof Value) {
              String val = ((Value) param).getName();
              if (val.startsWith("$")) {
                // Agent/role name
                ik.append("  ").append(val.substring(1)).append("\n");
              }
            }
          }
        }
      }

      // Add default public keys
      ik.append("  pub(a)\n");
      ik.append("  pub(b)\n");
      ik.append("  pub(c)\n");
    } else {
      // Default minimal knowledge
      ik.append("  a\n  b\n  i\n");
      ik.append("  pub(a)\n  pub(b)\n  pub(i)\n");
    }

    return ik.toString();
  }

  /**
   * Extracts protocol message exchanges from rules with Snd/Rcv facts.
   */
  private List<String> extractProtocolMessages(ArrayList<Rule> rules) {
    List<String> messages = new ArrayList<>();

    for (Rule rule : rules) {
      // Look for SndS, SndDY, or Out facts in postconditions
      for (Fact fact : rule.getPostconditions()) {
        String factName = fact.getF_name();

        if (factName.equals("SndS") || factName.equals("SndDY") || factName.equals("Out")) {
          // Extract message content
          String message = extractMessageContent(fact, rule);
          if (!message.isEmpty()) {
            messages.add(message);
          }
        }
      }
    }

    return messages;
  }

  /**
   * Extracts message content from a Snd/Out fact.
   */
  private String extractMessageContent(Fact fact, Rule rule) {
    StringBuilder msg = new StringBuilder();

    // Fact format: SndS(Sender, Receiver, Message) or Out(<Sender, Receiver, Message>)
    List<Object> params = fact.getParameters();

    if (params.size() >= 3) {
      Object sender = params.get(0);
      Object receiver = params.get(1);
      Object content = params.get(2);

      msg.append("from ").append(formatValue(sender))
         .append(" to ").append(formatValue(receiver))
         .append(": ").append(formatValue(content));
    }

    return msg.toString();
  }

  /**
   * Extracts the goal/target term the intruder wants to derive.
   */
  private String extractGoal(ArrayList<Rule> rules) {
    // Look for the final message or a specific goal term
    // Default: try to derive shared session key

    Rule lastRule = rules.isEmpty() ? null : rules.get(rules.size() - 1);

    if (lastRule != null) {
      // Check for final state or specific term
      for (Fact fact : lastRule.getPostconditions()) {
        if (fact.getF_name().equals("State") && !fact.getParameters().isEmpty()) {
          // Extract the last state value as potential goal
          Object lastParam = fact.getParameters().get(fact.getParameters().size() - 1);
          return "derive(" + formatValue(lastParam) + ")";
        }
      }
    }

    // Default goal: session key or shared secret
    return "derive(shared_key)";
  }

  /**
   * Formats a Value object for Haskell representation.
   */
  private String formatValue(Object value) {
    if (value instanceof Value) {
      Value v = (Value) value;
      String name = v.getName();

      // Remove Tamarin-specific prefixes
      if (name.startsWith("$")) {
        return name.substring(1);
      } else if (name.startsWith("~")) {
        return name.substring(1);
      }

      return name;
    }

    return value.toString();
  }

  /**
   * Converts Haskell derivation response back to human-readable format.
   *
   * @param haskellResponse Raw response from Haskell service
   * @return Formatted derivation tree
   */
  public String formatDerivationTree(String haskellResponse) {
    log.info("Formatting Haskell derivation tree response");

    StringBuilder formatted = new StringBuilder();
    formatted.append("\n========================================\n");
    formatted.append("       DERIVATION TREE ANALYSIS        \n");
    formatted.append("========================================\n\n");
    formatted.append(haskellResponse);
    formatted.append("\n========================================\n");

    return formatted.toString();
  }
}


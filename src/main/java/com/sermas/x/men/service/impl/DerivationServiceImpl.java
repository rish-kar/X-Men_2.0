package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.Encrypt;
import com.sermas.x.men.model.Message;
import com.sermas.x.men.model.Pair;
import com.sermas.x.men.model.PredictiveFunction;
import com.sermas.x.men.service.DerivationService;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;


/**
 * DerivationServiceImpl class implements the DerivationService interface. This service provides
 * methods to derive a target message from a set of knowledge messages and to print the derivation
 * tree.
 */
@Service
public class DerivationServiceImpl implements DerivationService {

  /**
   * Derives a target message from a set of knowledge messages up to a specified depth limit
   * according to the Dolev-Yao Model.
   *
   * @param target the target message to derive
   * @param knowledge the set of knowledge messages
   * @param depthLimit the maximum depth for derivation
   * @return a set of strings representing the derived messages
   */
  @Override
  public Set<String> derive(Message target, Set<Message> knowledge, int depthLimit) {
    System.out.println(
        "\nDerive called with target: " + target.represent() + ", depthLimit: " + depthLimit);
    System.out.println(
        "Knowledge: " + knowledge.stream().map(Message::represent).collect(Collectors.toSet()));

    return deriveRecursive(target, knowledge, depthLimit, new LinkedList<>());
  }

  /**
   * Recursively derives the target message from the knowledge set, handling various message types
   * and applying the Dolev-Yao Model rules.
   *
   * @param target the target message to derive
   * @param knowledge the set of knowledge messages
   * @param depthLimit the maximum depth for derivation
   * @param history a list to keep track of the derivation history
   * @return a set of strings representing the derived messages
   */
  private Set<String> deriveRecursive(
      Message target, Set<Message> knowledge, int depthLimit, List<String> history) {
    Set<String> results = new HashSet<>();

    if (knowledge.contains(target)) {
      System.out.println("Target found directly in knowledge: " + target.represent());
      results.add("Initial: " + target.represent());
      return results; // Explicitly return here
    }

    if (depthLimit <= 0) {
      System.out.println("Depth limit reached for target: " + target.represent());
      return results;
    }

    // Handle Pair explicitly
    if (target instanceof Pair pair) {
      System.out.println("Target is a Pair: " + pair.represent());

      Set<String> leftDerivations =
          deriveRecursive(pair.getLeft(), knowledge, depthLimit - 1, append(history, "Pair-Left"));
      Set<String> rightDerivations =
          deriveRecursive(
              pair.getRight(), knowledge, depthLimit - 1, append(history, "Pair-Right"));

      if (!leftDerivations.isEmpty() && !rightDerivations.isEmpty()) {
        for (String left : leftDerivations) {
          for (String right : rightDerivations) {
            results.add("Pairing: (" + left + ", " + right + ") yields " + pair.represent());
          }
        }
      }

      return results;
    }

    // Handle Encrypt explicitly
    for (Message msg : knowledge) {
      if (msg instanceof Encrypt encrypt
          && encrypt.getMsg().equals(target)
          && !lastRule(history, "Encryption")) {
        System.out.println("Target can be obtained via Decryption: " + target.represent());

        Set<String> keyDerivations =
            deriveRecursive(
                encrypt.getKey(), knowledge, depthLimit - 1, append(history, "Decryption"));
        for (String key : keyDerivations) {
          results.add(
              "Decryption: ("
                  + encrypt.represent()
                  + " with "
                  + key
                  + ") yields "
                  + target.represent());
        }
        return results;
      }
    }

    // Handle Projection from Pair explicitly
    for (Message msg : knowledge) {
      if (msg instanceof Pair pair) {
        if (pair.getLeft().equals(target)) {
          System.out.println("Target obtained by projection (first): " + target.represent());
          results.add(
              "Projection (first): from " + pair.represent() + " yields " + target.represent());
          return results;
        }
        if (pair.getRight().equals(target)) {
          System.out.println("Target obtained by projection (second): " + target.represent());
          results.add(
              "Projection (second): from " + pair.represent() + " yields " + target.represent());
          return results;
        }
      }
    }

    // Handle PredictiveFunction explicitly
    if (target instanceof PredictiveFunction func) {
      System.out.println("Target is a PredictiveFunction: " + func.represent());
      List<Set<String>> argsDerivations =
          func.getArgs().stream()
              .map(
                  arg ->
                      deriveRecursive(
                          arg,
                          knowledge,
                          depthLimit - 1,
                          append(history, "PredictiveFunction-" + func.getName())))
              .collect(Collectors.toList());

      Set<List<String>> cartesianProducts = cartesianProduct(argsDerivations);
      for (List<String> combo : cartesianProducts) {
        results.add(
            "PredictiveFunction: "
                + func.getName()
                + "("
                + String.join(", ", combo)
                + ") yields "
                + func.represent());
      }
    }

    return results;
  }

  /**
   * Checks if the last rule in the history matches the given rule.
   *
   * @param history the list of applied rules
   * @param rule the rule to check against the last applied rule
   * @return true if the last rule matches, false otherwise
   */
  private boolean lastRule(List<String> history, String rule) {
    return !history.isEmpty() && history.get(history.size() - 1).equals(rule);
  }

  /**
   * Appends a rule to the history list and returns a new list.
   *
   * @param history the current history list
   * @param rule the rule to append
   * @return a new list with the appended rule
   */
  private List<String> append(List<String> history, String rule) {
    List<String> newHist = new LinkedList<>(history);
    newHist.add(rule);
    return newHist;
  }

  /**
   * Computes the Cartesian product of a list of sets.
   *
   * @param sets the list of sets to compute the Cartesian product for
   * @return a set of lists representing the Cartesian product
   */
  private Set<List<String>> cartesianProduct(List<Set<String>> sets) {
    Set<List<String>> result = new HashSet<>();
    result.add(new ArrayList<>());

    for (Set<String> set : sets) {
      Set<List<String>> temp = new HashSet<>();
      for (List<String> list : result) {
        for (String element : set) {
          List<String> newList = new ArrayList<>(list);
          newList.add(element);
          temp.add(newList);
        }
      }
      result = temp;
    }

    return result;
  }

  /**
   * Prints the derivation tree for the target message using the given knowledge set and depth
   * limit. This method prints a visual tree in the console.
   *
   * @param target The target message.
   * @param knowledge The set of known messages.
   * @param depthLimit The recursion depth limit.
   */
  @Override
  public void printDerivationTree(Message target, Set<Message> knowledge, int depthLimit) {
    System.out.println("\nDerivation Tree for target: " + target.represent());
    printDerivationRecursive(target, knowledge, depthLimit, 0);
  }

  /**
   * Recursively prints the derivation tree with indentation.
   *
   * @param target The current target message.
   * @param knowledge The set of known messages.
   * @param depthLimit The remaining depth limit.
   * @param indent The current indentation level.
   */
  private void printDerivationRecursive(
      Message target, Set<Message> knowledge, int depthLimit, int indent) {
    String indentStr = "  ".repeat(indent);
    if (knowledge.contains(target)) {
      System.out.println(indentStr + "Initial: " + target.represent());
      return;
    }
    if (depthLimit <= 0) {
      System.out.println(indentStr + "Depth limit reached for: " + target.represent());
      return;
    }
    // Handle Pair explicitly
    if (target instanceof Pair pair) {
      System.out.println(indentStr + "Pair: " + pair.represent());
      System.out.println(indentStr + "├── Left derivation:");
      printDerivationRecursive(pair.getLeft(), knowledge, depthLimit - 1, indent + 2);
      System.out.println(indentStr + "└── Right derivation:");
      printDerivationRecursive(pair.getRight(), knowledge, depthLimit - 1, indent + 2);
      return;
    }
    // Handle Encrypt explicitly
    for (Message msg : knowledge) {
      if (msg instanceof Encrypt encrypt && encrypt.getMsg().equals(target)) {
        System.out.println(indentStr + "Decryption: " + encrypt.represent());
        System.out.println(indentStr + "└── Key derivation:");
        printDerivationRecursive(encrypt.getKey(), knowledge, depthLimit - 1, indent + 2);
        return;
      }
    }
    // Handle Projection from Pair explicitly
    for (Message msg : knowledge) {
      if (msg instanceof Pair pair) {
        if (pair.getLeft().equals(target)) {
          System.out.println(indentStr + "Projection (first) from: " + pair.represent());
          return;
        }
        if (pair.getRight().equals(target)) {
          System.out.println(indentStr + "Projection (second) from: " + pair.represent());
          return;
        }
      }
    }
    // Handle PredictiveFunction explicitly
    if (target instanceof PredictiveFunction func) {
      System.out.println(indentStr + "PredictiveFunction: " + func.represent());
      int argIndex = 0;
      for (Message arg : func.getArgs()) {
        System.out.println(indentStr + "├── Arg " + argIndex + " derivation:");
        printDerivationRecursive(arg, knowledge, depthLimit - 1, indent + 2);
        argIndex++;
      }
      return;
    }
    // If no rule applies, print that no derivation was found at this branch.
    System.out.println(indentStr + "No further derivation found for: " + target.represent());
  }
}

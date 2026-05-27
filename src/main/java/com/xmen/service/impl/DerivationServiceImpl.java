package com.xmen.service.impl;

import com.xmen.model.*;
import com.xmen.service.DerivationService;
import com.xmen.service.derivation.DerivationConfig;
import com.xmen.service.derivation.DerivationTreePrinter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DerivationServiceImpl class implements the DerivationService interface. This service provides
 * methods to derive a target message from a set of knowledge messages and to print the derivation
 * tree.
 *
 * IMPORTANT: This implementation restricts projections to a whitelist of built-in Tamarin
 * functions and never decomposes user-defined functions to prevent exponential blow-up.
 */
@Service
public class DerivationServiceImpl implements DerivationService {

  /** Configuration for derivation (whitelist, user-defined functions, limits) */
  private DerivationConfig config = new DerivationConfig();

  /** Printer for clean derivation tree output */
  private DerivationTreePrinter printer = new DerivationTreePrinter(config);

  /**
   * Sets the derivation configuration (for controlling projections).
   * @param config The configuration to use
   */
  public void setConfig(DerivationConfig config) {
    this.config = config != null ? config : new DerivationConfig();
    this.printer = new DerivationTreePrinter(this.config);
  }

  /**
   * Gets the current derivation configuration.
   */
  public DerivationConfig getConfig() {
    return this.config;
  }

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
  public Set<String> deriveLimited(Message target, Set<Message> knowledge, int depthLimit) {
    System.out.println(
        "\nDerive called with target: " + target.represent() + ", depthLimit: " + depthLimit);
    System.out.println(
        "Knowledge: " + knowledge.stream().map(Message::represent).collect(Collectors.toSet()));

    return deriveRecursive(target, knowledge, depthLimit, new LinkedList<>());
  }

  @Override
  public Set<Derivation> deriveToDepth(
      Message target, Set<Message> knowledge, int depthLimit) {
    Set<Derivation> derivations =
        deriveAllRecursive(target, knowledge, depthLimit, new java.util.HashSet<>(), 0);
    // Use the printer with full context; it now handles the zero-derivation case too
    printer.printAllTrees(derivations, target, knowledge);
    return derivations;
  }

  /**
   * Same as {@link #deriveToDepth} but does NOT print the derivation tree.
   * Use this when the caller only needs the set of derivations (e.g. for blocking
   * analysis) and another caller is responsible for visualisation.
   */
  public Set<Derivation> deriveToDepthNoPrint(
      Message target, Set<Message> knowledge, int depthLimit) {
    return deriveAllRecursive(target, knowledge, depthLimit, new java.util.HashSet<>(), 0);
  }

  @Override
  public Set<Derivation> deriveToInfinity(Message target, Set<Message> knowledge) {
    // Use config max depth instead of truly infinite to prevent explosion
    Set<Derivation> derivations =
        deriveAllRecursive(target, knowledge, config.getMaxDepth(), new java.util.HashSet<>(), 0);
    // Use the printer with full context; it now handles the zero-derivation case too
    printer.printAllTrees(derivations, target, knowledge);
    return derivations;
  }

  @Override
  public void printAllDerivationTrees(Set<Derivation> trees) {
    // When only the trees are known, delegate to printer without extra context.
    // DerivationTreePrinter will fall back to a compact view.
    if (trees == null || trees.isEmpty()) {
      printer.printZeroDerivationReport(null, Collections.emptySet());
      return;
    }
    int i = 1;
    for (Derivation t : trees) {
      printer.printTree(t, i++);
    }
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

    // Use a separate variable to keep original history untouched for lambda capture
    List<String> updatedHistory = history;
    String norm = target.represent();
    if (updatedHistory.contains("GOAL:" + norm)) {
      return results;
    }
    updatedHistory = append(updatedHistory, "GOAL:" + norm);

    // Handle Pair explicitly
    if (target instanceof Pair pair) {
      System.out.println("Target is a Pair: " + pair.represent());

      Set<String> leftDerivations =
          deriveRecursive(
              pair.getLeft(), knowledge, depthLimit - 1, append(updatedHistory, "Pair-Left"));
      Set<String> rightDerivations =
          deriveRecursive(
              pair.getRight(), knowledge, depthLimit - 1, append(updatedHistory, "Pair-Right"));

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
          && !lastRule(updatedHistory, "Encryption")) {
        System.out.println("Target can be obtained via Decryption: " + target.represent());

        Set<String> keyDerivations =
            deriveRecursive(
                encrypt.getKey(), knowledge, depthLimit - 1, append(updatedHistory, "Decryption"));
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
    // IMPORTANT: Only decompose if the function is in the whitelist AND not user-defined
    if (target instanceof PredictiveFunction func) {
      String funcName = func.getName();

      // Check if this function should be decomposed
      if (!config.isDecomposable(funcName)) {
        // User-defined or blacklisted function - treat as opaque, no decomposition
        return results;
      }

      System.out.println("Target is a PredictiveFunction (decomposable): " + func.represent());
      final List<String> finalHistory = updatedHistory; // effectively final for lambda capture
      List<Set<String>> argsDerivations =
          func.getArgs().stream()
              .map(
                  arg ->
                      deriveRecursive(
                          arg,
                          knowledge,
                          depthLimit - 1,
                          append(finalHistory, "PredictiveFunction-" + func.getName())))
              .collect(java.util.stream.Collectors.toList());
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
    System.out.println("Knowledge: " + knowledge.stream().map(Message::represent).toList());
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
    // IMPORTANT: Only decompose if the function is in the whitelist AND not user-defined
    if (target instanceof PredictiveFunction func) {
      String funcName = func.getName();

      if (!config.isDecomposable(funcName)) {
        // User-defined or blacklisted function - treat as opaque
        System.out.println(indentStr + "Opaque function (not decomposable): " + func.represent());
        return;
      }

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
  
  // ------------- Specified Depth or Infinite Derivations Logic -------------------------

      // Use a String key to avoid relying on Message.hashCode/equals if they’re complex.
      // Alternatively, store Message itself if Message.equals/hashCode is strong.
      private String goalKey(Message m) {
            return (m == null) ? "null" : m.represent();
      }

      private Set<Derivation> deriveAllRecursive(
              Message target,
              Set<Message> knowledge,
              Integer depthLeft,                 // null => infinite
              Set<String> visitedGoals,           // cycle safety
              int recursionDepth                  // for limiting output depth
      ) {
            Set<Derivation> results = new java.util.LinkedHashSet<>();

            if (target == null) return results;

            if (depthLeft != null && depthLeft < 0) return results;

            String key = goalKey(target);
            if (visitedGoals.contains(key)) {
                  return results; // prevent cycles in infinite mode (and also helps bounded mode)
            }

            Set<String> nextVisited = new java.util.HashSet<>(visitedGoals);
            nextVisited.add(key);

            // 1) Initial
            if (knowledge != null && knowledge.contains(target)) {
                  results.add(new Derivation(target, "Initial", java.util.List.of()));
                  // DO NOT return; other derivations may exist.
            }

            // 2) Pair construction: target = <L,R>
            if (target instanceof Pair pair) {
                  Integer nextDepth = (depthLeft == null) ? null : depthLeft - 1;

                  Set<Derivation> leftDerivs = deriveAllRecursive(pair.getLeft(), knowledge, nextDepth, nextVisited, recursionDepth);
                  Set<Derivation> rightDerivs = deriveAllRecursive(pair.getRight(), knowledge, nextDepth, nextVisited, recursionDepth);

                  for (Derivation ld : leftDerivs) {
                        for (Derivation rd : rightDerivs) {
                              results.add(new Derivation(target, "Pairing", java.util.List.of(ld, rd)));
                        }
                  }
            }

            // 3) Decryption: for ALL Encrypt in knowledge where enc.msg == target
            if (knowledge != null) {
                  Integer nextDepth = (depthLeft == null) ? null : depthLeft - 1;

                  for (Message msg : knowledge) {
                        if (msg instanceof Encrypt enc && enc.getMsg().equals(target)) {
                              Set<Derivation> keyDerivs = deriveAllRecursive(enc.getKey(), knowledge, nextDepth, nextVisited, recursionDepth);
                              for (Derivation kd : keyDerivs) {
                                    results.add(new Derivation(target, "Decryption(" + enc.represent() + ")", java.util.List.of(kd)));
                              }
                        }
                  }
            }

            // 4) Projection: for ALL pairs in knowledge containing target
            // FIX D: Include the pair as an Initial premise so hypothesis extraction
            // correctly identifies the PAIR (not the projected component) as the hypothesis
            if (knowledge != null) {
                  for (Message msg : knowledge) {
                        if (msg instanceof Pair p) {
                              // Create an Initial node for the pair (the actual hypothesis from knowledge)
                              Derivation pairInitial = new Derivation(p, "Initial", java.util.List.of());

                              if (p.getLeft().equals(target)) {
                                    // The projection node has the pair as its premise
                                    results.add(new Derivation(target, "Projection-First", java.util.List.of(pairInitial)));
                              }
                              if (p.getRight().equals(target)) {
                                    results.add(new Derivation(target, "Projection-Second", java.util.List.of(pairInitial)));
                              }
                        }
                  }
            }

            // 5) PredictiveFunction: all combinations of argument derivations
            // IMPORTANT: Only decompose if the function is in the whitelist AND not user-defined
            if (target instanceof PredictiveFunction func) {
                  String funcName = func.getName();

                  // Check if this function should be decomposed
                  if (!config.isDecomposable(funcName)) {
                        // User-defined or blacklisted function - treat as opaque
                        // Only derivable if directly in knowledge (already handled above)
                        // DO NOT expand into arguments
                        return results;
                  }

                  // Whitelist function - decompose into arguments
                  Integer nextDepth = (depthLeft == null) ? null : depthLeft - 1;

                  java.util.List<java.util.List<Derivation>> argOptions = new java.util.ArrayList<>();
                  boolean impossible = false;

                  for (Message arg : func.getArgs()) {
                        Set<Derivation> argDerivs = deriveAllRecursive(arg, knowledge, nextDepth, nextVisited, recursionDepth);
                        if (argDerivs.isEmpty()) {
                              impossible = true;
                              break;
                        }
                        argOptions.add(new java.util.ArrayList<>(argDerivs));
                  }

                  if (!impossible) {
                        buildCartesian(func, target, argOptions, 0, new java.util.ArrayList<>(), results);
                  }
            }

            return results;
      }

      private void buildCartesian(
              PredictiveFunction func,
              Message target,
              java.util.List<java.util.List<Derivation>> options,
              int idx,
              java.util.List<Derivation> current,
              Set<Derivation> out
      ) {
            // Limit cartesian product explosion
            if (out.size() >= config.getMaxDerivationsPerTarget()) {
                  return;
            }

            if (idx == options.size()) {
                  out.add(new Derivation(target, "PredictiveFunction(" + func.getName() + ")", new java.util.ArrayList<>(current)));
                  return;
            }
            for (Derivation choice : options.get(idx)) {
                  if (out.size() >= config.getMaxDerivationsPerTarget()) {
                        return;
                  }
                  current.add(choice);
                  buildCartesian(func, target, options, idx + 1, current, out);
                  current.remove(current.size() - 1);
            }
      }

      private void printDerivationTreeFromNode(Derivation node, int indent) {
            String pad = "  ".repeat(Math.max(0, indent));
            System.out.println(pad + node.getRule() + " ⇒ " + node.getGoal().represent());
            for (Derivation premise : node.getPremises()) {
                  printDerivationTreeFromNode(premise, indent + 1);
            }
      }

}

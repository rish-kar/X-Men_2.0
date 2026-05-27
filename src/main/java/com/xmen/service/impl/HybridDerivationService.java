package com.xmen.service.impl;

import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.model.Rule;
import com.xmen.service.DerivationService;
import com.xmen.service.HaskellDerivationFetcher;
import com.xmen.service.HaskellFormatConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Set;

/**
 * Hybrid Derivation Service that can use either: 1. Haskell-based derivation (when enabled) 2.
 * Java-based derivation (fallback)
 *
 * <p>This service acts as a facade, delegating to the appropriate implementation.
 */
@Service
@Primary
@Slf4j
public class HybridDerivationService implements DerivationService {

  @Autowired private DerivationServiceImpl javaDerivationService;

  @Autowired private HaskellDerivationFetcher haskellService;

  @Autowired private HaskellFormatConverter converter;

  // Thread-local flag to store rules/theory for Haskell derivation
  private static final ThreadLocal<ArrayList<Rule>> currentRules =
      ThreadLocal.withInitial(ArrayList::new);
  private static final ThreadLocal<String> currentTheory = ThreadLocal.withInitial(() -> "");

  /**
   * Enable Haskell-based derivation for the current thread.
   *
   * @param rules The parsed rules to use for Haskell conversion
   * @param theoryName The theory name
   */
  public static void enableHaskellDerivation(ArrayList<Rule> rules, String theoryName) {
    currentRules.set(rules);
    currentTheory.set(theoryName);
    DerivationModeContext.enableHaskell();
    log.info("Haskell derivation ENABLED for theory: {}", theoryName);
  }

  /** Disable Haskell-based derivation and use Java implementation. */
  public static void disableHaskellDerivation() {
    currentRules.remove();
    currentTheory.remove();
    DerivationModeContext.disableHaskell();
    log.info("Haskell derivation DISABLED - using Java implementation");
  }

  @Override
  public Set<String> deriveLimited(Message target, Set<Message> knowledge, int depthLimit) {
    if (DerivationModeContext.isHaskellEnabled()) {
      return deriveUsingHaskell(target, knowledge, depthLimit);
    } else {
      return javaDerivationService.deriveLimited(target, knowledge, depthLimit);
    }
  }

  /** Derives using Haskell service. */
  private Set<String> deriveUsingHaskell(Message target, Set<Message> knowledge, int depthLimit) {
    log.info("Using HASKELL derivation service for target: {}", target.represent());

    try {
      // Check if Haskell service is available
      if (!haskellService.isServiceAvailable()) {
        log.warn("Haskell service unavailable, falling back to Java derivation");
        return javaDerivationService.deriveLimited(target, knowledge, depthLimit);
      }

      // Get rules and theory name from thread-local
      ArrayList<Rule> rules = currentRules.get();
      String theoryName = currentTheory.get();

      if (rules.isEmpty()) {
        log.warn("No rules available for Haskell conversion, falling back to Java derivation");
        return javaDerivationService.deriveLimited(target, knowledge, depthLimit);
      }

      // Call Haskell service with converted rules
      String derivationTree = haskellService.deriveAnalysisFromRules(rules, theoryName);

      // Print formatted derivation tree
      System.out.println("\n" + "=".repeat(80));
      System.out.println("HASKELL DERIVATION TREE FOR: " + theoryName);
      System.out.println("Target: " + target.represent());
      System.out.println("=".repeat(80));
      System.out.println(derivationTree);
      System.out.println("=".repeat(80) + "\n");

      // Parse Haskell response to determine if target is derivable
      boolean targetDerivable = parseHaskellDerivability(derivationTree, target.represent());

      log.info(
          "Haskell derivation result: target '{}' is {}",
          target.represent(),
          targetDerivable ? "DERIVABLE" : "NOT DERIVABLE");

      // Return results compatible with Java derivation service format
      Set<String> results = new java.util.HashSet<>();
      if (targetDerivable) {
        results.add("Haskell-Derived: " + target.represent());
      }

      return results;

    } catch (Exception e) {
      log.error("Error during Haskell derivation, falling back to Java: {}", e.getMessage(), e);
      return javaDerivationService.deriveLimited(target, knowledge, depthLimit);
    }
  }

  /** Parses Haskell derivation tree response to determine if target is derivable. */
  private boolean parseHaskellDerivability(String haskellResponse, String targetRepresentation) {
    // Simple heuristic: if response contains derivation recipes/steps, target is derivable
    // This should be enhanced based on actual Haskell service response format

    if (haskellResponse == null || haskellResponse.isEmpty()) {
      return false;
    }

    // Check if response contains derivation recipes (non-empty recipe list)
    // Haskell service returns recipes like [Label l_5, Constructor Aenc ...]
    boolean hasRecipes =
        haskellResponse.contains("[")
            && !haskellResponse.contains("[]")
            && (haskellResponse.contains("Label")
                || haskellResponse.contains("Constructor")
                || haskellResponse.contains("Destructor"));

    // Also check for explicit derivability statements
    boolean explicitlyDerivable =
        haskellResponse.toLowerCase().contains("can.*derive")
            || haskellResponse.contains("derivable")
            || haskellResponse.contains("yields");

    log.debug(
        "Haskell response analysis: hasRecipes={}, explicitlyDerivable={}",
        hasRecipes,
        explicitlyDerivable);

    return hasRecipes || explicitlyDerivable;
  }

  @Override
  public void printDerivationTree(Message target, Set<Message> knowledge, int depthLimit) {
    if (DerivationModeContext.isHaskellEnabled()) {
      log.info("Derivation tree already printed by Haskell service");
      // Already printed during deriveLimited() call
    } else {
      javaDerivationService.printDerivationTree(target, knowledge, depthLimit);
    }
  }

  // Functions not required to be implemented in this case since the response either comes from Haskell Script or triggers a
  // default limited size derivation tree with size hardcoded to 5

  @Override
  public Set<Derivation> deriveToDepth(Message target, Set<Message> knowledge, int depthLimit) {
    return Set.of();
  }

  @Override
  public Set<Derivation> deriveToInfinity(Message target, Set<Message> knowledge) {
    return Set.of();
  }

  @Override
  public void printAllDerivationTrees(Set<Derivation> trees) {}
}

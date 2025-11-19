package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.Message;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.DerivationService;
import com.sermas.x.men.service.DerivationTreeService;
import com.sermas.x.men.service.HaskellFormatConverter;
import java.util.ArrayList;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Hybrid Derivation Service that can use either:
 * 1. Haskell-based derivation (when enabled)
 * 2. Java-based derivation (fallback)
 * 
 * This service acts as a facade, delegating to the appropriate implementation.
 */
@Service
@Primary
@Slf4j
public class HybridDerivationService implements DerivationService {

  @Autowired
  private DerivationServiceImpl javaDerivationService;
  
  @Autowired
  private DerivationTreeService haskellService;
  
  @Autowired
  private HaskellFormatConverter converter;
  
  // Thread-local flag to enable/disable Haskell derivation
  private static final ThreadLocal<Boolean> useHaskell = ThreadLocal.withInitial(() -> false);
  private static final ThreadLocal<ArrayList<Rule>> currentRules = ThreadLocal.withInitial(ArrayList::new);
  private static final ThreadLocal<String> currentTheory = ThreadLocal.withInitial(() -> "");

  /**
   * Enable Haskell-based derivation for the current thread.
   * 
   * @param rules The parsed rules to use for Haskell conversion
   * @param theoryName The theory name
   */
  public static void enableHaskellDerivation(ArrayList<Rule> rules, String theoryName) {
    useHaskell.set(true);
    currentRules.set(rules);
    currentTheory.set(theoryName);
    log.info("Haskell derivation ENABLED for theory: {}", theoryName);
  }

  /**
   * Disable Haskell-based derivation and use Java implementation.
   */
  public static void disableHaskellDerivation() {
    useHaskell.set(false);
    currentRules.remove();
    currentTheory.remove();
    log.info("Haskell derivation DISABLED - using Java implementation");
  }

  @Override
  public Set<String> derive(Message target, Set<Message> knowledge, int depthLimit) {
    if (Boolean.TRUE.equals(useHaskell.get())) {
      return deriveUsingHaskell(target, knowledge, depthLimit);
    } else {
      return javaDerivationService.derive(target, knowledge, depthLimit);
    }
  }

  /**
   * Derives using Haskell service.
   */
  private Set<String> deriveUsingHaskell(Message target, Set<Message> knowledge, int depthLimit) {
    log.info("Using HASKELL derivation service for target: {}", target.represent());
    
    try {
      // Check if Haskell service is available
      if (!haskellService.isServiceAvailable()) {
        log.warn("Haskell service unavailable, falling back to Java derivation");
        return javaDerivationService.derive(target, knowledge, depthLimit);
      }

      // Get rules and theory name from thread-local
      ArrayList<Rule> rules = currentRules.get();
      String theoryName = currentTheory.get();

      if (rules.isEmpty()) {
        log.warn("No rules available for Haskell conversion, falling back to Java derivation");
        return javaDerivationService.derive(target, knowledge, depthLimit);
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
      
      log.info("Haskell derivation result: target '{}' is {}",
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
      return javaDerivationService.derive(target, knowledge, depthLimit);
    }
  }

  /**
   * Parses Haskell derivation tree response to determine if target is derivable.
   */
  private boolean parseHaskellDerivability(String haskellResponse, String targetRepresentation) {
    // Simple heuristic: if response contains derivation recipes/steps, target is derivable
    // This should be enhanced based on actual Haskell service response format
    
    if (haskellResponse == null || haskellResponse.isEmpty()) {
      return false;
    }

    // Check if response contains derivation recipes (non-empty recipe list)
    // Haskell service returns recipes like [Label l_5, Constructor Aenc ...]
    boolean hasRecipes = haskellResponse.contains("[") 
                      && !haskellResponse.contains("[]") 
                      && (haskellResponse.contains("Label") 
                          || haskellResponse.contains("Constructor")
                          || haskellResponse.contains("Destructor"));

    // Also check for explicit derivability statements
    boolean explicitlyDerivable = haskellResponse.toLowerCase().contains("can.*derive")
                               || haskellResponse.contains("derivable")
                               || haskellResponse.contains("yields");

    log.debug("Haskell response analysis: hasRecipes={}, explicitlyDerivable={}",
              hasRecipes, explicitlyDerivable);

    return hasRecipes || explicitlyDerivable;
  }

  @Override
  public void printDerivationTree(Message target, Set<Message> knowledge, int depthLimit) {
    if (Boolean.TRUE.equals(useHaskell.get())) {
      log.info("Derivation tree already printed by Haskell service");
      // Already printed during derive() call
    } else {
      javaDerivationService.printDerivationTree(target, knowledge, depthLimit);
    }
  }
}


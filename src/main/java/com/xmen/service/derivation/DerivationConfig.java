package com.xmen.service.derivation;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DerivationConfig defines which function symbols are allowed for projection/decomposition
 * in the derivation tree, and tracks user-defined functions that should remain opaque.
 *
 * GOAL: Stop exponential blow-up by limiting projections to a whitelist of built-in
 * Tamarin functions, and never decomposing user-defined functions.
 */
public class DerivationConfig {

    /**
     * Built-in Tamarin functions that ARE allowed for projection/decomposition.
     * These represent ~60% of major Dolev-Yao operations:
     * - pair/2: tuple constructor (decompose to fst/snd)
     * - fst/1, snd/1: projection operators
     * - senc/2, sdec/2: symmetric encryption
     * - aenc/2, adec/2, pk/1: asymmetric encryption
     * - sign/2, verify/3: digital signatures
     * - h/1: hashing
     */
    private static final Set<String> PROJECTION_WHITELIST = Set.of(
        "pair", "fst", "snd",           // Pair operations
        "senc", "sdec",                  // Symmetric encryption
        "aenc", "adec", "pk",            // Asymmetric encryption
        "sign", "verify",                // Signatures
        "h"                              // Hashing
    );

    /**
     * Built-in functions that are EXPLICITLY blocked from decomposition.
     * These include XOR, multiset, DH exponentiation, bilinear pairing to keep tree manageable.
     */
    private static final Set<String> DECOMPOSITION_BLACKLIST = Set.of(
        "xor", "++", "^", "*", "inv", "pmult", "em",  // DH and bilinear
        "zero", "one", "mult", "exp", "g"              // Group operations
    );

    /** User-defined functions parsed from the model's "functions:" section */
    private final Set<String> userDefinedFunctions;

    /** Maximum derivations per target (to prevent explosion) */
    private final int maxDerivationsPerTarget;

    /** Maximum depth for derivation tree */
    private final int maxDepth;

    public DerivationConfig() {
        this(Set.of(), 10, 15);
    }

    public DerivationConfig(Set<String> userDefinedFunctions) {
        this(userDefinedFunctions, 10, 15);
    }

    public DerivationConfig(Set<String> userDefinedFunctions, int maxDerivationsPerTarget, int maxDepth) {
        this.userDefinedFunctions = new HashSet<>(userDefinedFunctions);
        this.maxDerivationsPerTarget = maxDerivationsPerTarget;
        this.maxDepth = maxDepth;
    }

    /**
     * Checks if a function name is decomposable/projectable.
     *
     * @param funcName The function name (e.g., "senc", "bal", "pair")
     * @return true if the function can be decomposed, false otherwise
     */
    public boolean isDecomposable(String funcName) {
        if (funcName == null || funcName.isEmpty()) {
            return false;
        }

        String normalized = funcName.toLowerCase().trim();

        // User-defined functions are NEVER decomposable
        if (isUserDefined(normalized)) {
            return false;
        }

        // Blacklisted functions are NEVER decomposable
        if (DECOMPOSITION_BLACKLIST.contains(normalized)) {
            return false;
        }

        // Only whitelist functions are decomposable
        return PROJECTION_WHITELIST.contains(normalized);
    }

    /**
     * Checks if a function is user-defined (from the model's "functions:" section).
     */
    public boolean isUserDefined(String funcName) {
        if (funcName == null) return false;
        String normalized = funcName.toLowerCase().trim();
        return userDefinedFunctions.contains(normalized);
    }

    /**
     * Checks if a function is in the projection whitelist.
     */
    public boolean isInWhitelist(String funcName) {
        if (funcName == null) return false;
        return PROJECTION_WHITELIST.contains(funcName.toLowerCase().trim());
    }

    public Set<String> getUserDefinedFunctions() {
        return Collections.unmodifiableSet(userDefinedFunctions);
    }

    public Set<String> getProjectionWhitelist() {
        return PROJECTION_WHITELIST;
    }

    public int getMaxDerivationsPerTarget() {
        return maxDerivationsPerTarget;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Creates a DerivationConfig from a list of Function objects.
     */
    public static DerivationConfig fromFunctions(List<com.xmen.model.Function> functions) {
        Set<String> userDefined = new HashSet<>();
        if (functions != null) {
            for (com.xmen.model.Function f : functions) {
                if (f.getName() != null) {
                    userDefined.add(f.getName().toLowerCase().trim());
                }
            }
        }
        return new DerivationConfig(userDefined);
    }

    @Override
    public String toString() {
        return "DerivationConfig{" +
               "whitelist=" + PROJECTION_WHITELIST +
               ", userDefined=" + userDefinedFunctions +
               ", maxDerivations=" + maxDerivationsPerTarget +
               ", maxDepth=" + maxDepth +
               '}';
    }
}


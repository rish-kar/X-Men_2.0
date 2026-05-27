package com.xmen.service.forget;

import com.xmen.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * BlockingChecker implements the three blocking cases from the paper:
 * - Case 1 (weak): h is blocked if h ∈ Forget
 * - Case 2 (medium): h is blocked if h is derivable from Forget using ONLY pairing rules
 * - Case 3 (strong): h is blocked if h is derivable from Forget using ALL DY rules
 */
@Slf4j
@Component
public class BlockingChecker {

    private static final int MAX_DEPTH = 10; // Prevent infinite recursion

    /**
     * Checks if a hypothesis h is blocked according to the context's blocking mode.
     *
     * @param h the hypothesis to check
     * @param ctx the forget context
     * @return true if h is blocked, false otherwise
     */
    public boolean isBlocked(Message h, ForgetContext ctx) {
        if (h == null || ctx == null) return false;

        return switch (ctx.getBlockingMode()) {
            case CASE1_WEAK -> isBlockedCase1(h, ctx.getForgetSet());
            case CASE2_PAIRING -> isBlockedCase2(h, ctx.getForgetSet());
            case CASE3_FULL_DY -> isBlockedCase3(h, ctx.getForgetSet());
        };
    }

    /**
     * Case 1 (weak): h is blocked if h ∈ Forget
     */
    private boolean isBlockedCase1(Message h, Set<Message> forgetSet) {
        return forgetSet.contains(h);
    }

    /**
     * Case 2 (medium): h is blocked if h is derivable from Forget using only pairing rules.
     * This includes both:
     * - Decomposition: projecting components from pairs (fst/snd)
     * - Composition: building pairs from derivable components (pair introduction)
     */
    private boolean isBlockedCase2(Message h, Set<Message> forgetSet) {
        return isDerivableUnderPairingRules(h, forgetSet, new HashSet<>(), MAX_DEPTH);
    }

    /**
     * Checks if term is derivable from Forget using ONLY pairing rules.
     * Per paper Case 2:
     * - Decomposition: if (x,y) ∈ S, then x and y are derivable
     * - Composition: if both x and y are derivable, then (x,y) is derivable
     */
    private boolean isDerivableUnderPairingRules(Message term, Set<Message> forgetSet,
                                                  Set<String> visited, int depth) {
        if (term == null || depth <= 0) return false;

        String key = term.represent();
        if (visited.contains(key)) return false;
        visited.add(key);

        // Direct membership in Forget
        if (forgetSet.contains(term)) {
            return true;
        }

        // Build decomposition closure: all components reachable by projection
        Set<Message> decompositionClosure = buildPairingClosure(forgetSet);
        if (decompositionClosure.contains(term)) {
            return true;
        }

        // Composition: if term is a Pair, check if both components are derivable
        if (term instanceof Pair p) {
            Set<String> leftVisited = new HashSet<>(visited);
            Set<String> rightVisited = new HashSet<>(visited);

            boolean leftDerivable = isDerivableUnderPairingRules(p.getLeft(), forgetSet, leftVisited, depth - 1);
            boolean rightDerivable = isDerivableUnderPairingRules(p.getRight(), forgetSet, rightVisited, depth - 1);

            if (leftDerivable && rightDerivable) {
                return true;
            }
        }

        return false;
    }

    /**
     * Builds the closure of a set under pairing projections.
     * If (x, y) is in the set, both x and y are added to the closure.
     */
    private Set<Message> buildPairingClosure(Set<Message> initial) {
        Set<Message> closure = new LinkedHashSet<>(initial);
        boolean changed = true;
        int iterations = 0;

        while (changed && iterations < MAX_DEPTH) {
            changed = false;
            iterations++;
            Set<Message> toAdd = new LinkedHashSet<>();

            for (Message m : closure) {
                if (m instanceof Pair p) {
                    if (!closure.contains(p.getLeft())) {
                        toAdd.add(p.getLeft());
                        changed = true;
                    }
                    if (!closure.contains(p.getRight())) {
                        toAdd.add(p.getRight());
                        changed = true;
                    }
                }
            }
            closure.addAll(toAdd);
        }

        return closure;
    }

    /**
     * Case 3 (strong): h is blocked if h is derivable from Forget using all DY rules.
     * This includes pairing, projections, encryption, decryption, etc.
     */
    private boolean isBlockedCase3(Message h, Set<Message> forgetSet) {
        // Use the full DY derivation check with Forget as knowledge
        return isDerivableFromSet(h, forgetSet, MAX_DEPTH, new HashSet<>());
    }

    /**
     * Checks if target is derivable from the given set using DY rules.
     */
    private boolean isDerivableFromSet(Message target, Set<Message> knowledge, int depth, Set<String> visited) {
        if (depth <= 0) return false;
        if (target == null) return false;

        String key = target.represent();
        if (visited.contains(key)) return false;
        visited.add(key);

        // Direct membership
        if (knowledge.contains(target)) {
            return true;
        }

        // Projection: if any pair in knowledge contains target
        for (Message m : knowledge) {
            if (m instanceof Pair p) {
                if (p.getLeft().equals(target) || p.getRight().equals(target)) {
                    return true;
                }
            }
        }

        // Pairing: if target is a Pair, check if both components are derivable
        if (target instanceof Pair p) {
            if (isDerivableFromSet(p.getLeft(), knowledge, depth - 1, new HashSet<>(visited)) &&
                isDerivableFromSet(p.getRight(), knowledge, depth - 1, new HashSet<>(visited))) {
                return true;
            }
        }

        // Decryption: if {target}_{k} is in knowledge and k is derivable
        for (Message m : knowledge) {
            if (m instanceof Encrypt enc && enc.getMsg().equals(target)) {
                if (isDerivableFromSet(enc.getKey(), knowledge, depth - 1, new HashSet<>(visited))) {
                    return true;
                }
            }
        }

        // Function application: if target is a function, check if all args are derivable
        if (target instanceof PredictiveFunction func) {
            boolean allDerivable = true;
            for (Message arg : func.getArgs()) {
                if (!isDerivableFromSet(arg, knowledge, depth - 1, new HashSet<>(visited))) {
                    allDerivable = false;
                    break;
                }
            }
            if (allDerivable) return true;
        }

        return false;
    }

    /**
     * Extracts the hypotheses (leaves/initial terms) from a derivation tree.
     * Hypotheses are the terms taken directly from knowledge without further derivation.
     *
     * Per paper: hypotheses are leaf facts/terms used from knowledge.
     * Only "Initial" nodes are true hypotheses. Projection/Decryption nodes
     * now include their source term (pair/ciphertext) as an Initial premise.
     */
    public Set<Message> extractHypotheses(Derivation derivation) {
        Set<Message> hypotheses = new LinkedHashSet<>();
        extractHypothesesRecursive(derivation, hypotheses);
        return hypotheses;
    }

    private void extractHypothesesRecursive(Derivation node, Set<Message> hypotheses) {
        if (node == null) return;

        // "Initial" rule means this is a hypothesis from knowledge
        if ("Initial".equals(node.getRule())) {
            hypotheses.add(node.getGoal());
            return;
        }

        // For empty premises, only treat as hypothesis if rule is Initial
        // (non-Initial nodes with empty premises are unusual but should recurse)
        if (node.getPremises().isEmpty() && !"Initial".equals(node.getRule())) {
            // This shouldn't happen after fixes, but handle gracefully
            // Do NOT add as hypothesis - it's a derivation step, not a leaf
            return;
        }

        // Recurse into premises to find the actual Initial leaves
        for (Derivation premise : node.getPremises()) {
            extractHypothesesRecursive(premise, hypotheses);
        }
    }

    /**
     * Finds all blocked hypotheses in a derivation.
     */
    public Set<Message> findBlockedHypotheses(Derivation derivation, ForgetContext ctx) {
        Set<Message> hypotheses = extractHypotheses(derivation);
        Set<Message> blocked = new LinkedHashSet<>();

        for (Message h : hypotheses) {
            if (isBlocked(h, ctx)) {
                blocked.add(h);
            }
        }

        return blocked;
    }

    /**
     * Checks if a derivation has any blocked hypotheses.
     */
    public boolean hasBlockedHypotheses(Derivation derivation, ForgetContext ctx) {
        return !findBlockedHypotheses(derivation, ctx).isEmpty();
    }
}


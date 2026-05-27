package com.xmen.service.forget;

import com.xmen.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ReplacementComputer implements Algorithm 1's replacement logic:
 * - For each blocked hypothesis, compute replacement candidates from updated knowledge K'
 * - Candidates must satisfy format constraints and label/type constraints
 * - Generate all combinations (cartesian product) of replacements for variants
 */
@Slf4j
@Component
public class ReplacementComputer {

    private static final int MAX_VARIANTS = 100; // Cap to prevent explosion

    @Autowired
    private BlockingChecker blockingChecker;

    /**
     * Computes the replacement set R_m̄ for a blocked hypothesis.
     * Per Algorithm 1:
     * - Candidate must NOT be blocked in ctx'
     * - Candidate must have matching format
     * - Candidate must satisfy label/type constraints
     *
     * @param blocked the blocked hypothesis to replace
     * @param ctx the updated context (K' and Forget')
     * @return set of valid replacement candidates
     */
    public Set<Message> computeReplacementSet(Message blocked, ForgetContext ctx) {
        Set<Message> replacements = new LinkedHashSet<>();

        if (blocked == null || ctx == null) {
            return replacements;
        }

        String blockedType = ctx.getTypeOf(blocked);
        TermFormat blockedFormat = TermFormat.formatOf(blocked);

        log.debug("Computing replacements for blocked hypothesis: {} (type={}, format={})",
                  blocked.represent(), blockedType, blockedFormat);

        // Search through knowledge K' for candidates
        for (Message candidate : ctx.getKnowledge()) {
            // Skip if candidate equals blocked
            if (candidate.equals(blocked)) {
                continue;
            }

            // Candidate must NOT be blocked
            if (blockingChecker.isBlocked(candidate, ctx)) {
                log.debug("  Candidate {} is blocked, skipping", candidate.represent());
                continue;
            }

            // Check format constraint: format(candidate) == format(blocked)
            if (!TermFormat.formatsCompatible(candidate, blocked)) {
                log.debug("  Candidate {} format mismatch, skipping", candidate.represent());
                continue;
            }

            // Check label/type constraint
            String candidateType = ctx.getTypeOf(candidate);
            if (blockedType != null && !blockedType.equals(candidateType)) {
                log.debug("  Candidate {} type mismatch ({} vs {}), skipping",
                         candidate.represent(), candidateType, blockedType);
                continue;
            }

            log.debug("  Candidate {} accepted as replacement", candidate.represent());
            replacements.add(candidate);
        }

        return replacements;
    }

    /**
     * Generates all variant messages by replacing blocked hypotheses.
     * For each combination of replacements, creates a new message with substitutions.
     *
     * @param originalMsg the original message to mutate
     * @param blockedToReplacements map from blocked hypothesis to its replacement set
     * @return list of variant messages (may be empty if no valid variants exist)
     */
    public List<Message> generateVariants(Message originalMsg,
                                          Map<Message, Set<Message>> blockedToReplacements) {
        List<Message> variants = new ArrayList<>();

        if (originalMsg == null || blockedToReplacements == null || blockedToReplacements.isEmpty()) {
            return variants;
        }

        // Check if any replacement set is empty (no variants possible)
        for (Map.Entry<Message, Set<Message>> entry : blockedToReplacements.entrySet()) {
            if (entry.getValue().isEmpty()) {
                log.info("Empty replacement set for blocked hypothesis {}, no variants possible",
                        entry.getKey().represent());
                return variants; // Return empty list
            }
        }

        // Generate cartesian product of all replacement choices
        List<Message> blockedList = new ArrayList<>(blockedToReplacements.keySet());
        List<Set<Message>> replacementSets = new ArrayList<>();
        for (Message blocked : blockedList) {
            replacementSets.add(blockedToReplacements.get(blocked));
        }

        // Generate all combinations
        List<List<Message>> combinations = cartesianProduct(replacementSets);

        // For each combination, create a variant by substitution
        int variantCount = 0;
        for (List<Message> combination : combinations) {
            if (variantCount >= MAX_VARIANTS) {
                log.warn("Reached maximum variant limit ({}), truncating", MAX_VARIANTS);
                break;
            }

            // Build substitution map
            Map<Message, Message> substitution = new HashMap<>();
            for (int i = 0; i < blockedList.size(); i++) {
                substitution.put(blockedList.get(i), combination.get(i));
            }

            // Apply substitution to create variant
            Message variant = applySubstitution(originalMsg, substitution);
            if (variant != null) {
                variants.add(variant);
                variantCount++;
            }
        }

        log.info("Generated {} variants for message {}", variants.size(), originalMsg.represent());
        return variants;
    }

    /**
     * Applies a substitution map to a message, replacing occurrences structurally.
     *
     * FIX 3 (Paper Algorithm 1): Substitutions should only replace BLOCKED HYPOTHESES,
     * not every occurrence of a blocked term inside other messages.
     *
     * For example, if $oyster is blocked and we have bal($oyster):
     * - If bal($oyster) is NOT blocked, it should remain UNCHANGED
     * - We should NOT recursively replace $oyster inside bal()
     *
     * This means: only replace at the TOP level where the entire message matches
     * a key in the substitution map. Do NOT recurse into function arguments
     * unless the function itself is being replaced.
     */
    public Message applySubstitution(Message msg, Map<Message, Message> substitution) {
        if (msg == null) return null;

        // Check if entire message is being substituted (this is the PRIMARY case)
        if (substitution.containsKey(msg)) {
            return substitution.get(msg);
        }

        // For Pairs: the pair itself is not in substitution, so recurse into components
        // This is correct because pairs are just structural containers, not semantic units
        if (msg instanceof Pair p) {
            Message newLeft = applySubstitution(p.getLeft(), substitution);
            Message newRight = applySubstitution(p.getRight(), substitution);
            // Only create new pair if something changed
            if (newLeft != p.getLeft() || newRight != p.getRight()) {
                return new Pair(newLeft, newRight);
            }
            return msg;
        }

        // For Encryptions: similar to pairs, encryption is structural
        if (msg instanceof Encrypt e) {
            Message newMsg = applySubstitution(e.getMsg(), substitution);
            Message newKey = applySubstitution(e.getKey(), substitution);
            if (newMsg != e.getMsg() || newKey != e.getKey()) {
                return new Encrypt(newMsg, newKey);
            }
            return msg;
        }

        // FIX 3: For Functions like bal($oyster), do NOT recurse into arguments!
        // If bal($oyster) itself is not in the substitution map, leave it unchanged.
        // This is the key difference from the previous (wrong) behavior.
        if (msg instanceof PredictiveFunction) {
            // The function is NOT in the substitution map, so return as-is
            // Do NOT modify the function's arguments
            return msg;
        }

        // Atom or unknown - return as-is (no recursive modification possible)
        return msg;
    }

    /**
     * Computes cartesian product of multiple sets.
     */
    private List<List<Message>> cartesianProduct(List<Set<Message>> sets) {
        List<List<Message>> result = new ArrayList<>();
        if (sets.isEmpty()) {
            result.add(new ArrayList<>());
            return result;
        }

        result.add(new ArrayList<>());

        for (Set<Message> set : sets) {
            List<List<Message>> newResult = new ArrayList<>();
            for (List<Message> existing : result) {
                for (Message element : set) {
                    if (newResult.size() >= MAX_VARIANTS) {
                        return newResult;
                    }
                    List<Message> newList = new ArrayList<>(existing);
                    newList.add(element);
                    newResult.add(newList);
                }
            }
            result = newResult;
        }

        return result;
    }

    /**
     * Converts a Message to its string representation for use in rule rewriting.
     */
    public String messageToString(Message msg) {
        if (msg == null) return "";

        if (msg instanceof Atom a) {
            return a.getValue();
        } else if (msg instanceof Pair p) {
            return "<" + messageToString(p.getLeft()) + "," + messageToString(p.getRight()) + ">";
        } else if (msg instanceof Encrypt e) {
            return "senc(" + messageToString(e.getMsg()) + "," + messageToString(e.getKey()) + ")";
        } else if (msg instanceof PredictiveFunction f) {
            StringBuilder sb = new StringBuilder(f.getName());
            sb.append("(");
            for (int i = 0; i < f.getArgs().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(messageToString(f.getArgs().get(i)));
            }
            sb.append(")");
            return sb.toString();
        }

        return msg.represent();
    }
}


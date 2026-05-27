package com.xmen.service.forget;

import com.xmen.model.Atom;
import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlockingChecker implementing the three blocking cases from the paper.
 */
class BlockingCheckerTest {

    private BlockingChecker blockingChecker;

    @BeforeEach
    void setUp() {
        blockingChecker = new BlockingChecker();
    }

    @Test
    @DisplayName("Case 1 (weak): h is blocked if h ∈ Forget")
    void testCase1WeakBlocking() {
        // Setup
        Set<Message> knowledge = new LinkedHashSet<>();
        knowledge.add(new Atom("m1"));
        knowledge.add(new Atom("m2"));
        knowledge.add(new Atom("m3"));

        Set<Message> forgetSet = new LinkedHashSet<>();
        forgetSet.add(new Atom("m2"));

        ForgetContext ctx = new ForgetContext(knowledge, forgetSet, BlockingMode.CASE1_WEAK, null);

        // Test
        assertFalse(blockingChecker.isBlocked(new Atom("m1"), ctx), "m1 should not be blocked");
        assertTrue(blockingChecker.isBlocked(new Atom("m2"), ctx), "m2 should be blocked (in Forget)");
        assertFalse(blockingChecker.isBlocked(new Atom("m3"), ctx), "m3 should not be blocked");
    }

    @Test
    @DisplayName("Case 2 (pairing): h is blocked if derivable from Forget via pairing")
    void testCase2PairingBlocking() {
        // Setup: Forget contains a pair (m1, m2)
        Set<Message> knowledge = new LinkedHashSet<>();
        knowledge.add(new Atom("m1"));
        knowledge.add(new Atom("m2"));
        knowledge.add(new Atom("m3"));

        Set<Message> forgetSet = new LinkedHashSet<>();
        Pair forgottenPair = new Pair(new Atom("m1"), new Atom("m2"));
        forgetSet.add(forgottenPair);

        ForgetContext ctx = new ForgetContext(knowledge, forgetSet, BlockingMode.CASE2_PAIRING, null);

        // Test: The pair is blocked
        assertTrue(blockingChecker.isBlocked(forgottenPair, ctx), "The pair should be blocked");
        // Components of the pair should also be blocked (derivable via projection)
        assertTrue(blockingChecker.isBlocked(new Atom("m1"), ctx), "m1 should be blocked (projectable from forgotten pair)");
        assertTrue(blockingChecker.isBlocked(new Atom("m2"), ctx), "m2 should be blocked (projectable from forgotten pair)");
        // m3 is not in the closure
        assertFalse(blockingChecker.isBlocked(new Atom("m3"), ctx), "m3 should not be blocked");
    }

    @Test
    @DisplayName("Case 2: Nested pair projection closure")
    void testCase2NestedPairClosure() {
        // Setup: Forget contains ((m1, m2), m3)
        Set<Message> knowledge = new LinkedHashSet<>();
        Set<Message> forgetSet = new LinkedHashSet<>();

        Pair inner = new Pair(new Atom("m1"), new Atom("m2"));
        Pair outer = new Pair(inner, new Atom("m3"));
        forgetSet.add(outer);

        ForgetContext ctx = new ForgetContext(knowledge, forgetSet, BlockingMode.CASE2_PAIRING, null);

        // Test: All nested components should be blocked
        assertTrue(blockingChecker.isBlocked(outer, ctx), "Outer pair should be blocked");
        assertTrue(blockingChecker.isBlocked(inner, ctx), "Inner pair should be blocked");
        assertTrue(blockingChecker.isBlocked(new Atom("m1"), ctx), "m1 should be blocked");
        assertTrue(blockingChecker.isBlocked(new Atom("m2"), ctx), "m2 should be blocked");
        assertTrue(blockingChecker.isBlocked(new Atom("m3"), ctx), "m3 should be blocked");
        assertFalse(blockingChecker.isBlocked(new Atom("m4"), ctx), "m4 should not be blocked");
    }

    @Test
    @DisplayName("Case 3 (full DY): h is blocked if derivable from Forget via all DY rules")
    void testCase3FullDYBlocking() {
        // Setup: Forget contains m1 and m2
        Set<Message> knowledge = new LinkedHashSet<>();
        Set<Message> forgetSet = new LinkedHashSet<>();
        forgetSet.add(new Atom("m1"));
        forgetSet.add(new Atom("m2"));

        ForgetContext ctx = new ForgetContext(knowledge, forgetSet, BlockingMode.CASE3_FULL_DY, null);

        // Direct membership
        assertTrue(blockingChecker.isBlocked(new Atom("m1"), ctx), "m1 should be blocked");
        assertTrue(blockingChecker.isBlocked(new Atom("m2"), ctx), "m2 should be blocked");

        // Pair constructible from Forget items - should be blocked in Case 3
        Pair constructiblePair = new Pair(new Atom("m1"), new Atom("m2"));
        assertTrue(blockingChecker.isBlocked(constructiblePair, ctx), "Pair(m1,m2) should be blocked (constructible)");

        // m3 not in Forget and not derivable
        assertFalse(blockingChecker.isBlocked(new Atom("m3"), ctx), "m3 should not be blocked");
    }

    @Test
    @DisplayName("Extract hypotheses from derivation tree")
    void testExtractHypotheses() {
        // Build a simple derivation tree: Pairing from two Initial derivations
        Derivation leftLeaf = new Derivation(new Atom("m1"), "Initial", List.of());
        Derivation rightLeaf = new Derivation(new Atom("m2"), "Initial", List.of());
        Pair target = new Pair(new Atom("m1"), new Atom("m2"));
        Derivation pairingDerivation = new Derivation(target, "Pairing", List.of(leftLeaf, rightLeaf));

        Set<Message> hypotheses = blockingChecker.extractHypotheses(pairingDerivation);

        assertEquals(2, hypotheses.size(), "Should have 2 hypotheses");
        assertTrue(hypotheses.contains(new Atom("m1")), "Should contain m1");
        assertTrue(hypotheses.contains(new Atom("m2")), "Should contain m2");
    }

    @Test
    @DisplayName("Find blocked hypotheses in derivation")
    void testFindBlockedHypotheses() {
        // Build derivation with 2 hypotheses
        Derivation leftLeaf = new Derivation(new Atom("m1"), "Initial", List.of());
        Derivation rightLeaf = new Derivation(new Atom("m2"), "Initial", List.of());
        Pair target = new Pair(new Atom("m1"), new Atom("m2"));
        Derivation derivation = new Derivation(target, "Pairing", List.of(leftLeaf, rightLeaf));

        // Forget m1
        Set<Message> forgetSet = new LinkedHashSet<>();
        forgetSet.add(new Atom("m1"));
        ForgetContext ctx = new ForgetContext(Set.of(), forgetSet, BlockingMode.CASE1_WEAK, null);

        Set<Message> blockedHypotheses = blockingChecker.findBlockedHypotheses(derivation, ctx);

        assertEquals(1, blockedHypotheses.size(), "Should have 1 blocked hypothesis");
        assertTrue(blockedHypotheses.contains(new Atom("m1")), "m1 should be blocked");
    }
}


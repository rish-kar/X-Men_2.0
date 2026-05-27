package com.xmen.service.forget;

import com.xmen.model.*;
import com.xmen.service.derivation.DerivationConfig;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.impl.DerivationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for Forget Mutation per Algorithm 1:
 * - When Forget($oyster), only $oyster should be replaced
 * - bal($oyster) should remain unchanged
 */
@DisplayName("Forget Mutation E2E Tests")
public class ForgetMutationE2ETest {

    private BlockingChecker blockingChecker;
    private ReplacementComputer replacementComputer;
    private DerivationServiceImpl derivationService;

    @BeforeEach
    void setup() {
        blockingChecker = new BlockingChecker();
        replacementComputer = new ReplacementComputer();
        derivationService = new DerivationServiceImpl();

        // Configure bal as user-defined (not decomposable)
        DerivationConfig config = new DerivationConfig(Set.of("bal"), 10, 10);
        derivationService.setConfig(config);
    }

    @Test
    @DisplayName("Forget($oyster) should produce variant <$ccard, bal($oyster), ~gid>")
    void testForgetOysterProducesCorrectVariant() {
        // Given: Oyster scenario
        Message oyster = new Atom("$oyster");
        Message ccard = new Atom("$ccard");
        Message balOyster = new PredictiveFunction("bal", List.of(oyster));
        Message balCcard = new PredictiveFunction("bal", List.of(ccard));
        Message gid = new Atom("~gid");

        // Target: <$oyster, bal($oyster), ~gid>
        Message target = new Pair(oyster, new Pair(balOyster, gid));

        // Knowledge: {$oyster, $ccard, bal($oyster), bal($ccard), ~gid}
        Set<Message> knowledge = new LinkedHashSet<>();
        knowledge.add(oyster);
        knowledge.add(ccard);
        knowledge.add(balOyster);
        knowledge.add(balCcard);
        knowledge.add(gid);

        // Forget set: only $oyster
        Set<Message> forgetSet = new HashSet<>();
        forgetSet.add(oyster);

        // Build context
        ForgetContext ctx = new ForgetContext(knowledge, forgetSet, BlockingMode.CASE1_WEAK, new HashMap<>());

        // Get derivations
        Set<Derivation> derivations = derivationService.deriveToDepth(target, knowledge, 5);
        assertThat(derivations).isNotEmpty();

        // For each derivation, check blocked hypotheses
        for (Derivation derivation : derivations) {
            Set<Message> hypotheses = blockingChecker.extractHypotheses(derivation);

            // Compute blocked hypotheses
            Set<Message> blockedHypotheses = new LinkedHashSet<>();
            for (Message h : hypotheses) {
                if (blockingChecker.isBlocked(h, ctx)) {
                    blockedHypotheses.add(h);
                }
            }

            // CRITICAL CHECK: bal($oyster) should NOT be in blocked hypotheses
            assertThat(blockedHypotheses)
                .as("bal($oyster) should NOT be blocked when only $oyster is in forget set")
                .doesNotContain(balOyster);

            // Only $oyster should be blocked (if present in hypotheses)
            for (Message blocked : blockedHypotheses) {
                assertThat(blocked.represent())
                    .as("Only $oyster should be blocked")
                    .isEqualTo("$oyster");
            }
        }

        // Now compute replacement and generate variant
        Map<Message, Set<Message>> blockedToReplacements = new LinkedHashMap<>();
        blockedToReplacements.put(oyster, Set.of(ccard));

        List<Message> variants = replacementComputer.generateVariants(target, blockedToReplacements);
        assertThat(variants).hasSize(1);

        // Verify variant structure
        Message variant = variants.get(0);
        assertThat(variant).isInstanceOf(Pair.class);

        Pair variantPair = (Pair) variant;

        // First element should be $ccard
        assertThat(variantPair.getLeft().represent()).isEqualTo("$ccard");

        // Inner pair
        Pair innerPair = (Pair) variantPair.getRight();

        // bal($oyster) should be UNCHANGED - this is the critical test!
        Message balInVariant = innerPair.getLeft();
        assertThat(balInVariant).isInstanceOf(PredictiveFunction.class);
        assertThat(balInVariant.represent()).isEqualTo("bal($oyster)");

        // ~gid unchanged
        assertThat(innerPair.getRight().represent()).isEqualTo("~gid");

        // Final variant representation
        String variantStr = variant.represent();
        System.out.println("Generated variant: " + variantStr);

        // Should contain $ccard and bal($oyster), NOT bal($ccard)
        assertThat(variantStr).contains("$ccard");
        assertThat(variantStr).contains("bal($oyster)");
        assertThat(variantStr).doesNotContain("bal($ccard)");
    }

    @Test
    @DisplayName("Forget(bal($oyster)) should produce variant <$oyster, bal($ccard), ~gid>")
    void testForgetBalOysterProducesCorrectVariant() {
        // Given: bal($oyster) is forgotten (not $oyster)
        Message oyster = new Atom("$oyster");
        Message ccard = new Atom("$ccard");
        Message balOyster = new PredictiveFunction("bal", List.of(oyster));
        Message balCcard = new PredictiveFunction("bal", List.of(ccard));
        Message gid = new Atom("~gid");

        // Target: <$oyster, bal($oyster), ~gid>
        Message target = new Pair(oyster, new Pair(balOyster, gid));

        // Forget set: only bal($oyster)
        Set<Message> forgetSet = new HashSet<>();
        forgetSet.add(balOyster);

        // Blocked to replacements: bal($oyster) -> bal($ccard)
        Map<Message, Set<Message>> blockedToReplacements = new LinkedHashMap<>();
        blockedToReplacements.put(balOyster, Set.of(balCcard));

        List<Message> variants = replacementComputer.generateVariants(target, blockedToReplacements);
        assertThat(variants).hasSize(1);

        // Verify variant: <$oyster, bal($ccard), ~gid>
        Message variant = variants.get(0);
        Pair variantPair = (Pair) variant;

        // First element should still be $oyster
        assertThat(variantPair.getLeft().represent()).isEqualTo("$oyster");

        // Inner pair
        Pair innerPair = (Pair) variantPair.getRight();

        // bal should now be bal($ccard)
        assertThat(innerPair.getLeft().represent()).isEqualTo("bal($ccard)");

        // Final variant representation
        String variantStr = variant.represent();
        System.out.println("Generated variant: " + variantStr);

        assertThat(variantStr).contains("$oyster");
        assertThat(variantStr).contains("bal($ccard)");
        assertThat(variantStr).doesNotContain("bal($oyster)");
    }
}


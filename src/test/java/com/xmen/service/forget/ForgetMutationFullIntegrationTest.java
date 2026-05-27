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
 * Integration tests that verify the COMPLETE forget mutation flow works correctly
 * from derivation through variant generation and substitution application.
 */
@DisplayName("Forget Mutation Full Integration Tests")
public class ForgetMutationFullIntegrationTest {

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
    @DisplayName("Full flow: Forget($oyster) should only replace $oyster in substitution map")
    void testFullFlowForgetOyster() {
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

        ForgetContext ctx = new ForgetContext(knowledge, forgetSet, BlockingMode.CASE1_WEAK, new HashMap<>());

        // Step 1: Get derivations
        Set<Derivation> derivations = derivationService.deriveToDepth(target, knowledge, 5);
        assertThat(derivations).as("Should have derivations").isNotEmpty();

        System.out.println("Found " + derivations.size() + " derivations");

        // Step 2: Process each derivation as ForgetMutationStrategy would
        Map<Message, Set<Message>> blockedToReplacements = new LinkedHashMap<>();
        boolean foundUnblockedDerivation = false;

        for (Derivation derivation : derivations) {
            Set<Message> hypotheses = blockingChecker.extractHypotheses(derivation);
            System.out.println("Derivation hypotheses: " + hypotheses.stream().map(Message::represent).toList());

            Set<Message> blockedHypotheses = new LinkedHashSet<>();
            for (Message h : hypotheses) {
                if (blockingChecker.isBlocked(h, ctx)) {
                    blockedHypotheses.add(h);
                }
            }

            System.out.println("Blocked hypotheses: " + blockedHypotheses.stream().map(Message::represent).toList());

            if (blockedHypotheses.isEmpty()) {
                foundUnblockedDerivation = true;
                System.out.println("Found unblocked derivation!");
                break;
            }

            // Collect blocked -> replacement mappings
            for (Message blocked : blockedHypotheses) {
                if (!blockedToReplacements.containsKey(blocked)) {
                    // Compute replacement set for this blocked hypothesis
                    Set<Message> replacements = computeReplacementSet(blocked, ctx);
                    blockedToReplacements.put(blocked, replacements);
                }
            }
        }

        System.out.println("Final blockedToReplacements: ");
        for (Map.Entry<Message, Set<Message>> entry : blockedToReplacements.entrySet()) {
            System.out.println("  " + entry.getKey().represent() + " -> " +
                             entry.getValue().stream().map(Message::represent).toList());
        }

        // CRITICAL ASSERTION: blockedToReplacements should ONLY contain $oyster
        assertThat(blockedToReplacements.keySet())
            .as("blockedToReplacements should only contain blocked hypotheses")
            .allMatch(m -> m.represent().equals("$oyster"));

        // Step 3: Generate variant
        List<Message> variants = replacementComputer.generateVariants(target, blockedToReplacements);
        assertThat(variants).as("Should have at least one variant").isNotEmpty();

        System.out.println("Generated " + variants.size() + " variants:");
        for (Message v : variants) {
            System.out.println("  " + v.represent());
        }

        // CRITICAL ASSERTION: ALL variants must keep bal($oyster) unchanged!
        for (Message variant : variants) {
            String variantStr = variant.represent();

            assertThat(variantStr)
                .as("Every variant should contain bal($oyster) UNCHANGED - got: " + variantStr)
                .contains("bal($oyster)");

            assertThat(variantStr)
                .as("No variant should contain bal($ccard) - got: " + variantStr)
                .doesNotContain("bal($ccard)");
        }

        // Step 4: Simulate string substitution map building
        Map<String, String> stringSubstitution = new HashMap<>();
        for (Map.Entry<Message, Set<Message>> entry : blockedToReplacements.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                Message blocked = entry.getKey();
                Message replacement = entry.getValue().iterator().next();
                stringSubstitution.put(blocked.represent(), replacement.represent());
            }
        }

        System.out.println("String substitution map: " + stringSubstitution);

        // CRITICAL: String substitution should ONLY have $oyster -> $ccard
        assertThat(stringSubstitution)
            .as("String substitution should only contain $oyster -> $ccard")
            .containsOnlyKeys("$oyster")
            .containsEntry("$oyster", "$ccard");
    }

    /**
     * Simulates ReplacementComputer.computeReplacementSet behavior
     */
    private Set<Message> computeReplacementSet(Message blocked, ForgetContext ctx) {
        Set<Message> replacements = new LinkedHashSet<>();

        for (Message candidate : ctx.getKnowledge()) {
            // Not in forget set
            if (ctx.getForgetSet().contains(candidate)) continue;

            // Same format (simplified check)
            if (blocked.getClass() != candidate.getClass()) continue;

            // Same type for functions
            if (blocked instanceof PredictiveFunction bf && candidate instanceof PredictiveFunction cf) {
                if (!bf.getName().equals(cf.getName())) continue;
            }

            replacements.add(candidate);
        }

        return replacements;
    }
}



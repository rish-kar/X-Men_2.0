package com.xmen.service.forget;

import com.xmen.model.Atom;
import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.model.PredictiveFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReplacementComputer ensuring Algorithm 1 compliance:
 * - Only blocked hypotheses should be replaced
 * - Function arguments should NOT be recursively modified
 */
@DisplayName("ReplacementComputer Algorithm 1 Tests")
public class ReplacementComputerTest {

    private ReplacementComputer replacementComputer;

    @BeforeEach
    void setup() {
        replacementComputer = new ReplacementComputer();
    }

    @Test
    @DisplayName("FIX 3: bal($oyster) should remain unchanged when only $oyster is replaced")
    void testFunctionNotModifiedWhenOnlyArgumentBlocked() {
        // Given: target is <$oyster, bal($oyster), ~gid>
        Message oyster = new Atom("$oyster");
        Message ccard = new Atom("$ccard");
        Message balOyster = new PredictiveFunction("bal", List.of(oyster));
        Message gid = new Atom("~gid");
        
        // Build target as nested pairs: Pair($oyster, Pair(bal($oyster), ~gid))
        Message target = new Pair(oyster, new Pair(balOyster, gid));
        
        // Given: substitution only has $oyster -> $ccard
        Map<Message, Message> substitution = new HashMap<>();
        substitution.put(oyster, ccard);
        
        // When: apply substitution
        Message variant = replacementComputer.applySubstitution(target, substitution);
        
        // Then: $oyster should be replaced but bal($oyster) should remain unchanged
        assertThat(variant).isInstanceOf(Pair.class);
        Pair variantPair = (Pair) variant;
        
        // First element should be $ccard (replaced)
        assertThat(variantPair.getLeft()).isInstanceOf(Atom.class);
        assertThat(((Atom) variantPair.getLeft()).getValue()).isEqualTo("$ccard");
        
        // Second element should be Pair(bal($oyster), ~gid)
        assertThat(variantPair.getRight()).isInstanceOf(Pair.class);
        Pair innerPair = (Pair) variantPair.getRight();
        
        // bal($oyster) should be UNCHANGED - NOT bal($ccard)
        assertThat(innerPair.getLeft()).isInstanceOf(PredictiveFunction.class);
        PredictiveFunction balFunc = (PredictiveFunction) innerPair.getLeft();
        assertThat(balFunc.getName()).isEqualTo("bal");
        assertThat(balFunc.getArgs()).hasSize(1);
        
        // THE KEY TEST: The argument of bal should still be $oyster, NOT $ccard
        Message balArg = balFunc.getArgs().get(0);
        assertThat(balArg).isInstanceOf(Atom.class);
        assertThat(((Atom) balArg).getValue()).isEqualTo("$oyster");
        
        // ~gid should be unchanged
        assertThat(innerPair.getRight()).isInstanceOf(Atom.class);
        assertThat(((Atom) innerPair.getRight()).getValue()).isEqualTo("~gid");
    }

    @Test
    @DisplayName("Function should be replaced when entire function is in substitution")
    void testFunctionReplacedWhenInSubstitution() {
        // Given: bal($oyster) is directly in substitution map
        Message oyster = new Atom("$oyster");
        Message ccard = new Atom("$ccard");
        Message balOyster = new PredictiveFunction("bal", List.of(oyster));
        Message balCcard = new PredictiveFunction("bal", List.of(ccard));
        Message gid = new Atom("~gid");
        
        Message target = new Pair(oyster, new Pair(balOyster, gid));
        
        // Substitution has both $oyster->$ccard AND bal($oyster)->bal($ccard)
        Map<Message, Message> substitution = new HashMap<>();
        substitution.put(oyster, ccard);
        substitution.put(balOyster, balCcard);
        
        // When: apply substitution
        Message variant = replacementComputer.applySubstitution(target, substitution);
        
        // Then: both should be replaced
        Pair variantPair = (Pair) variant;
        assertThat(((Atom) variantPair.getLeft()).getValue()).isEqualTo("$ccard");
        
        Pair innerPair = (Pair) variantPair.getRight();
        PredictiveFunction balFunc = (PredictiveFunction) innerPair.getLeft();
        assertThat(((Atom) balFunc.getArgs().get(0)).getValue()).isEqualTo("$ccard");
    }

    @Test
    @DisplayName("Variant generation with only $oyster blocked")
    void testGenerateVariantsOnlyOysterBlocked() {
        // Given: target <$oyster, bal($oyster), ~gid>
        Message oyster = new Atom("$oyster");
        Message ccard = new Atom("$ccard");
        Message balOyster = new PredictiveFunction("bal", List.of(oyster));
        Message gid = new Atom("~gid");
        
        Message target = new Pair(oyster, new Pair(balOyster, gid));
        
        // blockedToReplacements only has $oyster (bal($oyster) is NOT blocked in CASE1)
        Map<Message, Set<Message>> blockedToReplacements = new LinkedHashMap<>();
        blockedToReplacements.put(oyster, Set.of(ccard));
        
        // When: generate variants
        List<Message> variants = replacementComputer.generateVariants(target, blockedToReplacements);
        
        // Then: should have exactly one variant
        assertThat(variants).hasSize(1);
        
        // The variant should be <$ccard, bal($oyster), ~gid>
        Pair variant = (Pair) variants.get(0);
        assertThat(((Atom) variant.getLeft()).getValue()).isEqualTo("$ccard");
        
        Pair innerPair = (Pair) variant.getRight();
        PredictiveFunction balFunc = (PredictiveFunction) innerPair.getLeft();
        // bal's argument should still be $oyster!
        assertThat(((Atom) balFunc.getArgs().get(0)).getValue()).isEqualTo("$oyster");
    }
}


package com.xmen.service.forget;

import com.xmen.model.Atom;
import com.xmen.model.Message;
import com.xmen.model.PredictiveFunction;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for BlockingChecker CASE1 behavior:
 * - bal($oyster) should NOT be blocked when only $oyster is in forget set
 */
@DisplayName("BlockingChecker CASE1 Tests")
public class BlockingCheckerCase1Test {

    private BlockingChecker blockingChecker;

    @BeforeEach
    void setup() {
        blockingChecker = new BlockingChecker();
    }

    @Test
    @DisplayName("CASE1: $oyster is blocked when in forget set")
    void testOysterBlockedWhenInForgetSet() {
        // Given
        Message oyster = new Atom("$oyster");
        Set<Message> forgetSet = new HashSet<>();
        forgetSet.add(oyster);

        ForgetContext ctx = new ForgetContext(
            Set.of(oyster), // knowledge
            forgetSet,
            BlockingMode.CASE1_WEAK,
            new HashMap<>()
        );

        // When/Then
        assertThat(blockingChecker.isBlocked(oyster, ctx)).isTrue();
    }

    @Test
    @DisplayName("CASE1: bal($oyster) is NOT blocked when only $oyster is in forget set")
    void testBalOysterNotBlockedWhenOnlyOysterInForgetSet() {
        // Given
        Message oyster = new Atom("$oyster");
        Message balOyster = new PredictiveFunction("bal", List.of(oyster));

        Set<Message> forgetSet = new HashSet<>();
        forgetSet.add(oyster); // Only $oyster is in forget set, NOT bal($oyster)

        ForgetContext ctx = new ForgetContext(
            Set.of(oyster, balOyster), // knowledge
            forgetSet,
            BlockingMode.CASE1_WEAK,
            new HashMap<>()
        );

        // When/Then: bal($oyster) should NOT be blocked in CASE1!
        assertThat(blockingChecker.isBlocked(balOyster, ctx)).isFalse();
    }

    @Test
    @DisplayName("CASE1: bal($oyster) IS blocked when directly in forget set")
    void testBalOysterBlockedWhenDirectlyInForgetSet() {
        // Given
        Message oyster = new Atom("$oyster");
        Message balOyster = new PredictiveFunction("bal", List.of(oyster));

        Set<Message> forgetSet = new HashSet<>();
        forgetSet.add(balOyster); // bal($oyster) is directly in forget set

        ForgetContext ctx = new ForgetContext(
            Set.of(oyster, balOyster), // knowledge
            forgetSet,
            BlockingMode.CASE1_WEAK,
            new HashMap<>()
        );

        // When/Then: bal($oyster) IS blocked because it's directly in forget set
        assertThat(blockingChecker.isBlocked(balOyster, ctx)).isTrue();
    }

    @Test
    @DisplayName("CASE1: $ccard is NOT blocked when only $oyster is in forget set")
    void testCcardNotBlockedWhenOnlyOysterInForgetSet() {
        // Given
        Message oyster = new Atom("$oyster");
        Message ccard = new Atom("$ccard");

        Set<Message> forgetSet = new HashSet<>();
        forgetSet.add(oyster);

        ForgetContext ctx = new ForgetContext(
            Set.of(oyster, ccard), // knowledge
            forgetSet,
            BlockingMode.CASE1_WEAK,
            new HashMap<>()
        );

        // When/Then
        assertThat(blockingChecker.isBlocked(ccard, ctx)).isFalse();
    }
}


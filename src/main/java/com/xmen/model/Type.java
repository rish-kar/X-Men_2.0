package com.xmen.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Enum representing the type of a rule.
 * This enum is used to categorize rules into different types.
 * META: Represents a metadata rule.
 * P_RULE: Represents a primary rule.
 * MUTATED: Represents a mutated rule.
 */
@NoArgsConstructor
@Getter
public enum Type {
    META,
    P_RULE,
    MUTATED
}

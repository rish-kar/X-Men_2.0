package com.xmen.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Enum representing the type of fact.
 * This enum is used to categorize facts into different types.
 * PRE: Represents a pre-condition fact.
 * POST: Represents a post-condition fact.
 * ACTION: Represents an action fact.
 */
@Getter
@NoArgsConstructor
public enum TypeFact {
    PRE,
    POST,
    ACTION
}

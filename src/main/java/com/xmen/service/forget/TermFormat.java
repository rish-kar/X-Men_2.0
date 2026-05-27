package com.xmen.service.forget;

import com.xmen.model.*;

/**
 * TermFormat represents the structural format of a term/message.
 * Used to enforce format constraints when computing replacement sets per Algorithm 1.
 */
public enum TermFormat {
    ATOM,           // Simple atomic value
    PAIR,           // Pair/tuple (a, b)
    ENCRYPT,        // Encrypted term {m}_k
    HASH,           // Hash h(m)
    SIGN,           // Signature sign(m, k)
    FUNCTION,       // General function f(args...)
    UNKNOWN;        // Unknown format

    /**
     * Determines the format of a message.
     *
     * @param msg the message to analyze
     * @return the TermFormat of the message
     */
    public static TermFormat formatOf(Message msg) {
        if (msg == null) return UNKNOWN;

        if (msg instanceof Atom) {
            return ATOM;
        } else if (msg instanceof Pair) {
            return PAIR;
        } else if (msg instanceof Encrypt) {
            return ENCRYPT;
        } else if (msg instanceof PredictiveFunction func) {
            String name = func.getName().toLowerCase();
            if (name.contains("hash") || name.equals("h")) {
                return HASH;
            } else if (name.contains("sign")) {
                return SIGN;
            }
            return FUNCTION;
        }
        return UNKNOWN;
    }

    /**
     * Checks if two messages have compatible formats for replacement.
     * Per the paper, format(m') == format(m̄) is required for valid replacement.
     *
     * FIX F: Strict format matching:
     * - Atom matches Atom only
     * - Pair matches Pair only
     * - Encrypt matches Encrypt only
     * - PredictiveFunction matches PredictiveFunction only if same function name AND same arity
     *
     * @param candidate the candidate replacement message
     * @param blocked the blocked hypothesis being replaced
     * @return true if formats are compatible
     */
    public static boolean formatsCompatible(Message candidate, Message blocked) {
        if (candidate == null || blocked == null) {
            return false;
        }

        TermFormat candidateFormat = formatOf(candidate);
        TermFormat blockedFormat = formatOf(blocked);

        // Basic format must match
        if (candidateFormat != blockedFormat) {
            return false;
        }

        // For FUNCTION type, require same function name and arity
        if (candidateFormat == FUNCTION) {
            if (candidate instanceof PredictiveFunction cf && blocked instanceof PredictiveFunction bf) {
                // Function name must match
                if (!cf.getName().equalsIgnoreCase(bf.getName())) {
                    return false;
                }
                // Arity must match
                if (cf.getArgs().size() != bf.getArgs().size()) {
                    return false;
                }
            }
        }

        // For PAIR, check that arities match (both are pairs so this is always 2)
        // For ENCRYPT, both are encryptions (arity 2)
        // Additional recursive format checking could be added here if needed

        return true;
    }

    /**
     * Gets the arity (number of components) of a message.
     * Used for deeper format matching.
     */
    public static int arityOf(Message msg) {
        if (msg == null) return 0;

        if (msg instanceof Atom) {
            return 0;
        } else if (msg instanceof Pair) {
            return 2;
        } else if (msg instanceof Encrypt) {
            return 2; // message and key
        } else if (msg instanceof PredictiveFunction func) {
            return func.getArgs().size();
        }
        return 0;
    }

    /**
     * Deep format matching: checks if structures are compatible including nested terms.
     */
    public static boolean deepFormatsCompatible(Message candidate, Message blocked) {
        TermFormat candidateFormat = formatOf(candidate);
        TermFormat blockedFormat = formatOf(blocked);

        if (candidateFormat != blockedFormat) {
            return false;
        }

        // For structured types, check arity
        if (arityOf(candidate) != arityOf(blocked)) {
            return false;
        }

        // For pairs, recursively check components have compatible formats
        if (candidate instanceof Pair cp && blocked instanceof Pair bp) {
            return deepFormatsCompatible(cp.getLeft(), bp.getLeft()) &&
                   deepFormatsCompatible(cp.getRight(), bp.getRight());
        }

        // For functions, check name and argument formats
        if (candidate instanceof PredictiveFunction cf && blocked instanceof PredictiveFunction bf) {
            if (!cf.getName().equals(bf.getName())) {
                return false;
            }
            if (cf.getArgs().size() != bf.getArgs().size()) {
                return false;
            }
            for (int i = 0; i < cf.getArgs().size(); i++) {
                if (!deepFormatsCompatible(cf.getArgs().get(i), bf.getArgs().get(i))) {
                    return false;
                }
            }
        }

        return true;
    }
}


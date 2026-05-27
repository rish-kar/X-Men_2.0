package com.xmen.service.forget;

import com.xmen.model.Message;
import java.util.*;

/**
 * ForgetContext maintains the state for forget mutation processing according to Algorithm 1.
 * It tracks:
 * - Knowledge K: monotonically growing set of available messages (never shrinks)
 * - Forget set: blocked messages that cannot be used as hypotheses in derivations
 * - Blocking mode: Case 1 (weak), Case 2 (medium/pairing), or Case 3 (strong/full DY)
 */
public class ForgetContext {

    /**
     * Blocking mode determines how "blocked" hypotheses are computed.
     * CASE1_WEAK: h is blocked if h ∈ Forget
     * CASE2_PAIRING: h is blocked if h is derivable from Forget using only pairing rules
     * CASE3_FULL_DY: h is blocked if h is derivable from Forget using all DY rules
     */
    public enum BlockingMode {
        CASE1_WEAK,
        CASE2_PAIRING,
        CASE3_FULL_DY
    }

    private final Set<Message> knowledge;
    private final Set<Message> forgetSet;
    private final BlockingMode blockingMode;
    private final Map<String, String> typeMap; // maps message representation to type (e.g., "password", "nonce")

    public ForgetContext() {
        this.knowledge = new LinkedHashSet<>();
        this.forgetSet = new LinkedHashSet<>();
        this.blockingMode = BlockingMode.CASE1_WEAK;
        this.typeMap = new HashMap<>();
    }

    public ForgetContext(Set<Message> knowledge, Set<Message> forgetSet, BlockingMode mode, Map<String, String> typeMap) {
        this.knowledge = new LinkedHashSet<>(knowledge);
        this.forgetSet = new LinkedHashSet<>(forgetSet);
        this.blockingMode = mode;
        this.typeMap = typeMap != null ? new HashMap<>(typeMap) : new HashMap<>();
    }

    /**
     * Creates a copy of this context for the next transition.
     */
    public ForgetContext copy() {
        return new ForgetContext(this.knowledge, this.forgetSet, this.blockingMode, this.typeMap);
    }

    /**
     * Updates the context for a transition where message m1 is received and message m is forgotten.
     * Per paper:
     * - If m == m1: do NOT add m1 to K; remove m1 from Forget if present
     * - If m != m1: add m to Forget; if m1 was in Forget, remove it (unforget on re-receive)
     * - K is always monotonic (only grows) except when m == m1
     *
     * @param m1 the received message
     * @param m the message being forgotten (null if no forget at this transition)
     */
    public void updateForTransition(Message m1, Message m) {
        if (m1 == null && m == null) {
            return;
        }
        if (m != null && m.equals(m1)) {
            // Forget the just-received message: don't add m1 to K, remove from Forget
            forgetSet.remove(m1);
        } else {
            // Normal case: add m1 to knowledge
            if (m1 != null) {
                knowledge.add(m1);
            }
            // If forgetting something else, add it to Forget
            if (m != null) {
                forgetSet.add(m);
            }
            // Unforget-on-receive: if m1 was in Forget, remove it
            if (m1 != null) {
                forgetSet.remove(m1);
            }
        }
    }

    /**
     * Adds a message to knowledge (monotonic growth).
     */
    public void addKnowledge(Message msg) {
        if (msg != null) {
            knowledge.add(msg);
        }
    }

    /**
     * Adds multiple messages to knowledge.
     */
    public void addAllKnowledge(Collection<Message> messages) {
        if (messages != null) {
            knowledge.addAll(messages);
        }
    }

    /**
     * Adds a message to the forget set.
     */
    public void addForget(Message msg) {
        if (msg != null) {
            forgetSet.add(msg);
        }
    }

    public Set<Message> getKnowledge() {
        return Collections.unmodifiableSet(knowledge);
    }

    public Set<Message> getForgetSet() {
        return Collections.unmodifiableSet(forgetSet);
    }

    public BlockingMode getBlockingMode() {
        return blockingMode;
    }

    public Map<String, String> getTypeMap() {
        return Collections.unmodifiableMap(typeMap);
    }

    public void setTypeMap(Map<String, String> types) {
        this.typeMap.clear();
        if (types != null) {
            this.typeMap.putAll(types);
        }
    }

    /**
     * Returns the type of a message based on the type map.
     */
    public String getTypeOf(Message msg) {
        if (msg == null) return null;
        String repr = msg.represent();
        String t = typeMap.get(repr);
        if (t != null) return t;
        // typeMap keys may carry a Tamarin fresh-variable tilde prefix (e.g. "~pw1")
        if (repr.startsWith("~")) {
            t = typeMap.get(repr.substring(1));
            if (t != null) return t;
        } else {
            t = typeMap.get("~" + repr);
            if (t != null) return t;
        }
        return null;
    }

    @Override
    public String toString() {
        return "ForgetContext{" +
                "knowledge=" + knowledge.stream().map(Message::represent).toList() +
                ", forgetSet=" + forgetSet.stream().map(Message::represent).toList() +
                ", blockingMode=" + blockingMode +
                '}';
    }
}


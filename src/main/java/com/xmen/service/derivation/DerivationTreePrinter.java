package com.xmen.service.derivation;

import com.xmen.model.Atom;
import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.model.Pair;

import java.util.*;

/**
 * DerivationTreePrinter provides clean, formatted output for derivation trees.
 *
 * This class is PURELY about presentation. It does NOT change:
 * - which derivations exist,
 * - how blocking/replacements work,
 * - or any mutation logic.
 *
 * GOAL A: Print a "knowledge-used" tree that is easy for non-experts to read.
 * GOAL B: When there are zero derivations, still print a useful report
 *         (target, knowledge list, and a best-effort plain-text reason).
 */
public class DerivationTreePrinter {

    private final DerivationConfig config;

    public DerivationTreePrinter() {
        this(new DerivationConfig());
    }

    public DerivationTreePrinter(DerivationConfig config) {
        this.config = config != null ? config : new DerivationConfig();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Pretty-prints all derivation trees for a given target/knowledge pair.
     * If there are no derivations, prints a friendly zero-derivation report.
     */
    public void printAllTrees(Set<Derivation> trees, Message target, Set<Message> knowledge) {
        if (trees == null || trees.isEmpty()) {
            printZeroDerivationReport(target, knowledge);
            return;
        }

        printHeader(target, knowledge, trees.size());

        int treeNum = 1;
        for (Derivation tree : trees) {
            printKnowledgeTree(tree, treeNum++, target);
        }
    }

    /**
     * Legacy entry-point when we only know the trees (no explicit target/knowledge).
     * We keep it but downgrade to a simple view.
     */
    public void printTree(Derivation tree, int treeNumber) {
        if (tree == null) {
            System.out.println("No derivation tree to print.");
            return;
        }
        // Fallback: print a compact view rooted at the goal, with legend.
        Message target = tree.getGoal();
        Set<Message> emptyK = Collections.emptySet();
        printHeader(target, emptyK, 1);
        printKnowledgeTree(tree, treeNumber, target);
    }

    /**
     * Compact string form (used by logging). Kept as-is except for using
     * friendlier rule formatting.
     */
    public String toCompactString(Derivation tree) {
        if (tree == null) return "(null)";

        StringBuilder sb = new StringBuilder();
        buildCompactString(tree, sb, 0);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Zero-derivation reporting (GOAL B)
    // -------------------------------------------------------------------------

    /**
     * Prints a helpful report when no derivations exist.
     *
     * Format:
     *   Derivations: 0
     *   Target: <...>
     *   Legend: (K) = taken from knowledge set
     *   Knowledge (K):
     *     - term1
     *     - term2
     *   Reason (best-effort):
     *     <plain language explanation>
     */
    public void printZeroDerivationReport(Message target, Set<Message> knowledge) {
        System.out.println();
        System.out.println("Derivations: 0");
        System.out.println("Target: " + (target != null ? target.represent() : "(null)"));
        System.out.println("Legend: (K) = taken from knowledge set");

        // Print knowledge list
        System.out.println("Knowledge (K):");
        if (knowledge == null || knowledge.isEmpty()) {
            System.out.println("   (empty)");
        } else {
            for (Message m : knowledge) {
                System.out.println("   - " + pretty(m));
            }
        }

        // Best-effort reason
        System.out.println("Reason (best-effort):");
        System.out.println("   " + buildZeroDerivationReason(target, knowledge));
    }

    /**
     * Builds a simple, conservative explanation for the zero-derivation case.
     *
     * IMPORTANT: This does NOT run any extra derivation logic. It just looks
     * at the target + knowledge shape and prints a human-friendly hint.
     */
    private String buildZeroDerivationReason(Message target, Set<Message> knowledge) {
        if (target == null) {
            return "Target is null.";
        }
        if (knowledge == null || knowledge.isEmpty()) {
            return "Knowledge set is empty, so the target cannot be constructed.";
        }

        // If target is an Atom and not in K
        if (target instanceof Atom) {
            boolean inK = knowledge.stream().anyMatch(m -> m.equals(target));
            if (!inK) {
                return target.represent() +
                        " not found in K and cannot be obtained from any known pair/tuple.";
            } else {
                return "Target is known directly, but derivation enumeration returned no trees.";
            }
        }

        // If target is a Pair / tuple-like, try to see which components are missing
        if (target instanceof Pair) {
            List<Message> flat = flattenTuple(target);
            List<Message> missing = new ArrayList<>();
            outer:
            for (Message comp : flat) {
                for (Message k : knowledge) {
                    if (k.equals(comp)) {
                        continue outer;
                    }
                }
                missing.add(comp);
            }
            if (!missing.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("The following components of the target are not directly present in K: ");
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(missing.get(i).represent());
                }
                sb.append(". They also do not appear as full values in any known tuple.");
                return sb.toString();
            } else {
                return "All components of the target appear in K, but no complete derivation tree " +
                       "was found within the configured limits (depth/variants).";
            }
        }

        // For other structured terms, give a generic explanation.
        return "Target is a structured term that cannot be constructed from K using the " +
               "available decomposition rules within the configured depth limits.";
    }

    // -------------------------------------------------------------------------
    // Pretty tree printing (GOAL A)
    // -------------------------------------------------------------------------

    /**
     * Prints a single "knowledge-used" tree.
     *
     * Layout:
     *   Target: <term>
     *   Legend: (K) = taken from knowledge set
     *   Tree:
     *     <target>
     *        ├─ ...
     *        └─ ...
     */
    private void printKnowledgeTree(Derivation root, int treeNumber, Message explicitTarget) {
        System.out.println();
        System.out.println("DerivationTree #" + treeNumber);
        System.out.println("Legend: (K) = taken from knowledge set");

        Message target = explicitTarget != null ? explicitTarget : root.getGoal();
        System.out.println("Target: " + (target != null ? target.represent() : "(null)"));
        System.out.println("Tree:");

        // We render a tree where the root is the target, and nodes show how
        // they are obtained from children or from knowledge (K).
        Set<String> seenLeaves = new LinkedHashSet<>();

        // Render recursively, using ASCII connectors "|-" and "\\-" for simplicity.
        renderNode(root, "", true, seenLeaves);

        // Optionally, list unique knowledge leaves at the bottom
        if (!seenLeaves.isEmpty()) {
            System.out.println();
            System.out.println("Knowledge used (leaves):");
            for (String leaf : seenLeaves) {
                System.out.println("   - " + leaf + " (K)");
            }
        }
    }

    /**
     * Recursively renders a node as a knowledge-used tree.
     * We avoid internal jargon like HYPOTHESIS/PAIR/FST/SND.
     */
    private void renderNode(Derivation node, String prefix, boolean isLast, Set<String> seenLeaves) {
        if (node == null) return;

        String connector = isLast ? "\\-" : "|-";
        String linePrefix = prefix + connector + " ";

        Message goal = node.getGoal();
        String goalStr = goal != null ? pretty(goal) : "(null)";

        if (isLeafFromKnowledge(node)) {
            // Leaf: show term with (K)
            System.out.println(linePrefix + goalStr + " (K)");
            seenLeaves.add(goalStr);
            return;
        }

        // Internal node: show the goal term once
        System.out.println(linePrefix + goalStr);

        // For projection/decomposition rules, we try to add a short hint
        String hint = buildHumanHint(node);
        if (hint != null && !hint.isBlank()) {
            System.out.println(prefix + "   " + "-> " + hint);
        }

        List<Derivation> premises = node.getPremises();
        if (premises == null || premises.isEmpty()) {
            return;
        }

        for (int i = 0; i < premises.size(); i++) {
            boolean childIsLast = (i == premises.size() - 1);
            String childPrefix = prefix + (isLast ? "   " : "|  ");
            renderNode(premises.get(i), childPrefix, childIsLast, seenLeaves);
        }
    }

    /**
     * Determines if a node is a leaf coming directly from knowledge.
     * Per design, these are the "Initial" nodes with no premises.
     */
    private boolean isLeafFromKnowledge(Derivation node) {
        if (node == null) return false;
        List<Derivation> premises = node.getPremises();
        if (premises != null && !premises.isEmpty()) return false;
        String rule = node.getRule();
        if (rule == null) return false;
        String upper = rule.toUpperCase(Locale.ROOT);
        return upper.equals("INITIAL") || upper.contains("HYPOTHESIS");
    }

    /**
     * Builds a small human-readable hint for decomposition steps
     * such as taking components from a pair.
     */
    private String buildHumanHint(Derivation node) {
        if (node == null) return null;
        String rule = node.getRule();
        if (rule == null) return null;
        String upper = rule.toUpperCase(Locale.ROOT);

        if (upper.startsWith("PROJECTION-FIRST")) {
            return "from a pair, take the first component";
        }
        if (upper.startsWith("PROJECTION-SECOND")) {
            return "from a pair, take the second component";
        }
        if (upper.startsWith("DECRYPTION")) {
            return "decrypt a protected message using a key";
        }
        if (upper.startsWith("PAIRING")) {
            return "combine parts into a pair/tuple";
        }
        // For functions, we keep it simple
        if (upper.startsWith("PREDICTIVEFUNCTION")) {
            return "apply a function to its arguments";
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Compact representation (used by logs), lightly adjusted
    // -------------------------------------------------------------------------

    private void buildCompactString(Derivation node, StringBuilder sb, int depth) {
        if (node == null) return;

        String indent = "  ".repeat(depth);
        String term = node.getGoal() != null ? node.getGoal().represent() : "(null)";

        sb.append(indent).append(term).append("\n");

        List<Derivation> premises = node.getPremises();
        if (premises != null) {
            for (Derivation p : premises) {
                buildCompactString(p, sb, depth + 1);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers: pretty-printing and tuple flattening
    // -------------------------------------------------------------------------

    /**
     * Attempts to pretty-print a message using tuple-like syntax where possible.
     */
    private String pretty(Message m) {
        if (m == null) return "(null)";
        if (m instanceof Pair) {
            List<Message> flat = flattenTuple(m);
            StringBuilder sb = new StringBuilder();
            sb.append("<");
            for (int i = 0; i < flat.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(flat.get(i).represent());
            }
            sb.append(">");
            return sb.toString();
        }
        return m.represent();
    }

    /**
     * Flattens nested Pair structures that encode tuples into a flat list.
     * E.g., Pair(a, Pair(b, c)) -> [a, b, c].
     */
    private List<Message> flattenTuple(Message m) {
        List<Message> out = new ArrayList<>();
        flattenInto(m, out);
        return out;
    }

    private void flattenInto(Message m, List<Message> out) {
        if (m instanceof Pair p) {
            flattenInto(p.getLeft(), out);
            flattenInto(p.getRight(), out);
        } else {
            out.add(m);
        }
    }

    // -------------------------------------------------------------------------
    // Old formatting methods kept only if needed by other callers
    // (We no longer expose formatTreeToStringBuilder / formatNodeToStringBuilder
    //  publicly, but keep them private in case some tests still use them.)
    // -------------------------------------------------------------------------

    private StringBuilder formatTreeToStringBuilder(Derivation tree, int treeNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append("DerivationTree #").append(treeNumber).append("\n");
        buildCompactString(tree, sb, 0);
        return sb;
    }

    private void formatNodeToStringBuilder(Derivation node, int depth, Object ignored,
                                           Set<String> hypotheses, StringBuilder sb) {
        // Legacy helper – not used in the new pretty printer. Kept for compatibility.
        if (node == null) return;
        String indent = "  ".repeat(depth);
        String term = node.getGoal() != null ? node.getGoal().represent() : "(null)";
        sb.append(indent).append(term).append("\n");
        List<Derivation> premises = node.getPremises();
        if (premises != null) {
            for (Derivation p : premises) {
                formatNodeToStringBuilder(p, depth + 1, null, hypotheses, sb);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Header printing (reused by various entry points)
    // -------------------------------------------------------------------------

    private void printHeader(Message target, Set<Message> knowledge, int derivationCount) {
        System.out.println();
        System.out.println("Derivation report");
        System.out.println("Target: " + (target != null ? target.represent() : "(null)"));
        System.out.println("Knowledge size: " + (knowledge != null ? knowledge.size() : 0));
        System.out.println("Derivations found: " + derivationCount);
        System.out.println("Legend: (K) = taken from knowledge set");
        System.out.println();
    }
}

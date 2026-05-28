package com.xmen.service.forget;

import com.xmen.model.Derivation;
import com.xmen.model.Function;
import com.xmen.model.Message;
import com.xmen.service.derivation.DerivationConfig;
import com.xmen.service.impl.DerivationServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ForgetDerivationChecker implements the core derivation checking for Algorithm 1:
 * - Checks if a target is derivable under forgetting (exists unblocked derivation)
 * - Extracts hypotheses from derivations for blocked hypothesis analysis
 * - Computes whether mutation is needed based on unblocked derivation existence
 *
 * IMPORTANT: This checker uses a restricted derivation engine that only decomposes
 * whitelist built-in functions (pair, senc, aenc, etc.) and never decomposes
 * user-defined functions to prevent exponential blow-up.
 */
@Slf4j
@Component
public class ForgetDerivationChecker {

    private static final int DEFAULT_DEPTH_LIMIT = 10;
    private static final int MAX_DERIVATIONS = 50; // Cap derivation enumeration

    @Autowired
    private BlockingChecker blockingChecker;

    @Autowired
    @Qualifier("derivationServiceImpl")
    private DerivationServiceImpl derivationService;

    /** Current user-defined functions (non-decomposable) */
    private Set<String> userDefinedFunctions = new HashSet<>();

    /**
     * Active depth / derivation caps. Initialised to the safe defaults; a controller
     * can override these per-request (e.g. when the user selects "Infinite" in the
     * UI) via {@link #overrideLimits(int, int)} and must call {@link #resetLimits()}
     * in a finally block so the next request starts from the defaults again.
     */
    private int depthLimit = DEFAULT_DEPTH_LIMIT;
    private int maxDerivations = MAX_DERIVATIONS;

    /**
     * Lift (or tighten) the derivation caps for the current request. Pass
     * {@link Integer#MAX_VALUE} for both arguments to honor an "Infinite"
     * selection from the UI — the derivation engine will then attempt unbounded
     * exploration, which is allowed to OOM / stack-overflow on self-feeding
     * inputs (that's the whole point of the Infinite mode).
     */
    public void overrideLimits(int newDepth, int newMax) {
        this.depthLimit = newDepth;
        this.maxDerivations = newMax;
        refreshConfig();
        log.info("Derivation limits overridden: depth={}, maxDerivations={}",
                 newDepth, newMax);
    }

    /** Restore the static defaults. Call from controller `finally` blocks. */
    public void resetLimits() {
        this.depthLimit = DEFAULT_DEPTH_LIMIT;
        this.maxDerivations = MAX_DERIVATIONS;
        refreshConfig();
    }

    private void refreshConfig() {
        DerivationConfig config = new DerivationConfig(
            this.userDefinedFunctions,
            this.maxDerivations,
            this.depthLimit
        );
        derivationService.setConfig(config);
    }

    /**
     * Configures the derivation checker with user-defined functions.
     * These functions will NOT be decomposed during derivation to prevent explosion.
     *
     * @param functions List of user-defined Function objects from the model
     */
    public void configureUserDefinedFunctions(List<Function> functions) {
        this.userDefinedFunctions.clear();
        if (functions != null) {
            for (Function f : functions) {
                if (f.getName() != null) {
                    this.userDefinedFunctions.add(f.getName().toLowerCase().trim());
                }
            }
        }

        // Reuse whatever depth/max are currently active (defaults unless a
        // controller has overridden them for this request).
        refreshConfig();

        log.info("Configured derivation checker with {} user-defined functions: {}",
                 userDefinedFunctions.size(), userDefinedFunctions);
    }

    /**
     * Checks if target m2 is derivable under forgetting.
     * Per Algorithm 1: returns true if there EXISTS at least one derivation
     * where ALL hypotheses are NOT blocked.
     *
     * @param target the target message to derive (m2 - the send message)
     * @param ctx the forget context containing K and Forget
     * @return true if an unblocked derivation exists, false otherwise
     */
    public boolean derivableUnderForgetting(Message target, ForgetContext ctx) {
        if (target == null || ctx == null) {
            log.warn("Null target or context in derivableUnderForgetting");
            return false;
        }

        log.info("Checking derivability under forgetting for target: {}", target.represent());
        log.debug("Knowledge: {}", ctx.getKnowledge().stream().map(Message::represent).toList());
        log.debug("Forget set: {}", ctx.getForgetSet().stream().map(Message::represent).toList());
        log.debug("Blocking mode: {}", ctx.getBlockingMode());

        // Get all derivations
        Set<Derivation> derivations = getAllDerivations(target, ctx.getKnowledge());

        if (derivations.isEmpty()) {
            log.info("No derivations found for target {}", target.represent());
            return false;
        }

        log.info("Found {} derivations for target {}", derivations.size(), target.represent());

        // Check each derivation for blocked hypotheses
        for (Derivation derivation : derivations) {
            Set<Message> blockedHypotheses = blockingChecker.findBlockedHypotheses(derivation, ctx);

            if (blockedHypotheses.isEmpty()) {
                // Found an unblocked derivation - no mutation needed
                log.info("Found unblocked derivation for target {} - no mutation needed", target.represent());
                return true;
            } else {
                log.debug("Derivation has {} blocked hypotheses: {}",
                         blockedHypotheses.size(),
                         blockedHypotheses.stream().map(Message::represent).toList());
            }
        }

        log.info("All {} derivations have blocked hypotheses - mutation needed", derivations.size());
        return false;
    }

    /**
     * Gets all derivations for a target from knowledge.
     * Uses the Java derivation service to enumerate derivations.
     */
    public Set<Derivation> getAllDerivations(Message target, Set<Message> knowledge) {
        if (target == null || knowledge == null) {
            return Collections.emptySet();
        }

        try {
            // Use the SILENT derivation path here: this method drives the mutation
            // analysis (blocked-hypothesis checks). The visualisation tree is printed
            // separately by ForgetMutationStrategy via printDerivationTree(...), so
            // we do not want this call to also dump a (possibly less informative)
            // tree on top of it.
            Set<Derivation> derivations = derivationService.deriveToDepthNoPrint(target, knowledge, this.depthLimit);

            // Cap the number of derivations to prevent explosion
            if (derivations.size() > this.maxDerivations) {
                log.warn("Truncating derivations from {} to {}", derivations.size(), this.maxDerivations);
                Set<Derivation> truncated = new LinkedHashSet<>();
                int count = 0;
                for (Derivation d : derivations) {
                    if (count >= this.maxDerivations) break;
                    truncated.add(d);
                    count++;
                }
                return truncated;
            }

            return derivations;
        } catch (Exception e) {
            log.error("Error getting derivations for target {}: {}", target.represent(), e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Visualisation helper: runs the derivation engine for {@code target} from
     * {@code displayKnowledge} purely to render the derivation tree to stdout
     * (which is captured into the forget-mutation ZIP's _DerivationTree.txt by
     * DerivationTreeCaptureService). The returned derivations are discarded —
     * this method must NOT be used to drive mutation analysis. The caller is
     * expected to pass an enriched knowledge set so the printed tree is
     * informative; the mutation pipeline keeps using its own (transition-aware)
     * knowledge via {@link #getAllDerivations}.
     */
    public void printDerivationTree(Message target, Set<Message> displayKnowledge) {
        if (target == null || displayKnowledge == null) {
            return;
        }
        try {
            derivationService.deriveToDepth(target, displayKnowledge, this.depthLimit);
        } catch (Exception e) {
            log.error("Error rendering derivation tree for target {}: {}",
                      target.represent(), e.getMessage());
        }
    }

    /**
     * Finds a specific unblocked derivation if one exists.
     * Used when we need to inspect the derivation structure.
     */
    public Optional<Derivation> findUnblockedDerivation(Message target, ForgetContext ctx) {
        Set<Derivation> derivations = getAllDerivations(target, ctx.getKnowledge());

        for (Derivation derivation : derivations) {
            if (!blockingChecker.hasBlockedHypotheses(derivation, ctx)) {
                return Optional.of(derivation);
            }
        }

        return Optional.empty();
    }

    /**
     * Gets all derivations that have blocked hypotheses (need mutation).
     */
    public List<Derivation> getDerivationsWithBlockedHypotheses(Message target, ForgetContext ctx) {
        Set<Derivation> derivations = getAllDerivations(target, ctx.getKnowledge());
        List<Derivation> blocked = new ArrayList<>();

        for (Derivation derivation : derivations) {
            if (blockingChecker.hasBlockedHypotheses(derivation, ctx)) {
                blocked.add(derivation);
            }
        }

        return blocked;
    }

    /**
     * Collects all unique blocked hypotheses across all derivations.
     */
    public Set<Message> collectAllBlockedHypotheses(Message target, ForgetContext ctx) {
        Set<Message> allBlocked = new LinkedHashSet<>();
        Set<Derivation> derivations = getAllDerivations(target, ctx.getKnowledge());

        for (Derivation derivation : derivations) {
            allBlocked.addAll(blockingChecker.findBlockedHypotheses(derivation, ctx));
        }

        return allBlocked;
    }
}


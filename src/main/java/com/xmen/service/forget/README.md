<div align="center">

# 🧠 `service/forget`

**The brain of the Forget mutation — context, blocking, derivation, replacement**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Implements Algorithm 1 from the paper. Tracks knowledge `K`, the forget set, and the three blocking cases; decides derivability under forgetting; computes replacement candidates and variants.

## 🗺️ Where this sits

```text
Forget mutation request
       ↓
parse + extract knowledge
       ↓
🟢 forget/                ←  you are here
   ├── ForgetContext           (K + Forget + blocking mode)
   ├── BlockingChecker         (Case 1 / 2 / 3 blocking)
   ├── ForgetDerivationChecker (does an unblocked derivation exist?)
   ├── ReplacementComputer     (compute R_h, generate variants)
   └── TermFormat              (format compatibility for replacements)
       ↓
Mutation decision (keep / substitute / skip + neglect)
```

## 📁 Files at a glance

| File | Role |
| --- | --- |
| 🧾 `ForgetContext.java` | Holds `K`, the forget set, the blocking mode, and a type map. Drives Algorithm 1's per-transition update. |
| 🚧 `BlockingChecker.java` | Implements the three blocking cases (weak, pairing, full DY). |
| 🔍 `ForgetDerivationChecker.java` | Asks the DY engine whether an unblocked derivation of the target exists. |
| 🔁 `ReplacementComputer.java` | Computes replacement candidates and generates variant messages. |
| 🧮 `TermFormat.java` | Categorises terms (`ATOM`, `PAIR`, `ENCRYPT`, `HASH`, `SIGN`, `FUNCTION`) and checks format compatibility. |

## 🔬 Deep dive

<details>
<summary>🧾 <strong>ForgetContext.java</strong></summary>

<br/>

- **🎯 Job:** Carry the per-transition state of Algorithm 1: monotonic knowledge `K`, the Forget set, the blocking mode, and a type map for replacements.
- **🔧 Key logic:**
  1. Tracks monotonic knowledge growth (knowledge never shrinks).
  2. Updates the Forget set when something is forgotten (with tilde-tolerant type lookups).
  3. Maintains a value → type map for replacement filtering.
- **🤝 Used by:** `ForgetMutationStrategy`, `ReplacementComputer`.
- **🪝 Depends on:** `Message` (from `model`) and the setup type map from `SetupKnowledgeExtractor`.
- **⚠️ Heads-up:** This object encodes Algorithm 1 semantics — touch update logic carefully.

</details>

<details>
<summary>🚧 <strong>BlockingChecker.java</strong></summary>

<br/>

- **🎯 Job:** Decide whether a hypothesis is blocked under the active blocking mode.
- **🔧 Key logic:**
  1. **Case 1 (weak)** — blocked iff in the Forget set.
  2. **Case 2 (pairing)** — blocked iff derivable from Forget using only pairing rules.
  3. **Case 3 (full DY)** — blocked iff derivable from Forget using all DY rules.
- **🤝 Used by:** `ForgetDerivationChecker`, `ReplacementComputer`, `ForgetMutationStrategy`.
- **⚠️ Heads-up:** This is the semantic core — changes here change every Forget output.

</details>

<details>
<summary>🔍 <strong>ForgetDerivationChecker.java</strong></summary>

<br/>

- **🎯 Job:** Determine whether the target `m₂` is derivable from `K` without using any blocked hypothesis.
- **🔧 Key logic:**
  1. Enumerates derivations via the Java derivation engine.
  2. Calls `BlockingChecker` on each hypothesis.
  3. Returns true the moment an unblocked derivation is found.
- **🤝 Used by:** `ForgetMutationStrategy`.
- **⚠️ Heads-up:** Derivation depth limits and the user-defined-function whitelist matter for both correctness and performance.

</details>

<details>
<summary>🔁 <strong>ReplacementComputer.java</strong></summary>

<br/>

- **🎯 Job:** Compute the replacement set `R_h` for each blocked hypothesis and generate variant messages.
- **🔧 Key logic:**
  1. Filters candidates by type and structural format.
  2. Generates Cartesian-product variants with a configurable cap.
  3. Applies substitutions carefully for function terms to avoid invalid mutants.
- **🤝 Used by:** `ForgetMutationStrategy`.
- **⚠️ Heads-up:** Replacement rules must obey Algorithm 1's substitution semantics.

</details>

<details>
<summary>🧮 <strong>TermFormat.java</strong></summary>

<br/>

- **🎯 Job:** Categorise messages and check format compatibility for replacements.
- **🔧 Key logic:** Categories: `ATOM`, `PAIR`, `ENCRYPT`, `HASH`, `SIGN`, `FUNCTION`. Enforces strict format compatibility so replacements never produce ill-formed terms.
- **⚠️ Heads-up:** Loosening compatibility produces invalid mutated rules.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `../../model` (`Message`, `Pair`, `Encrypt`, …). `../impl/DerivationServiceImpl` for the underlying DY search. | `../impl/ForgetMutationStrategy` — the orchestrator of Algorithm 1. |

## ⚙️ At runtime

For each transition that contains `Forget(x)`:

1. `ForgetContext` is built (or reused) with the current `K`, Forget set, and blocking mode.
2. `ForgetDerivationChecker` asks: *can `m₂` be derived without blocked hypotheses?*
3. If yes → keep the send unchanged.
4. If no → `ReplacementComputer` finds type-compatible replacements and emits variants.
5. If no replacement exists → the send is skipped and matching internal actions are Neglected.

## 📨 Forget-specific headers

| Header | Effect |
| --- | --- |
| `Blocking-Mode` | Pick the blocking case (CASE1 / CASE2 / CASE3 aliases). |
| `Max-Variants-Per-Rule` | Cap on Cartesian product expansion. |
| `Witness-Actions` | Extra action names treated as witnesses during Forget processing. |

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Logging and diagnostic messages. Additional helpers in `ReplacementComputer`. | Blocking-case semantics, substitution rules, depth/variant caps (performance vs. completeness trade-offs). |

---

> 💡 **Summary** — Five files that implement Algorithm 1: track `K`, classify what's blocked, compute replacements, and decide whether the send survives.

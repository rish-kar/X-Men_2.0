<div align="center">

# 🛠️ `service/impl`

**Concrete mutation strategies and the derivation engine**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; The actual mutation muscle: Skip, Add, Replace, Forget, Neglect strategies plus the Java derivation engine and the hybrid Java/Haskell router.

## 🗺️ Where this sits

```text
service/ (interfaces)
       ↓
🟢 service/impl/   ←  you are here
       ├── MutationGeneratorServiceImpl    (orchestrator)
       ├── *MutationStrategy.java          (per-mutation logic)
       ├── DerivationServiceImpl           (Java DY engine)
       ├── HybridDerivationService         (Java ↔ Haskell switch)
       ├── DerivationCheckServiceImpl      (target + knowledge extraction)
       └── DerivationModeContext           (thread-local Haskell flag)
       ↓
   model · utilities · forget/ · derivation/
```

## 📁 Files at a glance

### Orchestrator

| File | Role |
| --- | --- |
| 🎼 `MutationGeneratorServiceImpl.java` | Runs configured strategies over human rules and persists mutated `.m` files. |

### Mutation strategies

| File | Mutation |
| --- | --- |
| ➕ `AddMutationStrategy.java` | *Add* |
| ❌ `NeglectMutationStrategy.java` | *Neglect* |
| 🧠 `ForgetMutationStrategy.java` | *Forget* (Algorithm 1) |
| 🔄 `ReplaceMutationService.java` | Shared *Replace* mechanics |
| 🔄 `ReplaceSubMessagesStrategy.java` | *Replace Sub-Messages* (delegates to the service above) |
| 🔄 `ReplaceTypeStrategy.java` | *Replace Type* (delegates to the service above) |
| ⏭ `SkipSendMutationStrategy.java` | *Skip Send* |
| ⏭ `SkipReceiveMutationStrategy.java` | *Skip Receive* |
| ⏭ `SkipSendReceiveMutationStrategy.java` | *Skip Send→Receive* |
| ⏭ `SkipReceiveSendMutationStrategy.java` | *Skip Receive→Send* |
| ⏭ `SkipReceiveSendReceiveMutationStrategy.java` | *Skip Receive→Send→Receive* |

### Derivation engine

| File | Role |
| --- | --- |
| 🔬 `DerivationServiceImpl.java` | Java DY derivation search & tree printing. |
| 🌉 `HybridDerivationService.java` | Routes derivation calls to Java or Haskell based on `DerivationModeContext`. |
| 🔎 `DerivationCheckServiceImpl.java` | Extracts target (`m₂`) and knowledge from rules; runs derivability checks. |
| 🚩 `DerivationModeContext.java` | Thread-local flag toggling Haskell vs. Java per request. |
| 🪧 `DerivationServiceRouter.java` | Empty placeholder; reserved for future routing logic. |

## 🔬 Deep dive — the headline classes

<details>
<summary>🎼 <strong>MutationGeneratorServiceImpl.java</strong> — the conductor</summary>

<br/>

- **🎯 Job:** Walk the enabled mutations, resolve their strategies, run them, and persist the result.
- **🔧 Key logic:**
  1. Iterate `Set<Mutations>`; ask `MutationStrategyFactory` for each strategy.
  2. Clone the rule list, filter to human rules.
  3. For each strategy: invoke and accumulate variants into `ParametersBundle.collections`.
  4. `MutatedFileGenerator` writes the resulting `.m` files.
- **🤝 Used by:** Every controller that needs mutations.
- **⚠️ Heads-up:** Strategy ordering can affect outputs when multiple mutations are combined.

</details>

<details>
<summary>🧠 <strong>ForgetMutationStrategy.java</strong> — Algorithm 1</summary>

<br/>

- **🎯 Job:** Implement the Forget mutation end-to-end.
- **🔧 Key logic:**
  1. Build / update `ForgetContext` per transition.
  2. Extract `m₂`; ask the derivation engine for all derivations.
  3. If an unblocked derivation exists → keep the send.
  4. Otherwise → compute type-compatible replacements, substitute throughout the rule (Send, Out, witness actions), and remove the `Forget(...)` action.
  5. If no replacement exists → Skip the send + Neglect internal actions that used the forgotten term.
- **🪝 Depends on:** `ForgetContext`, `BlockingChecker`, `ReplacementComputer`, `ForgetDerivationChecker`, `DerivationCheckService`.
- **⚠️ Heads-up:** This is the most semantically dense file in the project — coordinate changes with the paper.

</details>

<details>
<summary>🌉 <strong>HybridDerivationService.java</strong> — Java ↔ Haskell</summary>

<br/>

- **🎯 Job:** Decide per request whether derivation runs locally or talks to the Haskell service.
- **🔧 Key logic:**
  1. Consults `DerivationModeContext` (a thread-local toggle).
  2. If Haskell is active: converts rules, calls `/derive`, heuristically parses the result.
  3. Otherwise: delegates to `DerivationServiceImpl`.
- **⚠️ Heads-up:** Heuristic parsing of the Haskell response must match the upstream service's output format.

</details>

<details>
<summary>🔬 <strong>DerivationServiceImpl.java</strong> — the Java engine</summary>

<br/>

- **🎯 Job:** Symbolic DY derivation search with depth limits.
- **🔧 Key logic:** Respects `DerivationConfig` for decomposable functions; prints results with `DerivationTreePrinter`.
- **⚠️ Heads-up:** Changing the search affects Forget mutation outcomes directly.

</details>

<details>
<summary>🔎 <strong>DerivationCheckServiceImpl.java</strong></summary>

<br/>

- **🎯 Job:** Pull the target message and the agent's knowledge out of a rule so derivation can be checked.
- **🔧 Key logic:** Resolves Java vs. Haskell; extracts targets from `SndS`/`Out` facts; pulls knowledge from `State`/`RcvS`.
- **⚠️ Heads-up:** Target extraction logic must match the rule syntax produced by `Rule.toString()`.

</details>

<details>
<summary>➕ <strong>AddMutationStrategy.java</strong></summary>

<br/>

- **🎯 Job:** Add extra send/receive permutations to the trace.
- **🔧 Key logic:** Clone rules; build new `Snd` facts from knowledge permutations; adjust arrival rules to accept the new receive.
- **⚠️ Heads-up:** Chain propagation is intricate — verify connectivity after changes.

</details>

<details>
<summary>❌ <strong>NeglectMutationStrategy.java</strong></summary>

<br/>

- **🎯 Job:** Generate variants where subsets of internal actions are dropped.
- **🔧 Key logic:** Keep the first action; build subsets of the remaining ones using a power-set helper. Each subset yields one mutated theory.
- **⚠️ Heads-up:** Combinatorial explosion is real — keep subset sizes bounded.

</details>

<details>
<summary>🔄 <strong>ReplaceMutationService.java</strong> · <strong>ReplaceSubMessagesStrategy</strong> · <strong>ReplaceTypeStrategy</strong></summary>

<br/>

- **🎯 Job:** Replace message subcomponents or type tags inside `Snd` facts, then propagate.
- **🔧 Key logic:** Extract `Snd` parameters; match replacements by type / tag; propagate substitutions across the rule chain. The two strategies are thin façades that delegate to the shared service.
- **⚠️ Heads-up:** Propagation and tag matching are the tricky parts.

</details>

<details>
<summary>⏭ <strong>Skip*MutationStrategy.java</strong> — the Skip family</summary>

<br/>

- **🎯 Job:** Drop selected `Snd`/`Rcv` actions and propagate the consequences.
- **🔧 Key logic:**
  1. Clone rules.
  2. Mark `Snd`/`Rcv` facts as removed.
  3. Update state pre/postconditions and let `RulesModifier` fix the chain.
- **⚠️ Heads-up:** Chain propagation is fragile — every Skip variant test depends on it.

</details>

<details>
<summary>🚩 <strong>DerivationModeContext.java</strong></summary>

<br/>

- **🎯 Job:** Thread-local flag for "Haskell vs Java" per request.
- **🔧 Key logic:** `enableHaskell()` / `disableHaskell()` toggle the flag; `HybridDerivationService` reads it.
- **⚠️ Heads-up:** Ensure the flag is cleaned up after each request to avoid leaking across thread pools.

</details>

<details>
<summary>🪧 <strong>DerivationServiceRouter.java</strong></summary>

<br/>

Empty placeholder reserved for future routing logic.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `../../model` · `../../utilities` · `../forget` · `../derivation`. | `../controller` via `../MutationStrategyFactory`. |

## ⚙️ At runtime

A controller selects mutations → `MutationGeneratorServiceImpl` resolves each strategy → strategies modify cloned rules and accumulate variants into the `ParametersBundle` → `MutatedFileGenerator` writes each variant as a `.m` file → `ZipService` packages everything for the response.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding new strategies. Logging / diagnostics. | Forget's Algorithm 1 logic. Chain propagation in Skip strategies. Heuristic parsing of Haskell responses. |

---

> 💡 **Summary** — Where every mutation actually happens. `ForgetMutationStrategy` and the Skip family are the most semantically loaded files.

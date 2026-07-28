<div align="center">

# 🧠 `service/forget` (tests)

**Unit & integration coverage for the Forget mutation engine**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Six tests targeting the Forget building blocks — blocking, context transitions, replacement computation, and end-to-end flow.

## 📁 Files at a glance

| File | Role |
| --- | --- |
| 🚧 `BlockingCheckerCase1Test.java` | Validates Case 1 (weak) blocking behavior. |
| 🚧 `BlockingCheckerTest.java` | Validates blocking across all three cases. |
| 🧾 `ForgetContextTransitionTest.java` | Validates `ForgetContext.updateForTransition` (Algorithm 1's bookkeeping). |
| 🔁 `ReplacementComputerTest.java` | Validates replacement candidate sets and variant generation. |
| 🧪 `ForgetMutationE2ETest.java` | End-to-end Forget flow on representative inputs. |
| 🧪 `ForgetMutationFullIntegrationTest.java` | Wider Forget integration coverage (multiple scenarios). |

## 🔬 Deep dive

<details>
<summary>🚧 <strong>BlockingCheckerCase1Test.java</strong></summary>

<br/>

- **🎯 Job:** Pin Case 1 semantics: a hypothesis is blocked iff it's in the Forget set.
- **🪝 Depends on:** `BlockingChecker`.

</details>

<details>
<summary>🚧 <strong>BlockingCheckerTest.java</strong></summary>

<br/>

- **🎯 Job:** Cover Cases 2 (pairing) and 3 (full DY) of `BlockingChecker`.
- **⚠️ Heads-up:** Update expectations whenever the blocking rules evolve.

</details>

<details>
<summary>🧾 <strong>ForgetContextTransitionTest.java</strong></summary>

<br/>

- **🎯 Job:** Verify the per-transition updates of `ForgetContext` — knowledge monotonic growth, Forget-set updates, tilde-tolerant type lookups.
- **⚠️ Heads-up:** Sensitive to Algorithm 1 changes.

</details>

<details>
<summary>🔁 <strong>ReplacementComputerTest.java</strong></summary>

<br/>

- **🎯 Job:** Validate replacement candidate filtering and Cartesian-product variant generation.
- **🪝 Depends on:** `ReplacementComputer`, `TermFormat`.
- **⚠️ Heads-up:** Update expectations when type/format rules change.

</details>

<details>
<summary>🧪 <strong>ForgetMutationE2ETest.java</strong> · <strong>ForgetMutationFullIntegrationTest.java</strong></summary>

<br/>

- **🎯 Job:** Drive the full Forget pipeline (parse → context → derivation → substitute / skip + neglect) against curated SPTHY fixtures.
- **🪝 Depends on:** `ForgetMutationStrategy` and the derivation stack.
- **⚠️ Heads-up:** These are the canaries — if they fail after a refactor, double-check Algorithm 1 semantics before "fixing" them.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `src/main/java/com/xmen/service/forget`. | Surefire / CI runs. |

## ⚙️ At runtime

Each test seeds `Message`, `ForgetContext`, or full pipelines and asserts on derivability, replacement sets, and output rules.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding tests for new scenarios; sharpening assertions. | Coupling assertions to derivation-output formatting that may legitimately change. |

---

> 💡 **Summary** — The safety net for Algorithm 1. If you change blocking or replacement, you'll meet these tests first.

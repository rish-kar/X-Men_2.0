<div align="center">

# 🔌 `integrationTests`

**End-to-end mutation tests — one per mutation kind**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Drives the full mutation pipeline (parser → strategy → file generator) against curated `.spthy` inputs and compares results to checked-in `.m` fixtures.

## 🗺️ Where this sits

```text
   .spthy fixtures (src/test/resources)
              ↓
🟢  integrationTests   ←  you are here
              ↓
   Controllers / Services / Mutation strategies
              ↓
   Generated .m files ⇄  expected .m fixtures
```

## 📁 Files at a glance

### Add / Replace / Neglect

| File | Validates |
| --- | --- |
| ➕ `AddMutationTests.java` | Add mutation outputs. |
| ❌ `NeglectMutationTests.java` | Neglect mutation outputs. |
| 🔄 `ReplaceSubmessagesTests.java` | Replace Sub-Messages outputs. |
| 🔄 `ReplaceTypeTests.java` | Replace Type outputs. |

### Skip family

| File | Validates |
| --- | --- |
| ⏭ `SkipSendTests.java` | Skip Send. |
| ⏭ `SkipReceiveTests.java` | Skip Receive. |
| ⏭ `SkipSendReceiveTests.java` | Skip Send→Receive. |
| ⏭ `SkipReceiveSendTests.java` | Skip Receive→Send. |
| ⏭ `SkipReceiveSendReceiveTests.java` | Skip Receive→Send→Receive. |

### Forget

| File | Validates |
| --- | --- |
| 🧠 `ForgetMutationStrategyIntegrationTest.java` | Strategy-level Forget behaviour. |
| 🧠 `ForgetNeglectTriggerTest.java` | Forget triggering Neglect on internal actions. |
| 🧠 `TrialCaseForgetMutationTest.java` | Forget on the `TrialCase` model. |

## 🔬 Deep dive

<details>
<summary>🧠 <strong>ForgetMutationStrategyIntegrationTest.java</strong></summary>

<br/>

- **🎯 Job:** Drive `ForgetMutationStrategy` end-to-end and assert on the generated `.m` outputs.
- **📥 Inputs:** Forget-flavoured SPTHY fixtures under `src/test/resources`.
- **⚠️ Heads-up:** Sensitive to changes in derivation logic, replacement rules, and substitution semantics.

</details>

<details>
<summary>🧠 <strong>ForgetNeglectTriggerTest.java</strong></summary>

<br/>

- **🎯 Job:** Verify that Forget correctly triggers Neglect on internal actions that reference the forgotten term — and that it *does not* fire when substitution is the right path.
- **⚠️ Heads-up:** Pinned to Algorithm 1's branching behaviour.

</details>

<details>
<summary>🧠 <strong>TrialCaseForgetMutationTest.java</strong></summary>

<br/>

- **🎯 Job:** Run the Forget mutation against the shipped `TrialCase.spthy` and compare against the `CoachService_*.m` fixtures.
- **⚠️ Heads-up:** If the `TrialCase` model or fixtures are updated, regenerate expected outputs in lock-step.

</details>

<details>
<summary>⏭ <strong>Skip*Tests.java</strong> (the family)</summary>

<br/>

- **🎯 Job:** Validate every Skip variant individually.
- **🔧 Key logic:** Each test loads a Skip fixture, runs the matching strategy, and compares the produced `.m` files to checked-in expected outputs (`SkipSend_*`, `SkipReceive_*`, …).
- **⚠️ Heads-up:** Tied to `RulesModifier`'s chain-propagation semantics.

</details>

<details>
<summary>➕❌🔄 <strong>Add / Neglect / Replace tests</strong></summary>

<br/>

Same pattern as Skip — each test loads a small fixture, runs its strategy, and compares output `.m` files to checked-in fixtures (`Add_*`, `ReplaceSubMessages_*`, `ReplaceType_*`).

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `src/main/java/com/xmen/service/impl` (the strategies under test) · `src/test/resources` (fixtures). | Maven Surefire and CI runs. |

## ⚙️ At runtime

Each test seeds Spring, points the pipeline at a fixture, runs the relevant mutation, and asserts the resulting `.m` files match the expected ones byte-for-byte (or via tolerant comparison where appropriate).

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding new fixtures + assertions for new mutation behaviour. | Coupling to specific filenames in `src/test/resources` — they're the contract with the test. |

---

> 💡 **Summary** — One test per mutation kind. Edit the strategy → regenerate the matching fixture, or update the assertion.

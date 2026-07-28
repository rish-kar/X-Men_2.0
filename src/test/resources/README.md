<div align="center">

# 📦 `src/test/resources`

**Test fixtures — SPTHY inputs and expected `.m` outputs**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; The corpus that drives every integration test: a handful of `.spthy` inputs and a large set of `.m` files representing the expected mutation outputs. Plus a logging override for tests.

## 📁 Layout

### ⚙️ Test configuration

| File | Role |
| --- | --- |
| `application-test.yaml` | Test-time logging configuration (minimal banner, quieter levels). |

### 📥 SPTHY inputs

| File | Role |
| --- | --- |
| `CoachService.spthy` | Sample protocol model (CoachService). |
| `Oyster.spthy` | Sample protocol model (Oyster card). |
| `Forget_Bank_Input.spthy` | Forget mutation input — bank scenario. |
| `Forget_Neglect_Internal.spthy` | Forget + Neglect — forgotten term used **only** in an internal action. |
| `Forget_Neglect_Mixed.spthy` | Forget + Neglect — forgotten term used in **both** action and `m₂`. |
| `Forget_Neglect_NoInternal.spthy` | Forget + Neglect — forgotten term used **only** in `m₂`. |

### 📤 Expected `.m` outputs

| Family | Files | What they cover |
| --- | --- | --- |
| Add | `Add_0.m` … `Add_91.m` | Expected outputs for the Add mutation across many variants. |
| CoachService | `CoachService_1.m` … `CoachService_4.m` | Mutated outputs for the `CoachService` model. |
| Replace Sub | `ReplaceSubMessages_0.m` … `_7.m` | Replace Sub-Messages variants. |
| Replace Type | `ReplaceType_0.m` … `_2.m` | Replace Type variants. |
| Skip Send | `SkipSend_0.m`, `_1.m` | Skip Send variants. |
| Skip Receive | `SkipReceive_0.m`, `_1.m` | Skip Receive variants. |
| Skip Send→Receive | `SkipSendReceive_0.m` … `_2.m` | Skip Send→Receive variants. |
| Skip Receive→Send | `SkipReceiveSend_0.m` | Skip Receive→Send variant. |
| Skip Receive→Send→Receive | `SkipReceiveSendReceive_0.m` | Skip Receive→Send→Receive variant. |
| Forget | `Forget_Bank_Output.m` | Expected output for the bank Forget scenario. |

## 🔬 Deep dive

<details>
<summary>⚙️ <strong>application-test.yaml</strong></summary>

<br/>

- **🎯 Job:** Quiet down logging during test runs and disable the banner.
- **🤝 Used by:** Spring Boot tests when the `test` profile is active.
- **⚠️ Heads-up:** Keep noise low — verbose logs slow down CI feedback loops.

</details>

<details>
<summary>📥 <strong>SPTHY inputs</strong> (Forget + samples)</summary>

<br/>

- **🎯 Job:** Provide the SPTHY models the integration tests parse and mutate.
- **🪝 Depends on:** The SPTHY grammar and `ModelLoader`.
- **⚠️ Heads-up:** Tests assume specific rule names and structures — coordinate edits with the test classes.

</details>

<details>
<summary>📤 <strong>Expected <code>.m</code> outputs</strong></summary>

<br/>

- **🎯 Job:** The golden files mutation tests compare against.
- **🪝 Depends on:** `Rule.toString()` and the formatting choices in `MutatedFileGenerator`.
- **⚠️ Heads-up:** When intentional changes to mutation logic land, regenerate the matching fixture(s) — don't silently relax the test.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `src/main/java/com/xmen/utilities/ModelLoader` for parsing inputs. | Integration and service tests under `src/test/java`. |

## ⚙️ At runtime

Each integration test loads one SPTHY input from this folder, runs a mutation strategy, and compares the resulting `.m` files to the matching golden fixtures.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding new fixtures when adding new tests. | Renaming existing fixtures (they're referenced by string in tests). Editing existing `.m` outputs without re-running the matching test. |

---

> 💡 **Summary** — Inputs and golden outputs that pin mutation behavior. Treat the `.m` files as test contracts.

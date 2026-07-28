<div align="center">

# 🖥️ `userInterfaceTests`

**JavaFX UI tests**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Validates that `XMenInterface` launches and interacts with its controls as expected.

## 📁 Files at a glance

| File | Role |
| --- | --- |
| 🎬 `XMenInterfaceTest.java` | Smoke / interaction test for the JavaFX UI. |

## 🔬 Deep dive

<details>
<summary>🎬 <strong>XMenInterfaceTest.java</strong></summary>

<br/>

- **🎯 Job:** Exercise the JavaFX UI — likely via TestFX — and assert on key control IDs and basic flows.
- **🪝 Depends on:** `XMenInterface` and the JavaFX test framework.
- **⚠️ Heads-up:** Tests are coupled to control IDs/style classes used in `XMenInterface`; rename one and you'll see test failures here first.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `src/main/java/com/xmen/user_interface/XMenInterface`. | Surefire / CI runs. |

## ⚙️ At runtime

The UI test framework spins up a JavaFX scene, interacts with controls, and asserts on visible behavior.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding tests for new UI flows; tweaking timing for flaky interactions. | Control IDs and CSS classes — they're the contract with the UI test framework. |

---

> 💡 **Summary** — One UI test class. Update it whenever the JavaFX layout changes.

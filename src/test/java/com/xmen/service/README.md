<div align="center">

# 🧠 `service` (tests)

**Service-layer unit tests**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Targeted unit tests for service-layer helpers, plus a `forget/` sub-package covering the Forget mutation's internals.

## 📁 Files at a glance

| Entry | Role |
| --- | --- |
| ✂️ `FileSplitterServiceTest.java` | Validates that `FileSplitterService` correctly carves a `.spthy` into preamble / rules / postamble. |
| 🧠 `forget/` | Forget mutation helper tests — see [`forget/README.md`](forget/README.md). |

## 🔬 Deep dive

<details>
<summary>✂️ <strong>FileSplitterServiceTest.java</strong></summary>

<br/>

- **🎯 Job:** Exercise `FileSplitterService` against representative inputs (with / without `/****RULES****/` markers, missing `end`, etc.).
- **🤝 Used by:** Surefire and IDE runners.
- **🪝 Depends on:** `src/main/java/com/xmen/service/FileSplitterService`.
- **⚠️ Heads-up:** Keep aligned with the marker logic in `FileSplitterService` — drift will produce hard-to-debug parse failures elsewhere.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `src/main/java/com/xmen/service`. | Surefire / CI test runs. |

## ⚙️ At runtime

Each test feeds the service in-memory content and asserts on the returned section record.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Test cases, additional edge cases. | Tying tests to specific whitespace / formatting that the service may legitimately change. |

---

> 💡 **Summary** — One unit test here + a Forget-focused sub-package next door.

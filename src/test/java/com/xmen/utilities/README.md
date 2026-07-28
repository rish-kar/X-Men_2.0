<div align="center">

# 🧰 `utilities` (tests)

**Parser and utility tests**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Lexer / parser smoke tests plus a higher-level Tamarin parser test that validates parse-tree handling.

## 📁 Files at a glance

| File | Role |
| --- | --- |
| 🔬 `LexerDebugTest.java` | Debug-level coverage of the SPTHY lexer. |
| 🔬 `ParserDebugTest.java` | Debug-level coverage of the SPTHY parser. |
| 🧪 `TamarinParserTest.java` | End-to-end SPTHY parsing — parse tree creation and traversal. |

## 🔬 Deep dive

<details>
<summary>🔬 <strong>LexerDebugTest.java</strong></summary>

<br/>

- **🎯 Job:** Validate lexer token output against representative SPTHY snippets.
- **🪝 Depends on:** ANTLR-generated lexer in `src/main/java/com/xmen/model`.
- **⚠️ Heads-up:** Update when the grammar changes.

</details>

<details>
<summary>🔬 <strong>ParserDebugTest.java</strong></summary>

<br/>

- **🎯 Job:** Validate parser behavior on representative SPTHY input.
- **🪝 Depends on:** ANTLR-generated parser.
- **⚠️ Heads-up:** Update when the grammar evolves.

</details>

<details>
<summary>🧪 <strong>TamarinParserTest.java</strong></summary>

<br/>

- **🎯 Job:** Drive the full parsing pipeline (`ModelLoader` → ANTLR → `TamVisitor`) and assert on the resulting `Rule` list.
- **🪝 Depends on:** `ModelLoader` and the ANTLR runtime.
- **⚠️ Heads-up:** The closest thing to a parser regression test — update expectations when grammar or visitor change.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| ANTLR parser classes under `src/main/java/com/xmen/model`; parsing helpers in `src/main/java/com/xmen/utilities`. | Surefire / CI runs. |

## ⚙️ At runtime

Each test feeds the parser a sample input and asserts on the resulting token stream or parse tree.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding cases for new grammar features; debug-style assertions. | Coupling to parser output formatting — small grammar updates can ripple here. |

---

> 💡 **Summary** — Parser smoke tests. If you change the grammar, expect to touch these first.

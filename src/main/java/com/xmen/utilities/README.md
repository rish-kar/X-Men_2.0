<div align="center">

# 🧰 `utilities`

**Parsers, file I/O, tag plumbing, and shared helpers**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; The toolbox that powers every other layer: parses `.spthy`, organises rules, propagates mutations, extracts setup knowledge, and writes the mutated `.m` files.

## 🗺️ Where this sits

```text
controller / service
        ↓
🟢  utilities    ←  you are here
        ├── ModelLoader               (.spthy → ParametersBundle)
        ├── FileHandler               (rule chain, tags, persistent knowledge)
        ├── ForgetMutationParser      (extract Forget(...) facts)
        ├── SetupKnowledgeExtractor   (type map from humansetup)
        ├── RulesModifier             (Skip chain propagation)
        ├── TagSetter                 (combined-mutation flags)
        ├── UtilityFunctions          (cloning, traversal, state helpers)
        └── MutatedFileGenerator      (write .m files to disk)
        ↓
   model + filesystem
```

## 📁 Files at a glance

| File | Role |
| --- | --- |
| 📚 `ModelLoader.java` | Parses a `.spthy` upload via ANTLR and builds a populated `ParametersBundle`. |
| 🧷 `FileHandler.java` | Arranges rule chains, merges/demerges tags, spreads persistent knowledge through state. |
| 🧠 `ForgetMutationParser.java` | Walks rule actions for `Forget(...)` facts (with a regex fallback). |
| 🧪 `SetupKnowledgeExtractor.java` | Pulls type mappings from `humansetup` / `Setup` rules. |
| ✏️ `MutatedFileGenerator.java` | Writes `_M*.m` files into the working directory. |
| 🔗 `RulesModifier.java` | Propagates rule changes along the chain after Skip mutations. |
| 🏷️ `TagSetter.java` | Sets `Flags` in `ParametersBundle` for Replace / Add combinations. |
| 🛠️ `UtilityFunctions.java` | General helpers: clone, traverse, rebuild state facts, knowledge updates. |

## 🔬 Deep dive

<details>
<summary>📚 <strong>ModelLoader.java</strong></summary>

<br/>

- **🎯 Job:** Turn an uploaded `.spthy` into a `ParametersBundle`.
- **🔧 Key logic:**
  1. Validates the file extension.
  2. Runs the ANTLR lexer + parser.
  3. Delegates to `TamVisitor` to build `Rule` objects, then to `FileHandler` to organise them.
- **🤝 Used by:** `FileLoadingService`.
- **⚠️ Heads-up:** Parser error thresholds decide which files are accepted.

</details>

<details>
<summary>🧷 <strong>FileHandler.java</strong></summary>

<br/>

- **🎯 Job:** Wire up the post-parse rule structure: chain links, tags, persistent knowledge.
- **🔧 Key logic:**
  1. Connects each rule's `previous`/`next` pointers.
  2. Merges and de-merges tag values on `Snd`/`Rcv` facts.
  3. Spreads tag knowledge through `State` postconditions.
- **🤝 Used by:** `ModelLoader`, `MutatedFileGenerator`.
- **⚠️ Heads-up:** Tag merging must match mutation rules and output formatting expectations.

</details>

<details>
<summary>🧠 <strong>ForgetMutationParser.java</strong></summary>

<br/>

- **🎯 Job:** Find `Forget(...)` action facts and populate `ParametersBundle.forgetMutationSet`.
- **🔧 Key logic:**
  1. First-pass parse via the rule model.
  2. Regex fallback for cases the grammar path misses.
- **⚠️ Heads-up:** Keep the regex fallback in sync with SPTHY syntax.

</details>

<details>
<summary>🧪 <strong>SetupKnowledgeExtractor.java</strong></summary>

<br/>

- **🎯 Job:** Build the value → type map used by Forget for replacement filtering.
- **🔧 Key logic:**
  1. Find the principal in the `Setup` rule.
  2. Extract `!Type(...)` and `!Cred(...)` mappings from `humansetup`.
  3. Fall back to inferring types from `State` values when needed.
- **🤝 Used by:** `ForgetMutationStrategy`, `MutationController`.
- **⚠️ Heads-up:** Type inference accuracy directly affects which replacements Forget considers valid.

</details>

<details>
<summary>✏️ <strong>MutatedFileGenerator.java</strong></summary>

<br/>

- **🎯 Job:** Emit the actual `.m` artefacts.
- **🔧 Key logic:**
  1. Deletes prior `_M*.m` files in the working directory.
  2. Writes preamble + functions + builtins + rules + postamble.
  3. Calls `FileHandler.demergeTagsValues` before serialising.
- **🤝 Used by:** `MutationGeneratorServiceImpl`.
- **⚠️ Heads-up:** Naming (`<base>_M<n>.m`) is part of the contract with `ZipService`.

</details>

<details>
<summary>🔗 <strong>RulesModifier.java</strong></summary>

<br/>

- **🎯 Job:** Propagate Skip mutations down the rule chain.
- **🔧 Key logic:** Re-balance preconditions / actions / postconditions after a fact removal; append `_M` to mutated rules.
- **⚠️ Heads-up:** Chain consistency is fragile — incorrect updates manifest as broken `.m` files.

</details>

<details>
<summary>🏷️ <strong>TagSetter.java</strong></summary>

<br/>

- **🎯 Job:** Read the selected mutation set, set the corresponding `Flags` on the bundle.
- **🤝 Used by:** Controllers (before invoking mutation generation).

</details>

<details>
<summary>🛠️ <strong>UtilityFunctions.java</strong></summary>

<br/>

- **🎯 Job:** Catch-all helpers used across every strategy: cloning, rule traversal, knowledge updates, new state-fact construction.
- **⚠️ Heads-up:** Many strategies pull from here — utility changes ripple widely.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `../model` (parser classes + rule types). `../service` for integration points. | `../controller`, `../service/impl` strategies. |

## ⚙️ At runtime

Upload → `ModelLoader` parses → `FileHandler` arranges → mutation strategies (in `service/impl`) call into `UtilityFunctions` and `RulesModifier` as they work → `MutatedFileGenerator` writes the final `.m` files.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Output formatting tweaks; logging; helper methods. | Tag merging / demerging; rule-chain connectivity; file-deletion logic in `MutatedFileGenerator`. |

## 🧯 Notable error paths

- `ModelLoader` — ANTLR error threshold rejects malformed files.
- `ForgetMutationParser` — falls back to regex when grammar parsing fails.
- `UtilityFunctions` — defensive cloning + null checks.

---

> 💡 **Summary** — The plumbing that holds everything together. Top picks: `ModelLoader`, `FileHandler`, `MutatedFileGenerator`.

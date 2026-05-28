<div align="center">

# 🧱 `model`

**Domain model — parsed Tamarin AST, message terms, mutation enums**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Hand-written domain classes plus ANTLR-generated parser classes. Used everywhere — controllers, services, utilities, derivation logic — as the lingua franca of the codebase.

## 🗺️ Where this sits

```text
.spthy text
   ↓
TamarinLexer → TamarinParser → TamVisitor   (parsing)
   ↓
🟢 model    ←  Rule / Fact / Message / Pair / Encrypt / ...
   ↓
mutation strategies, derivation engine, file generator
```

## 📁 Files at a glance

### 🧬 Core domain types

| File | Role |
| --- | --- |
| `Rule.java` | A protocol rule with preconditions, actions, postconditions, variables, and mutation state. Renders itself back to `.m` syntax. |
| `Fact.java` | A fact inside a rule, with parameters and mutation flags. |
| `ParametersBundle.java` | The aggregating state object passed through the pipeline (theory + flags + Forget set + extras). |
| `Function.java` | A user-defined function declaration parsed from SPTHY. |
| `Builtins.java` | Built-in functions / constructs from SPTHY. |
| `Mutations.java` | Enum of supported mutations (Skip, Add, Replace, Forget, Neglect). |
| `Mutants.java` | An old → new replacement pair used by mutation strategies. |
| `Flags.java` | Boolean flags steering combined Add/Replace logic. |
| `Type.java` | Enum tag — e.g. `MUTATED`. |
| `TypeFact.java` | Enum tag — `PRE` / `POST`. |
| `DerivationType.java` | Derivation modes: `LIMITED`, `DEPTH_SPECIFIED`, `INFINITE`. |
| `Derivation.java` | A derivation-tree node (goal, rule label, premises). |
| `InMemoryMultipartFile.java` | In-memory `MultipartFile` for rule-only parsing scenarios. |
| `Component.java` | Base AST component type. |

### ✉️ Message-algebra types

| File | Role |
| --- | --- |
| `Message.java` | Common interface for every message term. |
| `Atom.java` | Atomic message term. |
| `Pair.java` | Pair / tuple message term. |
| `Encrypt.java` | Encrypted message term. |
| `PredictiveFunction.java` | Function term with arguments — used heavily in derivations. |
| `Nary_app.java` | N-ary function application term. |
| `FSpecial.java` | Special function-term container. |
| `PSpecial.java` | Positional tuple / value-group wrapper. |
| `Special.java` | Base class for special term containers. |
| `Variable.java` | Variable term containing nested values. |
| `Value.java` | Value term carrying tags, removal flags, and knowledge status. |
| `Abs_Value.java` | Base value type. |

### 🤖 ANTLR-generated parser

| File | Role |
| --- | --- |
| `TamarinLexer.java` | Generated lexer. |
| `TamarinParser.java` | Generated parser. |
| `TamarinVisitor.java` | Generated visitor interface. |
| `TamarinBaseVisitor.java` | Generated base visitor. |
| `TamVisitor.java` | Hand-written visitor that builds `Rule` objects from the parse tree. |

## 🔬 Deep dive — the headline classes

<details>
<summary>📄 <strong>Rule.java</strong> — the central rule type</summary>

<br/>

- **🎯 Job:** Represent one protocol rule end-to-end.
- **🔧 Key logic:**
  1. Holds preconditions, actions, postconditions, variables.
  2. Serializes itself back to `.m` syntax via `toString()`.
  3. Supports cloning so mutation strategies can spawn variants.
- **📥 Inputs:** Facts, variables, rule metadata.
- **📤 Outputs:** String form used in generated `.m` files.
- **🤝 Used by:** Every mutation strategy, every file generator.
- **🪝 Depends on:** `Fact`, `Variable`, `Value`, and the message terms.
- **⚠️ Heads-up:** `toString()` *is* the output format. Any change is observable in mutated files.

</details>

<details>
<summary>📄 <strong>Fact.java</strong></summary>

<br/>

- **🎯 Job:** Represent one fact with its parameters.
- **🔧 Key logic:** Parameters are a `List<Object>` of `Value`, `PSpecial`, or `Variable`. Supports cloning and removal marking.
- **🤝 Used by:** `Rule` and every mutation utility.
- **⚠️ Heads-up:** Removal flags propagate into rule output and matching logic.

</details>

<details>
<summary>📄 <strong>ParametersBundle.java</strong> — the pipeline state object</summary>

<br/>

- **🎯 Job:** Carry parsed theory + flags + Forget set + extras between layers.
- **🔧 Key logic:** Collections of rule variants, `Flags` state, forget map, derivation parameters, plus an `extraContent` map for preamble/postamble and current-rule context.
- **🤝 Used by:** Controllers, services, utilities.
- **⚠️ Heads-up:** This object is mutated in place — be deliberate when adding new fields.

</details>

<details>
<summary>📄 <strong>Mutations.java</strong> · <strong>Flags.java</strong></summary>

<br/>

- **Mutations** — enum of every supported mutation. Adding a value here requires a matching strategy and controller wiring.
- **Flags** — boolean state used by `TagSetter` and the Replace family for combined-mutation semantics.

</details>

<details>
<summary>📄 <strong>Message.java</strong> · <strong>Atom · Pair · Encrypt · PredictiveFunction</strong></summary>

<br/>

The message algebra. `Message` is the marker interface; each concrete subclass implements `represent()` for derivation analysis and equality semantics. Used pervasively by the Forget mutation and the Java derivation engine.

</details>

<details>
<summary>📄 <strong>Value.java</strong> · <strong>Variable.java</strong></summary>

<br/>

Atomic value and variable terms with removal flags, tags, and a knowledge-state bit. Equality semantics are load-bearing — many comparisons across the pipeline rely on them.

</details>

<details>
<summary>📄 <strong>ANTLR generated classes</strong></summary>

<br/>

`TamarinLexer`, `TamarinParser`, `TamarinVisitor`, `TamarinBaseVisitor` are regenerated from the grammar. **Do not edit by hand** — regenerate when the grammar changes. `TamVisitor` is the hand-written companion that converts parse trees into the domain model.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| ANTLR runtime + generated classes. | `../utilities` (parsing, rule manipulation) · `../service` (derivation & mutation) · `../controller` indirectly. |

## ⚙️ At runtime

A `.spthy` input goes through `TamarinLexer → TamarinParser → TamVisitor` to become a list of `Rule` objects wrapped in a `ParametersBundle`. Mutation strategies clone and mutate those rules; `Rule.toString()` then renders the result back into `.m` text.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding new model classes for new grammar features. Helper methods that don't change serialization. | `Rule.toString()` output format. ANTLR-generated parser classes. `Message` / `Value` equality semantics. |

---

> 💡 **Summary** — The shared vocabulary. `Rule`, `Fact`, `ParametersBundle`, and the `Message` family carry every piece of state moving through the pipeline.

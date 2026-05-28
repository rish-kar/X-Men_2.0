<div align="center">

# 🌲 `service/derivation`

**Java-side derivation configuration and pretty-printing**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Two small helpers that control what the Java DY engine is allowed to decompose, and how it renders derivation trees for humans.

## 🗺️ Where this sits

```text
Forget mutation request
       ↓
DerivationCheckService (impl)
       ↓
🟢 derivation/   ←  config + printer
       ↓
Forget decision (substitute / skip)
```

## 📁 Files at a glance

| File | Role |
| --- | --- |
| 🎛️ `DerivationConfig.java` | Whitelist of decomposable functions + depth and size caps. |
| 🖨️ `DerivationTreePrinter.java` | Pretty-prints derivation trees and zero-derivation reports. |

## 🔬 Deep dive

<details>
<summary>🎛️ <strong>DerivationConfig.java</strong></summary>

<br/>

- **🎯 Job:** Decide which function symbols the Java engine may decompose during derivation search, and how deep/wide it may go.
- **🔧 Key logic:**
  1. Whitelist of decomposable DY functions (pair, senc, aenc, …).
  2. Blocks decomposition of user-defined or blacklisted functions.
  3. Configurable max depth and max derivations per target.
- **📥 Inputs:** User-defined `Function`s parsed from the model.
- **📤 Outputs:** Config object consumed by the derivation engine.
- **🤝 Used by:** `DerivationServiceImpl`, `ForgetDerivationChecker`.
- **⚠️ Heads-up:** Whitelist changes directly affect derivation completeness and performance.

</details>

<details>
<summary>🖨️ <strong>DerivationTreePrinter.java</strong></summary>

<br/>

- **🎯 Job:** Render derivation trees and zero-derivation explanations for the console / zip output.
- **🔧 Key logic:**
  1. Renders knowledge-used trees with `(K)` markers.
  2. When no derivation exists, prints an explanatory report that the Forget pipeline uses for diagnostics.
  3. Offers compact one-line formatting for logs.
- **📥 Inputs:** `Derivation` tree, target `Message`, knowledge set.
- **📤 Outputs:** Human-readable text printed to `System.out` (captured by `DerivationTreeCaptureService`).
- **⚠️ Heads-up:** Don't remove the zero-derivation reporting — Forget diagnostics rely on it.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `../../model` (derivation types). | `../impl/DerivationServiceImpl` · `../forget/ForgetDerivationChecker`. |

## ⚙️ At runtime

During Forget processing, `DerivationConfig` limits decomposition while `DerivationTreePrinter` writes the derivation tree (or a zero-derivation report) to `System.out`. The output is captured by `DerivationTreeCaptureService` for inclusion in the response zip.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Output formatting tweaks; log messages. | The decomposable-function set; the textual format consumed by the capture service. |

---

> 💡 **Summary** — Tiny utility package: what the engine may decompose, and how to print the result.

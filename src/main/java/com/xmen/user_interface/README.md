<div align="center">

# 🖥️ `user_interface`

**The JavaFX native UI — the friendly face of X-Men**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; A desktop UI that lets users pick a `.spthy` file, toggle mutations, and trigger the API without touching a terminal. Optional — it only opens when the application isn't headless.

## 🗺️ Where this sits

```text
User
  ↓
🟢  XMenInterface (JavaFX)   ←  you are here
  ↓ HTTP (multipart)
controller → service → ... → zip response
```

## 📁 Files at a glance

| File | Role |
| --- | --- |
| 🎨 `XMenInterface.java` | Builds the UI, sends multipart mutation requests, surfaces results and derivation overlays. |

## 🔬 Deep dive

<details>
<summary>🎨 <strong>XMenInterface.java</strong></summary>

<br/>

- **🎯 Job:** Implement the entire JavaFX experience — splash screen, main scene, request submission, alerts.
- **🔧 Key logic:**
  1. Splash screen with background media (with `splash_fallback_logo.png` / `main_scene_dna_fallback.png` fallbacks if videos fail).
  2. Checkboxes for every mutation, plus the derivation-mode controls.
  3. Builds a multipart `POST` to `/api/generateMutations` (or `/api/forget/mutations` when Forget is selected) using OkHttp.
  4. On success, optionally extracts and displays the derivation-tree text file from the returned `.zip`.
- **📥 Inputs:** User-picked `.spthy` file + checkbox selections.
- **📤 Outputs:** HTTP requests, JavaFX alerts, derivation overlay.
- **🤝 Used by:** `Application.java` when the runtime isn't headless.
- **🪝 Depends on:** OkHttp, JavaFX, assets under `src/main/resources`.
- **⚠️ Heads-up:** Endpoint paths and header names must stay aligned with the controllers — drift breaks the UI silently.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `../controller` (target endpoints) · `src/main/resources/css` and `images` (styling and assets). | `../Application.java` launches the UI. |

## ⚙️ At runtime

Splash screen → main scene with checkbox grid and the file picker → user clicks **Start Mutation** → multipart request goes out → success/error alert → derivation overlay if a tree file is present.

## 🌐 Endpoints it calls

| Endpoint | Method | When |
| --- | --- | --- |
| `/api/generateMutations` | `POST` | Default for any non-Forget combination. |
| `/api/forget/mutations` | `POST` | When Forget is selected (alone or together). |

## 📨 Headers it sends

| Header | When set |
| --- | --- |
| `Skip-Send` / `Skip-Receive` / `Skip-Send-Receive` / `Skip-Receive-Send` / `Skip-Receive-Send-Receive` | Per Skip checkbox. |
| `Add-Mutation` | When *Add* is ticked. |
| `Replace-Sub-Messages` / `Replace-Type` | Per Replace checkbox. |
| `Forget-Mutation` | When *Forget* is ticked. |
| `Haskell-Activate` | When the Haskell toggle is on. |
| `Derivation-Type` / `Derivation-Depth` | When derivation mode controls are used. |

## 🧯 Edge cases

- No `.spthy` selected → warning alert.
- HTTP error → error alert with the response excerpt.
- Forget selected but no `Forget(...)` action found in the file → dedicated alert.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Layout, styling, alert copy. | Endpoint URLs, header names, ZIP extraction logic for derivation trees, resource paths. |

---

> 💡 **Summary** — One file does the whole desktop UI. Headers and endpoints must mirror what the controllers expect.

<div align="center">

# 🖼️ `resources/images`

**UI image assets — splash, alerts, branding, fallbacks**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; PNGs loaded by `XMenInterface` for alert icons, branding, and fallback visuals when video media can't be played.

## 📁 Files at a glance

| File | Used for |
| --- | --- |
| ✅ `dna_logo.png` | Success alert icon. |
| ❌ `error_mutation.png` | Error alert icon. |
| ⚠️ `warning_mutation.png` | Warning alert (e.g. no file selected). |
| 🧠 `forget_not_found.png` | Alert shown when Forget is selected but the file has no `Forget(...)` action. |
| 🦴 `main_scene_dna_fallback.png` | Fallback background when the main-scene video can't be loaded. |
| 🚀 `splash_fallback_logo.png` | Fallback splash image when the splash video can't be loaded. |
| 🏷️ `Front-End-Logo.png` | Branding asset — also used as the main project README banner. |
| 🖼️ `img.png` | Screenshot of the native UI — used in the project README. |

## 🔬 Deep dive

<details>
<summary>✅ <strong>dna_logo.png</strong></summary>

Shown by `XMenInterface` after a successful mutation request. Filename stable — referenced by string.

</details>

<details>
<summary>❌ <strong>error_mutation.png</strong> · ⚠️ <strong>warning_mutation.png</strong> · 🧠 <strong>forget_not_found.png</strong></summary>

Alert dialog icons for failure, warning, and the "no Forget action found" case. Loaded on demand from `XMenInterface`.

</details>

<details>
<summary>🦴 <strong>main_scene_dna_fallback.png</strong> · 🚀 <strong>splash_fallback_logo.png</strong></summary>

Used when the corresponding `.mp4` media (`videos/*.mp4` / `X - Men 2.0.mp4`) can't be loaded — e.g. on systems without the JavaFX media module.

</details>

<details>
<summary>🏷️ <strong>Front-End-Logo.png</strong> · 🖼️ <strong>img.png</strong></summary>

Branding asset and a screenshot of the native UI. Referenced in the top-level project README. Don't rename without updating the README links.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| Classpath resource loading via `getResourceAsStream("/images/<file>")`. | `../../java/com/xmen/user_interface/XMenInterface.java` and the project READMEs. |

## ⚙️ At runtime

`XMenInterface` loads each image when it needs the corresponding alert or fallback. Missing assets produce a logged warning and a text fallback in the UI.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding new images; replacing them with same-name files. | Renaming or removing files referenced by `XMenInterface` or the README. |

---
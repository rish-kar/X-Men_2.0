<div align="center">

# 🎒 `src/main/resources`

**Runtime resources — config, UI media, sample inputs, Postman collection**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Everything Spring Boot and JavaFX need at runtime: `application.yaml`, splash media, fallback images, stylesheets, a Postman collection, and a sample SPTHY model.

## 🗺️ Where this sits

```text
src/main/resources    ←  you are here
   ├── application.yaml          (Spring config + env-var defaults)
   ├── *.mp4 / *.png             (UI media)
   ├── css/                      (JavaFX stylesheets)
   ├── images/                   (UI icons + fallbacks)
   ├── X-Men.postman_collection.json   (API requests)
   ├── TrialCase.spthy           (sample input for demos)
   └── Forget_Mutation.pdf       (research artefact)
```

## 📁 Files at a glance

| File                               | Role |
|------------------------------------| --- |
| ⚙️ `application.yaml`              | Spring Boot configuration with env-var defaults (`SERVER_PORT`, `APP_CORS_ALLOWED_ORIGINS`, `DERIVATION_SERVICE_URL`, …). |
| 🎬 `DNA-Background.mp4`            | Background video for the JavaFX main scene. |
| 🎬 `X - Men 2.0.mp4`               | Splash-screen video. |
| 📦 `X-Men.postman_collection.json` | Importable Postman collection — every mutation endpoint pre-wired. |
| 🧪 `TrialCase.spthy`               | Sample SPTHY model used in demos and tests. |
| 📄 `Forget_Mutation.pdf`           | Research / documentation artefact for the Forget mutation. |
| 📝 `Packaging-Commands.txt`        | Packaging notes. |
| 🎨 `css/`                          | JavaFX stylesheets — see [`css/README.md`](css/README.md). |
| 🖼️ `images/`                      | UI image assets — see [`images/README.md`](images/README.md). |

## 🔬 Deep dive

<details>
<summary>⚙️ <strong>application.yaml</strong> — the live configuration</summary>

<br/>

- **🎯 Job:** Provide Spring Boot configuration with env-var-friendly defaults.
- **🔧 Key keys:**
  - `server.port` → `SERVER_PORT` (default `8081`)
  - `app.cors.allowed-origins` → `APP_CORS_ALLOWED_ORIGINS`
  - `derivation.service.url` → `DERIVATION_SERVICE_URL` (default `http://localhost:9091`)
  - `management.endpoints.web.exposure.include` → exposes `/actuator/health/**`
- **🤝 Used by:** `SecurityConfig`, `AppConfig`, `HaskellDerivationFetcher`.
- **⚠️ Heads-up:** Changing ports or URLs has knock-on effects on the UI, Docker, and the Haskell integration.

</details>

<details>
<summary>📦 <strong>X-Men.postman_collection.json</strong></summary>

<br/>

- **🎯 Job:** Ready-to-import Postman collection covering every mutation endpoint.
- **🤝 Used by:** Developers and testers exercising the API by hand.
- **⚠️ Heads-up:** Keep in sync when endpoints or headers change — or just regenerate it from the OpenAPI spec (`/v3/api-docs`).

</details>

<details>
<summary>🧪 <strong>TrialCase.spthy</strong></summary>

<br/>

- **🎯 Job:** A working sample model — used as demo input and by some tests.
- **🤝 Used by:** Tests and manual demos through `ModelLoader`.
- **⚠️ Heads-up:** Editing this file may break the tests that depend on it.

</details>

<details>
<summary>🎬 <strong>DNA-Background.mp4</strong> · <strong>X-Men-Logo.mp4</strong></summary>

<br/>

Media played by the JavaFX UI. If the video can't be loaded, `XMenInterface` falls back to the `splash_fallback_logo.png` / `main_scene_dna_fallback.png` images in `images/`. Keep filenames stable — they're referenced by string in code.

</details>

<details>
<summary>📄 <strong>Forget_Mutation.pdf</strong> · <strong>Packaging-Commands.txt</strong></summary>

<br/>

Reference artefacts — research notes for Forget and packaging hints. No runtime dependency on these files.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| Application code that performs classpath resource lookups. | `../java/com/sermas/x/men/config` (config keys) · `user_interface` (media, images, CSS) · documentation. |

## ⚙️ At runtime

On startup Spring Boot reads `application.yaml`. The JavaFX UI loads splash and main-scene media from this directory (with fallbacks from `images/`). Postman uses the JSON collection during development.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Postman collection examples, packaging notes. | `application.yaml` keys (Spring will silently fall back to defaults if you typo one). Filenames referenced by `XMenInterface`. |

---

> 💡 **TL;DR** — Config + media + a sample model + Postman. Read `application.yaml` first; everything else is presentation or reference material.

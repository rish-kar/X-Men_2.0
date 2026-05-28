<div align="center">

# 🌐 `controller`

**REST controllers — the public API surface of X-Men**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Receives multipart `.spthy` uploads, reads mutation-selection headers, and dispatches the work to the service layer. Returns `.zip` archives of mutated `.m` files.

## 🗺️ Where this sits

```text
   Client (Postman / Native UI / Swagger)
              ↓
🟢  controller   ←  you are here
              ↓
        service / impl
              ↓
   parser · derivation · file writer
              ↓
        zip response (.m files)
```

## 📁 Files at a glance

| File | Endpoint(s) | Purpose |
| --- | --- | --- |
| 🧬 `MutationController.java` | `POST /api/generateMutations` | Combined mutation engine, controlled by request headers. |
| ➕ `AddMutationController.java` | `POST /api/addMutations` | Generates *Add* mutations. |
| 🧠 `ForgetMutationController.java` | `POST /api/forget/mutations` | *Forget* mutations + optional Haskell derivation. |
| ❌ `NeglectMutationController.java` | `POST /api/neglect/mutations` | *Neglect* mutations. |
| 🔄 `ReplaceMutationController.java` | `POST /api/replace/subMessagesMutations`, `POST /api/replace/typeMutations` | *Replace* family. |
| ⏭ `SkipMutationController.java` | `POST /api/skip/*` | *Skip* family (send, receive, chains). |
| 🧪 `HaskellDerivationController.java` | `POST /api/derive`, `GET /api/derive/health` | Direct derivation analysis through the Haskell service. |

## 🔬 Deep dive

<details>
<summary>🧬 <strong>MutationController.java</strong> — combined engine</summary>

<br/>

- **🎯 Job:** Run any combination of mutations on one upload, picked by request headers.
- **🔧 Key logic:**
  1. Reads boolean headers → `Set<Mutations>`.
  2. `FileSplitterService` separates preamble / rules / postamble.
  3. `FileLoadingService` parses and `TagSetter` applies flags.
  4. If `Haskell-Activate=true`, switches Forget derivation to the external service.
  5. `MutationGeneratorService` runs; `ZipService` packages the response.
- **📥 Inputs:** `.spthy` file + headers (`Skip-*`, `Add-Mutation`, `Replace-*`, `Forget-Mutation`, `Neglect-Mutation`, `Haskell-Activate`, `Derivation-Type`, `Derivation-Depth`).
- **📤 Outputs:** `.zip` of `.m` files; sometimes also a derivation-tree text file.
- **🤝 Used by:** External clients (Postman, Swagger UI, native UI).
- **🪝 Depends on:** `FileLoadingService`, `FileSplitterService`, `TagSetter`, `SetupKnowledgeExtractor`, `MutationGeneratorService`, `HaskellDerivationFetcher`, `DerivationTreeCaptureService`, `ZipService`.
- **⚠️ Heads-up:** Header names form the public contract — renaming one breaks every client.

</details>

<details>
<summary>🧠 <strong>ForgetMutationController.java</strong> — Forget with derivation knobs</summary>

<br/>

- **🎯 Job:** Dedicated *Forget* endpoint with extra header knobs.
- **🔧 Key logic:**
  1. Validates optional headers (`Max-Variants-Per-Rule`, `Blocking-Mode`, `Witness-Actions`).
  2. Parses Forget facts via `ForgetMutationParser`.
  3. Optionally activates the Haskell engine.
  4. Returns a zip; adds `X-Variants-Truncated` when the cap was hit.
- **📥 Inputs:** `.spthy` + Forget-specific headers.
- **📤 Outputs:** `.zip` of Forget variants; optional derivation tree.
- **🤝 Used by:** Native UI's Forget mode and any API client.
- **🪝 Depends on:** `ForgetMutationParser`, `ForgetMutationStrategy`, plus the same plumbing as `MutationController`.
- **⚠️ Heads-up:** Header validation is part of the API contract — keep error responses descriptive.

</details>

<details>
<summary>➕ <strong>AddMutationController.java</strong> — Add mutation</summary>

<br/>

- **🎯 Job:** Generate *Add* mutation variants.
- **🔧 Key logic:** Split → load → `MutationGeneratorService` with `Mutations.ADD` → zip.
- **📥 Inputs:** `.spthy` upload.
- **📤 Outputs:** `.zip` of Add variants.
- **🪝 Depends on:** `FileLoadingService`, `FileSplitterService`, `MutationGeneratorService`, `ZipService`.

</details>

<details>
<summary>❌ <strong>NeglectMutationController.java</strong> — Neglect mutation</summary>

<br/>

- **🎯 Job:** Generate *Neglect* mutation variants.
- **🔧 Key logic:** Split → load → `MutationGeneratorService` with `Mutations.NEGLECT` → zip.
- **📥 Inputs:** `.spthy` upload.
- **📤 Outputs:** `.zip` of Neglect variants.

</details>

<details>
<summary>🔄 <strong>ReplaceMutationController.java</strong> — Replace family</summary>

<br/>

- **🎯 Job:** Expose Replace Sub-Messages and Replace Type as separate endpoints.
- **🔧 Key logic:** Two endpoints, each routing to its specific `Mutations.REPLACE_*` strategy.
- **⚠️ Heads-up:** Replacement semantics live in `ReplaceMutationService` — coordinate changes there.

</details>

<details>
<summary>⏭ <strong>SkipMutationController.java</strong> — Skip family</summary>

<br/>

- **🎯 Job:** Five endpoints covering Skip Send, Skip Receive, and three send/receive chains.
- **🔧 Key logic:** Each endpoint targets a specific `Mutations.SKIP_*` strategy.
- **⚠️ Heads-up:** Endpoint paths are mirrored in tests — keep them in sync.

</details>

<details>
<summary>🧪 <strong>HaskellDerivationController.java</strong> — direct derivation</summary>

<br/>

- **🎯 Job:** Talk to the external Haskell derivation service directly (bypassing mutations).
- **🔧 Key logic:** Validates the upload; calls `HaskellDerivationFetcher`; returns `text/plain`. Also exposes a health check.
- **📥 Inputs:** `.spthy` upload for `/api/derive`.
- **📤 Outputs:** Plain-text derivation tree; health status.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `../service` (delegation target) · `../utilities` (parsing & tag helpers) · `application.yaml` (server + derivation URL). | `../user_interface` (HTTP calls from JavaFX) · external clients · the Postman collection. |

## ⚙️ At runtime

A `POST` with a `.spthy` payload reaches a controller method. The controller splits the file, picks a strategy based on the endpoint or headers, calls the service layer, and streams back a `.zip` of mutated `.m` files. Forget endpoints can additionally include a `_DerivationTree.txt` in the archive.

## 🌐 API surface

| Endpoint | Method | What it does |
| --- | --- | --- |
| `/api/generateMutations` | `POST` | Combined mutation engine (header-driven). |
| `/api/addMutations` | `POST` | Add mutation. |
| `/api/forget/mutations` | `POST` | Forget mutation (+ optional Haskell). |
| `/api/neglect/mutations` | `POST` | Neglect mutation. |
| `/api/replace/subMessagesMutations` | `POST` | Replace sub-messages. |
| `/api/replace/typeMutations` | `POST` | Replace type. |
| `/api/skip/sendMutations` | `POST` | Skip Send. |
| `/api/skip/receiveMutations` | `POST` | Skip Receive. |
| `/api/skip/sendReceiveMutations` | `POST` | Skip Send→Receive. |
| `/api/skip/receiveSendMutations` | `POST` | Skip Receive→Send. |
| `/api/skip/receiveSendReceiveMutations` | `POST` | Skip Receive→Send→Receive. |
| `/api/derive` | `POST` | Direct Haskell derivation. |
| `/api/derive/health` | `GET` | Haskell service health probe. |

## 📨 Headers controllers care about

| Header | Effect |
| --- | --- |
| `Skip-Send` / `Skip-Receive` / `Skip-Send-Receive` / `Skip-Receive-Send` / `Skip-Receive-Send-Receive` | Enable the matching Skip variant on the combined endpoint. |
| `Add-Mutation` | Enable Add. |
| `Replace-Sub-Messages` / `Replace-Type` | Enable Replace family. |
| `True-Replace` | Use knowledge-based replacements (not random). |
| `Forget-Mutation` / `Neglect-Mutation` | Enable Forget / Neglect. |
| `Haskell-Activate` | Route Forget derivation to the Haskell service. |
| `Derivation-Type` | `LIMITED`, `DEPTH_SPECIFIED`, or `INFINITE`. |
| `Derivation-Depth` | Numeric depth when `DEPTH_SPECIFIED`. |
| `Max-Variants-Per-Rule` | Cap on Forget variants per rule. |
| `Blocking-Mode` | Forget blocking case (CASE1 / CASE2 / CASE3). |
| `Witness-Actions` | Names to treat as witness facts for Forget processing. |

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding new endpoints that wrap existing services. Expanding OpenAPI annotations. Adding header validations. | Endpoint paths and header names (client compatibility). Zip filename conventions. Haskell-activation logic. |

## 🧯 Common error responses

- Empty / missing file upload → `400`.
- Invalid `.spthy` extension on `/api/derive` → `400`.
- Invalid Forget headers (`Blocking-Mode`, `Max-Variants-Per-Rule`) → `400`.
- Haskell service unreachable when activated → `503`.

---

> 💡 **Summary** — The HTTP front door. Endpoints, headers, and zip responses live here; the heavy lifting is one layer down in `service/`.

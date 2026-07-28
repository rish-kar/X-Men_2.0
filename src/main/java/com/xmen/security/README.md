<div align="center">

# 🛡️ `security`

**HTTP origin gate — the first filter every request meets**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Enforces origin-based restrictions on inbound HTTP requests while still letting loopback / native clients through. Complements the CORS config in `../config`.

## 🗺️ Where this sits

```text
Incoming HTTP request
        ↓
🟢 OriginRestrictionFilter   ←  you are here
        ↓
Spring Security chain → CORS → Controller → Service → ...
```

## 📁 Files at a glance

| File | Role |
| --- | --- |
| 🚪 `OriginRestrictionFilter.java` | `OncePerRequestFilter` that permits static / health endpoints, allows loopback clients, and rejects disallowed `Origin` headers with `403`. |

## 🔬 Deep dive

<details>
<summary>🚪 <strong>OriginRestrictionFilter.java</strong></summary>

<br/>

- **🎯 Job:** Allow only loopback clients or browser requests whose `Origin` is in the allowlist.
- **🔧 Key logic:**
  1. Lets static-resource paths and health endpoints through unconditionally.
  2. Allows loopback addresses even without an `Origin` header.
  3. Blocks every other request with HTTP `403` if `Origin` is missing or not in the allowlist.
- **📥 Inputs:** Allowed-origins CSV from configuration, plus the request's `Origin` header and remote address.
- **📤 Outputs:** Either continues the filter chain or returns a `403` response.
- **🤝 Used by:** `../config/SecurityConfig` registers the filter.
- **🪝 Depends on:** `HttpHeaders.ORIGIN`, the origin list pulled from `application.yaml`.
- **⚠️ Heads-up:** Tweaking allowed paths or origin matching directly affects API accessibility and native UI behavior.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `../config/SecurityConfig` (wiring). | `../controller` indirectly — every request is filtered before reaching a controller. |

## ⚙️ At runtime

Each HTTP request is checked once. Static-resource and health URLs pass through untouched. Loopback clients are allowed even without an `Origin` (this is what the native UI relies on). Browser requests must present an `Origin` header that matches the configured allowlist; otherwise they get `403` and never reach Spring Security.

## 📨 Header it looks at

| Header | Role |
| --- | --- |
| `Origin` | Source of truth for allowlist matching on browser requests. |

## 🧯 Edge cases

- **Missing `Origin`** on a non-loopback request → treated as disallowed.
- **Preflight `OPTIONS`** with a disallowed origin → blocked before CORS handling.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| The list of always-allowed paths (e.g. add a new static prefix). Logging. | Loopback detection (used by the native UI) and preflight handling. |

---

> 💡 **Summary** — One small filter that quietly enforces "where can you talk to us from" on every request.

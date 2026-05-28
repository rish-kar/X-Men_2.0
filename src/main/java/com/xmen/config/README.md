<div align="center">

# ⚙️ `config`

**Spring configuration — wiring, security, CORS, and OpenAPI metadata**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Bean definitions, CORS rules, security filter chain, and Swagger/OpenAPI metadata. Shapes how the API layer behaves and how clients talk to it.

## 🗺️ Where this sits

```text
            ┌── AppConfig         (beans + CORS)
config  ────┼── SecurityConfig    (filter chain + origin policy)
            └── OpenApiConfig     (Swagger tags + metadata)
                        ↓
            Controller layer + global Spring context
```

## 📁 Files at a glance

| File | Role |
| --- | --- |
| 🧩 `AppConfig.java` | Registers utility beans (`FileHandler`, `UtilityFunctions`, `RulesModifier`, `Random`), wires `SkipSendMutationStrategy`, and declares CORS for `/api/**`. |
| 🛡️ `SecurityConfig.java` | Configures the `SecurityFilterChain`, permits static and Swagger endpoints, and inserts `OriginRestrictionFilter`. |
| 🧭 `OpenApiConfig.java` | OpenAPI metadata + tag groups for Swagger UI. |

## 🔬 Deep dive

<details>
<summary>🧩 <strong>AppConfig.java</strong> — application-level beans + CORS</summary>

<br/>

- **🎯 Job:** Register utility beans and declare CORS for the `/api/**` surface.
- **🔧 Key logic:**
  1. Beans: `FileHandler`, `UtilityFunctions`, `RulesModifier`, `Random`.
  2. Wires a `SkipSendMutationStrategy` bean with its dependencies.
  3. Declares CORS for `/api/**` with the allowlisted origins and headers.
- **📥 Inputs:** `app.cors.allowed-origins` from `application.yaml`.
- **📤 Outputs:** Spring-managed beans + CORS configuration.
- **🤝 Used by:** Services and utilities across the mutation pipeline.
- **🪝 Depends on:** `UtilityFunctions`, `RulesModifier`, `SkipSendMutationStrategy`.
- **⚠️ Heads-up:** Changing the allowed headers can break the UI and external clients.

</details>

<details>
<summary>🛡️ <strong>SecurityConfig.java</strong> — HTTP security & CORS source</summary>

<br/>

- **🎯 Job:** Configure HTTP security and the global CORS source.
- **🔧 Key logic:**
  1. Disables CSRF and sets stateless session creation.
  2. Permits static resources, Swagger UI, and `/actuator/health/**`.
  3. Inserts `OriginRestrictionFilter` ahead of authentication.
  4. Declares allowed origins, HTTP methods, and custom headers.
- **📥 Inputs:** `app.cors.allowed-origins`.
- **📤 Outputs:** `SecurityFilterChain` bean and the `CorsConfigurationSource`.
- **🤝 Used by:** Every inbound HTTP request handled by Spring Security.
- **🪝 Depends on:** `OriginRestrictionFilter` from `../security`.
- **⚠️ Heads-up:** Mis-declared allowed headers silently break mutation requests.

</details>

<details>
<summary>🧭 <strong>OpenApiConfig.java</strong> — Swagger UI metadata</summary>

<br/>

- **🎯 Job:** Declare the OpenAPI document — title, version, description, tag groups.
- **🔧 Key logic:** `@OpenAPIDefinition` with `@Info` and a `@Tag` array (Mutations, Add, Forget, Neglect, Replace, Skip, Derivation).
- **📥 Inputs:** Static metadata.
- **📤 Outputs:** The document served at `/v3/api-docs` and rendered at `/swagger-ui/index.html`.
- **🤝 Used by:** Swagger UI and any tool consuming the OpenAPI spec.
- **🪝 Depends on:** `springdoc-openapi`.
- **⚠️ Heads-up:** Add new tags here whenever you add a controller group.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `../security` (`OriginRestrictionFilter`) · `../utilities` (`FileHandler`, `UtilityFunctions`, `RulesModifier`) · `application.yaml`. | `../controller` (CORS + security affect endpoints) · `../service` (bean wiring used by strategies). |

## ⚙️ At runtime

Spring reads these classes at startup. `AppConfig` registers utility beans and CORS rules; `SecurityConfig` builds the filter chain; `OpenApiConfig` populates Swagger UI. Every API request afterwards passes through the configured filters before reaching the controller layer.

## 📨 CORS-allowed headers

The following mutation headers must remain in the CORS allowlist:

| Group | Headers |
| --- | --- |
| Skip family | `Skip-Send`, `Skip-Receive`, `Skip-Send-Receive`, `Skip-Receive-Send`, `Skip-Receive-Send-Receive` |
| Add / Replace | `Add-Mutation`, `Replace-Sub-Messages`, `Replace-Type`, `True-Replace` |
| Forget / Neglect | `Forget-Mutation`, `Neglect-Mutation` |
| Derivation | `Haskell-Activate`, `Derivation-Type`, `Derivation-Depth` |

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| OpenAPI metadata in `OpenApiConfig`; adding new allowed headers as the API expands. | The security filter-chain order; production CORS allowlist values. |

---

> 💡 **Summary** — Three small classes that wire beans, lock down CORS/security, and feed Swagger. Touch the security chain only with care.

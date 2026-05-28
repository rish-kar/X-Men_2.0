<div align="center">

# 🚀 `com.xmen`

**Application entry point and root of the X-Men package tree**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Bootstraps Spring Boot, conditionally launches the native JavaFX UI, and anchors the mutation-analysis workflow.

## 🗺️ Where this sits

```text
User / Postman / Native UI
        ↓
🟢  Application.java   (entry point — you are here)
        ↓
controller  →  service  →  model + utilities  →  mutated .m files  →  zip
```

## 📁 Files at a glance

| File | Role |
| --- | --- |
| 🟢 `Application.java` | Spring Boot `@SpringBootApplication` entry point. Starts the context and conditionally launches `XMenInterface` (JavaFX) when not headless. |

## 🔬 Deep dive

<details>
<summary>📄 <strong>Application.java</strong> — Spring Boot entry point</summary>

<br/>

- **🎯 Job:** Bootstraps the application; conditionally launches the JavaFX UI.
- **🔧 Key logic:**
  1. `SpringApplication.run(...)` starts the Spring context.
  2. Implements `CommandLineRunner` so it can run logic after startup.
  3. Detects `java.awt.headless`; if a GUI is available, launches `XMenInterface`.
- **📥 Inputs:** JVM properties (e.g. `java.awt.headless`).
- **📤 Outputs:** Running HTTP API on port `8081`; optional JavaFX window.
- **🤝 Used by:** The OS / `java -jar app.jar` command.
- **🪝 Depends on:** `com.xmen.user_interface.XMenInterface`.
- **⚠️ Heads-up:** Changing startup affects both API and UI modes. Keep the `@SpringBootApplication` package scan aligned with `com.xmen`.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| `user_interface/` (launches the JavaFX scene). | The Spring Boot runtime — this is the JVM entry class. |

## ⚙️ At runtime

On startup Spring initializes the context and scans all sub-packages. If headless mode is off, `XMenInterface` opens as the main interaction surface; if it is on, the application runs purely as an HTTP API.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Logging statements and UI-launch conditional logic. | The `@SpringBootApplication` annotation and the base-package scan. JavaFX launch on systems without graphics. |

---

> 💡 **Summary** — One file, one job: start Spring, optionally start the UI. Most action happens in sub-packages.

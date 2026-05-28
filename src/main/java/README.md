<div align="center">

# 📁 `src/main/java`

**Main Java source root for the X-Men Spring Boot application**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; The root of every production class shipped by X-Men. All concrete code lives one level down, under `com/xmen`.

## 🗺️ Where this sits

```text
src/main/java   ←  you are here
   └── com/xmen          (application root package)
         ├── Application.java    (Spring Boot entry point)
         ├── controller/         (REST controllers)
         ├── service/            (mutation + derivation services)
         ├── model/              (domain model & parsed AST)
         ├── utilities/          (parsers, file I/O, helpers)
         ├── user_interface/     (JavaFX native UI)
         ├── config/             (Spring config + Swagger)
         └── security/           (HTTP origin filter)
```

## 📁 Contents

| Entry | Type | What it is |
| --- | --- | --- |
| `com/` | package tree | The full `com.xmen` namespace — every class lives below this. |

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| The classes in `com/xmen`. | The Java compiler and the Spring Boot runtime. |

## ⚙️ At runtime

Every class compiled from this tree is loaded by the JVM at startup. Spring's component scan starts from `com.xmen` and discovers controllers, services, and configurations automatically.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding new packages and classes under `com/xmen`. | The base package name — moving it requires updating `@SpringBootApplication`'s scan settings. |

---
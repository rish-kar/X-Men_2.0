<div align="center">

# 🧪 `src/test/java`

**Test source root**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Hosts every unit, integration, and UI test for X-Men. All test classes live under `com/xmen`.

## 🗺️ Where this sits

```text
src/test/java   ←  you are here
   └── com/xmen/
         ├── ApplicationTests           (Spring context smoke test)
         ├── integrationTests/          (end-to-end mutation tests)
         ├── service/                   (service unit tests)
         │     └── forget/              (Forget mutation tests)
         ├── userInterfaceTests/        (JavaFX UI tests)
         └── utilities/                 (parser + helper tests)
```

## 📁 Contents

| Entry | Type | What it is |
| --- | --- | --- |
| `com/` | package tree | The full `com.xmen` test namespace. |

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| The production code under `src/main/java`. | Maven Surefire and IDE test runners. |

## ⚙️ At runtime

`mvn test` (or your IDE's test runner) loads every class in this tree, initialises a Spring context where required, and reports results.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding new test classes under the right sub-package. | Test package names — they must mirror the production packages for component scanning. |

---

> 💡 **Summary** — Empty container; tests live one level deeper.

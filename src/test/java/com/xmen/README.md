<div align="center">

# 🧪 `com.xmen` (tests)

**Top-level test classes and test sub-packages**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; A small smoke test plus the four sub-packages that organise the rest of the suite (integration, service, UI, utilities).

## 🗺️ Where this sits

```text
src/test/java/com/xmen    ←  you are here
   ├── ApplicationTests.java       (Spring smoke test)
   ├── integrationTests/           (end-to-end mutation tests)
   ├── service/ + service/forget/  (service unit tests)
   ├── userInterfaceTests/         (JavaFX tests)
   └── utilities/                  (parser + helper tests)
```

## 📁 Files & sub-packages

| Entry | Role |
| --- | --- |
| 🚦 `ApplicationTests.java` | Spring Boot smoke test — verifies the application context loads. |
| 🔌 `integrationTests/` | End-to-end mutation tests (one per mutation kind). |
| 🧠 `service/` | Service-layer unit tests; `service/forget/` covers the Forget pipeline. |
| 🖥️ `userInterfaceTests/` | JavaFX UI tests. |
| 🧰 `utilities/` | Parser and utility tests. |

## 🔬 Deep dive

<details>
<summary>🚦 <strong>ApplicationTests.java</strong></summary>

<br/>

- **🎯 Job:** Verify that the Spring Boot context loads — the cheapest test that catches the widest set of regressions.
- **🤝 Used by:** Surefire and IDE runners.
- **⚠️ Heads-up:** Keep this test minimal; richer coverage belongs in the sub-packages.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| Production code under `src/main/java/com/xmen`. | Maven Surefire and IDE test runners. |

## ⚙️ At runtime

`mvn test` boots Spring (where needed) and runs every test class under this package and its children.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Adding tests; sharpening assertions for new mutation behavior. | Tests that depend on file fixtures — keep paths and filenames stable. |

---

> 💡 **Summary** — One smoke test plus four sub-packages. Everything else hangs off this point.

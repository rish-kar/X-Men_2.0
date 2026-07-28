<div align="center">

# 🎨 `resources/css`

**JavaFX stylesheets — the look and feel of the native UI**

</div>

---

> 🎯 &nbsp;**At a glance** &nbsp;·&nbsp; Two CSS files that style the JavaFX scene and its alert dialogs.

## 📁 Files at a glance

| File | Role |
| --- | --- |
| 🖼️ `main.css` | Main UI stylesheet for the JavaFX scene. |
| 💬 `alert.css` | Styles for alert dialogs (success, error, warning, Forget-not-found). |

## 🔬 Deep dive

<details>
<summary>🖼️ <strong>main.css</strong></summary>

<br/>

- **🎯 Job:** Define the JavaFX scene's look — colors, fonts, borders, layout polish.
- **🤝 Used by:** `XMenInterface` when constructing the main scene.
- **⚠️ Heads-up:** Selectors here must match the IDs/style classes assigned in `XMenInterface`.

</details>

<details>
<summary>💬 <strong>alert.css</strong></summary>

<br/>

- **🎯 Job:** Style the alert dialog pane and its icon area.
- **🤝 Used by:** `XMenInterface` when building success/error/warning alerts.
- **⚠️ Heads-up:** Keep consistent with the IDs `XMenInterface` sets on alert dialogs.

</details>

## 🔗 Connections

| ⬇️ Depends on | ⬆️ Used by |
| --- | --- |
| JavaFX CSS support. | `../../java/com/xmen/user_interface/XMenInterface.java`. |

## ⚙️ At runtime

When the UI loads, `main.css` is applied to the scene's root pane and `alert.css` is applied to each alert's dialog pane.

## 🚦 Modification guide

| ✅ Safe to touch | ⚠️ Handle with care |
| --- | --- |
| Colors, fonts, spacing, gradients. | The selector names — they're contracts with `XMenInterface`. |

---
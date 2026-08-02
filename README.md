<div align="center">

<img src="src/main/resources/images/Front-End-Logo.png" alt="X-Men logo" width="220"/>

# X-Men 2.0

### A Mutation-based Approach for the Formal Analysis of Security Ceremonies

<p>
  <img alt="Java 21"        src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white"/>
  <img alt="Spring Boot"    src="https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=spring&logoColor=white"/>
  <img alt="Maven"          src="https://img.shields.io/badge/Maven-3.9-C71A36?logo=apachemaven&logoColor=white"/>
  <img alt="Tamarin"        src="https://img.shields.io/badge/Tamarin-spthy-8E44AD"/>
  <img alt="Docker"         src="https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white"/>
  <img alt="Swagger"        src="https://img.shields.io/badge/OpenAPI-Swagger%20UI-85EA2D?logo=swagger&logoColor=black"/>
</p>

<sub><strong>Keywords:</strong> Security ceremonies, Mutations, Formal modeling and analysis</sub>

</div>

---

## ✨ Table of Contents

- [About The Project](#-about-the-project)

> ### 🚀 [**Section 1 — Quick Start (just use the app)**](#-section-1--quick-start-just-use-the-app)
> *The easiest, fastest path to test the functionality. Download an installer, click through the prompts, upload a `.spthy`, hit Start Mutation. No JDK, no Git, no command line.*

- [What X-Men 2.0 actually does (no jargon)](#-what-x-men-20-actually-does-no-jargon)
  - [The 30-second mental model](#the-30-second-mental-model)
  - [The mutation menu — what each checkbox actually does](#the-mutation-menu--what-each-checkbox-actually-does)
  - [The end-to-end flow — step by step](#the-end-to-end-flow--step-by-step)
  - [Forget mutation — what the "derivation tree" is](#forget-mutation--what-the-derivation-tree-is-and-why-youll-see-it)
  - ["Infinite Derivations" vs "Specified Depth"](#infinite-derivations-vs-specified-depth--which-one-to-pick)
  - [After the run — where do my files go?](#after-the-run--where-do-my-files-go)
- [Downloads & Installation](#-downloads--installation)
  - [Windows install](#-windows-install)
  - [macOS install (Intel & Apple Silicon)](#-macos-install-intel--apple-silicon)
  - [Linux install (Debian / Ubuntu)](#-linux-install-debian--ubuntu)
- [Running your first mutation](#-running-your-first-mutation)

> ### 🛠️ [**Section 2 — Developer Setup & Internals**](#-section-2--developer-setup--internals)
> *The path to understand the internal structure of the system. Clone the repo, build from source, learn the architecture, drive the API directly, extend the codebase.*

- [Built With](#-built-with)
- [Architecture at a Glance](#-architecture-at-a-glance)
- [Local Setup](#-local-setup)
  - [Installing Java and Maven](#installing-java-and-maven)
  - [Setting Up Path Variables](#setting-up-path-variables)
  - [Installing Git](#installing-git)
  - [Cloning the Project](#cloning-the-project)
  - [Installing IntelliJ IDEA](#installing-intellij-idea)
  - [Building and Running the Project](#building-and-running-the-project)
  - [Setting Up Postman](#setting-up-postman)
- [API Reference](#-api-reference)
- [Docker](#-docker)
- [Settings, Vocabulary & Themes](#-settings-vocabulary--themes)
- [Configuration](#-configuration)
- [Haskell Derivation Service](#-haskell-derivation-service)
- [Project Structure](#-project-structure)
- [Troubleshooting](#-troubleshooting)
- [Test Coverage with JaCoCo](#-test-coverage-with-jacoco)

---

## 📖 About The Project

> *Security ceremonies extend security protocols by explicitly including
> human users. They may thus suffer from vulnerabilities that stem from
> unexpected or incorrect human behavior during interactions with other
> ceremony participants (or agents), such as the system, or with attackers,
> who may be internal or external to the ceremony.*

**X-Men 2.0** automates the analysis of such ceremonies by **mutating** them
according to a formal catalog of human-induced deviations and feeding the
mutated models to [Tamarin](https://tamarin-prover.github.io/) for
verification. The current toolchain implements the following mutations:

| Mutation | Captured behavior |
| --- | --- |
| **SKIP** | A user skips an external action |
| **ADD** | A user adds an action unforeseen by the ceremony |
| **REPLACE** | A user replaces the message to be sent with another message |
| **NEGLECT** | A user neglects an internal action |
| **FORGET** | A user forgets to add newly acquired information to their knowledge or forgets some information they previously possessed |

The **Forget** mutation — the most recent addition — is the focus of the
companion research paper and is the engine behind X-Men 2.0's example use case,
the **Bank Login ceremony**. It models the eminently human behavior of
forgetting a credential and substituting a similar one from memory.

<details>
<summary><strong>Why does this matter?</strong></summary>

<br/>

Typically, protocol analysis assumes that the human users act according to the
ceremony specification. However, in reality, users do not. X-Men 2.0 models this by
generating executable mutated ceremony models that Tamarin can analyze
end-to-end, capturing attacks that depend on human mistakes rather than
cryptographic weaknesses.

</details>

---

> # 🚀 Section 1 — Quick Start (just use the app)
>
> **This is the easiest path to test X-Men 2.0's functionality.** Pick the
> installer that matches your operating system (OS), run through the prompts, double-click
> the app, upload a `.spthy` file, and click **Start Mutation**.
>
> You **do not** need a JDK, Maven, Git, IntelliJ, or any command-line tool to
> use this section. If all you want is to see the tool generate mutated
> ceremony files for a `.spthy` you already have, you can stop reading once
> this section ends.

## 🧠 What X-Men 2.0 actually does (no jargon)

Before you install the tool, it would be helpful to know what you're about to use. This whole section targets researchers involved in formal modeling and analysis of security protocols.

### The 30-second mental model

You hand X-Men 2.0 **one** ceremony file (`.spthy`). X-Men 2.0 hands you back
**many** mutated variants of that ceremony (`.m` files inside a `.zip`).
Each variant is the same ceremony **with one realistic human mistake baked
into it**. You then feed those variants into the [Tamarin
Prover](https://tamarin-prover.github.io/) (or any tool that consumes
`.m` files) to see which mistakes actually break your security goals.

```
                          ┌────────────────────────────┐
   your ceremony.spthy ─► │       X-Men (the app)      │ ─► ceremony_M0.m   "user skipped step 2"
                          │  • parses your ceremony    │    ceremony_M1.m   "user replaced password"
   pick mistakes via UI ─►│  • applies chosen mistakes │    ceremony_M2.m   "user forgot the key"
                          │  • emits one file per case │    …
                          └────────────────────────────┘    bundled into ceremony_mutations.zip
```

> 💡 **Why does this matter?** Standard security analysis assumes the human
> follows the script perfectly. Real people don't. X-Men 2.0 generates the
> "what if the human messed up like *this*?" versions automatically, so you
> can catch attacks that only show up when a human makes a mistake.

### The mutation menu — what each checkbox actually does

When you launch the app you see a panel of checkboxes. Here's what each one
means in everyday language, with a concrete *"what mistake does it model?"*
example.

| Checkbox | Plain-English meaning                                                                                                                                                                             | Mini example |
| --- |---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| --- |
| **Skip → Send** | The user **doesn't send** a message they were supposed to send.                                                                                                                                   | Forgetting to tap your Oyster card *in* at the gate. |
| **Skip → Receive** | The user **doesn't read / receive** a message they were supposed to receive.                                                                                                                      | Ignoring the verification email that the bank just sent. |
| **Skip → Send Receive** | The user skips a send *and* the reply that would have come from it.                                                                                                                               | Never tapping in *and* never waiting for the gate's beep. |
| **Skip → Receive Send** | The user misses an incoming message *and* therefore never replies.                                                                                                                                | Missing the 2FA prompt, so never typing the code back. |
| **Skip → Receive Send Receive** | The user misses a whole round-trip — incoming, outgoing, incoming.                                                                                                                                | Missing the password reset email, never clicking it, never receiving the confirmation. |
| **Add Mutation** | The user **does something extra** that the ceremony didn't ask for.                                                                                                                               | Tapping a second card at the gate when one card was enough. |
| **Replace → Sub Messages** | The user sends a message of the **right type** but with a **wrong piece** inside.                                                                                                                | Pasting the wrong meeting ID into an otherwise-correct reply. |
| **Replace → Type** | The user swaps one value for **another value of the same kind**.                                                                                                                                  | Using yesterday's QR code instead of today's. |
| **Combination → Combination in Addition** | Two mistakes at once: the user **adds** an extra send **and** uses a **replaced** value in it.                                                                                                    | Tapping a second card *and* using someone else's card to do it. |
| **Combination → Combination Only** | Same as above but X-Men 2.0 *only* generates the combined case (no plain Add results).                                                                                                                | Same scenario, narrower output. |
| **Forget Mutation** | The user **had** the right value at some point but **can't recall it now**.                                                                                                                       | Forgetting your bank password and typing the one for a different bank. |
| **Forget Mutation using external Haskell Script** | Same Forget behavior, same result but X-Men 2.0 hands the heavy "what can the user still figure out?" math to an optional external Haskell helper. | (Same as above.) |
| **Neglect Mutation** | The user **doesn't check** something they should have.                                                                                                                                            | Accepting an e-ticket without comparing the date on it to today's date. |

> 🛈 You can tick **as many checkboxes as you want at once** — X-Men 2.0 generates
> a separate `.m` file for every requested mutation, so you get a full bundle
> in a single run.

### The end-to-end flow — step by step

Here is exactly what happens between you clicking **Start Mutation** and a
`.zip` landing on your desktop:

1. **You tick** the mutation checkboxes you care about.
2. **You upload** your `.spthy` ceremony with the **Upload File** button.
3. **You click Start Mutation.**
4. X-Men 2.0 **reads your ceremony** and figures out different roles (including the human), 
their interactions, and initial knowledge to begin with.
5. For **each selected mutation**, X-Men 2.0 creates one (or several) variants of
   the ceremony with that mutation, and writes each variant
   to its file: `MyCeremony_M0.m`, `MyCeremony_M1.m`, `MyCeremony_M2.m`, …
6. The files are saved to **`~/.xmen/runs/`** on your computer (this folder
   is the same on Windows, macOS, and Linux).
7. A green success card appears, and a **Download** button shows up next to
   Start Mutation.
8. **Click Download** to save a single `.zip` of every generated `.m` file
   wherever you'd like.
9. (Outside X-Men 2.0) You feed those `.m` files to Tamarin to discover *which*
   variants violate your security goals. Each variant corresponds to an attack due to human 
   mutations you didn't know your ceremony was vulnerable to.

### Forget mutation — what the "derivation tree" is and why you'll see it

**Forget** is the deepest mutation X-Men 2.0 ships and it's the one that uses a
derivation tree, so it gets its own short explainer.

> 🧩 **The question Forget is trying to answer:**  
> *"The human forgot a piece of information. Given what they still remember,
> can they still produce the message the ceremony expects? And if not, what
> would they send instead?"*

That second question is where the **derivation tree** comes in. A
derivation tree is a step-by-step representation of how a message can be
**built from parts/components the human still knows**, using ordinary operations
(pairing two values, encrypting with a key, decrypting with a key, signing,
hashing). Each leaf of the tree is a piece of remembered knowledge; each
branch is one of those operations.

#### A concrete tiny example

Suppose the human is asked to send the message  
`(my_account, sign(my_account, my_private_key))`  
*("here's my account number, signed with my private key")*. They remember:

- `my_account`
- `my_private_key`
- `the bank's public key`

The derivation tree X-Men 2.0 builds looks like this:

```
      (my_account, sign(my_account, my_private_key))     ← the message to send
                       │
                ┌──────┴──────────────────────┐
                │                             │
            my_account              sign(my_account, my_private_key)
                                              │
                                  ┌───────────┴──────────┐
                                  │                      │
                              my_account           my_private_key
                                                   (still remembered ✔)
```

Every leaf is in *remembered* knowledge → the message can be built →
**the send goes through unchanged**. Now imagine the human **forgot
`my_private_key`**. The right branch can't be completed. X-Men 2.0 marks the
tree as **blocked** and tries to replace the forgotten leaf for *another value
the human still remembers of the same type* — that's where you sometimes
get the *"sent the wrong password"* style attack. If no compatible replacement
exists, the send is skipped instead.

#### What the "Show derivation tree on screen" checkbox does

If you tick it, the app pops up the derivation tree it actually used for
each mutated variant after Start Mutation finishes. Handy when you want to
*see* why a particular substitution was chosen instead of guessing.

### "Infinite Derivations" vs "Specified Depth" — which one to pick

When you tick **Forget Mutation**, the app enables two radio choices. 
The radio choices — **Infinite** and **Specified Depth** — control **how
hard X-Men 2.0 tries** when building the derivation trees.

The reason this knob exists: every operation (pair, encrypt, …) can be
nested inside another operation, so derivation trees can grow forever in
principle. X-Men 2.0 needs to know how big you'll let them get.

| Setting | What it does | Best for | Trade-off |
| --- | --- | --- | --- |
| **Infinite Derivations** *(default)* | Let X-Men 2.0 keep building trees until it has explored **every** way the human could still construct each message. | Small to medium ceremonies where you want to catch every possible substitution attack. | Slower and produces more `.m` files. On big ceremonies, generation can take minutes. |
| **Specified Depth** | You type a number `N` in the box that appears. X-Men 2.0 only considers derivations that nest operations at most `N` deep. | Big ceremonies where Infinite is too slow, or when you only care about "obvious" mistakes that don't require fancy multi-step constructions. | May miss substitutions that need deeper nesting. Faster and smaller output. |

#### How to choose, in one paragraph

Start with **Infinite Derivations**. If
generation takes too long, or the resulting `.zip` is unmanageably big,
switch to **Specified Depth** and start with **3**. Bump it up to 5 if you
suspect a deeper attack; drop it to 1 if you only want the shallowest
substitutions for a quick sanity check.

> 🧪 **Rule of thumb:** depth 1–2 catches the *"forgot a single value and
> swapped in another"* class of mistakes. Depth 3–5 catches mistakes that
> need the user to *combine* a few remembered parts/components. Infinite catches
> everything the formal model can express, at the cost of time.

### After the run — where do my files go?

| Location | What's in it |
| --- | --- |
| `~/.xmen/runs/` | Every `.m` mutation file X-Men 2.0 generates on your computer. Overwritten on the next run, so save anything you want to keep. |
| The **Download** button | One `.zip` bundling every `.m` from the latest run, ready to save anywhere you want. |
| `~/.xmen/settings.json` | Your saved theme, vocabulary profile, and UI preferences. Persists across launches. |

> 📍 `~` means your home folder: `C:\Users\<you>` on Windows,
> `/Users/<you>` on macOS, `/home/<you>` on Linux.

That's the whole user-facing model. The rest of Section 1 just tells you
how to install the app on your platform — once it's running, this is what
you'll be doing inside it.

---

## 📦 Downloads & Installation

Installers are attached to the latest release section. Pick the one that
matches your machine, then follow the per-OS notes below:

| Platform | Installer | Notes |
| --- | --- | --- |
| Windows 10/11 | [**Download X-Men.exe**](https://github.com/rish-kar/X-Men_2.0/releases/latest/download/X-Men.exe) | Per-user install; no admin rights needed. |
| macOS (Intel) | [**Download X-Men-x64.dmg**](https://github.com/rish-kar/X-Men_2.0/releases/latest/download/X-Men-x64.dmg) | x86_64 build; runs natively on Intel Macs. |
| macOS (Apple Silicon) | [**Download X-Men-arm64.dmg**](https://github.com/rish-kar/X-Men_2.0/releases/latest/download/X-Men-arm64.dmg) | arm64 build; runs natively on M1/M2/M3/M4 Macs. |
| Linux (Debian / Ubuntu) | [**Download X-Men.deb**](https://github.com/rish-kar/X-Men_2.0/releases/latest/download/X-Men.deb) | x86_64 `.deb`; tested on Ubuntu 22.04–26.04. |

> 🗂️ **Where mutation output goes.** Generated `.m` files are written under
> `~/.xmen/runs/` regardless of OS, so the app never needs to write inside its
> install directory. Persisted settings (themes, vocabulary profiles, UI
> preferences) live in `~/.xmen/settings.json`.

### 🪟 Windows install

1. Download **X-Men.exe** from the Releases page.
2. Double-click the installer. Choose an install location when prompted
   (the default `%LOCALAPPDATA%\X-Men` works without elevation).
3. Tick **Add to Start Menu** and **Create desktop shortcut** if you'd like
   them; the installer creates per-user shortcuts only.
4. Launch **X-Men** from the Start Menu or the desktop shortcut.

Windows SmartScreen may show *"Windows protected your PC"* the first time
because the .exe is not yet code-signed. Click **More info → Run anyway**.

### 🍎 macOS install (Intel & Apple Silicon)

1. Download the matching `.dmg` for your Mac:
   - **Intel Mac** → `X-Men-x64.dmg`
   - **Apple Silicon Mac** (M1/M2/M3/M4) → `X-Men-arm64.dmg`

   Not sure which one you have? Click  → **About This Mac** → look at the
   **Chip** line. *"Apple M…"* means Apple Silicon; *"Intel Core…"* means Intel.
2. Double-click the `.dmg` to mount it, then drag **X-Men.app** into your
   **Applications** folder.
3. Eject the disk image (right-click on the desktop → **Eject "X-Men"**).

> 🛡️ **Gatekeeper / "X-Men is damaged or can't be opened" — required step.**
> Because this release is not signed with an Apple Developer ID, macOS
> Gatekeeper will block the first launch with a message like:
>
> > *"'X-Men.app' is damaged and can't be opened."*  
> > *"'X-Men.app' cannot be opened because the developer cannot be verified."*
>
> This is expected. Use **one** of the two methods below to allow it.

**Method 1 — System Settings (recommended, no Terminal)**

1. Double-click **X-Men.app** in Applications and dismiss the warning.
2. Open the Apple menu  → **System Settings** → **Privacy & Security**.
3. Scroll down to the **Security** section. You will see:
   > *"X-Men was blocked from use because it is not from an identified developer."*
4. Click **Open Anyway** next to that message.
5. macOS shows one more confirmation popup — click **Open**. Your
   administrator password may be requested.
6. From this point on, **X-Men.app** launches normally from Launchpad.

**Method 2 — Strip the quarantine flag (Terminal one-liner)**

If Gatekeeper is in lockdown mode and **Open Anyway** doesn't appear, drop
the quarantine extended attribute that Safari/Chrome added when the .dmg
was downloaded:

```bash
xattr -dr com.apple.quarantine /Applications/X-Men.app
```

Then double-click **X-Men.app** from Applications.

**Troubleshooting macOS launches**

| Symptom | Cause | Fix                                                                                                                   |
| --- | --- |-----------------------------------------------------------------------------------------------------------------------|
| *"X-Men is damaged and can't be opened. You should move it to the Trash."* | Quarantine flag from the download. | Run `xattr -dr com.apple.quarantine /Applications/X-Men.app`.                                                         |
| App icon bounces in the Dock, then disappears. | Gatekeeper killed it silently. | Open **Privacy & Security** within 60 s and click **Open Anyway**.                                                    |
| *"This Mac is not running on the right architecture."* | Downloaded the wrong .dmg. | Use `X-Men-x64.dmg` on Intel Mac, `X-Men-arm64.dmg` on Apple Silicon.                                                 |
| Splash plays but the main window never appears. | Stale `~/.xmen/settings.json` referencing a theme that no longer exists. | Quit X-Men 2.0, run `rm ~/.xmen/settings.json`, relaunch.                                                             |
| Screen goes black / Mac freezes shortly after launch. | JavaFX GStreamer pipeline hung the WindowServer while decoding the splash or background video. | If you hit it on another platform, set `XMEN_BG_VIDEO=false` before launch (see *Disabling background videos* below). |

**Disabling background videos (any OS)**

If the background or splash MP4s cause performance issues or graphics
glitches on your machine, force the app into image-only mode. The flag is
respected on Windows, macOS, and Linux:

```bash
# macOS / Linux
launchctl setenv XMEN_BG_VIDEO false              # macOS: persists across launches
# or, just this run:
XMEN_BG_VIDEO=false open -a X-Men
```

```powershell
# Windows
setx XMEN_BG_VIDEO false                          # persists across launches
# or, just this run:
$env:XMEN_BG_VIDEO = 'false'; & 'C:\Users\<you>\AppData\Local\X-Men\X-Men.exe'
```

Set it back to `true` (or `setx XMEN_BG_VIDEO ""` / `launchctl unsetenv
XMEN_BG_VIDEO`) to re-enable the videos.

### 🐧 Linux install (Debian / Ubuntu)

1. Download **X-Men.deb** from the Releases page.
2. Install with apt (resolves any runtime dependencies automatically):

   ```bash
   sudo apt install ./X-Men.deb
   ```

   Or use `dpkg` if you prefer:

   ```bash
   sudo dpkg -i X-Men.deb
   sudo apt-get install -f          # fix any missing deps
   ```
3. Launch from the application menu (it appears under *Development* /
   *Security*) or from a terminal:

   ```bash
   /opt/x-men/bin/X-Men
   ```

Fedora / RHEL builds are not currently published — `mvnw clean package -P installer -Djpackage.type=RPM`
on a Fedora host produces a working `.rpm` if you need one.

### 🧹 Uninstalling X-Men 2.0

| OS | How to uninstall |
| --- | --- |
| **Windows** | *Settings* ▸ *Apps* ▸ *Installed apps* ▸ find **X-Men** ▸ **Uninstall**. The installer registers itself per-user, so administrator rights are not required. |
| **macOS** | Drag **X-Men.app** from `/Applications` to the Trash. To also clear runtime data run `rm -rf ~/.xmen ~/Library/Application\ Support/X-Men` in Terminal. |
| **Linux (Debian/Ubuntu)** | Open *Ubuntu Software* (or *GNOME Software* / *KDE Discover*) ▸ *Installed* ▸ **X-Men** ▸ **Uninstall**.  Or from a terminal: `sudo apt remove x-men` (preferred) or `sudo dpkg -r x-men`. |

> 💡 **Linux note**: The .deb registers AppStream metadata at install time so
> the OS Software Center can list and uninstall it without a terminal. If
> your distro hides .deb apps from the GUI Software Center (some snap-only
> setups do), `sudo apt remove x-men` always works.

---

## ▶️ Running your first mutation

Once the app is installed and open:

1. Click **Upload File** in the top-right of the main scene and pick a
   `.spthy` ceremony.
2. Tick the mutation checkboxes you want to generate — *Skip Send*,
   *Replace Type*, *Forget Mutation*, etc. You can combine as many as you
   want; the engine runs the union.
3. Click **Start Mutation**. The status shows on screen and a **Download**
   button appears next to **Start Mutation** when the run is complete.
4. Click **Download** to save the generated `.zip` of mutated `.m` files
   anywhere you like. (The raw `.m` files also stay under `~/.xmen/runs/`
   between runs.)

That's the whole user-facing flow. Everything below is for developers who
want to build, extend, or script X-Men 2.0 from the command line.

---

> # 🛠️ Section 2 — Developer Setup & Internals
>
> **This is the path to understand the internal structure of the system.**
> Clone the repo, build from source, learn the architecture, drive the REST
> API directly, and extend the codebase. Everything past this point assumes
> you have (or are willing to install) a JDK, Maven, and Git.

## 🛠 Built With

| Layer | Technology |
| --- | --- |
| Language | [**Java 21**](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html) |
| Build | [**Apache Maven 3.9**](https://maven.apache.org/) (via `mvnw` wrapper) |
| Framework | [**Spring Boot 3**](https://spring.io/projects/spring-boot) |
| API docs | [**springdoc-openapi** / Swagger UI](https://springdoc.org/) |
| Native UI | **JavaFX** (in-process) |
| Verifier (downstream) | [**Tamarin Prover**](https://tamarin-prover.github.io/) |
| Optional analytics | [**Haskell Derivation Service**](./HASKELL_DERIVATION_SERVICE.md) |
| Container | **Docker** (multi-stage, JRE 21 slim runtime) |

---

## 🧭 Architecture at a Glance

```
              ┌─────────────────────────────────────────────────────┐
              │                  X-Men (Spring Boot)                │
              │                                                     │
   .spthy ──► │  Parser ─► Mutation Engine ─► .m generator ──► .zip │ ──► Tamarin
              │            │                                        │
              │            └─► HybridDerivationService              │
              │                    │                                │
              │                    ├─ Java DY engine (default)      │
              │                    └─ Haskell service (opt-in)──────┼─► POST /derive
              └─────────────────────────────────────────────────────┘
```

- **Input:** a Tamarin `.spthy` ceremony specification.
- **Output:** a `.zip` of mutated `.m` files, one per mutation variant.
- **Modes of access:** Native JavaFX UI, REST API, Postman collection, or
  Swagger UI.

---

## 🚀 Local Setup

The shortest path from a clean machine to a running X-Men 2.0 instance built
from source.

### Installing Java and Maven

1. **Java 21** — download from
   [Oracle](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
   or [Adoptium Temurin](https://adoptium.net/) and run the platform installer.
2. **Maven 3.9+** — download the binary archive from the
   [Apache Maven site](https://maven.apache.org/download.cgi) and unpack it to
   a stable location (e.g. `C:\Program Files\Apache\maven`).

> 💡 The repository ships with the Maven Wrapper (`mvnw`, `mvnw.cmd`), so a
> system-wide Maven install is **optional** — you can use `./mvnw …` instead.

---

### Setting Up Path Variables

<details>
<summary><strong>🪟 Windows</strong></summary>

<br/>

1. Press <kbd>Win</kbd> + <kbd>R</kbd>, type `sysdm.cpl`, press <kbd>Enter</kbd>.
2. Switch to **Advanced ▸ Environment Variables…**
3. Under **System variables**, click **New…** and add:
   - `JAVA_HOME` → `C:\Program Files\Java\jdk-21`
   - `MAVEN_HOME` → `C:\Program Files\Apache\maven`
4. Edit **Path** and append:
   - `%JAVA_HOME%\bin`
   - `%MAVEN_HOME%\bin`
5. Open a **new** terminal — old shells won't see the change.

</details>

<details>
<summary><strong>🍎 macOS</strong> / <strong>🐧 Linux</strong></summary>

<br/>

Append to `~/.zshrc` (or `~/.bashrc`):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
# export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk    # Linux
export MAVEN_HOME=/opt/apache-maven
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
```

Reload:

```bash
source ~/.zshrc
```

</details>

#### Verify the install

```bash
java -version
mvn  -version
```

You should see Java 21.x and Maven 3.9.x respectively.

---

### Installing Git

| Platform | Command |
| --- | --- |
| **Windows** | Download from [git-scm.com](https://git-scm.com/) and run the installer. Choose **Git Bash** as the default terminal. |
| **macOS**   | `brew install git` |
| **Linux**   | `sudo apt install git`  (Debian/Ubuntu) — `sudo dnf install git` (Fedora) |

Verify:

```bash
git --version
```

---

### Cloning the Project

```bash
git clone <repository-url>
cd X-Men_2.0
```

> Paths containing apostrophes (e.g. `a folder named O'Reilly`) are supported
> on Windows — `mvnw.cmd` already escapes them before invoking PowerShell.

---

### Installing IntelliJ IDEA

1. Download **IntelliJ IDEA Community Edition** from
   [JetBrains](https://www.jetbrains.com/idea/download/).
2. Install for your OS.
3. Launch IntelliJ ▸ **Open…** ▸ select the cloned `X-Men_2.0` folder.
4. IntelliJ auto-detects `pom.xml`. If it doesn't, right-click `pom.xml` ▸
   **Add as Maven Project**.

---

### Building and Running the Project

#### 1) Build

From the project root:

```bash
# Using the wrapper (recommended — works without a system Maven)
./mvnw clean install        # macOS / Linux
mvnw.cmd clean install      # Windows

# Or your system Maven
mvn clean install
```

#### 2) Run — the Spring Boot app (REST API)

```bash
./mvnw spring-boot:run
```

The server comes up on **`http://localhost:8081`**.

#### 3) Run — with the Native JavaFX UI

In IntelliJ:

1. **Run ▸ Edit Configurations…**
2. **+** ▸ **Application** ▸ Main class `com.xmen.Application`.
3. **Modify options ▸ Add VM options:** `-Djava.awt.headless=false`.
4. **Environment variables (optional):** `JAVA_OPTS=-Djava.awt.headless=false`.
5. **Apply**, then **Run ▶**.

The Native UI looks like this:

<p align="center">
  <img src="src/main/resources/images/img.png" alt="X-Men 2.0 native UI" width="640"/>
</p>

From the UI:

1. Tick the checkboxes for the mutations you want generated.
2. Upload a `.spthy` file.
3. Click **Start Mutation** — a `.zip` containing all mutated `.m` files is
   produced in the working directory.



---

### Setting Up Postman

1. Install [Postman](https://www.postman.com/downloads/).
2. **File ▸ Import** ▸ select
   `src/main/resources/X-Men.postman_collection.json`.
3. The full **X-Men 2.0** collection appears in the sidebar with one folder per
   mutation type.
4. With the Spring Boot app running, pick a request (e.g. *Forget Mutation*),
   attach a `.spthy` file under `form-data ▸ file`, and **Send**.

> Tip: each request stores the response as `application/octet-stream`.
> Use **Save response ▸ Save to a file** to grab the `.zip`.

---

## 📡 API Reference

All endpoints listen on `http://localhost:8081` by default and accept a
single `.spthy` upload as `form-data` under the key `file`. Responses are
streamed `.zip` archives containing the mutated `.m` files.

### Browsing the API with Swagger UI

X-Men 2.0 ships with **[springdoc-openapi](https://springdoc.org/)**, which means
the moment the Spring Boot app is running you get a **live, interactive API
explorer** in your browser — no extra setup, no extra dependencies, and no
need to write or import a Postman collection just to poke at an endpoint.

#### 🌐 Where to go

| Asset | URL | Use it for |
| --- | --- | --- |
| 🧭 **Swagger UI** | <http://localhost:8081/swagger-ui/index.html> | Human-friendly playground — browse, expand, upload `.spthy`, hit **Try it out**. |
| 📜 **OpenAPI JSON** | <http://localhost:8081/v3/api-docs> | Machine-readable spec — feed into Postman, Insomnia, Bruno, or a client generator. |
| 📜 **OpenAPI YAML** | <http://localhost:8081/v3/api-docs.yaml> | Same spec, friendlier diffing in PRs. |

#### 🏷️ How the API is organised

The endpoints are grouped under tags so you can navigate large mutation
catalogs quickly. The left rail of Swagger UI lists them in this order:

| Tag | What lives here |
| --- | --- |
| **Mutations** | The combined `/api/generateMutations` endpoint controlled by request headers. |
| **Add Mutations** | The dedicated *Add* mutation endpoint. |
| **Forget Mutations** | All *Forget* endpoints, including the Haskell-activated variant. |
| **Neglect Mutations** | The dedicated *Neglect* mutation endpoint. |
| **Replace Mutations** | *Replace Sub-messages* and *Replace Type* endpoints. |
| **Skip Mutations** | *Skip Send / Receive / Send→Receive / Receive→Send / Receive→Send→Receive*. |
| **Derivation** | Haskell-backed derivation analysis (`/api/derive`, `/api/derive/health`). |

#### ▶️ Running a request from the browser — 60-second tour

1. Make sure the app is up: `./mvnw spring-boot:run` (or `docker compose up`).
2. Open <http://localhost:8081/swagger-ui/index.html>.
3. Expand the tag you care about (e.g. **Forget Mutations**) and click a
   `POST` row to reveal the request panel.
4. Hit **Try it out** — every input becomes editable.
5. For mutation endpoints:
   - **Body** → `file` field → click **Choose File** → pick your `.spthy`.
   - **Headers** → flip the boolean toggles or paste values
     (`Haskell-Activate: true`, `Blocking-Mode: CASE1`, …).
6. Click **Execute**. You'll get:
   - The **HTTP status** and timing in the response section.
   - A **Download file** button for the streamed `.zip` of mutated `.m` files.
   - A ready-to-paste **`curl`** invocation in the *Curl* block — perfect for
     scripting the same call later.

#### 🧪 Worked example — Forget mutation with the Haskell engine

In Swagger UI:

> **Forget Mutations ▸ `POST /api/forget/mutations`** ▸ *Try it out* ▸
> upload `Bank_revised_new.spthy` ▸ set header
> `Haskell-Activate: true` ▸ **Execute**

The *Curl* block will display the equivalent shell call, e.g.

```bash
curl -X POST "http://localhost:8081/api/forget/mutations" \
  -H "Haskell-Activate: true" \
  -F "file=@Bank_revised_new.spthy" \
  -o forget_mutations.zip
```

…which you can copy verbatim into a terminal or CI pipeline.


### Health checks

| Method | Path | Purpose |
| --- | --- | --- |
| `GET`  | `/actuator/health/` | Liveness / readiness probe (Spring Boot Actuator). |

### Individual mutation endpoints

Each one performs a single mutation pattern and returns a `.zip` of mutated
`.m` files.

| Method | Endpoint | Mutation |
| --- | --- | --- |
| `POST` | `/api/skip/sendMutations` | Skip Send |
| `POST` | `/api/skip/receiveMutations` | Skip Receive |
| `POST` | `/api/skip/sendReceiveMutations` | Skip Send→Receive chain |
| `POST` | `/api/skip/receiveSendMutations` | Skip Receive→Send chain |
| `POST` | `/api/skip/receiveSendReceive` | Skip Receive→Send→Receive chain |
| `POST` | `/api/addMutations` | Add Mutation |
| `POST` | `/api/replace/subMessagesMutations` | Replace Sub-messages |
| `POST` | `/api/replace/typeMutations` | Replace Type |
| `POST` | `/api/neglect/mutations` | Neglect Mutation |
| `POST` | `/api/forget/mutations` | Forget Mutation |

Example:

```bash
curl -X POST "http://localhost:8081/api/forget/mutations" \
  -F "file=@Bank_revised_new.spthy" \
  -o forget_mutations.zip
```

### Combined mutation endpoint

`POST /api/generateMutations` runs **any combination** of the above mutations
in a single call. Selection is via boolean headers:

| Header | Effect |
| --- | --- |
| `Skip-Send: true` | Enable Skip Send |
| `Skip-Receive: true` | Enable Skip Receive |
| `Skip-Send-Receive: true` | Enable Skip Send Receive |
| `Skip-Receive-Send: true` | Enable Skip Receive Send |
| `Skip-Receive-Send-Receive: true` | Enable longer skip chains |
| `Add-Mutation: true` | Enable Add |
| `Replace-Sub-Messages: true` | Enable Replace Sub-messages |
| `Replace-Type: true` | Enable Replace Type |
| `Neglect-Mutation: true` | Enable Neglect |
| `Forget-Mutation: true` | Enable Forget |
| `True-Replace: true` | Knowledge-based (rather than random) replacements |
| `Haskell-Activate: true` | Route Forget derivability to the external Haskell service |

Example — Forget mutation with the Haskell engine:

```bash
curl -X POST "http://localhost:8081/api/generateMutations" \
  -H "Forget-Mutation: true" \
  -H "Haskell-Activate: true" \
  -F "file=@CoachService.spthy" \
  -o mutants_forget_haskell.zip
```

> 📚 For everything to do with the optional Haskell engine — installation,
> wire-up, examples, troubleshooting — see
> **[`HASKELL_DERIVATION_SERVICE.md`](./HASKELL_DERIVATION_SERVICE.md)**.

---

## 🐳 Docker

<table>
<tr>
<td>

> **Docker is for API testing and service-mode runs only.**  
> X-Men 2.0's primary interface is a native JavaFX desktop application. Docker
> Desktop cannot open that JavaFX UI as a normal Windows/macOS/Linux desktop
> window from inside a Linux container. Use Docker when you want a reproducible
> Spring Boot API runtime for Swagger, Postman, curl, or integration testing.
> Use the local Java/Maven launch path in [Local Setup](#-local-setup)
> when you need the full native desktop application.

</td>
</tr>
</table>

X-Men 2.0 ships with a production-grade **multi-stage `Dockerfile`**:

- **Stage 1** uses `maven:3.9.9-eclipse-temurin-21` to compile and package.
- **Stage 2** runs the resulting jar on a slim `eclipse-temurin:21-jre` image
  as a **non-root** API/service process, with a built-in `HEALTHCHECK`.

A `.dockerignore` keeps the build context tight (no `target/`, no IDE files,
no docs) so image builds are fast and reproducible.

### Build & run the image

```bash
# Build
docker build -t x-men:latest .

# Run
docker run --rm -p 8081:8081 --name x-men x-men:latest

# Or in the background
docker run -d -p 8081:8081 --name x-men x-men:latest

# Verify
curl http://localhost:8081/actuator/health/
```

Once the container is running, API documentation is available at:

```text
http://localhost:8081/swagger-ui/index.html
```

### Docker Compose

A reference `docker-compose.yml` is included. It runs the X-Men 2.0 Spring Boot API
service by itself, and — under the optional `haskell` profile — also brings up
the Haskell derivation service on `9091`, wired together over Compose's default
network.

```bash
# X-Men 2.0 only
docker compose up --build

# X-Men 2.0 + Haskell Derivation Service
docker compose --profile haskell up --build

# Tear everything down
docker compose down
```

### Environment overrides

| Variable | Default | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8081` | HTTP port the app binds to. |
| `APP_NAME` | `X-Men` | Application display name. |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:8081,…,http://localhost:5173` | Allowed CORS origins (comma-separated). |
| `DERIVATION_SERVICE_URL` | `http://localhost:9091` | Endpoint of the Haskell service. |
| `JAVA_OPTS` | `-Djava.awt.headless=false` | Extra JVM flags. |
| `XMEN_UI_ENABLED` | `false` in Docker, `true` locally | Controls whether the JavaFX desktop UI is launched. Docker uses service mode; local desktop runs keep the UI enabled. |
| `SPRING_PROFILES_ACTIVE` | `default` | Spring profile selector. |

Pass them at run-time like:

```bash
docker run --rm -p 9090:9090 \
  -e SERVER_PORT=9090 \
  -e DERIVATION_SERVICE_URL=http://host.docker.internal:9091 \
  x-men:latest
```

---

## 🎨 Settings, Vocabulary & Themes

X-Men 2.0 ships with a runtime-mutable settings layer so the same tool can be re-targeted at
different naming conventions and re-skinned with a new color palette **without changing
any code**.

### 🗣️ Vocabulary (Level 1 generalisation)

Every "hardcoded" word like `Send`, `Receive`, `Forget`, `State`, `Out`, `In` lives in
[`src/main/resources/vocabulary.yaml`](src/main/resources/vocabulary.yaml). The Forget
mutation reads its non-internal action set from there, and the file binds to a Spring
`@ConfigurationProperties` bean (`CeremonyVocabulary`) that the Settings API can mutate at
runtime.

| Endpoint | What it does |
| --- | --- |
| `GET /api/settings/vocabulary` | Read the live vocabulary. |
| `POST /api/settings/vocabulary` | Patch the live vocabulary (partial JSON allowed). |
| `POST /api/settings/vocabulary/reset` | Restore built-in defaults. |
| `GET /api/settings/vocabulary/export` | Download the active vocabulary as `vocabulary.yaml`. |
| `POST /api/settings/vocabulary/import` | Upload a YAML file (`multipart/form-data` field `file`). |

### 🎨 Themes (28 built-in palettes)

[`src/main/resources/themes.yaml`](src/main/resources/themes.yaml) ships twenty-eight named
themes — Classic (default), Garden Mint, Midnight Violet, Ocean Cobalt, Sunset
Coral, Amber Grove, Rose Quartz, Forest Emerald, Cyber Teal, Solar Yellow, Indigo Night,
Lavender Mist, Charcoal Mono, Paper Light, Arctic Ice, Volcanic Red, Mocha Brown, Slate
Blue, Neon Pink, Sage Green, Copper Bronze, Crimson Wine, Powder Blue, Sandstone, Magenta
Storm, Pine Forest, Royal Purple, Tangerine. Each tile in the Settings dialog renders the
theme's name beneath a small classy preview card.

Each theme controls the accent color, glass-panel shade, overlay tone, and text color
all at once. Switching a theme is a single HTTP call:

```bash
curl -X POST http://localhost:8081/api/settings/themes/active \
  -H "Content-Type: application/json" \
  -d '{"id":"midnight-violet"}'
```

| Endpoint | What it does |
| --- | --- |
| `GET /api/settings/themes` | List all 28 themes + the active id. |
| `GET /api/settings/themes/active` | Resolve the active theme. |
| `POST /api/settings/themes/active` | Switch the active theme by id. |
| `GET /api/settings/themes/{id}` | Look up one theme. |

### ✅ Pre-flight Tamarin syntax validation

Upload a `.spthy` file to the validator before kicking off a mutation run. The validator
performs a structural check (mandatory `theory … begin … end` envelope, balanced
delimiters) followed by the full ANTLR parse. Errors come back with line and column
numbers in a JSON body.

```bash
curl -X POST http://localhost:8081/api/settings/validate \
  -F "file=@Bank_revised_new.spthy"
```

### 🖥️ Native UI

The JavaFX UI exposes all of the above through a redesigned hero scene (glass panels +
gradient CTAs + pill nav) plus a dedicated **Settings** dialog (gear icon, bottom-left).
The dialog has three tabs:

| Tab | Contents |
| --- | --- |
| **Vocabulary** | Editable table of every key/value, with **Detect · Reset · Export YAML · Import YAML**. Importing a YAML prompts you to save it as a named profile. Profile dropdown · **Load** · **Delete**. |
| **Themes** | The 28 palette tiles — preview + name under each. Clicking one re-tints the whole UI instantly. |
| **Preferences** | UI toggles — validate on upload, animations, derivation overlay. |

Behavioural notes that ship with the redesigned UI:

- **Multi-monitor aware** — the app sizes itself to whichever monitor it is launched on; popups, toasts, settings, and chat panels all stay on that monitor.
- **Dark window chrome** — the OS title bar is requested in dark mode on Windows, macOS, and (best-effort) Linux so it matches the active theme.
- **Themed popups** — Success, Error, and Confirm dialogs render as themed cards with no white wrapper. Non-confirm popups auto-dismiss after 5 seconds.
- **Forget mutation defaults** — selecting Forget enables **Infinite** by default; the radio choices are Infinite · Specified Depth.
- **Download after success** — once a mutation run completes, a Download button appears next to Start Mutation (and inside the derivation-tree panel) so the generated zip can be saved anywhere.
- **Chat with X-Men** — bot answers render with markdown-lite bullets/bold; conversations can be deleted from the sidebar. The bot knows about every mutation, depth modes, derivation trees, vocabularies, themes, the Tamarin/Dolev–Yao backdrop, and how to drive the UI.

### Snapshot in one call

Need every setting at once (for a new UI client)?

```bash
curl http://localhost:8081/api/settings
```

Returns `{ "vocabulary": …, "themes": { "active": …, "catalog": [...] } }` in a single
request.

---

## ⚙️ Configuration

Application defaults live in `src/main/resources/application.yaml` and read
from environment variables with sensible fallbacks. The most common knobs are
documented in
**[`ENVIRONMENT_CONFIG.md`](./ENVIRONMENT_CONFIG.md)** — including the local
`.env` workflow for development.

---

## 🧪 Haskell Derivation Service

> 🛈 **External service — currently out of scope for this submission.** The
> Haskell derivation engine is an **optional external microservice** that
> runs in its own process and is invoked over HTTP. It is **not bundled with
> the installer, not built by `mvn install`, and not exercised by the default
> test suite**. The default in-process Java derivation engine handles every
> Forget mutation X-Men 2.0 ships with, so the Haskell route is **currently
> out of scope for this submission** and is documented below only for
> completeness and future work.

The optional Haskell microservice is a self-contained topic with its own
configuration, request format, and operational notes. Everything lives in:

➡️ **[`HASKELL_DERIVATION_SERVICE.md`](./HASKELL_DERIVATION_SERVICE.md)**

That document covers: when you need the service, how to start it, the exact
conversion pipeline, environment overrides, networking from Docker / Compose,
the failure-handling contract, and a focused troubleshooting table.

---

## 🗂 Project Structure

```
X-Men_2.0/
├── src/
│   ├── main/
│   │   ├── java/com/xmen/
│   │   │   ├── controller/        # REST controllers
│   │   │   ├── service/           # Mutation engines, derivation services
│   │   │   │   ├── forget/        # Forget mutation helpers
│   │   │   │   ├── derivation/    # DY derivation utilities
│   │   │   │   └── impl/          # Concrete mutation strategies
│   │   │   ├── model/             # Parsed Tamarin AST + DTOs
│   │   │   ├── utilities/         # File I/O, Setup extractor, etc.
│   │   │   └── user_interface/    # JavaFX views
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── images/            # UI artwork (used by README too)
│   │       └── X-Men.postman_collection.json
│   └── test/                      # Unit / integration tests
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── mvnw, mvnw.cmd                 # Maven Wrapper
├── pom.xml
├── ENVIRONMENT_CONFIG.md          # Environment-variable reference
├── HASKELL_DERIVATION_SERVICE.md  # Sub-README for the Haskell integration
└── README.md                      # ← you are here
```

---

## 🧯 Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `mvnw.cmd` errors on Windows: *"Unexpected token 's' in expression or statement."* | Project path contains an apostrophe. | Already patched — `mvnw.cmd` escapes `'` before calling PowerShell. If you re-ran `mvn -N wrapper:wrapper`, re-apply the patch. |
| Native UI doesn't open. | Headless mode left on. | Add VM option `-Djava.awt.headless=false` to the run configuration. |
| `Forget` mutation produces no changes. | The `.spthy` rule has no `Forget(...)` action. | Add an explicit `Forget(<term>)` annotation to the rule of interest. |
| `Haskell-Activate: true` has no effect. | External Haskell service not reachable. | See [`HASKELL_DERIVATION_SERVICE.md`](./HASKELL_DERIVATION_SERVICE.md#9-troubleshooting). |
| CORS blocked from a custom frontend. | Origin not whitelisted. | Set `APP_CORS_ALLOWED_ORIGINS` to include your origin. |
| Docker build slow on subsequent rebuilds. | Layer cache invalidated by source edits. | Edits under `src/` only invalidate Stage 1's source layer — dependency resolution stays cached. |

---

## 📊 Test Coverage with JaCoCo

The build is wired up with the [**JaCoCo**](https://www.jacoco.org/jacoco/) Maven
plugin so that every `mvn test` (or `mvn verify` / `mvn install`) run produces a
full code-coverage report alongside the usual Surefire test results.

### Run the tests and generate the report

```bash
# Using the wrapper
./mvnw clean test            # macOS / Linux
mvnw.cmd clean test          # Windows

# Or your system Maven
mvn clean test
```

### Where to find the results

| Artifact | Path | Use it for |
| --- | --- | --- |
| 🧪 **Surefire test results** | `target/surefire-reports/` | JUnit XML + plain-text per-test reports. |
| 📈 **JaCoCo HTML report** | `target/site/jacoco/index.html` | Open in any browser to drill into per-package / per-class line & branch coverage. |
| 📄 **JaCoCo XML report** | `target/site/jacoco/jacoco.xml` | Machine-readable feed for CI dashboards (Codecov, SonarQube, etc.). |
| 🧷 **Raw execution data** | `target/jacoco.exec` | Binary coverage data the report goal consumes. |

> 💡 Model classes (`com.xmen.model.*`) are intentionally excluded from the
> coverage figures because they are mostly generated parsers and DTOs.

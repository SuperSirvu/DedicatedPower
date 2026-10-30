# AGENTS.md

Instructions for AI coding agents working in this repository.

## 1. Project overview

**DedicatedPower** (Modrinth name **Server OS**, mod id `dedicatedpower`) is an
open-source (MIT) Fabric mod that replaces the default Minecraft dedicated
server window with a cleaner Swing interface: live log console, command input
with suggestions, online player list, server statistics, MOTD editor, world and
server controls, and a themed menu bar.

- **Language/build:** Java 25, Gradle wrapper, Fabric Loom.
- **UI:** Swing / AWT, no external UI libraries.
- **Services:** none. The mod runs entirely inside the server process.
- **Layout:** a single Gradle module at the repository root.
- **Target:** dedicated servers only. Client and integrated server entry points
  are not part of the project.

## 2. Architecture and file map

| Path | Role |
|---|---|
| `build.gradle` | Loom plugin, dependencies, `version` read from `version.txt` |
| `gradle.properties` | Minecraft, Fabric Loader, Loom and Fabric API versions |
| `version.txt` | **single source of truth** for the version, `<minecraft>-<mod version>` |
| `src/main/resources/fabric.mod.json` | mod metadata, loader and API constraints |
| `src/main/resources/dedicatedpower.mixins.json` | mixin list and compatibility level |
| `src/main/resources/dedicatedpower.accesswidener` | access widener (currently empty) |
| `src/main/java/net/supersirvu/DedicatedPower.java` | entry point, ready banner, `/opengui` |
| `src/main/java/net/supersirvu/ServerGuiState.java` | shared frame/opening/`--nogui` state |
| `src/main/java/net/supersirvu/mixin/` | `MinecraftServerGui`, `DedicatedServer` and `Main` hooks |
| `src/main/java/net/supersirvu/gui/EnhancedServerMenuBar.java` | menu bar, dialogs, MOTD editor, close flow |
| `src/main/java/net/supersirvu/gui/EnhancedLogPanel.java` | log view, search/filter, command console, suggestions |
| `src/main/java/net/supersirvu/gui/EnhancedPlayerStatsGui.java` | memory/tick graphs and statistics lines |
| `src/main/java/net/supersirvu/gui/EnhancedPlayerListGui.java` | online player table and actions |
| `src/main/java/net/supersirvu/gui/ThemeManager.java` | Light/Dark theme, log colors, theme pass |
| `src/main/java/net/supersirvu/gui/MotdTextUtils.java` | MOTD normalization, mojibake repair, line limit |
| `src/main/java/net/supersirvu/gui/MotdHistoryStore.java` | saved MOTD history (`config/`) |
| `src/main/java/net/supersirvu/gui/Utf8Properties.java` | UTF-8 `.properties` read/write |
| `src/main/java/net/supersirvu/gui/CommandSuggestionUtils.java` | leading slash and namespace completion rules |
| `src/main/java/net/supersirvu/gui/WrapLayout.java` | wrapping flow layout for the MOTD toolbar |
| `src/test/java/net/supersirvu/gui/` | JUnit 5 tests for the pure helpers |

**Mixin contract.** The server GUI is built by `MinecraftServerGui`. The mixin
replaces `buildInfoPanel` and `buildChatPanel` and installs the menu bar at the
end of `showFrameFor`. `buildChatPanel` must keep `logAppenderThread` populated,
because the vanilla GUI starts that field after the panels are built and a null
value leaves the window blank. `CaptureNoGuiMixin` only records `--nogui`, and
`AlwaysShowGuiMixin` opens the GUI at the end of `DedicatedServer.initServer`.

## 3. Branch and release policy

Each supported Minecraft version lives on its own branch:

- `mc-26.1` - the Minecraft 26.1 build (`minecraft_version=26.1.2`).
- `mc-26.2` - the Minecraft 26.2 build.
- `mc-26.3` - the Minecraft 26.3 build.

Rules:

- **Never mix versions in one branch.** A branch keeps the Minecraft, Fabric
  Loader, Fabric API, Loom and Gradle versions it was created with, plus its own
  `version.txt`, README and changelog entry.
- **A behaviour fix belongs to every supported branch.** When a change to the
  mod code applies to all versions, it is committed on `mc-26.1`, `mc-26.2` and
  `mc-26.3` with equivalent code, each with its own version in `version.txt`. A
  fix on only one branch is unfinished work.
- **Version-specific work stays on its branch.** The Minecraft 26.3 port itself
  (build files, wrapper, README badge, changelog entry) is not backported.
- `main` is the repository default branch and carries the newest line (26.3)
  plus the release workflow. Do not push version-specific work to it unless the
  owner asks.
- **Only `main` publishes releases.** `mc-26.1`, `mc-26.2` and `mc-26.3` build
  and test on every push through the shared
  `.github/workflows/build-jar.yml` (called by `build.yml`) and never create a
  release. A `v` commit on `main` makes its `auto-release.yml` build every
  supported branch through the same reusable workflow and publish one release
  carrying one jar per branch. Adding a Minecraft line means adding its branch
  to the list in that workflow and keeping `build-jar.yml` on it.
- Never create scratch branches. Do the work on the branch that owns it.

## 4. Versioning, changelog and commits

### `version.txt` is the single version source

`version.txt` holds one line, `<minecraft>-<mod version>`, for example
`26.3-1.3.2` on `mc-26.3` and `26.2-1.3.2` on `mc-26.2`. The Minecraft prefix
must match the branch. `build.gradle` reads the whole line
(`version = file('version.txt').text.trim()`), names the archive
`<mod>-<version>` and `processResources` expands the version into
`fabric.mod.json`. The jar is therefore `dedicatedpower-26.3-1.3.2.jar`.
`Auto Release` reads the same file and uses the part after the first hyphen as
its tag and title, so every branch of one release shares the mod version.
**Do not add a second copy of the version anywhere**, and do not edit the jar
name or the metadata by hand.

- **MINOR** (`1.3.0` -> `1.4.0`): new features.
- **PATCH** (`1.3.0` -> `1.3.1`): compatible fixes and documentation-only
  program changes.
- **MAJOR**: only for genuinely breaking changes.
- The Minecraft target lives in `gradle.properties` (`minecraft_version`) and
  in the jar file name. It never goes into a release tag or a release title:
  only the mod part of `version.txt` is used for those.
- When the owner names the segment in the message, use that segment for that
  message only.

### `CHANGELOG.md`

Keep a Changelog format, newest entry first, sections `Added`, `Changed`,
`Fixed`, `Removed`, `Security` (omit empty ones). Every program change gets an
entry under the version being prepared. The section is what `Auto Release`
publishes, so write it for users: what the app does differently, in English, no
quotations of a report or of the owner's own words.

### Commits

- **Every commit that changes program code starts with `v`**, for example
  `v: fix MOTD encoding and console completion`. The title does not carry the
  version: `Auto Release` reads `version.txt` on `main` and uses the mod part of
  it for the tag and the release title. On a version branch the prefix only
  triggers the normal build, because releases are published from `main` only.
- **English only**, title and body.
- **No co-author, no attribution footer.** Never add `Co-Authored-By:` or
  "Generated with" lines, and never credit the agent or the client.
- Put the details (what changed and why) in the body after a blank line.
- Documentation-only commits may use `docs:` and do not release anything.

## 5. Code conventions

- **Golden rule: if it works, do not touch it.** Change only what the task
  needs. No drive-by refactors, reformatting or renames.
- **Swing thread rules.** All component creation and mutation happens on the
  EDT. Server calls go through `server.execute(...)`. A background thread may
  only prepare data and hand it back with `SwingUtilities.invokeLater`.
- **Theme everything through `ThemeManager`.** Components that paint themselves
  (graphs, statistics, tooltips) must read `getForeground()`,
  `getPanelBackground()`, `getInputBackground()`, `getBorderColor()` and the
  other accessors. A literal `Color.BLACK` or `new Color(0, 0, 0)` for text is a
  bug: it stays black on the dark theme.
- **Theme switch must reach open windows.** `ThemeManager.applyTo(window)`
  updates the tree and every open window; components with custom painting
  implement `ThemeManager.ThemeListener`.
- **A theme pass must never move a caret.** `SwingUtilities.updateComponentTreeUI`
  reinstalls the UI and resets every text component to position 0, and
  `applyTo` runs whenever any window opens. It captures the caret and the
  selection first and restores them on the same event dispatch turn; keep that
  behaviour when changing the theme code.
- **MOTD text goes through `MotdTextUtils`.** It repairs mojibake, decodes
  `\u00A7` / `/u00A7` escapes, normalizes line endings and enforces the two-line
  limit. Never parse formatting codes anywhere else.
- **`.properties` files are UTF-8.** Use `Utf8Properties` for anything Minecraft
  also reads or writes (`server.properties`, the MOTD history). `Properties`
  with streams is ISO-8859-1 and turns `§` into `Â§`.
- **Console suggestions go through `CommandSuggestionUtils`.** It handles the
  optional leading slash and the default namespace for registry ids. Keep the
  suggestion popup free of document mutations: it may only read the text and
  move a non-focusable `JWindow`, otherwise typed characters can be reordered.
- **The console accepts commands with or without a leading slash.** Parse
  suggestions on the text after the slash; execution still goes through
  `server.handleConsoleInput`.
- **No long dash (em dash).** Use a hyphen, comma, colon, parentheses or a full
  stop in comments, docs, changelog entries and commit messages.
- **Keep comments factual.** Explain why a workaround exists (for example the
  vanilla `logAppenderThread` contract) and keep them in English.
- **Do not add dependencies** for anything the JDK, Swing or Brigadier already
  provide. The mod ships with Fabric API only.

## 6. Build and verification

Requirements: JDK 25. Loom 1.19 requires Gradle 9.8 or newer, so the wrapper
ships Gradle 9.8.2.

```bash
./gradlew build          # compile, run tests, build the jar
./gradlew test           # unit tests only
./gradlew compileJava    # fastest compile check
```

On Windows, if the default JVM is older than 25, point Gradle at a JDK 25
installation:

```bash
./gradlew build -Dorg.gradle.java.home="C:/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot"
```

Verification rules:

- A compile is not verification of behaviour. When a change touches the console,
  the MOTD editor, the theme or a dialog, exercise the affected path (or cover
  the pure logic with a JUnit test) and say what was actually run.
- New pure helpers get tests in `src/test/java/net/supersirvu/gui/`. The GUI
  classes themselves are not unit tested; keep logic that can be tested out of
  them.
- Report a check that could not run as a limitation, never as a pass.

## 7. Installed skills

Community skills are installed under `.agents/skills/` (gitignored, local to the
machine) and tracked in `skills-lock.json`:

| Skill | Source | Use |
|---|---|---|
| `ponytail` | `dietrichgebert/ponytail` | smallest working solution, reuse before writing |
| `minecraft-fabric-dev` | `mcdxai/meteor-mcp-addon` | Fabric mod development conventions |
| `port-a-mod` | `rehan-remade/universal-modder` | porting a mod between Minecraft versions |
| `minecraft-server-admin` | `jahrome907/minecraft-agent-skills` | dedicated server operations |

- `ponytail` applies to every coding task: prefer the smallest working change,
  reuse what exists, stdlib and native APIs before new code or dependencies.
  It never overrides an explicit request from the owner.
- `minecraft-fabric-dev` is installed from the author's public
  `meteor-mcp-addon` repository because `mcdxai/meteor-rejects-v2` returns 404.
- Install or update a skill with
  `npx skills add <owner/repo> -s <skill> -y -a '*'`. Skills are trusted
  instructions: read a new skill before using it, and confirm with the owner
  before installing anything from an unfamiliar author.

## 8. Fixes already made: do not regress them

These were reported by users and fixed; each one has a root cause worth keeping
in mind.

**MOTD showed `Â§` instead of `§`.** `server.properties` was read and written
with `Properties.load(InputStream)` / `store(OutputStream)`, which always use
ISO-8859-1, while Minecraft reads and writes the file as UTF-8. Every MOTD save
therefore encoded the section sign twice. `Utf8Properties` now reads UTF-8 with
an ISO-8859-1 fallback (for files written by older builds) and writes UTF-8.
`MotdTextUtils.repairEncoding` still repairs a value that is already corrupted
when it is loaded from an older file.

**Command suggestions disappeared.** They had been reduced to `TAB` because
live completion once reordered typed characters. The document listener now only
schedules a refresh and the popup never edits the command field. Suggestions
appear while typing, on `TAB`, and for a leading `/`.

**Typed characters were still reordered (`give` became `iveg`).** The
completion popup is a `JWindow`, and opening it fires `WINDOW_OPENED`, which
made `ThemeManager` run a full theme pass. `updateComponentTreeUI` reinstalls
the text UI and moves the command caret to 0, so every later character was
inserted in front of the first one; `/give` became `give/`. The theme pass now
saves and restores the caret and the selection of every text component in the
same event dispatch turn. Do not remove that capture/restore pair, and never
call `updateComponentTreeUI` or `updateUI` on a window the user is typing in.

**Registry ids complete with the default namespace.** An argument that is a
known registry id (`oak_button`) is shown and inserted as
`minecraft:oak_button`, like the Aternos console. The command name itself, player
names, numbers and free text are never qualified.

**Statistics stayed black on the dark theme.** `EnhancedPlayerStatsGui` painted
the memory/tick lines and the graph tooltip with a hard-coded black. They now
use the theme foreground.

**The console said nothing when the GUI was closed.** `Close GUI Only` and
`Close GUI and Server` now log a message through the command source, so the
console mirrors the existing "GUI is already open" line.

## 9. Do not touch

- The vanilla GUI contract described in section 2 (`buildInfoPanel`,
  `buildChatPanel`, `logAppenderThread`, `showFrameFor`).
- The `--nogui` and headless handling in `CaptureNoGuiMixin` and
  `AlwaysShowGuiMixin`.
- The `Login`-free scope: the mod never reads player credentials, tokens or
  server secrets, and no code may start doing so.
- `LICENSE` (MIT). Do not change it.

## 10. Security and legal

- Never log, print or commit credentials, tokens, server secrets, keystores or
  personal data.
- Only use APIs and content the project is allowed to use; when a request
  involves third-party content or a license that is not clearly permissive,
  stop and ask the owner.
- Runtime files created by the mod (`config/dedicatedpower-*.properties`) stay
  local and gitignored.

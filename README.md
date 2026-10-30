# <img src="icon.png" alt="DedicatedPower" width="40" align="center"> DedicatedPower

<p align="center">
  <a href="https://modrinth.com/mod/server-os"><img src="https://cdn.modrinth.com/data/6Ai7Gm6t/images/3d104b8b1b477919b7ac77a9c7ccf859d4abbdbe.png" alt="DedicatedPower main screen" width="720"></a>
</p>

<p align="center">
  <a href="https://modrinth.com/mod/server-os"><img src="https://img.shields.io/badge/Modrinth-DedicatedPower-00AF5C?style=for-the-badge&logo=modrinth" alt="Modrinth"></a>
  <a href="https://modrinth.com/mod/server-os"><img src="https://img.shields.io/modrinth/dt/server-os?style=for-the-badge&logo=modrinth&label=Downloads&color=00AF5C" alt="Modrinth downloads"></a>
  <a href="https://modrinth.com/mod/server-os"><img src="https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fapi.modrinth.com%2Fv2%2Fproject%2Fserver-os%2Fversion&query=%24%5B0%5D.version_number&label=Modrinth&logo=modrinth&color=00AF5C&style=for-the-badge" alt="Modrinth version"></a>
  <a href="https://github.com/PiBOH/DedicatedPower/releases"><img src="https://img.shields.io/github/v/release/PiBOH/DedicatedPower?include_prereleases&style=for-the-badge&logo=github&label=Release" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/PiBOH/DedicatedPower?style=for-the-badge&label=License" alt="License"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.3-2E7D32?style=for-the-badge&logo=minecraft" alt="Minecraft 26.3">
  <img src="https://img.shields.io/badge/Fabric-0.20.1%2B-1769aa?style=for-the-badge&logo=fabric" alt="Fabric">
  <img src="https://img.shields.io/badge/Java-25%2B-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25+">
</p>

DedicatedPower is a Fabric mod that improves the graphical interface of a Minecraft dedicated server.

> **Modrinth page:** <https://modrinth.com/mod/server-os>


It replaces the default server administration window with a cleaner interface for monitoring the server, viewing online players, running commands, and accessing common server controls.

## Compatibility

- **Minecraft:** Java Edition 26.3
- **Platform:** Fabric
- **Environment:** Dedicated servers only
- **Fabric Loader:** 0.20.1 or newer
- **Java:** 25 or newer
- **License:** MIT

DedicatedPower is not intended for single-player worlds or the integrated server.

## Links

- [Modrinth page](https://modrinth.com/mod/server-os)
- [GitHub repository](https://github.com/PiBOH/DedicatedPower)
- [Releases](https://github.com/PiBOH/DedicatedPower/releases)

## Features

### Dedicated server interface

- Custom server GUI for dedicated servers
- Server menu bar with common administration actions
- Quick access to saving worlds, reloading data packs, and stopping the server
- World controls for time and weather
- Command dialog for running server commands

### Console and commands

- Live server log output with color-coded log levels
- Dedicated command console panel
- Command execution directly from the GUI, with or without a leading slash
- Live command suggestions while typing and on Tab
- Registry ids complete with the default namespace: `oak_button` becomes `minecraft:oak_button`
- Command history with the Up and Down arrow keys
- Clearable console output

### Player management

- Online player list
- Player name filtering
- Game mode display
- Refreshable player information

### Server monitoring

- Current TPS estimate
- Average tick time
- Online player count
- Loaded level count
- Compact statistics panel integrated into the server GUI

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.3.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft 26.3.
3. Install Java 25 or newer.
4. Download the latest DedicatedPower release from the [Releases](https://github.com/PiBOH/DedicatedPower/releases) page. Each release carries one jar per supported Minecraft version (26.1, 26.2 and 26.3), so pick the jar that matches your server.
5. Place the DedicatedPower `.jar` file in the server's `mods` folder.
6. Start the dedicated server.

Always make a backup of your server before installing or updating mods.

## Usage

DedicatedPower is loaded automatically when the dedicated server starts. The enhanced server window is created by the mod and can be used to monitor the server and run commands. The console panel streams live server log output with color-coded levels and accepts commands entered through the GUI.

Use the **Server**, **World**, and **Tools** menus to access the available controls. Commands can be entered with or without the leading slash, for example:

```text
save-all
/save-all
```

Suggestions follow what you type: the list opens above the input, `Tab` applies the selected entry, `Up` and `Down` move through the list (or through the command history when it is closed), and `Esc` dismisses it. Registry ids are completed with the default namespace, so typing `oak` offers `minecraft:oak_button`.

## Building from source

### Requirements

- JDK 25
- Git
- Internet access for Gradle and Minecraft dependencies

### Build commands

Clone the repository and build the mod with the Gradle wrapper:

```bash
git clone https://github.com/PiBOH/DedicatedPower.git
cd DedicatedPower
./gradlew build
```

On Windows, use:

```bat
gradlew.bat build
```

The compiled files are generated in `build/libs/`.

## Development status

DedicatedPower is actively being updated for Minecraft 26.3. The project uses the official Minecraft names and the non-remapping Fabric Loom toolchain introduced for newer unobfuscated Minecraft versions. The Minecraft 26.1, 26.2 and 26.3 builds are kept on their own branches and published together in every release.

The project currently focuses on the dedicated server GUI and administration workflow. Some advanced features from earlier versions may be reintroduced or expanded in future updates.

## Reporting issues

Before opening an issue, make sure that:

- You are using Minecraft 26.3.
- You are using Java 25 or newer.
- You are using compatible Fabric Loader and Fabric API versions.
- You have searched existing issues.
- You have included the relevant server log or crash report.

Use the repository's [English bug report form](https://github.com/PiBOH/DedicatedPower/issues/new/choose) for bug reports. Use [GitHub Discussions](https://github.com/PiBOH/DedicatedPower/discussions) for general questions and setup help.

## Authors

- SuperSirvu
- PiBOH

## License

DedicatedPower is licensed under the [MIT License](LICENSE).

Enjoy running your dedicated server.

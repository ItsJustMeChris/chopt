# Chopt (Fabric)

Timber-style tree felling for Minecraft 1.21.11 on Fabric. Trees take a few chops based on size, then the whole tree falls — while avoiding player-built structures.

## Features
- Size-based chops: required swings scale with log count (capped at 32).
- Sneak to opt out: crouching (`Shift`) does normal single-block breaking.
- Safe detection: only fells structures with nearby leaves and up to 256 logs.
- Clean drops: final hit uses the original (unstripped) log state for correct drops.
- Fair durability: every swing costs durability; timbering only fells as many logs as your axe has durability for, leaving the rest if it breaks.
- Shared effort: chop progress is tracked per tree, so multiple players can contribute swings to the same timber.
- Client-agnostic: no client-side setup required; works server-side.
- Jade integration (optional): hovering a shrinking stump shows the original log's name and icon plus a chop-progress bar, instead of the placeholder block.

## Requirements
- Minecraft `1.20.1` / `1.21.8` / `1.21.10` / `1.21.11` / `26.1` / `26.1.1` / `26.1.2` / `26.2` / `26.3-snapshot-6`
- Fabric Loader `>=0.18.2` (`>=0.19.3` on 26.x)
- Fabric API for your MC version
- Java 21 (Java 17 for the 1.20.x profile, Java 25 for the 26.x profiles)
- Optional: [Jade](https://modrinth.com/mod/jade) — not required, and not bundled
  (no Jade build exists for 26.3 yet, so that target ships without the tooltip)

## Install (players)
1. Install Fabric Loader for 1.21.11.
2. Place `chopt-*.jar` (from releases/build) and Fabric API in your `mods/` folder.
3. Launch the game; no config screen is needed.

## Usage
- Break any log with an axe; progress is tracked per tree.
- Keep swinging the same log until the quota is met; the rest of the tree is felled automatically.
- If your axe breaks mid-timber, only the logs you could afford are felled—finish the remainder with a fresh tool.
- Hold `Shift` while breaking to disable timbering for that action.
- Player-placed log piles without leaves nearby will not be felled.

## Building from source
```sh
./gradlew build
```
Outputs are under `build/libs/` (`-dev` jars are for development, the remapped jar is for players/servers).

To switch Minecraft targets, use the built-in profiles:
```sh
./gradlew useMc1201
./gradlew useMc1218
./gradlew useMc12110
./gradlew useMc12111
./gradlew useMc261
./gradlew useMc2611
./gradlew useMc2612
./gradlew useMc262
./gradlew useMc263Snapshot6
```
Each profile pins its own Minecraft, Fabric, Loom and Jade versions, and switching
profiles clears the previous one's toolchain settings, so they can be run in any order.
The `26.x` profiles are deobfuscated (no mappings) and target Java 25.

## Development notes
- Uses official Mojang mappings; see `AGENTS.md` for cache and inspection tips.
- Tree scanning lives in `src/main/java_v*/mod/chopt/TreeChopper.java`.
- Jade compat lives in `src/main/java_v*/mod/chopt/compat/jade/` (entrypoint, loaded on
  servers too) and `src/client/java_v*/mod/chopt/compat/jade/` (the tooltip itself).
  Jade is a `compileOnly` dependency, so the mod runs fine without it.

## Known limits
- Hard cap of 256 logs per tree scan.
- No configuration file yet; behavior is fixed (shift-to-skip, leaf check, caps).

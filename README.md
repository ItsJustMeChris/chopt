# Chopt (Fabric)

Timber-style tree felling for Minecraft 1.21.11-rc3 on Fabric. Trees take a few chops based on size, then the whole tree falls — while avoiding player-built structures.

## Features
- Size-based chops: required swings scale with log count (capped at 32).
- Sneak to opt out: crouching (`Shift`) does normal single-block breaking.
- Safe detection: only fells structures with nearby leaves and up to 256 logs.
- Clean drops: final hit uses the original (unstripped) log state for correct drops.
- Fair durability: every swing costs durability; timbering only fells as many logs as your axe has durability for, leaving the rest if it breaks.
- Shared effort: chop progress is tracked per tree, so multiple players can contribute swings to the same timber.
- Client-agnostic: no client-side setup required; works server-side.

## Requirements
- Minecraft `1.21.11-rc3`
- Fabric Loader `>=0.18.1`
- Fabric API `0.139.4+1.21.11` (or newer for this MC version)
- Java 17

## Install (players)
1. Install Fabric Loader for 1.21.11-rc3.
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

## Development notes
- Uses official Mojang mappings; see `AGENTS.md` for cache and inspection tips.
- Tree scanning lives in `src/main/java/mod/chopt/TreeChopper.java`.

## Known limits
- Hard cap of 256 logs per tree scan.
- No configuration file yet; behavior is fixed (shift-to-skip, leaf check, caps).

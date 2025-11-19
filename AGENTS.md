# AGENTS.md

Guidance for future agents working in this repo with **official Mojang mappings** (no Yarn). These steps are version-agnostic; directory hashes and versions will change, but the approach stays the same.

---

## 1. Know where Loom stores things
- Fabric Loom caches everything under `.gradle/loom-cache/`.
- Inside you'll usually find:
  - `minecraftMaven/net/minecraft/minecraft-clientOnly-<hash>/<mc-version>-loom.mappings...`
  - `minecraftMaven/net/minecraft/minecraft-common-<hash>/...`
  - `source_mappings/` if Loom extracted layered mappings.
- Each folder contains both `*.jar` and `*-sources.jar`. When targeting client-only code (GUIs, rendering, keybinds) use the `clientOnly` artifacts; cross-cutting logic can live in `common`.

> Tip: hashes and build numbers change per version. Use globbing rather than hard-coding names.
```sh
find .gradle/loom-cache -path "*minecraft-clientOnly*sources.jar"
```

---

## 2. Inspect jar contents quickly
- You now have the standard JDK `jar` tool available (`/usr/bin/jar`). Use it when you want simple listings or to extract targeted files:
  - `jar tf <jar> | head` lists entries (like `unzip -l` but faster and pipes cleanly).
  - `jar xf <jar> path/Inside/File.java` extracts a specific file (drops it into the current directory hierarchy).
  - `jar tf <jar> | rg "BlockRender"` pairs nicely with `rg` for quick searches.
- If you need to stream file contents without extracting, `unzip -p` is still great:
  - `unzip -p <jar> path/Inside/File.java | sed -n '1,80p'`
- Combine the two approaches depending on the task. Example:
```sh
jar tf $CLIENT_JAR | rg "net/minecraft/client/gui"
unzip -p $CLIENT_JAR net/minecraft/client/gui/Gui.java | rg -n "render"
```

---

## 3. Map gameplay concepts to official names
Common replacements encountered while porting from Yarn:

| Concept                         | Official name (1.21.x)          | Notes                                  |
|---------------------------------|---------------------------------|----------------------------------------|
| `MinecraftClient`               | `net.minecraft.client.Minecraft`| Field names: `level`, `player`, `gameMode`, `levelRenderer`. |
| `ClientPlayerInteractionManager`| `net.minecraft.client.multiplayer.MultiPlayerGameMode` | Access via `Minecraft.gameMode`.       |
| `KeyBinding`                    | `net.minecraft.client.KeyMapping` | Register with Fabric’s helper but instantiate with official enum/category. |
| `TextRenderer`                  | `net.minecraft.client.gui.Font` | HUD rendering now uses `GuiGraphics`.  |
| `Box`                           | `net.minecraft.world.phys.AABB` | Use `inflate` vs `expand`.             |
| Block/entity packages           | `net.minecraft.world.level.block.*`, `net.minecraft.world.entity.*` | Often split across `clientOnly`/`common`. |

When unsure, search the sources:
```sh
unzip -p $CLIENT_SOURCES net/minecraft/client/Minecraft.java | rg -n "gameMode"
```
Look for familiar logic (attack, renderer refresh, option access) to confirm names.

---

## 4. Searching strategies
1. **Find class/package names**  
   `rg -n "class KeyMapping" -g "*.java" .gradle/loom-cache/minecraftMaven`
2. **Locate specific members**  
   Use `rg -n "gamma" <Minecraft Options source>` to see how option instances expose getters.
3. **Follow imports**  
   Once you know the package, switch project code to the official import (e.g., `net.minecraft.world.level.block.state.BlockState`).

Remember that Fabric API sources are also remapped into `.gradle/loom-cache/remapped_mods/...`. Use the same `find` + `unzip` combo there when you need helper classes such as `KeyBindingHelper`.

---

## 5. Applying findings in the codebase
- Replace Yarn types/methods with their official equivalents before writing new logic.
- Update mixin targets to official method names (`renderBatched` vs `renderBlock` depending on dispatcher class).
- If the IDE reports “class cannot be resolved,” check whether you’re importing the Yarn location; if so, revisit steps 1–4 to find the official package.

---

## 6. Lessons learned from this migration
1. **Use the cached jars—no network required.** Even with blocked internet, everything Loom downloaded earlier remains accessible. Search there first.
2. **Prefer sources jars (`*-sources.jar`).** They retain readable Java files with full official names, making it trivial to copy APIs.
3. **Be flexible with tooling.** When `jar`/`javap` is missing, `unzip` plus `rg` covers 99% of inspection needs.
4. **Look at context, not just names.** Identifying methods like `attack` or fields like `levelRenderer` required scanning the class body and matching behavior, not string equality.
5. **Document translations as you go.** Keeping a small table (like the one above) accelerates later work and prevents regressions.

Following this workflow lets future agents adapt to new Minecraft versions or cache layouts without needing Yarn or external docs—everything necessary is already stored locally. Experiment, verify against the jars, and port code confidently. Good luck!

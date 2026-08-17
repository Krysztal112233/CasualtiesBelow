# AGENTS.md — CasualtiesBelow

A Fabric mod written in Scala 3, targeting Minecraft 26.2 (officially unobfuscated
since 26.1 — no yarn/Mojang mappings). Java 25 toolchain; `gradle.properties` is the
single source of truth for version numbers.

## Common commands

```bash
./gradlew build        # Build
./gradlew runClient    # Launch a dev client
./gradlew runServer    # Launch a dev server
./gradlew sources      # Generate and extract dependency sources into sources/ (see below)
```

## Where to read source code (important)

Due to the nature of Minecraft modding, most API questions require consulting the
Minecraft / Fabric API sources.

**Do not answer Minecraft API questions from memory — consult the source trees below first.**
These source trees are very large (thousands of files): use subagents for code
exploration instead of reading them into the main context yourself.

| Directory                      | Contents                                 | Notes                                                                                                                                                                                                                                                         |
| ------------------------------ | ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `src/main/scala/`              | The mod itself                           | Entrypoints are declared in `src/main/resources/fabric.mod.json` (`scala` language adapter, provided by krysztal-language-scala)                                                                                                                              |
| `sources/minecraft/`           | Decompiled Minecraft 26.2 sources (Java) | Produced by Fabric Loom `genSources` (Vineflower); package roots are `net.minecraft.*` and `com.mojang.*`. Decompiled code may differ from the real implementation (especially generics and control flow), but class names and method signatures are reliable |
| `sources/fabric-api/<module>/` | Official Fabric API sources              | One directory per module (e.g. `fabric-lifecycle-events-v1`, `fabric-networking-api-v1`)                                                                                                                                                                      |

`sources/` is gitignored — it is a regenerable local cache. If it is missing or stale
after a version bump, regenerate it with `./gradlew sources`: this runs Loom
`genSources` to decompile and extract Minecraft, and resolves the official Fabric API
sources jars, extracting them per module. Takes a few minutes.
The task implementation lives in `gradle/sources.gradle.kts`.

### Searching

- Search class/method names directly with `rg`, e.g.:
  `rg "class ServerPlayer" sources/minecraft -l`
- Locate by package path: Minecraft classes live at
  `sources/minecraft/net/minecraft/<package>/<Class>.java`.
- Fabric API events/interfaces live at
  `sources/fabric-api/<module>/net/fabricmc/fabric/api/...`.
- These sources are **read-only reference**: any edits are overwritten by the next
  `./gradlew sources` run. To change mod behavior, edit `src/`.

## Commit conventions

Commit messages follow Conventional Commits: `type(scope): description`.

- Types: `feat`, `fix`, `refactor`, `docs`, `chore`, `test`
- Scope is the **mod subsystem or feature area** (e.g. `worldgen`, `loot`, `config`),
  never a file name. Omit the scope for cross-cutting changes; use `docs(agents)` when
  syncing `AGENTS.md`.
- Description: lowercase English, imperative mood, no trailing period; wrap
  identifiers, paths, and class names in backticks.
- Breaking changes get a `!` before the colon, e.g. `refactor(config)!: ...`.
- Subject-only commits are the norm; bodies and footers are rarely used. Keep each
  commit focused on one concern.

## Notes

- Scala 3 with significant-indentation syntax; `scala_version` must stay in sync with
  the Scala version bundled at runtime by krysztal-language-scala (see the comments in
  `gradle.properties`).
- Since the game is unobfuscated, dependencies are declared with plain
  `implementation`; no mappings configuration is needed.
- `run/` is the dev-runtime directory (worlds, logs, configs) — do not treat it as a
  source directory.

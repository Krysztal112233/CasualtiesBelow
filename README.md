# CasualtiesBelow

A Fabric mod written in Scala 3, targeting Minecraft 26.2 (unobfuscated).

## Requirements

- **JDK 25+** (Minecraft 26.2 requires Java 25; Fabric Loom also requires Gradle to run on it)
- Gradle is provided via the wrapper (`./gradlew`)

## Build

```bash
./gradlew build
```

## Run in dev

```bash
./gradlew runClient
./gradlew runServer
```

## Stack

| Component                                                                     | Version                                                              |
| ----------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| Minecraft                                                                     | 26.2 (no mappings — game is unobfuscated since 26.1)                 |
| Fabric Loader                                                                 | 0.19.3                                                               |
| Fabric API                                                                    | 0.157.0+26.2                                                         |
| Fabric Loom                                                                   | 1.17-SNAPSHOT                                                        |
| Scala                                                                         | 3.7.3 (compile-time, matches the version bundled by the runtime mod) |
| [Krysztal's Language Scala](https://modrinth.com/mod/krysztal-language-scala) | 3.3.2+scala.3.7.3 (runtime dependency, from Modrinth Maven)          |

Versions live in [`gradle.properties`](gradle.properties).

## Notes

- Since Minecraft 26.1 the game is no longer obfuscated: no yarn/Mojang mappings
  are configured, and dependencies are declared with plain `implementation`
  (per the official Fabric template for 26.x).
- Scala entrypoints use the `"scala"` language adapter provided by
  `krysztal-language-scala` (see `src/main/resources/fabric.mod.json`).
- Keep `scala_version` in sync with the Scala version bundled by
  `krysztal-language-scala` (see `krysztal_scala_version`).

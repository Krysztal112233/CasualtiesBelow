// Readable dependency sources for source browsing (see AGENTS.md).
// `./gradlew sources` extracts Minecraft + Fabric API sources into the
// gitignored sources/ directory:
//   sources/minecraft/          decompiled Minecraft (Loom genSources / Vineflower)
//   sources/fabric-api/<module> official Fabric API module sources jars

import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType

// Resolves the official sources jars of Fabric API (aggregate pulls in all modules transitively).
val fabricApiSources: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
    }
}

dependencies {
    fabricApiSources("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
}

// The genSources output jar lives under a hash-suffixed directory in the Loom cache;
// locate it at execution time (after genSources has run).
fun locateMinecraftSourcesJar(): File {
    val pattern = Regex("minecraft-merged-.+-sources\\.jar")
    val candidates = sequenceOf(
        rootDir.resolve(".gradle/loom-cache/minecraftMaven"),
        File(gradle.gradleUserHomeDir, "caches/fabric-loom/minecraftMaven")
    ).filter { it.isDirectory }.flatMap { root ->
        root.walkTopDown().filter { it.isFile && pattern.matches(it.name) }
    }.toList()
    return checkNotNull(candidates.maxByOrNull { it.lastModified() }) {
        "Minecraft sources jar not found; did genSources run?"
    }
}

val extractMinecraftSources = tasks.register("extractMinecraftSources") {
    group = "fabric"
    description = "Decompile Minecraft (Loom genSources) and extract into sources/minecraft/."
    dependsOn("genSources")
    doLast {
        val out = layout.projectDirectory.dir("sources/minecraft").asFile
        out.deleteRecursively()
        out.mkdirs()
        copy {
            from(zipTree(locateMinecraftSourcesJar()))
            into(out)
        }
    }
}

val extractFabricApiSources = tasks.register("extractFabricApiSources") {
    group = "fabric"
    description = "Extract Fabric API module sources into sources/fabric-api/<module>/."
    doLast {
        val out = layout.projectDirectory.dir("sources/fabric-api").asFile
        out.deleteRecursively()
        out.mkdirs()
        fabricApiSources.resolve()
            .filter { it.name.endsWith("-sources.jar") }
            .forEach { jar ->
                val module = jar.name.replace(Regex("-[0-9][^-]*-sources\\.jar$"), "")
                copy {
                    from(zipTree(jar))
                    into(out.resolve(module).apply { mkdirs() })
                }
            }
    }
}

tasks.register("sources") {
    group = "fabric"
    description = "Extract readable Minecraft + Fabric API sources into sources/ (gitignored)."
    dependsOn(extractMinecraftSources, extractFabricApiSources)
}

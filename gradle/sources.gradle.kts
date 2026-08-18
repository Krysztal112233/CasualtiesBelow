// Readable dependency sources for source browsing (see AGENTS.md).
// `./gradlew sources` extracts Minecraft + Fabric API + mod dependency sources into the
// gitignored sources/ directory:
//   sources/minecraft/          decompiled Minecraft (Loom genSources / Vineflower)
//   sources/fabric-api/<module> official Fabric API module sources jars
//   sources/mods/<mod>          sources jars of other mod dependencies (ModMenu, FCAP, CCA...)

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

// Sources jars of other mod dependencies, for API browsing. Non-transitive: only the listed mods.
val modSources: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    // Only the listed mods, not their transitive dependencies.
    isTransitive = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
    }
}

dependencies {
    fabricApiSources("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Mods whose sources are worth browsing locally. Mods without a published sources jar
    // are skipped silently (lenient resolution in extractModSources).
    modSources("maven.modrinth:modmenu:${property("modmenu_version")}")
    modSources("fuzs.forgeconfigapiport:forgeconfigapiport-fabric:${property("forge_config_api_port_version")}")
    modSources("org.ladysnake.cardinal-components-api:cardinal-components-base:${property("cca_version")}")
    modSources("org.ladysnake.cardinal-components-api:cardinal-components-entity:${property("cca_version")}")
    modSources("maven.modrinth:krysztal-language-scala:${property("krysztal_scala_version")}")
}

// Extract a set of sources jars into outDir/<module>/, deriving the module directory name
// from the jar file name (e.g. modmenu-20.0.1-sources.jar -> modmenu).
fun Project.extractSourcesJars(jars: Iterable<File>, outDir: File) {
    jars.filter { it.name.endsWith("-sources.jar") }.forEach { jar ->
        val module = jar.name.replace(Regex("-[0-9][^-]*-sources\\.jar$"), "")
        copy {
            from(zipTree(jar))
            into(outDir.resolve(module).apply { mkdirs() })
        }
    }
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
        extractSourcesJars(fabricApiSources.resolve(), out)
    }
}

val extractModSources = tasks.register("extractModSources") {
    group = "fabric"
    description = "Extract mod dependency sources into sources/mods/<mod>/."
    doLast {
        val out = layout.projectDirectory.dir("sources/mods").asFile
        out.deleteRecursively()
        out.mkdirs()
        // Lenient: skip mods that do not publish a sources jar.
        val jars = modSources.incoming.artifactView { isLenient = true }.files
        extractSourcesJars(jars, out)
    }
}

tasks.register("sources") {
    group = "fabric"
    description = "Extract readable Minecraft + Fabric API + mod dependency sources into sources/ (gitignored)."
    dependsOn(extractMinecraftSources, extractFabricApiSources, extractModSources)
}

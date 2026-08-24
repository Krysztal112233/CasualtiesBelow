plugins {
    scala
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("com.diffplug.spotless") version "7.2.1"
}

// `./gradlew sources`: extract readable Minecraft + Fabric API sources (see AGENTS.md).
apply(from = "gradle/sources.gradle.kts")

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName = property("archives_base_name") as String
}

loom {
    runs.configureEach {
        // Export transformed classes for mixin inspection and retain failed targets for diagnosis.
        systemProperties.put("mixin.debug.export", "true")
        systemProperties.put("mixin.dumpTargetOnFailure", "true")
    }

    // `./gradlew runDatagen` (entrypoint: datagen/CasualtiesBelowDataGenerator).
    // Output lands in src/main/generated and is packaged automatically.
    fabricApi {
        configureDataGeneration()
    }
}

repositories {
    // Loom adds the Fabric and Mojang repositories automatically.
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
    maven("https://maven.ladysnake.org/releases") { name = "Ladysnake" }
    maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/") { name = "Fuzs" }
    // JEI (mezz): optional integration, compile against the API artifact only.
    maven("https://maven.blamejared.com/") { name = "BlameJared" }
    // Aliyun mirror of Maven Central: repo.maven.apache.org returns 403 from this network.
    maven("https://maven.aliyun.com/repository/central") { name = "AliyunCentral" }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    // Minecraft is no longer obfuscated since 26.1: no mappings needed, plain `implementation`.

    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // ModMenu (client mod list / config screen integration).
    implementation("maven.modrinth:modmenu:${property("modmenu_version")}")

    // JEI: optional informational integration. Compile-only against the API artifact; the full
    // jar rides the dev runtime. The plugin class loads only when JEI is present (lazy
    // `jei_mod_plugin` entrypoint), and JEI is a `suggests`, not a `depends`.
    compileOnly("mezz.jei:jei-26.2-fabric-api:${property("jei_version")}")
    runtimeOnly("mezz.jei:jei-26.2-fabric:${property("jei_version")}")

    // Scala 3 language support (Fabric language adapter + Scala runtime, bundled at runtime by this mod).
    implementation("maven.modrinth:krysztal-language-scala:${property("krysztal_scala_version")}")

    // Cardinal Components API: base module + entity module, bundled jar-in-jar
    // so players do not need to install CCA separately.
    // (Unobfuscated game: no remapping, plain `implementation` like the other deps.)
    implementation("org.ladysnake.cardinal-components-api:cardinal-components-base:${property("cca_version")}")
    implementation("org.ladysnake.cardinal-components-api:cardinal-components-entity:${property("cca_version")}")
    add("include", "org.ladysnake.cardinal-components-api:cardinal-components-base:${property("cca_version")}")
    add("include", "org.ladysnake.cardinal-components-api:cardinal-components-entity:${property("cca_version")}")

    // EvalEx: math expression compiler for user-configurable formulas. Pure Java library
    // with no transitive dependencies; bundled jar-in-jar.
    implementation("com.ezylang:EvalEx:${property("evalex_version")}")
    add("include", "com.ezylang:EvalEx:${property("evalex_version")}")

    // Forge Config API Port: NeoForge-style config system on Fabric. External dependency
    // (Fuzs ecosystem convention): declared in fabric.mod.json `depends`, users install it
    // separately — do NOT bundle it jar-in-jar.
    implementation("fuzs.forgeconfigapiport:forgeconfigapiport-fabric:${property("forge_config_api_port_version")}")
    // NightConfig: bundled inside Forge Config API Port at runtime, needed on the compile classpath.
    compileOnly("com.electronwill.night-config:core:3.8.4")
    compileOnly("com.electronwill.night-config:toml:3.8.4")

    // Compile-time Scala 3 library; kept in sync with the version bundled by krysztal-language-scala,
    // which provides it at runtime.
    compileOnly("org.scala-lang:scala3-library_3:${property("scala_version")}")
}

java {
    // Minecraft 26.2 requires Java 25.
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<ScalaCompile>().configureEach {
    options.release = 25
    // Enforce the project's brace style: reject significant-indentation syntax.
    scalaCompileOptions.additionalParameters.add("-no-indent")
    // Explicit nulls: Java members annotated @Nullable (JSpecify) become hard T | Null and
    // must be null-checked before dereference; unannotated Java types stay flexible.
    scalaCompileOptions.additionalParameters.add("-Yexplicit-nulls")
}

spotless {
    scala {
        scalafmt("3.11.5").configFile(".scalafmt.conf")
    }
    kotlinGradle {
        ktlint()
    }
    format("misc") {
        target("*.md", ".gitignore", "gradle.properties", "src/main/resources/*.json")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.processResources {
    val replaceProperties = mapOf("version" to version)
    inputs.properties(replaceProperties)

    filesMatching("fabric.mod.json") {
        expand(replaceProperties)
    }
}

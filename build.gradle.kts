plugins {
    scala
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName = property("archives_base_name") as String
}

repositories {
    // Loom adds the Fabric and Mojang repositories automatically.
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    // Minecraft is no longer obfuscated since 26.1: no mappings needed, plain `implementation`.

    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // ModMenu (client mod list / config screen integration).
    implementation("maven.modrinth:modmenu:${property("modmenu_version")}")

    // Scala 3 language support (Fabric language adapter + Scala runtime, bundled at runtime by this mod).
    implementation("maven.modrinth:krysztal-language-scala:${property("krysztal_scala_version")}")

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
}

tasks.processResources {
    val replaceProperties = mapOf("version" to version)
    inputs.properties(replaceProperties)

    filesMatching("fabric.mod.json") {
        expand(replaceProperties)
    }
}

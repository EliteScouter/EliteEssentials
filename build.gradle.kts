plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.3.1"
    id("run-hytale")
}

group = findProperty("pluginGroup") as String? ?: "com.eliteessentials"
version = findProperty("pluginVersion") as String? ?: "1.1.7"
description = findProperty("pluginDescription") as String? ?: "Essential commands for Hytale servers"

repositories {
    mavenLocal()
    mavenCentral()

    maven("https://repo.helpch.at/releases")
    
    // VaultUnlocked API repository
    maven("https://repo.codemc.io/repository/creatorfromhell/") {
        name = "VaultUnlocked"
    }
}

dependencies {
    // Hytale Server API (provided by server at runtime)
    compileOnly(files("hytaleserver.jar"))

    compileOnly("at.helpch:placeholderapi-hytale:1.0.4")
    
    // VaultUnlocked - compileOnly since it's provided by server at runtime
    compileOnly("net.cfh.vault:VaultUnlocked:2.18.3")
    
    // JSON handling
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains:annotations:24.1.0")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Configure server testing
runHytale {
    // TODO: Update this URL when Hytale server is available
    jarUrl = "https://fill-data.papermc.io/v1/objects/d5f47f6393aa647759f101f02231fa8200e5bccd36081a3ee8b6a5fd96739057/paper-1.21.10-115.jar"
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
    }
    
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        
        val props = mapOf(
            "group" to project.group,
            "version" to project.version,
            "description" to project.description
        )
        inputs.properties(props)
        
        filesMatching("manifest.json") {
            expand(props)
        }
    }
    
    shadowJar {
        archiveBaseName.set(rootProject.name)
        archiveClassifier.set("")
        
        relocate("com.google.gson", "com.eliteessentials.libs.gson")
        
        minimize()
    }
    
    test {
        useJUnitPlatform()
    }
    
    build {
        dependsOn(shadowJar)
    }
}

java {
    toolchain {
        val javaVersion = findProperty("java_version")?.toString()?.toInt() ?: 25
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

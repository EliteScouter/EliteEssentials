plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.3.1"
    id("run-hytale")
}

group = findProperty("pluginGroup") as String? ?: "com.eliteessentials"
version = findProperty("pluginVersion") as String? ?: "1.1.3"
description = findProperty("pluginDescription") as String? ?: "Essential commands for Hytale servers"

repositories {
    mavenLocal()
    mavenCentral()
    
    // VaultUnlocked API repository
    // Note: VaultUnlocked 2.18.3 for Hytale requires JVM 25+ at runtime
    // We use reflection-based integration to allow building with JVM 21
    // maven("https://repo.codemc.io/repository/creatorfromhell/") {
    //     name = "VaultUnlocked"
    // }
}

dependencies {
    // Hytale Server API (provided by server at runtime)
    compileOnly(files("hytaleserver.jar"))
    
    // VaultUnlocked - integration is reflection-based to allow building with JVM 21
    // while running on JVM 25+ servers. No compile-time dependency needed.
    
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
        options.release = 21
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
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.3.1"
    id("run-hytale")
}

group = findProperty("pluginGroup") as String? ?: "com.eliteessentials"
version = findProperty("pluginVersion") as String? ?: "2.0.0"
description = findProperty("pluginDescription") as String? ?: "Essential commands for Hytale servers"

repositories {
    mavenLocal()
    mavenCentral()

    maven("https://repo.helpch.at/releases")

    // Official Hytale Maven repository
    maven( "https://maven.hytale.com/release")
    maven( "https://maven.hytale.com/pre-release")

    // CodeMC Hytale repository (mirrors server JARs)
    maven("https://repo.codemc.io/repository/hytale/")
    
    // VaultUnlocked API repository
    maven("https://repo.codemc.io/repository/creatorfromhell/") {
        name = "VaultUnlocked"
    }
}

dependencies {
    // Hytale Server API (provided by server at runtime)
    val serverVersion = findProperty("serverVersion") as String? ?: "2026.03.26-89796e57b"
    compileOnly("com.hypixel.hytale:Server:$serverVersion")

    compileOnly("at.helpch:placeholderapi-hytale:1.0.4")

    // Luckperms Support
    compileOnly("net.luckperms:api:5.4")
    
    // VaultUnlocked - compileOnly since it's provided by server at runtime
    compileOnly("net.cfh.vault:VaultUnlocked:2.18.3")
    
    // JSON handling
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains:annotations:24.1.0")

    // SQL storage support
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.h2database:h2:2.3.232")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    
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
            "description" to project.description,
            "serverVersion" to (findProperty("serverVersion") as String? ?: "2026.03.26-89796e57b")
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
        relocate("com.zaxxer.hikari", "com.eliteessentials.libs.hikari")
        // Note: H2 is NOT relocated because its internal engine uses class name lookups
        // and service loaders that break when packages are renamed
        relocate("com.mysql", "com.eliteessentials.libs.mysql")
        
        minimize {
            // JDBC drivers are loaded via reflection (Class.forName), so the shadow
            // plugin can't detect usage. Exclude them from minimization.
            exclude(dependency("com.h2database:h2:.*"))
            exclude(dependency("com.mysql:mysql-connector-j:.*"))
        }
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

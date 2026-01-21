import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

plugins {
    id ("java")
    id ("xyz.jpenilla.run-paper") version "2.3.1"
    id ("com.github.johnrengelman.shadow") version "8.1.1"
}

var buildFull = properties["buildFull"].toString() == "true"
var libraries = listOf<String>()
libraries += "net.kyori:adventure-platform-bukkit:4.3.3"

// beta or alpha based on git branch
var suffixes = getAndIncrementVersion()

group = "cn.lunadeer.mc"
version = "1.0.0-$suffixes"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

// utf-8
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}


allprojects {
    apply(plugin = "java")
    apply(plugin = "com.github.johnrengelman.shadow")

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://oss.sonatype.org/content/groups/public")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
    }

    dependencies {
        if (!buildFull) {
            libraries.forEach {
                compileOnly(it)
            }
        } else {
            libraries.forEach {
                implementation(it)
            }
        }
    }

    tasks.processResources {
        outputs.upToDateWhen { false }
        // copy languages folder from PROJECT_DIR/languages to core/src/main/resources
        from(file("${projectDir}/languages")) {
            into("languages")
        }
        // replace @version@ in plugin.yml with project version
        filesMatching("**/plugin.yml") {
            filter {
                it.replace("@version@", rootProject.version.toString())
            }
            if (!buildFull) {
                var libs = "libraries: ["
                libraries.forEach {
                    libs += "$it,"
                }
                filter {
                    it.replace("libraries: [ ]", libs.substring(0, libs.length - 1) + "]")
                }
            }
        }
    }

    tasks.shadowJar {
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())
        dependsOn(tasks.withType<ProcessResources>())
        // add -lite to the end of the file name if BuildLite is true or -full if BuildLite is false
        archiveFileName.set("${project.name}-${project.version}${if (buildFull) "-full" else "-lite"}.jar")
    }
}

dependencies {
    implementation(project(":core"))
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
}

tasks.register("Clean&Build") { // <<<< RUN THIS TASK TO BUILD PLUGIN
    dependsOn(tasks.clean)
    dependsOn(tasks.shadowJar)
}

// Function to get current git branch
fun getCurrentGitBranch(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .directory(projectDir)
            .start()
        process.inputStream.bufferedReader().readText().trim()
    } catch (_: Exception) {
        "unknown"
    }
}

// Function to get and increment version based on branch
fun getAndIncrementVersion(): String {
    val versionFile = file("version.properties")
    val props = Properties()

    // Load existing version or create default
    if (versionFile.exists()) {
        FileInputStream(versionFile).use { props.load(it) }
    }

    val currentBranch = getCurrentGitBranch()
    val versionType = if (currentBranch.startsWith("dev/")) "alpha" else "beta"

    val currentSuffix = props.getProperty("suffixes", if (versionType == "beta") "beta" else "${versionType}.24")

    // Check if we need to switch version type (branch changed)
    val currentType = currentSuffix.split(".")[0]
    if (currentType != versionType) {
        // Branch changed, reset to default for new type
        val newSuffix = if (versionType == "beta") "beta" else "${versionType}.1"
        props.setProperty("suffixes", newSuffix)
        FileOutputStream(versionFile).use {
            props.store(it, "Auto-generated version file - branch: $currentBranch")
        }
        return newSuffix
    }

    // For beta, just return "beta" without incrementing
    if (versionType == "beta") {
        props.setProperty("suffixes", "beta")
        FileOutputStream(versionFile).use {
            props.store(it, "Auto-generated version file - branch: $currentBranch")
        }
        return "beta"
    }

    // For alpha, increment the number
    if (currentSuffix.startsWith("alpha.")) {
        val parts = currentSuffix.split(".")
        if (parts.size >= 2) {
            val currentNumber = parts[1].toIntOrNull() ?: 1
            val newSuffix = "${versionType}.${currentNumber + 1}"

            // Save the new version
            props.setProperty("suffixes", newSuffix)
            FileOutputStream(versionFile).use {
                props.store(it, "Auto-generated version file - branch: $currentBranch")
            }

            return newSuffix
        }
    }

    return currentSuffix
}

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.papermc.hangarpublishplugin.model.Platforms
import java.io.ByteArrayOutputStream
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Copy

plugins {
    `java-library`
    `maven-publish`
    id("io.papermc.hangar-publish-plugin") version "0.1.2"
    id("com.gradleup.shadow") version "8.3.6"
}

repositories {
    mavenLocal()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://oss.sonatype.org/content/groups/public/") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo.maven.apache.org/maven2/") }
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/Lyttle-Development/LyttleUtils")
        credentials {
            username = project.findProperty("GPR_USER") as String? ?: System.getenv("GPR_USER")
            password = project.findProperty("GPR_API_KEY") as String? ?: System.getenv("GPR_API_KEY")
        }

    }
}

dependencies {
    compileOnly(libs.io.papermc.paper.paper.api)
    implementation("com.lyttledev:lyttleutils:1.0.1")
}

group = "com.lyttledev"
version = (property("pluginVersion") as String)
description = "LyttleNametag"
java.sourceCompatibility = JavaVersion.VERSION_21

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    configurations = listOf(project.configurations.runtimeClasspath.get())

    dependencies {
        include(dependency("com.lyttledev:lyttleutils"))
    }
}

tasks.named<Jar>("jar") {
    enabled = false // Disable regular jar to prevent accidental use
}

tasks.named("build") {
    dependsOn("shadowJar")
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

// Define the folders using project.file to ensure paths are resolved correctly
val folderToDelete = project.file("src/main/resources/#defaults")
val sourceFolder = project.file("src/main/resources")
val destinationFolder = project.file("src/main/resources/#defaults")

// Task to delete the folder
val deleteFolder by tasks.registering(Delete::class) {
    delete(folderToDelete)
    doLast {
        println("Deleted folder: $folderToDelete")
    }
}

// Task to copy the contents of sourceFolder into destinationFolder
val copyContents by tasks.registering(Copy::class) {
    dependsOn(deleteFolder)

    // Create the destination folder if it doesn't exist
    doFirst {
        println("Creating destination folder: $destinationFolder")
        destinationFolder.mkdirs()
    }

    from(sourceFolder) {
        // Exclude the destination folder itself to avoid copying it into itself
        exclude("#defaults/**")
        exclude("plugin.yml")
    }
    into(destinationFolder)

    doLast {
        println("Copied contents from $sourceFolder to $destinationFolder")
    }
}

// Ensure that processResources depends on copyContents
tasks.named("processResources") {
    dependsOn(copyContents)
}

// Define the build task to depend on copyContents
tasks.named("build") {
    dependsOn(copyContents)
}

// Helper methods
fun executeGitCommand(vararg command: String): String {
    val byteOut = ByteArrayOutputStream()
    exec {
        commandLine = listOf("git", *command)
        standardOutput = byteOut
    }
    return byteOut.toString(Charsets.UTF_8.name()).trim()
}

fun latestCommitMessage(): String {
    return executeGitCommand("log", "-1", "--pretty=%B")
}

// Add -SNAPSHOT to the version if the channel is not Release
val versionString: String =  if (System.getenv("CHANNEL") == "Release") {
    version.toString()
} else {
    val versionPrefix = if (System.getenv("CHANNEL") == "Snapshot") {
        "SNAPSHOT"
    } else {
        "ALPHA"
    }

    if (System.getenv("GITHUB_RUN_NUMBER") != null) {
        "${version}-${versionPrefix}+${System.getenv("GITHUB_RUN_NUMBER")}"
    } else {
        "$version-${versionPrefix}"
    }
}

tasks.named<ProcessResources>("processResources") {
    filesMatching("plugin.yml") {
        expand("projectVersion" to versionString)
    }
}

// Get the channel from the environment variable or default to Alpha
val envChannel = System.getenv("CHANNEL") ?: "Alpha"

// Get the latest commit message for the changelog
val changelogContent: String = latestCommitMessage()

// Log the version and channel
println("Version: $versionString")
println("Channel: $envChannel")

hangarPublish {
    publications.register("plugin") {
        version.set(versionString)
        channel.set(envChannel)
        changelog.set(changelogContent)
        id.set("LyttleNametag")
        apiKey.set(System.getenv("HANGAR_API_TOKEN"))
        platforms {
            register(Platforms.PAPER) {
                jar.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })

                // Get platform versions from gradle.properties file
                val versions: List<String> = (property("paperVersion") as String)
                    .split(",")
                    .map { it.trim() }
                platformVersions.set(versions)
            }
        }
    }
}

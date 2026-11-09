import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

plugins {
    kotlin("jvm") version "2.3.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    `maven-publish`
}

group = "io.github.team-sneakymouse"

version = providers.exec {
    workingDir(rootDir)
    commandLine("git", "show", "-s", "--format=%ct:%h", "--abbrev=12", "HEAD")
}.standardOutput.asText.map { commit ->
    val (timestamp, hash) = commit.trim().split(":", limit = 2)
    val date = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC)
        .format(Instant.ofEpochSecond(timestamp.toLong()))
    "$date-$hash"
}.get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.sneakyrp.com/releases")
}

/**
 * Keys under paper-plugin.yml `dependencies.server` → required (default true).
 * Expects the template's 2-space indent shape; ignores comments and blank lines.
 */
fun parsePaperServerDependencies(yml: File): Map<String, Boolean> {
    if (!yml.isFile) return emptyMap()
    val lines = yml.readLines()
    fun indentOf(s: String) = s.takeWhile { it == ' ' }.length
    fun skippable(s: String) = s.isBlank() || s.trimStart().startsWith("#")

    var i = 0
    while (i < lines.size) {
        if (!skippable(lines[i]) && lines[i].trim() == "dependencies:") break
        i++
    }
    if (i >= lines.size) return emptyMap()
    val depsIndent = indentOf(lines[i])
    i++

    var serverIndent = -1
    while (i < lines.size) {
        val line = lines[i]
        if (skippable(line)) {
            i++
            continue
        }
        val ind = indentOf(line)
        if (ind <= depsIndent) return emptyMap()
        if (line.trim() == "server:") {
            serverIndent = ind
            i++
            break
        }
        i++
    }
    if (serverIndent < 0) return emptyMap()

    val result = linkedMapOf<String, Boolean>()
    var current: String? = null
    var required = true
    fun flush() {
        current?.let { result[it] = required }
        current = null
        required = true
    }

    val nameIndent = serverIndent + 2
    while (i < lines.size) {
        val line = lines[i]
        if (skippable(line)) {
            i++
            continue
        }
        val ind = indentOf(line)
        if (ind <= serverIndent) {
            flush()
            break
        }
        val trimmed = line.trim()
        if (ind == nameIndent && trimmed.endsWith(":")) {
            flush()
            current = trimmed.removeSuffix(":").trim()
            required = true
        } else if (current != null && trimmed.startsWith("required:")) {
            val value =
                trimmed
                    .substringAfter("required:")
                    .trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
            required = !value.equals("false", ignoreCase = true)
        }
        i++
    }
    flush()
    return result
}

/** Bukkit/Paper plugin id from a jar's paper-plugin.yml or plugin.yml `name:` field. */
fun pluginNameFromJar(jar: File): String? {
    if (!jar.isFile || !jar.name.endsWith(".jar", ignoreCase = true)) return null
    return try {
        ZipFile(jar).use { zip ->
            val entry = zip.getEntry("paper-plugin.yml") ?: zip.getEntry("plugin.yml") ?: return null
            zip.getInputStream(entry).bufferedReader().use { reader ->
                reader
                    .lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("name:") }
                    ?.substringAfter("name:")
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.removeSurrounding("'")
            }
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * compileClasspath jars whose embedded plugin name matches a paper-plugin.yml server dependency.
 * (compileOnly itself is not resolvable in modern Gradle; compileClasspath includes those deps.)
 * Required deps with no match fail; optional deps log and skip.
 */
fun resolveRunServerPluginJars(): List<File> {
    val declared = parsePaperServerDependencies(file("src/main/resources/paper-plugin.yml"))
    if (declared.isEmpty()) return emptyList()

    val byName = linkedMapOf<String, File>()
    for (jar in configurations.compileClasspath.get().files) {
        val name = pluginNameFromJar(jar) ?: continue
        if (name in declared) {
            byName.putIfAbsent(name, jar)
        }
    }

    val missingRequired = declared.filter { (name, required) -> required && name !in byName }.keys
    if (missingRequired.isNotEmpty()) {
        throw GradleException(
            "runServer: required paper-plugin.yml server dependencies have no matching " +
                "compileOnly plugin jar: ${missingRequired.joinToString(", ")}. " +
                "Add compileOnly(\"group:artifact:version\") for each " +
                "(jar must contain paper-plugin.yml / plugin.yml with that name).",
        )
    }
    declared
        .filter { (name, required) -> !required && name !in byName }
        .keys
        .forEach { name ->
            logger.lifecycle(
                "runServer: optional server dependency {} has no matching compileOnly jar; skipping",
                name,
            )
        }
    return declared.keys.mapNotNull { byName[it] }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("io.github.team-sneakymouse:sneakyutils:2026.10.18-657d452bd6f4")
    compileOnly("io.github.team-sneakymouse:sneakyitems:2026.11.08-edc6088f8201")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

kotlin {
    jvmToolchain(25)
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks {
    processResources {
        inputs.property("version", project.version.toString())
        filesMatching("paper-plugin.yml") {
            expand("version" to project.version.toString())
        }
    }

    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }

    // Fat plugin JAR (kotlin-stdlib + gson bundled) — also what Maven publishes.
    jar {
        archiveBaseName.set(rootProject.name)
        archiveFileName.set("${rootProject.name}-${project.version}.jar")
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    runServer {
        minecraftVersion("26.2")
        // Stage every paper-plugin.yml server dep whose compileOnly jar embeds that plugin name.
        pluginJars.from(provider { resolveRunServerPluginJars() })
        // Drop hand-copied / stale jars for those plugin names so only the resolved versions load.
        doFirst {
            val pluginsDir = runDirectory.get().asFile.resolve("plugins")
            if (!pluginsDir.isDirectory) return@doFirst
            val declaredNames =
                parsePaperServerDependencies(file("src/main/resources/paper-plugin.yml")).keys
            if (declaredNames.isEmpty()) return@doFirst
            pluginsDir
                .listFiles()
                ?.filter { it.isFile && it.name.endsWith(".jar", ignoreCase = true) }
                ?.filter { jar -> pluginNameFromJar(jar)?.let { it in declaredNames } == true }
                ?.forEach { stale ->
                    logger.lifecycle("runServer: removing stale {}", stale.name)
                    stale.delete()
                }
        }
    }

    // Focused dry resolve for runServer plugin staging (no server start).
    register("resolveRunServerPlugins") {
        group = "help"
        description = "Print paper-plugin.yml server dependency jars that runServer would stage."
        doLast {
            val jars = resolveRunServerPluginJars()
            if (jars.isEmpty()) {
                logger.lifecycle("runServer: no paper-plugin.yml server dependencies to stage")
                return@doLast
            }
            jars.forEach { jar ->
                logger.lifecycle("runServer: {} <- {}", pluginNameFromJar(jar), jar.name)
            }
        }
    }

    withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xdoclint:none", "-quiet")
        }
        isFailOnError = false
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "sneakybagofholding"
            from(components["java"])
            pom {
                name.set("SneakyBagOfHolding")
                description.set(
                    "Paper plugin for virtual bag-of-holding storage with categories and autopickup."
                )
                url.set("https://github.com/Team-Sneakymouse/SneakyBagOfHolding")
                properties.put("sneakyrp.deployment.type", "paper-plugin")
                licenses {
                    license {
                        name.set("GNU General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("team-sneakymouse")
                        name.set("Team Sneakymouse")
                        url.set("https://github.com/Team-Sneakymouse")
                    }
                }
                scm {
                    url.set("https://github.com/Team-Sneakymouse/SneakyBagOfHolding")
                    connection.set("scm:git:https://github.com/Team-Sneakymouse/SneakyBagOfHolding.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sneakyrp"
            url = uri("https://maven.sneakyrp.com/releases")
            credentials(PasswordCredentials::class)
            authentication {
                create<org.gradle.authentication.http.BasicAuthentication>("basic")
            }
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(tasks.check)
}

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("io.github.team-sneakymouse:magicspells-core:4.0-Beta-18")
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

runPaper {
    disablePluginJarDetection()
}

base {
    // Thin/Maven artifacts use lowercase artifact-style names; the fat plugin JAR is named separately.
    archivesName.set("sneakybagofholding")
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

    // Thin library JAR for Maven consumers (default `jar` task).
    jar {
        archiveClassifier.set("")
    }

    // Fat plugin JAR for the Paper plugins folder (kotlin-stdlib + gson bundled).
    val pluginJar by registering(Jar::class) {
        archiveClassifier.set("plugin")
        archiveFileName.set("${rootProject.name}-${project.version}.jar")
        from(sourceSets["main"].output)
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        dependsOn(classes)
    }

    build {
        dependsOn(pluginJar)
    }

    runServer {
        minecraftVersion("26.2")
        pluginJars.from(pluginJar.flatMap { it.archiveFile })
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
        create<MavenPublication>("library") {
            artifactId = "sneakybagofholding"
            from(components["java"])
            pom {
                name.set("SneakyBagOfHolding")
                description.set(
                    "Paper plugin for virtual bag-of-holding storage with categories and autopickup."
                )
                url.set("https://github.com/Team-Sneakymouse/SneakyBagOfHolding")
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
                // Published POM should not pull in compileOnly Paper/MagicSpells for consumers.
                withXml {
                    val root = asNode()
                    val dependenciesNode = root.get("dependencies") as? groovy.util.NodeList ?: return@withXml
                    if (dependenciesNode.isEmpty()) {
                        return@withXml
                    }
                    val depsContainer = dependenciesNode[0] as groovy.util.Node
                    val allowedGroupPrefixes = listOf(
                        "io.github.team-sneakymouse",
                        "org.jetbrains",
                        "com.google.code.gson",
                        "com.google.gson",
                    )
                    val dependencyNodes =
                        (depsContainer.get("dependency") as? groovy.util.NodeList)?.toList().orEmpty()
                    val toRemove = dependencyNodes.mapNotNull { depObj ->
                        val dep = depObj as groovy.util.Node
                        fun childText(name: String): String =
                            ((dep.get(name) as? groovy.util.NodeList)?.firstOrNull() as? groovy.util.Node)
                                ?.text()
                                .orEmpty()
                        val groupId = childText("groupId")
                        val allowed = allowedGroupPrefixes.any { groupId.startsWith(it) }
                        if (!allowed) dep else null
                    }
                    toRemove.forEach { depsContainer.remove(it) }
                    val remaining = depsContainer.get("dependency") as? groovy.util.NodeList
                    if (remaining == null || remaining.isEmpty()) {
                        root.remove(depsContainer)
                    }
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

import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.plugins.signing.SigningExtension

plugins {
    kotlin("jvm") version "1.9.21"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    // 0.34+ uses Central Portal only (OSSRH removed); 0.36+ requires Kotlin 2.2
    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = findProperty("mavenCentralGroup")?.toString() ?: "io.github.team-sneakymouse"
version = findProperty("version")?.toString() ?: "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.github.team-sneakymouse:magicspells-core:4.0-Beta-14")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.21")
    implementation("com.google.code.gson:gson:2.10.1")
}

kotlin {
    jvmToolchain(21)
}

base {
    archivesName.set("sneakybagofholding")
}

tasks {
    processResources {
        filesMatching("paper-plugin.yml") {
            expand("version" to project.version)
        }
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "21"
        }
    }

    // Thin library JAR for Maven Central (default `jar` task).
    jar {
        archiveClassifier.set("")
    }

    // Fat plugin JAR for the Paper plugins folder (kotlin-stdlib + gson bundled).
    val pluginJar by registering(Jar::class) {
        archiveClassifier.set("plugin")
        archiveFileName.set("SneakyBagOfHolding.jar")
        from(sourceSets["main"].output)
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        dependsOn(classes)
    }

    build {
        dependsOn(pluginJar)
    }

    runServer {
        minecraftVersion("1.21.4")
    }

    withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xdoclint:none", "-quiet")
        }
        isFailOnError = false
    }
}

// flatDir/plugin JARs can produce null group in Gradle metadata; POM is filtered below.
tasks.withType<org.gradle.api.publish.tasks.GenerateModuleMetadata>().configureEach {
    enabled = false
}

gradle.taskGraph.whenReady {
    val publishingToCentral = allTasks.any { task ->
        task.name.endsWith("publishToMavenCentral") ||
            task.name.endsWith("publishAndReleaseToMavenCentral") ||
            task.name.contains("publishAllPublicationsToMavenCentral") ||
            task.name.contains("publishMavenPublicationToMavenCentral")
    }
    if (!publishingToCentral) {
        return@whenReady
    }

    val missing = mutableListOf<String>()
    if (findProperty("mavenCentralUsername") == null) {
        missing += "mavenCentralUsername"
    }
    if (findProperty("mavenCentralPassword") == null) {
        missing += "mavenCentralPassword"
    }
    val useGpgCmd = findProperty("signing.gnupg.useGpgCmd")?.toString() != "false"
    val hasInMemoryKey = findProperty("signing.inMemoryKey") != null

    if (findProperty("signing.keyId") == null && !hasInMemoryKey) {
        missing += "signing.keyId (or signing.inMemoryKey)"
    }
    if (findProperty("signing.password") == null) {
        missing += "signing.password"
    }
    if (!useGpgCmd && !hasInMemoryKey && findProperty("signing.secretKeyRingFile") == null) {
        missing += "signing.secretKeyRingFile (or set signing.gnupg.useGpgCmd=true)"
    }
    if (useGpgCmd && !hasInMemoryKey) {
        val gpgKeyName = findProperty("signing.gnupg.keyName") ?: findProperty("signing.keyId")
        if (gpgKeyName == null) {
            missing += "signing.gnupg.keyName (or signing.keyId; required with signing.gnupg.useGpgCmd=true)"
        }
    }

    if (missing.isNotEmpty()) {
        throw GradleException(
            "Maven Central publish is not configured. Add these properties to .gradle/gradle.properties " +
                "(in this repo, gitignored) or ~/.gradle/gradle.properties (see gradle.properties.example):\n  - " +
                missing.joinToString("\n  - ")
        )
    }
}

plugins.withId("signing") {
    val signingKeyId = findProperty("signing.keyId")?.toString()
    val gpgKeyName = findProperty("signing.gnupg.keyName")?.toString() ?: signingKeyId
    if (gpgKeyName != null) {
        extensions.extraProperties.set("signing.gnupg.keyName", gpgKeyName)
    }
    extensions.configure<SigningExtension> {
        val inMemoryKey = findProperty("signing.inMemoryKey")
        if (inMemoryKey != null) {
            val normalizedKey = inMemoryKey.toString().replace("\\n", "\n")
            useInMemoryPgpKeys(normalizedKey, findProperty("signing.password")?.toString())
        } else if (signingKeyId != null || gpgKeyName != null) {
            val gpgCmdEnabled = findProperty("signing.gnupg.useGpgCmd")?.toString() != "false"
            if (gpgCmdEnabled) {
                useGpgCmd()
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral(true)
    if (findProperty("signing.keyId") != null || findProperty("signing.inMemoryKey") != null) {
        signAllPublications()
    }

    coordinates(
        project.group.toString(),
        "sneakybagofholding",
        project.version.toString()
    )

    pom {
        name.set(findProperty("POM_NAME")?.toString() ?: "SneakyBagOfHolding")
        description.set(
            findProperty("POM_DESCRIPTION")?.toString()
                ?: "Paper plugin for virtual bag-of-holding storage with categories and autopickup."
        )
        inceptionYear.set(findProperty("POM_INCEPTION_YEAR")?.toString() ?: "2025")
        url.set(
            findProperty("POM_URL")?.toString()
                ?: "https://github.com/Team-Sneakymouse/SneakyBagOfHolding"
        )

        licenses {
            license {
                name.set(findProperty("POM_LICENSE_NAME")?.toString() ?: "GNU General Public License v3.0")
                url.set(findProperty("POM_LICENSE_URL")?.toString() ?: "https://www.gnu.org/licenses/gpl-3.0.txt")
            }
        }

        developers {
            developer {
                id.set(findProperty("POM_DEVELOPER_ID")?.toString() ?: "team-sneakymouse")
                name.set(findProperty("POM_DEVELOPER_NAME")?.toString() ?: "Team Sneakymouse")
                url.set(findProperty("POM_DEVELOPER_URL")?.toString() ?: "https://github.com/Team-Sneakymouse")
            }
        }

        scm {
            url.set(
                findProperty("POM_SCM_URL")?.toString()
                    ?: "https://github.com/Team-Sneakymouse/SneakyBagOfHolding"
            )
            connection.set(
                findProperty("POM_SCM_CONNECTION")?.toString()
                    ?: "scm:git:git@github.com:Team-Sneakymouse/SneakyBagOfHolding.git"
            )
            developerConnection.set(
                findProperty("POM_SCM_DEVELOPER_CONNECTION")?.toString()
                    ?: "scm:git:git@github.com:Team-Sneakymouse/SneakyBagOfHolding.git"
            )
        }

        // Maven Central rejects SNAPSHOT deps; published API JARs must not pull in plugin-only libraries.
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
                "com.google.gson"
            )
            val dependencyNodes = (depsContainer.get("dependency") as? groovy.util.NodeList)?.toList().orEmpty()
            val toRemove = dependencyNodes.mapNotNull { depObj ->
                val dep = depObj as groovy.util.Node
                fun childText(name: String): String =
                    ((dep.get(name) as? groovy.util.NodeList)?.firstOrNull() as? groovy.util.Node)?.text().orEmpty()
                val versionText = childText("version")
                val groupId = childText("groupId")
                val allowed = allowedGroupPrefixes.any { groupId.startsWith(it) }
                if (!allowed || versionText.endsWith("-SNAPSHOT")) dep else null
            }
            toRemove.forEach { depsContainer.remove(it) }
            val remaining = depsContainer.get("dependency") as? groovy.util.NodeList
            if (remaining == null || remaining.isEmpty()) {
                root.remove(depsContainer)
            }
        }
    }
}

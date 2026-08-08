rootProject.name = "SneakyBagOfHolding"

val localGradleProperties = java.util.Properties().also { properties ->
    val localGradlePropertiesFile = file(".gradle/gradle.properties")
    if (localGradlePropertiesFile.isFile) {
        localGradlePropertiesFile.inputStream().use { properties.load(it) }
    }
}

// Expose on each Project for findProperty() (signing.*, preflight in build.gradle.kts).
// Repo-local signing.* and mavenCentral* always win over ~/.gradle/gradle.properties.
gradle.beforeProject {
    localGradleProperties.forEach { (key, value) ->
        val name = key.toString()
        if (name.startsWith("signing.") || name.startsWith("mavenCentral")) {
            extensions.extraProperties.set(name, value.toString())
        } else if (findProperty(name) == null) {
            extensions.extraProperties.set(name, value.toString())
        }
    }
    // useGpgCmd() reads signing.gnupg.keyName, not signing.keyId; without keyName gpg uses its default key.
    val useGpgCmd = findProperty("signing.gnupg.useGpgCmd")?.toString() != "false"
    if (useGpgCmd && findProperty("signing.gnupg.keyName") == null) {
        findProperty("signing.keyId")?.toString()?.let { keyId ->
            extensions.extraProperties.set("signing.gnupg.keyName", keyId)
        }
    }
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

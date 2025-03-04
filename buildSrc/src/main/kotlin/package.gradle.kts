import java.util.regex.Pattern

plugins {
    java
    id("com.github.johnrengelman.shadow")
}

// fuck 'shadow' config usage because of adding of manifest attribute 'Class-Path'
// ... and this feature cannot be disabled, of course
configurations.create("packaged")

configurations.implementation {
    extendsFrom(configurations["packaged"])
}

tasks.shadowJar {
    archiveAppendix = project.name
    archiveBaseName = "aswm"
    archiveClassifier = ""
    archiveVersion = ""

    configurations = listOf(project.configurations["packaged"])
    destinationDirectory = rootProject.layout.projectDirectory.dir("build")

    isEnableRelocation = true
    relocationPrefix = "com.grinderwolf.swm.internal"

    exclude("**/*.SF")
    exclude("**/*.DSA")
    exclude("META-INF/maven/**")

    // strip off useless Zstd natives
    val zstdNativesPattern = Pattern.compile("^(?<os>[^/]+)/(?<arch>[^/]+)/libzstd-.+\\.(?:so|dll)\$")
    exclude {
        if (it.name.contains("libzstd")) {
            val matcher = zstdNativesPattern.matcher(it.path)
            if (matcher.matches()) {
                val os = matcher.group("os")
                if (os !in listOf("darwin", "linux", "win"))
                    return@exclude true

                val arch = matcher.group("arch")
                return@exclude when (os) {
                    "linux" -> arch != "aarch64" && arch != "amd64"
                    "win" -> arch == "x86"
                    else -> false
                }
            }
        }
        return@exclude false
    }
}

tasks.jar {
    finalizedBy(tasks.shadowJar)
}

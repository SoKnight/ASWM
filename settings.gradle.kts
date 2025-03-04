import java.nio.file.Files
import java.nio.file.Path

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "aswm"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

val rootDir: Path = file(".").toPath()
Files.list(file("modules").toPath())
    .filter(Files::isDirectory)
    .filter { dir -> Files.isRegularFile(dir.resolve("build.gradle.kts")) }
    .forEach { dir -> include(":${rootDir.relativize(dir).toString().replace(File.separatorChar, ':')}") }

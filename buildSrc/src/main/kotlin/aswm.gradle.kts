import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
}

project.group = rootProject.group
project.version = rootProject.version

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.glaremasters.me/repository/concuncan/")
    mavenLocal()
}

afterEvaluate {
    dependencies {
        versionCatalogs.named("libs").findLibrary("lombok").ifPresent {
            compileOnly(it)
            annotationProcessor(it)
        }
    }
}

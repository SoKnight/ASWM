plugins {
    `package`
}

dependencies {
    packaged(projects.modules.platformNms) {
        exclude(module = "classmodifier")
    }

    compileOnly(libs.paperApi)

    packaged(libs.hikariCP)
    packaged(libs.mongoDriver)
    runtimeOnly(libs.mysqlDriver)
    packaged(libs.lettuceCore)

    packaged(libs.commonsIo)
    packaged(libs.configurateYaml)
    packaged(libs.zstdJni)
}

tasks.processResources {
    inputs.property("project.version", rootProject.version)

    filesMatching(listOf("plugin.yml")) {
        expand(project.properties)
    }
}

tasks.shadowJar {
    relocate("org.bstats", "com.grinderwolf.swm.internal.bstats")
    relocate("ninja.leaping.configurate", "com.grinderwolf.swm.internal.configurate")
    relocate("com.flowpowered.nbt", "com.grinderwolf.swm.internal.nbt")
    relocate("com.zaxxer.hikari", "com.grinderwolf.swm.internal.hikari")
    relocate("com.mongodb", "com.grinderwolf.swm.internal.mongodb")
    relocate("io.lettuce", "com.grinderwolf.swm.internal.lettuce")
    relocate("org.bson", "com.grinderwolf.swm.internal.bson")
}

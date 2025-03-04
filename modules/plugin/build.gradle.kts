plugins {
    `package`
}

dependencies {
    packaged(projects.modules.platformNms)

    compileOnly(libs.paperApi)

    packaged(libs.hikariCP)
    packaged(libs.mongoDriver)
    runtimeOnly(libs.mysqlDriver)

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

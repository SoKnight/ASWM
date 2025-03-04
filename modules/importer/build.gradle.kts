plugins {
    `package`
}

dependencies {
    packaged(projects.modules.api)
    packaged(projects.modules.platformCore)

    packaged(libs.chalk)
    packaged(libs.zstdJni)
}

tasks.jar {
    manifest {
        attributes(mapOf(
            "Main-Class" to "com.grinderwolf.swm.importer.SWMImporter",
        ))
    }
}

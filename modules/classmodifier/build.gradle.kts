plugins {
    `java-library`
    `package`
}

dependencies {
    compileOnlyApi(libs.paperApi)
    compileOnly(libs.paper) // required to be installed in Maven local repo

    packaged(libs.javaAssist)
    packaged(libs.snakeYaml)
}

tasks.jar {
    manifest {
        attributes(mapOf(
            "Premain-Class" to "com.grinderwolf.swm.clsm.NMSTransformer"
        ))
    }
}

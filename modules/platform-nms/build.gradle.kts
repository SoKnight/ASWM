plugins {
    `java-library`
}

dependencies {
    api(projects.modules.api)
    compileOnlyApi(projects.modules.classmodifier)
    api(projects.modules.platformCore)

    compileOnlyApi(libs.paperApi)
    compileOnly(libs.paper) // required to be installed in Maven local repo
}
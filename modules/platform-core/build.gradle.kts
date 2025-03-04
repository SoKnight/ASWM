plugins {
    `java-library`
}

dependencies {
    api(projects.modules.api)

    compileOnlyApi(libs.paperApi)

    implementation(libs.zstdJni)
}
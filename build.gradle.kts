plugins {
    base
}

group = "com.grinderwolf"
version = "2.6.2+starmc.3"

subprojects {
    if (project.path.startsWith(":modules:")) {
        apply(plugin = "aswm")
    }
}

plugins {
    base
}

group = "com.grinderwolf"
version = "2.6.2+starmc.1"

subprojects {
    if (project.path.startsWith(":modules:")) {
        apply(plugin = "aswm")
    }
}

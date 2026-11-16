pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://teavm.org/maven/repository/")
    }
}

rootProject.name = "grid-of-shadows"

include("common", "server", "client", "lwjgl3", "teavm")

plugins {
    application
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    implementation(project(":client"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.14.2")
    implementation("com.badlogicgames.gdx:gdx-platform:1.14.2:natives-desktop")
}

application {
    mainClass.set("com.gridofshadows.desktop.DesktopLauncher")
    // macOS exige que GLFW corra en el primer hilo
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
    }
}

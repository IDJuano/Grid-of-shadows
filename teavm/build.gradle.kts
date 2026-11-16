plugins {
    `java-library`
    id("com.github.xpenatan.gdx-teavm") version "1.6.1"   // libGDX 1.14.2 + TeaVM 0.15.0
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.compilerArgs.add("-XDstringConcat=inline")
}

dependencies {
    implementation(project(":client"))
    implementation("com.badlogicgames.gdx:gdx:1.14.2")
    implementation("org.teavm:teavm-jso-apis:0.15.0")      // WebSocket y arrays tipados del navegador
}

// API de gdx-teavm 1.6.1: todo va dentro de js { } y con .set(...)
//   Desarrollo:   ./gradlew :teavm:gdx_teavm_web_js_run    ->  http://localhost:8000
//   Publicación:  ./gradlew :teavm:gdx_teavm_web_js_build  ->  teavm/build/dist/js/webapp
gdxTeaVM {
    js {
        mainClass.set("com.gridofshadows.web.WebLauncher")
        htmlTitle.set("Grid of Shadows")
        htmlWidth.set(1280)
        htmlHeight.set(720)
        serverPort.set(8000)   // el 8080 es del servidor de juego
    }
}

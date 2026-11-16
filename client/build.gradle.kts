plugins {
    `java-library`
}

// El cliente se compila a JavaScript con TeaVM: bytecode Java 17, sin invokedynamic en strings.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.compilerArgs.add("-XDstringConcat=inline")
}

dependencies {
    api(project(":common"))
    api("com.badlogicgames.gdx:gdx:1.14.2")
}

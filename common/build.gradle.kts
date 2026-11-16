plugins {
    `java-library`
}

// Compartido por el servidor (JVM) y el cliente (TeaVM -> JS): bytecode Java 17
// y sin concatenación de strings por invokedynamic.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.compilerArgs.add("-XDstringConcat=inline")
}

// Pruebas sin dependencias externas:  ./gradlew :common:selfTest
tasks.register<JavaExec>("selfTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.gridofshadows.common.SelfTest")
}
tasks.named("check") { dependsOn("selfTest") }

plugins {
    application
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    implementation(project(":common"))
    implementation("io.netty:netty-codec-http:4.1.139.Final")
}

application {
    mainClass.set("com.gridofshadows.server.ServerMain")
}

// Pruebas de la simulación sin dependencias:  ./gradlew :server:selfTest
tasks.register<JavaExec>("selfTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.gridofshadows.server.ServerSelfTest")
}
tasks.named("check") { dependsOn("selfTest") }

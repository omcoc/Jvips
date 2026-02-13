plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta9"
}

group = "br.com.julio.jvips"
version = "1.3.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    flatDir { dirs("libs") }
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

tasks.named("shadowJar") {
    this as org.gradle.jvm.tasks.Jar
    archiveBaseName.set("Jvips")
    archiveClassifier.set("") // gera Jvips-1.0.0.jar direto
}

tasks.named("build") {
    dependsOn("shadowJar")
}

tasks.named<org.gradle.jvm.tasks.Jar>("jar") {
    archiveBaseName.set("Jvips")
}

plugins {
    kotlin("jvm") version "2.2.20"
    application
    id("org.graalvm.buildtools.native") version "0.11.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("plugin.serialization") version "2.2.21"
}

group = "com.github.shieru_lab"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    implementation("com.github.ajalt.clikt:clikt-markdown:5.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.ktor:ktor-client-core:3.3.2")
    implementation("io.ktor:ktor-client-cio:3.3.2")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

application {
    mainClass = "com.github.shieru_lab.MainKt"
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

graalvmNative {
    binaries {
        getByName("main") {
            javaLauncher.set(project.javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            })
            buildArgs.add("--initialize-at-build-time")
        }
    }
}
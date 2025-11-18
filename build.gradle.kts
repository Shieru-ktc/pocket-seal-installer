plugins {
    kotlin("jvm") version "2.2.20"
    application
    id("org.graalvm.buildtools.native") version "0.11.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
        }
    }
}
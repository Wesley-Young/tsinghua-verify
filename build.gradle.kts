plugins {
    val kotlinVersion = "1.9.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("net.mamoe.mirai-console") version "2.15.0"
}

group = "pub.gdt.verify"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.sun.mail:jakarta.mail:2.0.1")
}

mirai {
    jvmTarget = JavaVersion.VERSION_1_8
}

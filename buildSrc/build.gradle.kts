plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral {
        url = uri("https://maven.myket.ir")
    }
    maven {
        url = uri("https://maven.myket.ir")
    }
}

dependencies {
    // NOTE: Keep this list ordered alphabetically
    implementation("com.bmuschko:gradle-docker-plugin:6.7.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("com.github.johnrengelman:shadow:8.1.1")
    implementation("com.google.cloud.tools:jib-gradle-plugin:3.4.0")
    implementation("org.ajoberstar.grgit:grgit-core:5.0.0-rc.3")
}
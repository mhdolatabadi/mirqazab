plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.jpa") version "1.9.22"
    application
    id("java-docker-convention")
}

group = "ir.mhdolatabadi"
version = getGitTag().ifEmpty { "1.0-SNAPSHOT" }

dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.ibm.icu:icu4j:74.2")
    implementation("ir.huri:JalaliCalendar:1.3.2")

    // Hibernate & JPA
    implementation("org.hibernate.orm:hibernate-core:6.4.4.Final")
    implementation("org.hibernate.orm:hibernate-hikaricp:6.4.4.Final")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")

    // PostgreSQL
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.flywaydb:flyway-core:10.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.10.0")

    implementation("jakarta.xml.bind:jakarta.xml.bind-api:3.0.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:3.0.2")

    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.8")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Connection Pool
    implementation("com.zaxxer:HikariCP:5.1.0")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.7.3") // برای JSONB

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass.set("ir.mhdolatabadi.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

jib {
    from {
        image = "docker.arvancloud.ir/eclipse-temurin:17-jre"
        platforms {
            platform {
                // Build for whatever machine runs `jibDockerBuild`, so the image
                // is always native to the Docker daemon that will run it.
                architecture = when (System.getProperty("os.arch")) {
                    "aarch64", "arm64" -> "arm64"
                    else -> "amd64"
                }
                os = "linux"
            }
        }
    }
    to {
        image = "dailybot"
        tags = setOf(version.toString(), "latest", getGitCommitShort())
    }
    container {
        mainClass = "ir.mhdolatabadi.MainKt"
        jvmFlags = listOf(
            "-Xms256m",
            "-Xmx512m",
            "-Dfile.encoding=UTF-8"
        )
        environment = mapOf(
            "JAVA_TOOL_OPTIONS" to "-Dfile.encoding=UTF-8"
        )
    }
}

fun getGitCommitShort(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

fun getGitTag(): String {
    return try {
        val process = ProcessBuilder("git", "describe", "--tags", "--exact-match")
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0) output else ""
    } catch (e: Exception) {
        ""
    }
}


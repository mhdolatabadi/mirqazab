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
    implementation("org.telegram:telegrambots:6.9.0")
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
                architecture = "amd64"
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

// Deploy tasks
// Deploy tasks - نسخه اصلاح شده برای پشتیبانی از دیتابیس و docker compose

tasks.register("publishRaspberryPi") {
    group = "deployment"
    description = "Build and package Docker image for Raspberry Pi (ARM64)"

    dependsOn("jibDockerBuild")

    doLast {
        val outputFile = file("build/dailybot-arm64.tar.gz")
        outputFile.parentFile.mkdirs()

        println("📦 Saving Docker image to tar.gz...")
        exec {
            commandLine("sh", "-c", "docker save dailybot:latest | gzip > ${outputFile.absolutePath}")
        }

        println("✅ Image ready: ${outputFile.absolutePath}")
        println("   Size: ${outputFile.length() / 1024 / 1024} MB")
    }
}

tasks.register("deployToRaspberryPi") {
    group = "deployment"
    description = "Build, package and transfer bot image, docker-compose and env file to Raspberry Pi"

    dependsOn("publishRaspberryPi")

    doLast {
        val raspberryHost = project.findProperty("raspberry.host") as? String ?: "mohammadhossein@192.168.220.221"
        val raspberryPath = project.findProperty("raspberry.path") as? String ?: "~/dailybot"
        val imageFile = file("build/dailybot-arm64.tar.gz")
        val composeFile = file("docker-compose.yml")
        val envExampleFile = file(".env.example")

        println("📤 Transferring files to $raspberryHost:$raspberryPath...")
        exec {
            commandLine("ssh", raspberryHost, "mkdir", "-p", raspberryPath)
        }

        // انتقال فایل image
        exec {
            commandLine("scp", imageFile.absolutePath, "$raspberryHost:$raspberryPath/")
        }

        // انتقال docker-compose.yml در صورت وجود
        if (composeFile.exists()) {
            exec {
                commandLine("scp", composeFile.absolutePath, "$raspberryHost:$raspberryPath/")
            }
        } else {
            println("⚠️ docker-compose.yml not found in project root, skipping...")
        }

        // انتقال .env.example در صورت وجود
        if (envExampleFile.exists()) {
            exec {
                commandLine("scp", envExampleFile.absolutePath, "$raspberryHost:$raspberryPath/")
            }
            // ایجاد .env از روی مثال (اگر قبلاً وجود نداشته باشد)
            exec {
                commandLine("ssh", raspberryHost, "cd $raspberryPath && if [ ! -f .env ]; then cp .env.example .env; fi")
            }
            println("✅ .env file prepared (from .env.example)")
        } else {
            println("⚠️ .env.example not found, please create .env manually on the remote.")
        }

        println("✅ Files transferred successfully!")
        println("🚀 To start services on Raspberry Pi, run: ./gradlew remoteStart")
    }
}

tasks.register("remoteStart") {
    group = "deployment"
    description = "Load the bot image and start all services using docker-compose on Raspberry Pi"

    doLast {
        val raspberryHost = project.findProperty("raspberry.host") as? String ?: "mohammadhossein@192.168.220.221"
        val raspberryPath = project.findProperty("raspberry.path") as? String ?: "~/dailybot"

        println("🚀 Starting services on $raspberryHost using docker-compose...")

        exec {
            commandLine("ssh", raspberryHost, "cd $raspberryPath && docker load < dailybot-arm64.tar.gz || true")
        }

        exec {
            commandLine("ssh", raspberryHost, "cd $raspberryPath && docker compose up -d")
        }

        println("✅ Services started!")
        println("📊 Check logs: ssh $raspberryHost 'cd $raspberryPath && docker compose logs -f'")
    }
}

tasks.register("remoteStop") {
    group = "deployment"
    description = "Stop all services using docker-compose on Raspberry Pi"

    doLast {
        val raspberryHost = project.findProperty("raspberry.host") as? String ?: "mohammadhossein@192.168.220.221"
        val raspberryPath = project.findProperty("raspberry.path") as? String ?: "~/dailybot"

        println("🛑 Stopping services on $raspberryHost...")
        exec {
            commandLine("ssh", raspberryHost, "cd $raspberryPath && docker compose down")
        }
        println("✅ Services stopped!")
    }
}

tasks.register("remoteRestart") {
    group = "deployment"
    description = "Restart all services on Raspberry Pi (stop, transfer latest, start)"

    dependsOn("remoteStop", "deployToRaspberryPi", "remoteStart")
    doLast {
        println("✅ Restart cycle completed.")
    }
}
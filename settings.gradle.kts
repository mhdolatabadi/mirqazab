pluginManagement {
    repositories {
        maven { url = uri("https://maven.myket.ir") }

    }
    plugins {
        kotlin("jvm") version "1.9.22"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {

        maven {
            url = uri("https://maven.myket.ir")
        }
    }
}
rootProject.name = "dailybot"


plugins {
    id("com.google.cloud.tools.jib")
}

jib {
    val javaBaseDocker: String by project
    from {
        image = javaBaseDocker
    }
    to {
        image = "${project.name}:${project.version}"
    }
}

tasks.named("jibDockerBuild") {
    doFirst {
        println("Building Docker image: ${project.name}:${project.version}")
    }
}

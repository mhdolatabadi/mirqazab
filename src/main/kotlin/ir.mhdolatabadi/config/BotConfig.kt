package ir.mhdolatabadi.config

import java.io.FileInputStream
import java.util.Properties

object BotConfig {
    private val properties = Properties()

    init {
        try {
            // Try to load from resources
            val inputStream = javaClass.classLoader.getResourceAsStream("application.properties")
            if (inputStream != null) {
                properties.load(inputStream)
            } else {
                // Fallback to file system
                properties.load(FileInputStream("application.properties"))
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load application.properties", e)
        }
    }

    val matrixHomeserverUrl: String
        get() = System.getenv("MATRIX_HOMESERVER_URL")
            ?: properties.getProperty("matrix.homeserver.url")
            ?: throw IllegalStateException("MATRIX_HOMESERVER_URL not found in environment or properties")

    val matrixAccessToken: String
        get() = System.getenv("MATRIX_ACCESS_TOKEN")
            ?: properties.getProperty("matrix.access.token")
            ?: throw IllegalStateException("MATRIX_ACCESS_TOKEN not found in environment or properties")

    val dbUrl: String
        get() = properties.getProperty("db.url", "")

    val dbUser: String
        get() = properties.getProperty("db.user", "")

    val dbPassword: String
        get() = properties.getProperty("db.password", "")
}

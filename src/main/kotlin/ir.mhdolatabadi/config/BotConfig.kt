package ir.mhdolatabadi.config

import java.io.File
import java.util.Properties

/**
 * Config resolution order for every setting: environment variable, then
 * application.properties (optional - fine if it's absent, e.g. when running
 * purely off a docker-compose .env file), then a hardcoded default if any.
 */
object BotConfig {
    private val properties = Properties()

    init {
        val inputStream = javaClass.classLoader.getResourceAsStream("application.properties")
        if (inputStream != null) {
            properties.load(inputStream)
        } else {
            val file = File("application.properties")
            if (file.exists()) {
                file.inputStream().use { properties.load(it) }
            }
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
        get() = System.getenv("DB_URL")
            ?: properties.getProperty("db.url", "jdbc:postgresql://postgres:5432/dailybot")

    val dbUser: String
        get() = System.getenv("DB_USER")
            ?: properties.getProperty("db.user", "postgres")

    val dbPassword: String
        get() = System.getenv("DB_PASSWORD")
            ?: properties.getProperty("db.password", "postgres")
}

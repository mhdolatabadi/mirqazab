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

    val botToken: String
        get() = System.getenv("BOT_TOKEN")
            ?: properties.getProperty("bot.token")
            ?: throw IllegalStateException("BOT_TOKEN not found in environment or properties")

    val botUsername: String
        get() = properties.getProperty("bot.username", "moseinbot")

    val baseUrl: String
        get() = properties.getProperty("bot.base.url", "https://tapi.bale.ai/")

    val blockedUserId: Long
        get() = properties.getProperty("bot.blocked.user.id", "0").toLong()

    val dbUrl: String
        get() = properties.getProperty("db.url", "")

    val dbUser: String
        get() = properties.getProperty("db.user", "")

    val dbPassword: String
        get() = properties.getProperty("db.password", "")
}

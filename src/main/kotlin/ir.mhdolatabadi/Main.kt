package ir.mhdolatabadi

import ir.mhdolatabadi.config.BotConfig
import jakarta.persistence.Persistence
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

fun main() {
    val botOptions = DefaultBotOptions().apply {
        baseUrl = BotConfig.baseUrl
    }
    val properties = mapOf(
        "jakarta.persistence.jdbc.url" to BotConfig.dbUrl,
        "jakarta.persistence.jdbc.user" to BotConfig.dbUser,
        "jakarta.persistence.jdbc.password" to BotConfig.dbPassword,
        "jakarta.persistence.jdbc.driver" to "org.postgresql.Driver",
        "hibernate.hbm2ddl.auto" to "update",
        "hibernate.show_sql" to "false",
        "hibernate.format_sql" to "true",
        "hibernate.hikari.maximumPoolSize" to "10",
        "hibernate.hikari.minimumIdle" to "2"
    )
    val emf = Persistence.createEntityManagerFactory("dailybot-pu", properties)
    val bot = DailyBot( BotConfig.botToken, BotConfig.botUsername, botOptions, emf )

    // Register bot
    val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
    botsApi.registerBot(bot)

    println("✅ Bot started successfully with database persistence")

    // Graceful shutdown
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down...")
    })
}
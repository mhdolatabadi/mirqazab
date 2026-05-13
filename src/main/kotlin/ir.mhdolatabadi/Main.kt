package ir.mhdolatabadi

import DailyBot
import ir.mhdolatabadi.config.BotConfig
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

fun main() {
    val botOptions = DefaultBotOptions().apply {
        baseUrl = BotConfig.baseUrl
    }
    // Initialize bot
    val bot = DailyBot( BotConfig.botToken, BotConfig.botUsername, botOptions )

    // Register bot
    val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
    botsApi.registerBot(bot)

    println("✅ Bot started successfully with database persistence")

    // Graceful shutdown
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down...")
    })
}
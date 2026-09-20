package ir.mhdolatabadi

import ir.mhdolatabadi.config.BotConfig
import ir.mhdolatabadi.matrix.MatrixClient
import jakarta.persistence.Persistence
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ir.mhdolatabadi.Main")

fun main() {
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

    val client = MatrixClient(BotConfig.matrixHomeserverUrl, BotConfig.matrixAccessToken)
    val bot = DailyBot(client, emf)
    bot.startBot()

    log.info("Bot started successfully with database persistence")

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down...")
    })

    // start() only launches background coroutines on daemon threads - without
    // something blocking here, the JVM would exit immediately after this point.
    runBlocking { awaitCancellation() }
}

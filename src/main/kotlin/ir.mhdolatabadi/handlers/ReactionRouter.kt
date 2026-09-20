package ir.mhdolatabadi.handlers

import ir.mhdolatabadi.matrix.MatrixLongPollingBot
import ir.mhdolatabadi.services.ActivityService
import ir.mhdolatabadi.services.AttendanceService
import ir.mhdolatabadi.services.MemberService
import ir.mhdolatabadi.services.ReportService
import ir.mhdolatabadi.utils.DateUtils
import java.time.LocalDate

/**
 * Matrix has no inline-keyboard/callback-query concept, so every action that used
 * to be a button is now a reaction key on a message. Routes each incoming reaction
 * to whichever per-flow handler owns the message it landed on - decided by which
 * handler recognizes the event id, not by a self-describing callback data string
 * like Telegram had.
 */
class ReactionRouter(
    bot: MatrixLongPollingBot,
    private val sessionManager: SessionManager,
    activityService: ActivityService,
    reportService: ReportService,
    memberService: MemberService,
    private val attendanceService: AttendanceService
) {
    private val activityHandler: ActivityQuestionHandler = ActivityQuestionHandler(bot, activityService, memberService)

    private val candidateHandler: CandidateSelectionHandler = CandidateSelectionHandler(bot, memberService) { chatId, eventId ->
        mainMenuHandler.showMainMenuOnExistingMessage(chatId, eventId)
    }

    private val mainMenuHandler: MainMenuHandler = MainMenuHandler(
        bot, attendanceService, reportService,
        onOpenCandidateSelection = { chatId -> candidateHandler.showCandidateSelection(chatId) },
        onStartSession = { chatId, senderId -> sessionManager.startSession(chatId, senderId) },
        onResetAttendance = { chatId ->
            attendanceService.resetTodayAttendance(chatId, DateUtils.today())
            sessionManager.resetSession(chatId)
        }
    )

    fun buildMainMenuText(chatId: String): String = mainMenuHandler.buildMainMenuText(chatId)

    fun mainMenuKeys(): List<String> = mainMenuHandler.mainMenuKeys()

    fun sendActivityMessage(chatId: String, date: LocalDate = DateUtils.today()): String =
        activityHandler.sendActivityMessage(chatId, date)

    fun handleReaction(chatId: String, senderId: String, eventId: String, key: String) {
        when {
            candidateHandler.isCandidateMessage(chatId, eventId) -> candidateHandler.handleReaction(chatId, eventId, key)
            activityHandler.isActivityMessage(eventId) -> activityHandler.handleReaction(chatId, senderId, eventId, key)
            sessionManager.isSessionMessage(chatId, eventId) -> sessionManager.handleReaction(chatId, senderId, key)
            else -> mainMenuHandler.handleReaction(chatId, senderId, eventId, key)
        }
    }
}

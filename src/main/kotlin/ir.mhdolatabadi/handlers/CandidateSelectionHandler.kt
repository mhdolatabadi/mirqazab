package ir.mhdolatabadi.handlers

import ir.mhdolatabadi.matrix.MatrixLongPollingBot
import ir.mhdolatabadi.services.MemberService
import ir.mhdolatabadi.utils.NumberUtils
import ir.mhdolatabadi.utils.ReactionKeys
import kotlin.random.Random

/** The "میرغضب تصادفی" candidate-picking flow: pick who's eligible, then roll one at random. */
class CandidateSelectionHandler(
    private val bot: MatrixLongPollingBot,
    private val memberService: MemberService,
    private val onCancel: (chatId: String, eventId: String) -> Unit
) {
    private val candidateSelections = mutableMapOf<String, MutableSet<String>>() // "chatId-eventId" -> candidate userIds

    fun isCandidateMessage(chatId: String, eventId: String): Boolean =
        candidateSelections.containsKey(key(chatId, eventId))

    fun showCandidateSelection(chatId: String, eventId: String) {
        val members = memberService.getGroupMembers(chatId)
        if (members.isEmpty()) {
            bot.editMessage(chatId, eventId, "⚠️ هنوز عضوی شناسایی نشده است.")
            return
        }
        candidateSelections[key(chatId, eventId)] = members.keys.toMutableSet()
        bot.editMessage(chatId, eventId, buildCandidateText(chatId, eventId))
        val keys = members.keys.indices.map { ReactionKeys.forIndex(it + 1) } + listOf(KEY_RANDOM_MIRGHAZAB, KEY_CANCEL)
        bot.reactAll(chatId, eventId, keys)
    }

    fun handleReaction(chatId: String, eventId: String, key: String) {
        when (key) {
            KEY_RANDOM_MIRGHAZAB -> performRandomFromCandidates(chatId, eventId)
            KEY_CANCEL -> cancelSelection(chatId, eventId)
            else -> {
                val orderedUserIds = memberService.getGroupMembers(chatId).keys.toList()
                val targetUserId = orderedUserIds.withIndex()
                    .firstOrNull { (i, _) -> ReactionKeys.forIndex(i + 1) == key }?.value
                if (targetUserId != null) toggleCandidate(chatId, eventId, targetUserId)
            }
        }
    }

    private fun buildCandidateText(chatId: String, eventId: String): String {
        val members = memberService.getGroupMembers(chatId)
        val orderedUserIds = members.keys.toList()
        val selectedSet = candidateSelections[key(chatId, eventId)] ?: members.keys.toMutableSet()
        val counts = memberService.getMirGhazabCounts(chatId)
        return buildString {
            appendLine("کیا می‌تونن میرغضب وایستن؟")
            appendLine()
            for ((index, userId) in orderedUserIds.withIndex()) {
                val name = members[userId] ?: continue
                val isSelected = userId in selectedSet
                val countPersian = NumberUtils.toPersianNumber(counts[userId] ?: 0)
                val mark = if (isSelected) "✅" else "❌"
                appendLine("${ReactionKeys.forIndex(index + 1)} $mark $name ($countPersian بار)")
            }
            appendLine()
            appendLine("$KEY_RANDOM_MIRGHAZAB شانسی انتخاب کن   $KEY_CANCEL انصراف")
        }
    }

    private fun toggleCandidate(chatId: String, eventId: String, userId: String) {
        val currentSet = candidateSelections[key(chatId, eventId)] ?: return
        if (userId in currentSet) currentSet.remove(userId) else currentSet.add(userId)
        bot.editMessage(chatId, eventId, buildCandidateText(chatId, eventId))
    }

    private fun performRandomFromCandidates(chatId: String, eventId: String) {
        val candidates = candidateSelections[key(chatId, eventId)]?.toList() ?: emptyList()
        if (candidates.isEmpty()) {
            bot.editMessage(chatId, eventId, "⚠️ هیچ کاندیدی انتخاب نشده است. لطفاً حداقل یک عضو را انتخاب کنید.\n\n" + buildCandidateText(chatId, eventId))
            return
        }
        val randomUserId = candidates.random(Random)
        val name = memberService.getGroupMembers(chatId)[randomUserId] ?: "کاربر"
        memberService.incrementMirGhazabCount(chatId, randomUserId)
        val newCountPersian = NumberUtils.toPersianNumber(memberService.getMirGhazabCounts(chatId)[randomUserId] ?: 0)
        bot.editMessage(chatId, eventId, "🎭 میرغضب امروز: $name\n\nتا حالا $newCountPersian بار میرغضب شده است.")
        candidateSelections.remove(key(chatId, eventId))
    }

    private fun cancelSelection(chatId: String, eventId: String) {
        candidateSelections.remove(key(chatId, eventId))
        onCancel(chatId, eventId)
    }

    private fun key(chatId: String, eventId: String) = "$chatId-$eventId"
}

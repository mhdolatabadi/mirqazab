package ir.mhdolatabadi.handlers

// Reaction keys double as both the visible chip and the action id (Matrix has
// no hidden "callback data" like Telegram did), so they're shared across every
// handler that can appear on the same logical message.

internal const val KEY_RANDOM_MIRGHAZAB = "🎲"
internal const val KEY_CANDIDATE_ROLL = "🎯"
internal const val KEY_DAILY_REPORT = "📋"
internal const val KEY_WEEKLY_REPORT = "📅"
internal const val KEY_OVERALL_REPORT = "📈"
internal const val KEY_RESET_ATTENDANCE = "🔄"
internal const val KEY_CONFIRM = "✅"
internal const val KEY_CANCEL = "❌"

internal const val KEY_ACTIVITY_DEVELOPMENT = "🛠️"
internal const val KEY_ACTIVITY_QUICK_REACTION = "⚡"
internal const val KEY_ACTIVITY_VACATION = "🏖️"

internal const val KEY_SESSION_DONE_PRESENT = "➡️"
internal const val KEY_SESSION_DONE_FINAL = "✔️"

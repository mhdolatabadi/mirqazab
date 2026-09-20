package ir.mhdolatabadi.utils

/**
 * Matrix reactions have no label, unlike Telegram inline buttons - each member
 * row in a roster gets a stable numbered-keycap reaction so users know what to tap.
 */
object ReactionKeys {
    private val keycaps = listOf("1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟")

    fun forIndex(oneBasedIndex: Int): String =
        if (oneBasedIndex in 1..keycaps.size) keycaps[oneBasedIndex - 1] else "u$oneBasedIndex"
}

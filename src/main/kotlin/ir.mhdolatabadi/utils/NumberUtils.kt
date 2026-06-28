package ir.mhdolatabadi.utils

object NumberUtils {
    fun toPersianNumber(input: Any?): String {
        val englishDigits = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        val persianDigits = listOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return input.toString().map { char ->
            val index = englishDigits.indexOf(char)
            if (index != -1) persianDigits[index] else char
        }.joinToString("")
    }
}
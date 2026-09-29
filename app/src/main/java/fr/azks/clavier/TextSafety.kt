package fr.azks.clavier

import android.text.InputType
import android.view.inputmethod.EditorInfo

fun isSensitiveInput(type: Int, options: Int): Boolean {
    val kind = type and InputType.TYPE_MASK_CLASS
    val variation = type and InputType.TYPE_MASK_VARIATION
    return type == InputType.TYPE_NULL ||
        kind == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
        kind == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
        options and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
}

/** Number of UTF-16 units to delete. Augments older Java/Android emoji boundaries. */
fun lastGraphemeLength(text: String): Int {
    if (text.isEmpty()) return 0
    fun previous(index: Int) = index - Character.charCount(text.codePointBefore(index))
    fun extension(cp: Int) = Character.getType(cp) in setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt()) ||
        cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F || cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF
    var start = previous(text.length)
    if (text.codePointAt(start) in 0x1F1E6..0x1F1FF) {
        var runStart = start; var count = 1
        while (runStart > 0 && text.codePointBefore(runStart) in 0x1F1E6..0x1F1FF) { runStart = previous(runStart); count++ }
        return if (count % 2 == 0) 4 else 2
    }
    while (true) {
        while (start > 0 && extension(text.codePointAt(start))) start = previous(start)
        if (start > 0 && text.codePointBefore(start) == 0x200D) {
            start = previous(start)
            if (start > 0) start = previous(start) else break
        } else break
    }
    val iterator = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
    iterator.setText(text); iterator.last()
    return text.length - minOf(start, iterator.previous().coerceAtLeast(0))
}

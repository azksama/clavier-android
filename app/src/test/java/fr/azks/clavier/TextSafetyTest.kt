package fr.azks.clavier

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test

class TextSafetyTest {
    @Test fun deletesWholeEmojiIncludingModifiersAndJoiners() {
        listOf("😀", "👍🏽", "👨‍👩‍👧‍👦", "👩🏽‍💻", "❤️‍🔥", "1️⃣", "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F").forEach { cluster -> assertEquals(cluster, cluster.length, lastGraphemeLength("texte $cluster")) }
    }
    @Test fun preservesOtherFlagsAndOrdinaryText() {
        assertEquals(4, lastGraphemeLength("🇫🇷🇨🇦"))
        assertEquals(2, lastGraphemeLength("🇫🇷🇨"))
        assertEquals(1, lastGraphemeLength("😀x"))
        assertEquals(0, lastGraphemeLength(""))
    }
    @Test fun handlesAccentsAndLineEndings() {
        assertEquals(2, lastGraphemeLength("cafe\u0301"))
        assertEquals(1, lastGraphemeLength("café"))
        assertEquals(2, lastGraphemeLength("a\r\n"))
    }
    @Test fun allPasswordVariationsArePrivateEvenWithOtherFlags() {
        listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD).forEach { variation ->
            assertTrue(isSensitiveInput(InputType.TYPE_CLASS_TEXT or variation or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 0))
        }
        assertTrue(isSensitiveInput(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, 0))
    }
    @Test fun respectsNoPersonalizedLearningAndNullInput() {
        assertTrue(isSensitiveInput(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertTrue(isSensitiveInput(InputType.TYPE_NULL, 0))
        assertFalse(isSensitiveInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 0))
        assertFalse(isSensitiveInput(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, 0))
    }
}

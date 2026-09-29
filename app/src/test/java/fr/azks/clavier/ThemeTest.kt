package fr.azks.clavier

import org.junit.Assert.*
import org.junit.Test

class ThemeTest {
    @Test fun acceptsOnlyOpaqueSixDigitHex() {
        assertEquals(0xFF286A83, parseHex(" #286a83 "))
        assertEquals(0xFF000000, parseHex("000000"))
        listOf("", "#FFF", "#11223344", "gggggg", "#1234567", "-12345").forEach { assertNull(parseHex(it)) }
    }
    @Test fun serializesHexWithoutAlpha() { assertEquals("#286A83", hex(0xFF286A83)); assertEquals("#000001", hex(0xFF000001)) }
    @Test fun contrastExtremesMatchWcag() {
        assertEquals(21.0, contrastRatio(0xFF000000, 0xFFFFFFFF), .0001)
        assertEquals(1.0, contrastRatio(0xFF286A83, 0xFF286A83), .0001)
    }
    @Test fun preservesCustomColorsAcrossDarkMode() {
        val custom = ThemeConfig.preset(KeyStyle.GLASS).copy(custom = true)
        assertEquals(custom, custom.resolved(true))
        assertNotEquals(ThemeConfig().background, ThemeConfig().resolved(true).background)
        assertEquals(ThemeConfig().background, ThemeConfig(mode = ColorMode.LIGHT).resolved(true).background)
    }
}

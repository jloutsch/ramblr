package com.trevornk.ramblr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeChipLabelTest {
    private val german = TranslationLanguage("de", "German", "Deutsch")
    private val fmt = "→ %1\$s · %2\$s"

    @Test fun `without a target the chip is exactly what it always was`() {
        assertEquals("Natural", imeChipText(true, "Natural", "Cleanup off", null, fmt))
        assertEquals("Cleanup off", imeChipText(false, "Natural", "Cleanup off", null, fmt))
    }

    @Test fun `with a target the language leads, so ellipsizing can only cut the style`() {
        val text = imeChipText(true, "Professional email", "Cleanup off", german, fmt)
        assertEquals("→ DE · Professional email", text)
        assertTrue(text.startsWith("→ DE"))
    }

    @Test fun `translating with cleanup off says so`() {
        assertEquals("→ DE · Cleanup off", imeChipText(false, "Natural", "Cleanup off", german, fmt))
    }
}

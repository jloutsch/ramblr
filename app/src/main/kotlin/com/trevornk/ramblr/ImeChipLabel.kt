package com.trevornk.ramblr

/**
 * Text of the keyboard's single style chip. Without a translation it is exactly what the chip
 * always showed (the style, or "Cleanup off"), so nothing changes for anyone who never picks a
 * language. With one, the target language comes FIRST ("→ ES · Natural") so a long style name that
 * gets ellipsized at the chip's width can never hide that the next dictation will be translated.
 */
internal fun imeChipText(
    cleanupEnabled: Boolean,
    styleTitle: String,
    cleanupOffTitle: String,
    target: TranslationLanguage?,
    translateFormat: String,
): String {
    val style = if (cleanupEnabled) styleTitle else cleanupOffTitle
    return if (target == null) style else String.format(translateFormat, target.code.uppercase(), style)
}

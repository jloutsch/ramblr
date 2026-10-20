package com.trevornk.ramblr

import android.app.AlertDialog
import android.content.Context

/**
 * The "Translation languages" picker: which languages appear under "Translate to" on the Ramblr
 * keyboard and in the floating icon's long-press menu (one shared list, [KeyboardTranslation]).
 * Lives on the AI cleanup screen next to the cloud provider the translation runs on.
 */
internal object TranslationLanguagesDialog {
    fun show(context: Context, onSaved: () -> Unit) {
        val all = KeyboardTranslation.ALL
        val chosen = KeyboardTranslation.chosen(context).map { it.code }.toSet()
        val checked = BooleanArray(all.size) { all[it].code in chosen }
        val labels = all.map { if (it.nativeName == it.englishName) it.englishName else "${it.englishName} (${it.nativeName})" }
        AlertDialog.Builder(context)
            .setTitle(R.string.keyboard_translation_languages_dialog)
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.keyboard_translation_languages_save) { _, _ ->
                KeyboardTranslation.setChosen(context, all.filterIndexed { i, _ -> checked[i] }.map { it.code })
                onSaved()
            }
            .setNegativeButton(R.string.keyboard_cancel, null)
            .show()
    }
}

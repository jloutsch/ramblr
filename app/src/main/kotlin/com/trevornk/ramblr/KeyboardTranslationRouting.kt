package com.trevornk.ramblr

import android.content.Context

/**
 * Decides how a keyboard translation request is carried out, separate from [DictationRuntime] so
 * the rules are plain JVM-testable.
 *
 * Translation is done by the existing cloud cleanup providers (the same chain, credentials and
 * consent as cleanup) and by nothing else:
 *  - On-device cleanup models ignore the prompt they are given (they always run their own fixed
 *    cleanup prompt), so a LOCAL step "succeeds" without translating. The route therefore removes
 *    every LOCAL step and the translation runs on the first cloud step that has a credential. A
 *    chain of `[local, cloud]` used to hand the dictation to the local step and silently drop the
 *    translation.
 *  - If nothing can translate (no cloud step, no credential, cloud cleanup switched off, or the
 *    one-time off-device consent missing) the route is [Route.Unavailable], carrying the reason the
 *    runtime shows in the panel; the dictation is then inserted untranslated.
 */
internal object KeyboardTranslationRouting {
    enum class Reason { CONSENT_MISSING, NO_CLOUD_PROVIDER }

    sealed class Route {
        /** No translation was requested. */
        object NotRequested : Route()

        /** Translate with [chain]'s cloud steps (never a LOCAL one), in chain order. */
        data class Cloud(val chain: ProviderChain, val waterfall: CleanupWaterfall) : Route()

        data class Unavailable(val reason: Reason) : Route()
    }

    /**
     * @param target the requested language (English name), or null for "keep the spoken language"
     * @param effectiveChain the cleanup chain after the "Use cloud for cleanup" and on-device
     *   fallback settings were applied ([ProviderChainRuntime.effectiveChainForCleanup])
     * @param consentMissing whether the one-time "send text off-device" consent (#23) is still due
     * @param hasCredential whether a step's provider has an API key stored
     */
    fun route(
        target: String?,
        effectiveChain: ProviderChain,
        consentMissing: Boolean,
        hasCredential: (ProviderChainEntry) -> Boolean,
    ): Route {
        if (target == null) return Route.NotRequested
        if (consentMissing) return Route.Unavailable(Reason.CONSENT_MISSING)
        val cloudEntries = effectiveChain.capableEntriesFor(needsTranscription = false)
            .filter { it.kind != ProviderKind.LOCAL && hasCredential(it) }
        if (cloudEntries.isEmpty()) return Route.Unavailable(Reason.NO_CLOUD_PROVIDER)
        val chain = ProviderChain(cloudEntries)
        return Route.Cloud(chain, ProviderChainRuntime.cleanupWaterfallFor(chain))
    }

    /**
     * The prompt for a translated dictation. With cleanup on the user's style prompt is kept (the
     * output is both restyled and translated); with it off, only [PostProcessor.TRANSLATE_ONLY_PROMPT]
     * runs, so "As is" plus a language does not restyle the text.
     */
    fun prompt(cleanupEnabled: Boolean, stylePrompt: String, vocabulary: List<String>, languageName: String): String {
        val base = if (cleanupEnabled) stylePrompt else PostProcessor.TRANSLATE_ONLY_PROMPT
        return PostProcessor.withTranslation(PostProcessor.interpolateVocabulary(base, vocabulary), languageName)
    }

    @androidx.annotation.StringRes
    fun messageRes(reason: Reason): Int = when (reason) {
        Reason.CONSENT_MISSING -> R.string.ime_translate_unavailable_consent
        Reason.NO_CLOUD_PROVIDER -> R.string.ime_translate_unavailable_cloud
    }
}

/** Live-state lookups behind the routing, for the picker (notice + consent) and the runtime. */
internal object KeyboardTranslationReadiness {
    private const val PREFS_NAME = "ramblr"
    /** The flag Settings and the style menu already use for the #23 consent. */
    const val KEY_CONSENT = "local_cleanup_consent_seen"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Translation always sends the recognized text to a cloud provider. While speech is recognized
     * on-device that is the exact case the #23 consent covers, so it is due until given once.
     */
    fun consentMissing(context: Context): Boolean = LocalCleanupConsent.shouldPrompt(
        useLocal = prefs(context).getBoolean("use_local", true),
        usePostProcessing = true,
        hasConsented = prefs(context).getBoolean(KEY_CONSENT, false),
        cleanupIsLocalOnly = false,
    )

    /** Records the consent the keyboard just collected in its own panel. */
    fun grantConsent(context: Context) {
        prefs(context).edit().putBoolean(KEY_CONSENT, true).apply()
    }

    /** Where the text would go, for the consent question. */
    fun consentHost(context: Context): String = CleanupDestination.consentHost(ProviderChainStore.load(context))

    /** Mirrors the executor's lookup: the entry's own key first, else the legacy per-kind key. */
    fun hasCredential(context: Context, entry: ProviderChainEntry): Boolean =
        ProviderCredentialStore.get(context, entry.id).isNotBlank() ||
            ProviderCredentialStore.getLegacyByKind(context, entry.kind).isNotBlank()

    fun effectiveChain(context: Context): ProviderChain = ProviderChainRuntime.effectiveChainForCleanup(
        ProviderChainStore.load(context), CloudFeatureToggle.cleanupEnabled(context), DictationModeToggle.allowLocalFallback(context),
    )

    /** Whether a cloud step with a key exists for translation, whatever the consent state: what
     *  the picker's "needs a cloud AI provider" notice reflects. */
    fun cloudReady(context: Context): Boolean = KeyboardTranslationRouting.route(
        target = "probe", effectiveChain = effectiveChain(context), consentMissing = false,
        hasCredential = { hasCredential(context, it) },
    ) is KeyboardTranslationRouting.Route.Cloud

    /** The route a dictation started now would take for [target]. */
    fun route(context: Context, target: String?): KeyboardTranslationRouting.Route =
        KeyboardTranslationRouting.route(
            target = target,
            effectiveChain = effectiveChain(context),
            consentMissing = consentMissing(context),
            hasCredential = { hasCredential(context, it) },
        )
}

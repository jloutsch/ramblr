package com.trevornk.ramblr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reviewer-found hole in #318: with `[local, cloud]` the local step "succeeded" with its own
 * fixed prompt and the translation was silently dropped. Translation must run on a cloud step or
 * say plainly that it could not.
 */
class KeyboardTranslationRoutingTest {

    private val local = ProviderChainEntry(ProviderKind.LOCAL, "on-device")
    private val openai = ProviderChainEntry(ProviderKind.OPENAI, "gpt-4o-mini", id = "o1")
    private val gemini = ProviderChainEntry(ProviderKind.GEMINI, "gemini-flash", id = "g1")
    private val anthropic = ProviderChainEntry(ProviderKind.ANTHROPIC, "claude-haiku", id = "a1")

    private fun route(
        chain: List<ProviderChainEntry>,
        target: String? = "German",
        consentMissing: Boolean = false,
        withKey: Set<String> = setOf("o1", "g1", "a1"),
    ) = KeyboardTranslationRouting.route(target, ProviderChain(chain), consentMissing) { it.id in withKey }

    private fun cloud(r: KeyboardTranslationRouting.Route) = r as KeyboardTranslationRouting.Route.Cloud

    @Test fun `no target means no translation and no message`() {
        assertEquals(KeyboardTranslationRouting.Route.NotRequested, route(listOf(openai), target = null))
    }

    @Test fun `a local step ahead of a cloud step is skipped and the cloud step translates`() {
        val r = cloud(route(listOf(local, openai)))
        assertEquals(listOf(openai), r.chain.entries)
        assertTrue("no LOCAL step may remain in the waterfall", r.waterfall.steps.none { it.group == CleanupStepGroup.LOCAL_LLM })
        assertEquals(1, r.waterfall.steps.size)
    }

    @Test fun `cloud steps keep chain order and ones without a key are dropped`() {
        val r = cloud(route(listOf(local, openai, gemini, anthropic), withKey = setOf("g1", "a1")))
        assertEquals(listOf(gemini, anthropic), r.chain.entries)
    }

    @Test fun `a chain that is only the local step cannot translate`() {
        assertEquals(
            KeyboardTranslationRouting.Route.Unavailable(KeyboardTranslationRouting.Reason.NO_CLOUD_PROVIDER),
            route(listOf(local)),
        )
    }

    @Test fun `cloud steps without credentials cannot translate`() {
        assertEquals(
            KeyboardTranslationRouting.Route.Unavailable(KeyboardTranslationRouting.Reason.NO_CLOUD_PROVIDER),
            route(listOf(local, openai, gemini, anthropic), withKey = emptySet()),
        )
    }

    @Test fun `an empty chain cannot translate`() {
        assertEquals(
            KeyboardTranslationRouting.Route.Unavailable(KeyboardTranslationRouting.Reason.NO_CLOUD_PROVIDER),
            route(emptyList()),
        )
    }

    @Test fun `a step switched off for cleanup does not translate`() {
        val off = openai.copy(useForCleanup = false)
        assertEquals(
            KeyboardTranslationRouting.Route.Unavailable(KeyboardTranslationRouting.Reason.NO_CLOUD_PROVIDER),
            route(listOf(local, off)),
        )
    }

    @Test fun `missing off-device consent is reported before anything is sent`() {
        assertEquals(
            KeyboardTranslationRouting.Route.Unavailable(KeyboardTranslationRouting.Reason.CONSENT_MISSING),
            route(listOf(openai), consentMissing = true),
        )
    }

    @Test fun `each unavailable reason has its own panel message`() {
        assertEquals(R.string.ime_translate_unavailable_consent, KeyboardTranslationRouting.messageRes(KeyboardTranslationRouting.Reason.CONSENT_MISSING))
        assertEquals(R.string.ime_translate_unavailable_cloud, KeyboardTranslationRouting.messageRes(KeyboardTranslationRouting.Reason.NO_CLOUD_PROVIDER))
    }

    @Test fun `with cleanup off only the translate-only prompt runs, with the language clause`() {
        val p = KeyboardTranslationRouting.prompt(false, PostProcessor.NATURAL_PROMPT, emptyList(), "French")
        assertTrue(p.contains(PostProcessor.TRANSLATE_ONLY_PROMPT.substringBefore('.')))
        assertFalse("the style prompt must not leak into a translate-only run", p.contains("filler"))
        assertTrue(p.trimEnd().endsWith("Return only the French text."))
    }

    @Test fun `with cleanup on the style prompt is kept and translated`() {
        val p = KeyboardTranslationRouting.prompt(true, PostProcessor.NATURAL_PROMPT, emptyList(), "French")
        assertFalse(p.contains(PostProcessor.TRANSLATE_ONLY_PROMPT))
        assertTrue(p.trimEnd().endsWith("Return only the French text."))
    }

    /** End to end through the real executor: the routed chain reaches the cloud transport with the
     *  translation prompt, where the unrouted chain would have been served by the local step. */
    @Test fun `routed mixed chain sends the translation prompt to the cloud provider`() {
        val bodies = mutableListOf<String>()
        val transport = object : CleanupHttpTransport {
            override fun send(
                url: String, headers: Map<String, String>, jsonBody: String, timeouts: CleanupStepTimeouts,
                cancelHolder: InFlightCall, callback: (CleanupHttpOutcome) -> Unit,
            ) {
                bodies += jsonBody
                callback(CleanupHttpOutcome.Ok("""{"choices":[{"message":{"content":"Hallo Welt."}}]}"""))
            }
        }
        val r = cloud(route(listOf(local, openai)))
        val prompt = KeyboardTranslationRouting.prompt(true, PostProcessor.NATURAL_PROMPT, emptyList(), "German")
        var result: PostProcessor.Result? = null
        PostProcessor.processProviderChain(
            text = "hello world", prompt = prompt, chain = r.chain, cursor = CleanupWaterfallCursor(),
            cancelHolder = InFlightCall(), credentialLookup = { "key" }, transport = transport,
            fidelityGuard = false, callback = { result = it },
        )
        assertEquals("Hallo Welt.", result?.text)
        assertEquals(1, bodies.size)
        assertTrue(bodies.single().contains("Return only the German text."))
        assertNull(result?.error)
    }
}

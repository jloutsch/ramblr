package com.trevornk.ramblr

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent

/**
 * The accessibility-event subscription behind [RingVisibilityMode].
 *
 * Privacy contract (README "Why does it need Accessibility?", PRIVACY.md): with the default
 * [RingVisibilityMode.ALWAYS] the service keeps exactly the subscription its XML config declares
 * (`typeViewFocused`) and [WhisperAccessibilityService.onAccessibilityEvent] does nothing. Only when
 * the user opts into "only while the keyboard is open" or "while a text field is selected" does the
 * service additionally listen for window and focus changes -- just enough to know whether a
 * keyboard window or an editable field is up. It never reads text content, only the window type,
 * and whether the focused node is editable / an EditText.
 *
 * The static XML configs are deliberately NOT changed: the extra event type is added at runtime
 * through `setServiceInfo` ([RingEventSubscription]) and removed again when the mode goes back
 * to ALWAYS.
 */
object RingEventTypes {
    /** The only event types the non-ALWAYS modes add on top of the static config. */
    const val WINDOW_AND_FOCUS: Int =
        AccessibilityEvent.TYPE_WINDOWS_CHANGED or AccessibilityEvent.TYPE_VIEW_FOCUSED

    /** The event types the service should be subscribed to for [mode], given the static config's
     *  [baseEventTypes]. ALWAYS returns [baseEventTypes] untouched. */
    fun forMode(baseEventTypes: Int, mode: RingVisibilityMode): Int =
        if (mode == RingVisibilityMode.ALWAYS) baseEventTypes else baseEventTypes or WINDOW_AND_FOCUS

    /** Whether [onAccessibilityEvent] should look at [eventType] at all in [mode]. */
    fun isRelevant(mode: RingVisibilityMode, eventType: Int): Boolean =
        mode != RingVisibilityMode.ALWAYS && (eventType and WINDOW_AND_FOCUS) != 0
}

/**
 * Applies [RingEventTypes.forMode] to the live service. [getInfo]/[setInfo] are the service's
 * `serviceInfo` property; they are injected so the decision is unit-testable without a bound
 * service. The first [apply] records the static config's event types as the base, so dropping back
 * to ALWAYS restores exactly what the XML declared rather than a guess.
 */
class RingEventSubscription(
    private val getInfo: () -> AccessibilityServiceInfo?,
    private val setInfo: (AccessibilityServiceInfo) -> Unit,
) {
    private var baseEventTypes: Int? = null

    /** Subscribes (or unsubscribes) for [mode]. Returns true if the subscription was changed. */
    fun apply(mode: RingVisibilityMode): Boolean {
        val info = getInfo() ?: return false
        val base = baseEventTypes ?: info.eventTypes.also { baseEventTypes = it }
        val wanted = RingEventTypes.forMode(base, mode)
        if (info.eventTypes == wanted) return false
        info.eventTypes = wanted
        setInfo(info)
        return true
    }
}

/**
 * Remembers the last "is the ring's anchor present" answer (keyboard up, or text field focused,
 * per mode) so the overlay is only re-laid-out when it actually changes -- each re-layout itself
 * fires another window event. The remembered answer is only valid for the mode it was computed
 * under (a keyboard-up "true" says nothing about TEXT_FIELD), so [reset] must run whenever the mode
 * changes; otherwise a KEYBOARD <-> TEXT_FIELD switch can swallow the first real transition.
 */
class RingAnchorTracker {
    private var last: Boolean? = null

    /** Forget the last answer. Call on every mode change. */
    fun reset() { last = null }

    /** Records [present]; returns true when it differs from the last answer (re-layout needed). */
    fun update(present: Boolean): Boolean {
        if (present == last) return false
        last = present
        return true
    }

    /** The last recorded answer, or null if none since the last [reset]. For tests/diagnostics. */
    fun lastPresent(): Boolean? = last
}

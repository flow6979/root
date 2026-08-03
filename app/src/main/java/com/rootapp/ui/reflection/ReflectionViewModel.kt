package com.rootapp.ui.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootapp.ai.ChatMessage
import com.rootapp.ai.LlmClient
import com.rootapp.ai.Prompts
import com.rootapp.analytics.Events
import com.rootapp.analytics.Track
import com.rootapp.data.Insights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives a reflection session: keeps the full transcript (system prompt hidden),
 * calls the LLM on each user turn, and exposes a single immutable UI state.
 */
class ReflectionViewModel(
    private val llm: LlmClient,
    private val userName: String = "",
    pastMemory: String = "",
    tone: String = "Gentle",
    private val onUserMessage: (String) -> Unit = {},
    /**
     * Called once, when the session ends, with the distilled takeaway (main concern + one
     * intention). Wired by the screen to persist it via LocalStore. Best-effort by contract:
     * implementations must not throw. Default no-op keeps the ViewModel usable in tests.
     */
    private val onTakeaway: (Insights.Takeaway) -> Unit = {},
    /** Returns memory relevant to a given user message, injected just before the model turn (RAG). */
    private val retrieve: suspend (String) -> String = { "" },
    /** The evolving user profile to seed the persona, and a callback to persist an updated one. */
    private val profile: String = "",
    private val onProfile: (String) -> Unit = {},
    /**
     * Executes an in-session coach action (start focus, set budget, log meal, set bedtime) and
     * returns a short confirmation to show, or null if it couldn't act. Wired by the screen to
     * [com.rootapp.shield.CoachActions]. Default no-op keeps the ViewModel usable in tests.
     */
    private val onAction: (com.rootapp.ai.CoachAction) -> String? = { null },
) : ViewModel() {

    data class UiState(
        val visible: List<ChatMessage> = emptyList(), // user + assistant only
        val sending: Boolean = false,
        val error: String? = null,
    )

    // Full transcript incl. the hidden system prompt, sent to the model each turn.
    private val transcript = mutableListOf(
        ChatMessage.system(Prompts.friendSystemPrompt(userName, pastMemory.ifBlank { null }, tone, profile.ifBlank { null })),
        ChatMessage.assistant(Prompts.opener(userName, tone)),
    )

    private val _state = MutableStateFlow(UiState(visible = visibleFrom(transcript)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _state.value.sending) return

        transcript += ChatMessage.user(text)
        onUserMessage(text)
        Track.event(Events.REFLECTION_MESSAGE_SENT)
        _state.value = _state.value.copy(
            visible = visibleFrom(transcript),
            sending = true,
            error = null,
        )

        viewModelScope.launch {
            try {
                val reply = llm.complete(withRelevantMemory(text))
                // Extract any coach action, execute it, and fold its confirmation into the reply.
                val parsed = com.rootapp.ai.ActionParser.parse(reply)
                val confirmations = parsed.actions.mapNotNull { a ->
                    runCatching { onAction(a) }.getOrNull()
                }
                val shown = if (confirmations.isEmpty()) parsed.text
                else parsed.text + "\n\n" + confirmations.joinToString("\n") { "Done: $it" }
                transcript += ChatMessage.assistant(shown)
                _state.value = _state.value.copy(visible = visibleFrom(transcript), sending = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    sending = false,
                    error = t.message ?: "Something went wrong. Try again in a moment.",
                )
            }
        }
    }

    /** Transcript to send, with memory relevant to [latestUserText] injected before that turn. */
    private suspend fun withRelevantMemory(latestUserText: String): List<ChatMessage> {
        val mem = runCatching { retrieve(latestUserText) }.getOrDefault("")
        val list = transcript.toList()
        if (mem.isBlank()) return list
        return list.dropLast(1) + ChatMessage.system("Relevant things you remember about them:\n$mem") + list.last()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** True once the user has actually said something worth distilling into a takeaway. */
    private fun hasUserContent(): Boolean = transcript.any { it.role == "user" }

    /** Guards against extracting/persisting a takeaway more than once per session. */
    private var takeawayDone = false

    /**
     * End the session: ask the LLM for a SHORT structured takeaway (main concern + one intention,
     * each under ~120 chars) from the transcript so far, then hand it to [onTakeaway] for
     * persistence. Best-effort by design - if the AI fails or the session is empty, we quietly do
     * nothing (never crash, never surface an error). Idempotent within a session.
     *
     * Runs on [scope] (defaults to [viewModelScope]) so it can be awaited from a test, or fired
     * from [onCleared] where the ViewModel scope is already cancelling.
     */
    fun endSession(scope: kotlinx.coroutines.CoroutineScope = viewModelScope) {
        if (takeawayDone || !hasUserContent()) return
        takeawayDone = true
        val convo = transcript.toList()
        scope.launch {
            val takeaway = runCatching { extractTakeaway(convo) }.getOrDefault(Insights.Takeaway("", ""))
            if (takeaway.concern.isNotBlank() || takeaway.intention.isNotBlank()) {
                runCatching { onTakeaway(takeaway) }
            }
            // Fold this session into the evolving user profile (best-effort).
            val updated = runCatching { com.rootapp.ai.ProfileUpdater.update(llm, profile, convo) }.getOrNull()
            if (!updated.isNullOrBlank() && updated != profile) runCatching { onProfile(updated) }
        }
    }

    /** Ask the model for the takeaway and parse it. Isolated so failures stay contained. */
    private suspend fun extractTakeaway(convo: List<ChatMessage>): Insights.Takeaway {
        val instruction = ChatMessage.user(
            "Summarise this conversation as the user's ONE main concern and ONE intention they " +
                "landed on. Reply in EXACTLY this format, each under 120 characters, no extra " +
                "text:\nConcern: <their main concern>\nIntention: <the one thing they want to do>",
        )
        val reply = llm.complete(convo + instruction)
        return Insights.parseTakeaway(reply)
    }

    /** When the ViewModel is torn down (user leaves the screen), capture the takeaway. */
    override fun onCleared() {
        endSession()
        super.onCleared()
    }

    private fun visibleFrom(all: List<ChatMessage>): List<ChatMessage> =
        all.filter { it.role != "system" }
}

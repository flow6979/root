package com.rootapp.ui.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootapp.ai.ChatMessage
import com.rootapp.ai.LlmClient
import com.rootapp.ai.Prompts
import com.rootapp.analytics.Events
import com.rootapp.analytics.Track
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
) : ViewModel() {

    data class UiState(
        val visible: List<ChatMessage> = emptyList(), // user + assistant only
        val sending: Boolean = false,
        val error: String? = null,
    )

    // Full transcript incl. the hidden system prompt, sent to the model each turn.
    private val transcript = mutableListOf(
        ChatMessage.system(Prompts.friendSystemPrompt(userName, pastMemory.ifBlank { null }, tone)),
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
                val reply = llm.complete(transcript.toList())
                transcript += ChatMessage.assistant(reply)
                _state.value = _state.value.copy(visible = visibleFrom(transcript), sending = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    sending = false,
                    error = t.message ?: "Something went wrong. Try again in a moment.",
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun visibleFrom(all: List<ChatMessage>): List<ChatMessage> =
        all.filter { it.role != "system" }
}

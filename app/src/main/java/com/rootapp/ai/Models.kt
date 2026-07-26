package com.rootapp.ai

/** One turn in a conversation. role is "system", "user", or "assistant". */
data class ChatMessage(val role: String, val content: String) {
    companion object {
        fun system(text: String) = ChatMessage("system", text)
        fun user(text: String) = ChatMessage("user", text)
        fun assistant(text: String) = ChatMessage("assistant", text)
    }
}

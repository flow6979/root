package com.rootapp

import com.rootapp.ai.Prompts
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {

    @Test fun `system prompt names the user and encodes core rules`() {
        val p = Prompts.friendSystemPrompt("Vaibhav")
        assertTrue(p.contains("Vaibhav"))
        assertTrue(p.contains("friend"))
        assertTrue(p.lowercase().contains("never shame"))
        assertTrue(p.lowercase().contains("present"))
    }

    @Test fun `blank name falls back gracefully`() {
        val p = Prompts.friendSystemPrompt("")
        assertTrue(p.contains("friend"))
    }

    @Test fun `memory block is included only when provided`() {
        val without = Prompts.friendSystemPrompt("A")
        val with = Prompts.friendSystemPrompt("A", memory = "sleeps late, loves football")
        assertTrue(!without.contains("remember about them"))
        assertTrue(with.contains("remember about them"))
        assertTrue(with.contains("football"))
    }

    @Test fun `opener greets by name`() {
        assertTrue(Prompts.opener("Sam").contains("Sam"))
        assertTrue(Prompts.opener("").contains("Hey"))
    }
}

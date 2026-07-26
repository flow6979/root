package com.rootapp

import com.rootapp.ai.FakeLlmClient
import com.rootapp.ai.LlmClient
import com.rootapp.ui.reflection.ReflectionViewModel
import com.rootapp.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReflectionViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    @Test fun `starts with a visible opener and no system message`() {
        val vm = ReflectionViewModel(FakeLlmClient(), userName = "Sam")
        val s = vm.state.value
        assertEquals(1, s.visible.size)
        assertEquals("assistant", s.visible.first().role)
        assertFalse(s.visible.any { it.role == "system" })
    }

    @Test fun `send appends user turn then assistant reply and clears sending`() = runTest {
        val vm = ReflectionViewModel(FakeLlmClient("I hear you."), userName = "Sam")
        vm.send("I keep scrolling at 2am")
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.sending)
        assertNull(s.error)
        // opener + user + assistant = 3 visible
        assertEquals(3, s.visible.size)
        assertEquals("user", s.visible[1].role)
        assertEquals("I keep scrolling at 2am", s.visible[1].content)
        assertEquals("assistant", s.visible[2].role)
        assertEquals("I hear you.", s.visible[2].content)
    }

    @Test fun `system prompt is sent to the model but never shown`() = runTest {
        val fake = FakeLlmClient("ok")
        val vm = ReflectionViewModel(fake, userName = "Sam")
        vm.send("hi")
        advanceUntilIdle()

        val sent = fake.lastMessages!!
        assertEquals("system", sent.first().role)
        assertTrue(sent.first().content.contains("Root"))
        assertFalse(vm.state.value.visible.any { it.role == "system" })
    }

    @Test fun `blank input is ignored`() = runTest {
        val vm = ReflectionViewModel(FakeLlmClient(), userName = "Sam")
        val before = vm.state.value.visible.size
        vm.send("   ")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.visible.size)
    }

    @Test fun `llm failure surfaces an error and stops sending`() = runTest {
        val boom = object : LlmClient {
            override suspend fun complete(messages: List<com.rootapp.ai.ChatMessage>): String =
                throw RuntimeException("network down")
        }
        val vm = ReflectionViewModel(boom, userName = "Sam")
        vm.send("hello")
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.sending)
        assertEquals("network down", s.error)
    }
}

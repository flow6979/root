package com.rootapp

import com.rootapp.ai.ActionParser
import com.rootapp.ai.CoachAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionParserTest {

    @Test fun `parses start_focus and strips the directive`() {
        val raw = "Let's lock in for a bit. I'll start a 25 minute focus.\n[[ACTION: start_focus minutes=25]]"
        val out = ActionParser.parse(raw)
        assertEquals(listOf(CoachAction.StartFocus(25)), out.actions)
        assertTrue(!out.text.contains("ACTION"))
        assertTrue(out.text.endsWith("focus."))
    }

    @Test fun `parses quoted meal with healthy flag`() {
        val raw = "Nice, logging that.\n[[ACTION: log_meal food=\"paneer wrap\" healthy=true]]"
        val out = ActionParser.parse(raw)
        assertEquals(listOf(CoachAction.LogMeal("paneer wrap", true)), out.actions)
    }

    @Test fun `parses set_budget and set_bedtime`() {
        assertEquals(
            listOf(CoachAction.SetBudget(90)),
            ActionParser.parse("[[ACTION: set_budget minutes=90]]").actions,
        )
        assertEquals(
            listOf(CoachAction.SetBedtime(23)),
            ActionParser.parse("[[ACTION: set_bedtime hour=23]]").actions,
        )
    }

    @Test fun `no directive returns text unchanged and no actions`() {
        val raw = "Just here to listen. What's up?"
        val out = ActionParser.parse(raw)
        assertEquals(raw, out.text)
        assertTrue(out.actions.isEmpty())
    }

    @Test fun `malformed or unknown directives are dropped`() {
        assertTrue(ActionParser.parse("[[ACTION: start_focus]]").actions.isEmpty())
        assertTrue(ActionParser.parse("[[ACTION: teleport minutes=5]]").actions.isEmpty())
        assertTrue(ActionParser.parse("[[ACTION: start_focus minutes=abc]]").actions.isEmpty())
    }
}

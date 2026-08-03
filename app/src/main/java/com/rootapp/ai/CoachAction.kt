package com.rootapp.ai

/**
 * An action the coach can take mid-conversation. The model proposes one in natural language and
 * appends a machine directive; [ActionParser] extracts it, the app executes it. Kept provider-
 * agnostic (a directive in the reply text) so it works identically on Groq, Gemini, and offline.
 */
sealed interface CoachAction {
    /** Start a focus block that fully pauses time-sink apps. */
    data class StartFocus(val minutes: Int) : CoachAction

    /** Set today's daily screen-time budget, in minutes. */
    data class SetBudget(val minutes: Int) : CoachAction

    /** Log something the person ate into Moments. */
    data class LogMeal(val food: String, val healthy: Boolean) : CoachAction

    /** Set the nightly wind-down bedtime (24h hour) and enable the reminder. */
    data class SetBedtime(val hour: Int) : CoachAction
}

/**
 * Extracts `[[ACTION: name key=value ...]]` directives from an assistant reply and returns the
 * cleaned, user-facing text plus the parsed actions. Pure + unit-testable. Unknown or malformed
 * directives are dropped (never surfaced, never crash).
 */
object ActionParser {
    data class Parsed(val text: String, val actions: List<CoachAction>)

    private val directive = Regex("""\[\[ACTION:(.*?)]]""", RegexOption.IGNORE_CASE)
    // key=value where value is either "quoted" or a bareword with no spaces.
    private val pairs = Regex("""(\w+)\s*=\s*("([^"]*)"|(\S+))""")

    fun parse(raw: String): Parsed {
        val actions = directive.findAll(raw).mapNotNull { toAction(it.groupValues[1].trim()) }.toList()
        val text = raw.replace(directive, "").trim().replace(Regex("\n{3,}"), "\n\n")
        return Parsed(text, actions)
    }

    private fun toAction(body: String): CoachAction? {
        val name = body.substringBefore(' ').lowercase()
        val args = pairs.findAll(body).associate { m ->
            m.groupValues[1].lowercase() to (m.groupValues[3].ifEmpty { m.groupValues[4] })
        }
        return when (name) {
            "start_focus" -> args["minutes"]?.toIntOrNull()?.let { CoachAction.StartFocus(it) }
            "set_budget" -> args["minutes"]?.toIntOrNull()?.let { CoachAction.SetBudget(it) }
            "log_meal" -> args["food"]?.takeIf { it.isNotBlank() }
                ?.let { CoachAction.LogMeal(it, args["healthy"]?.equals("true", true) ?: false) }
            "set_bedtime" -> args["hour"]?.toIntOrNull()?.let { CoachAction.SetBedtime(it) }
            else -> null
        }
    }
}

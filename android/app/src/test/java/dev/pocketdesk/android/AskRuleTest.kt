package dev.pocketdesk.android

import org.junit.Assert.assertEquals
import org.junit.Test

class AskRuleTest {
    @Test
    fun theComputersAnswersAreKnown() {
        assertEquals(AskRule.Outcome.Answered, AskRule.outcome(null))
        assertEquals(AskRule.Outcome.NotAllowed, AskRule.outcome("not_allowed"))
        assertEquals(AskRule.Outcome.Empty, AskRule.outcome("empty"))
        assertEquals(AskRule.Outcome.NotInstalled, AskRule.outcome("not_installed"))
        assertEquals(AskRule.Outcome.NotRunning, AskRule.outcome("not_running"))
        assertEquals(AskRule.Outcome.NeedsConfirmation, AskRule.outcome("needs_confirmation"))
        assertEquals(AskRule.Outcome.TimedOut, AskRule.outcome("timed_out"))
    }

    @Test
    fun anythingElseIsAFailureSaidAsItCame() {
        assertEquals(AskRule.Outcome.Failed, AskRule.outcome("failed"))
        assertEquals(AskRule.Outcome.Failed, AskRule.outcome("Still answering the last one"))
        assertEquals(AskRule.Outcome.Failed, AskRule.outcome("Connection refused"))
        assertEquals(AskRule.Outcome.Failed, AskRule.outcome(""))
    }
}

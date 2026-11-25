package dev.pocketdesk.android

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.pocketdesk.android.ui.plainErrorText

/**
 * "Ask FRIDAY" in the share sheet: its entry, turned on or off
 * from Settings, and the words for how an ask went.
 */
object AskShare {
    /** The manifest's "Ask FRIDAY" alias of the share screen. */
    const val ALIAS = "dev.pocketdesk.android.AskFriday"

    private fun component(ctx: Context) = ComponentName(ctx.packageName, ALIAS)

    /** Whether the share sheet offers it; on until turned off. */
    fun shown(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(component(ctx)) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setShown(ctx: Context, shown: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            component(ctx),
            if (shown) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** How an ask to [computer] went, from the bridge's answer ([error] null: answered). */
    fun words(ctx: Context, computer: String, error: String?): String = when (AskRule.outcome(error)) {
        AskRule.Outcome.Answered -> ctx.getString(R.string.ask_friday_response_title)
        AskRule.Outcome.NotAllowed -> ctx.getString(R.string.ask_not_allowed, computer)
        AskRule.Outcome.Empty -> ctx.getString(R.string.share_nothing)
        AskRule.Outcome.NotInstalled -> ctx.getString(R.string.ask_not_installed, computer)
        AskRule.Outcome.NotRunning -> ctx.getString(R.string.ask_not_running, computer)
        AskRule.Outcome.NeedsConfirmation -> ctx.getString(R.string.ask_needs_confirmation, computer)
        AskRule.Outcome.TimedOut -> ctx.getString(R.string.ask_timed_out, computer)
        AskRule.Outcome.Failed -> ctx.getString(R.string.share_failed, ctx.plainErrorText(error))
    }
}

/** How an ask went, from the bridge's answer; kept apart from Android so it is tested. */
object AskRule {
    enum class Outcome {
        Answered,
        NotAllowed,
        Empty,
        NotInstalled,
        NotRunning,
        NeedsConfirmation,
        TimedOut,
        Failed,
    }

    fun outcome(error: String?): Outcome = when (error?.trim()) {
        null -> Outcome.Answered
        "not_allowed" -> Outcome.NotAllowed
        "empty" -> Outcome.Empty
        "not_installed" -> Outcome.NotInstalled
        "not_running" -> Outcome.NotRunning
        "needs_confirmation" -> Outcome.NeedsConfirmation
        "timed_out" -> Outcome.TimedOut
        else -> Outcome.Failed
    }
}

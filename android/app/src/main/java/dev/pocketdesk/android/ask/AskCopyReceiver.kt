package dev.pocketdesk.android.ask

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.pocketdesk.android.R

/**
 * Copies the FRIDAY answer to clipboard from a notification action and clears the notification.
 */
class AskCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val answer = intent.getStringExtra(EXTRA_ANSWER) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("FRIDAY", answer))

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)
        }

        Toast.makeText(context, context.getString(R.string.ask_friday_copied), Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_ANSWER = "dev.pocketdesk.android.ask.EXTRA_ANSWER"
        const val EXTRA_NOTIFICATION_ID = "dev.pocketdesk.android.ask.EXTRA_NOTIFICATION_ID"
    }
}

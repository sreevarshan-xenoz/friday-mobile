package dev.pocketdesk.android

import dev.pocketdesk.android.ui.plainErrorText

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dev.pocketdesk.android.bridge.RustCore
import dev.pocketdesk.android.links.LinkRule
import dev.pocketdesk.android.service.TransferService
import dev.pocketdesk.android.shortcuts.ShortcutPlan
import dev.pocketdesk.android.shortcuts.Shortcuts

/**
 * Share target (Tier 1 #4, F7): text goes to the desktop clipboard; one or
 * more files go to the desktop's Downloads folder, sent by [TransferService]
 * so the transfer outlives this screen, which closes at once.
 *
 * Requires a saved host ([HostStore]) — the shared item never picks a
 * destination itself. Another app (an automation app, T7b) may name one of
 * the saved computers with the extra [Shortcuts.EXTRA_ADDRESS]; without it,
 * the last computer used.
 *
 * Shared through its "Open on computer" entry (T8a), text is not copied: its
 * first web link opens in the computer's browser. Through "Note to computer"
 * (BF4), it is added to the computer's notes file.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A window that shows nothing and lets every touch through while a
        // send runs, so "To computer" does not cover the app it came from
        // (N2 review); it stays until the send is done, or the process
        // could be frozen mid-send.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        RustCore.start(this)
        handle(intent)
    }

    // A share made while this screen is still closing arrives here, not as
    // a new screen; it must not be dropped.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        // Present at all counts as naming one: an extra of the wrong type is
        // refused, not taken for "the last computer".
        val asked = if (intent.hasExtra(Shortcuts.EXTRA_ADDRESS)) {
            intent.getStringExtra(Shortcuts.EXTRA_ADDRESS).orEmpty()
        } else {
            null
        }
        // N2: a computer chosen in the share sheet's Direct Share row is the
        // person's own choice; its id names a saved computer or nothing.
        // Intent.EXTRA_SHORTCUT_ID, by value: the constant is API 29, where
        // Direct Share through shortcuts starts; older share sheets send none.
        val shortcut = intent.getStringExtra("android.intent.extra.shortcut.ID")
        val share = if (shortcut != null) {
            ShortcutPlan.shortcutHost(shortcut, HostStore.hosts(this), Prefs.shareToken(this)) {
                HostStore.pairedDesktopId(this, it)
            }
        } else {
            ShortcutPlan.shareHost(asked, HostStore.hosts(this), Prefs.othersChooseComputer(this))
        }
        val host = when (share) {
            is ShortcutPlan.Share.To -> share.host
            ShortcutPlan.Share.NoneSaved -> return done(getString(R.string.share_no_host))
            ShortcutPlan.Share.NotSaved -> return done(getString(R.string.share_not_saved))
            ShortcutPlan.Share.NotAllowed -> return done(getString(R.string.share_choosing_off))
        }

        // N2: text selected in another app ("To computer" in its menu) goes
        // to the computer's clipboard; the selection is left as it was.
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val selected = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            return if (selected.isNullOrBlank()) done(getString(R.string.share_nothing)) else sendText(host, selected)
        }
        val uris = sharedUris(intent)
        val text =
            if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT) else null
        if (intent.component?.className == AskShare.ALIAS) {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            val shareText = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            return if (shareText.isNullOrBlank()) done(getString(R.string.share_nothing)) else askFriday(host, shareText)
        }
        if (intent.component?.className == NoteShare.ALIAS) {
            // Styled text too (BF4 review): it is no String extra.
            val note = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            return if (note.isNullOrBlank()) done(getString(R.string.share_nothing)) else sendNote(host, note)
        }
        if (intent.component?.className == OPEN_ON_COMPUTER) {
            // N9: every link it holds, as KDE Connect sends them.
            val urls = text?.let { LinkRule.findAll(it) }.orEmpty()
            if (urls.isEmpty()) return done(getString(R.string.link_not_web))
            return sendLinks(host, urls)
        }
        when {
            uris.isNotEmpty() && !ShortcutPlan.shareable(uris.map { it.scheme }) ->
                done(getString(R.string.share_not_content))
            uris.isNotEmpty() -> {
                // The service reads the files under this screen's grant. A
                // share that gave no permission to read them (the sender
                // did not grant it) is refused by the system here, and must
                // end with a message, not a crash.
                try {
                    TransferService.start(this, host.address, host.controlPort, uris)
                } catch (e: SecurityException) {
                    android.util.Log.w("ShareActivity", "Shared files are not readable", e)
                    done(getString(R.string.share_unreadable))
                    return
                }
                done(resources.getQuantityString(
                        R.plurals.share_sending_files,
                        uris.size,
                        uris.size,
                        HostStore.shownName(this, host.address),
                    ))
            }
            !text.isNullOrBlank() -> sendText(host, text)
            else -> done(getString(R.string.share_nothing))
        }
    }

    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND_MULTIPLE ->
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else ->
            listOfNotNull(@Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }

    private fun sendText(host: HostStore.Host, text: String) {
        Thread {
            val result = RustCore.sendClipboard(host.address, host.controlPort, text)
            runOnUiThread {
                done(
                    if (result.isSuccess) {
                        getString(R.string.share_sent_clipboard_to, HostStore.shownName(this, host.address))
                    } else {
                        getString(R.string.share_failed, plainErrorText(result.exceptionOrNull()?.message))
                    },
                )
            }
        }.start()
    }

    private fun sendNote(host: HostStore.Host, text: String) {
        Thread {
            val result = RustCore.sendNote(host.address, host.controlPort, text)
            runOnUiThread {
                done(NoteShare.words(this, HostStore.shownName(this, host.address), result.exceptionOrNull()?.message))
            }
        }.start()
    }

    private fun sendLinks(host: HostStore.Host, urls: List<String>) {
        Thread {
            // In order, stopping at the first that does not go.
            var sent = 0
            var failure: Throwable? = null
            for (url in urls) {
                val result = RustCore.sendLink(host.address, host.controlPort, url)
                if (result.isFailure) {
                    failure = result.exceptionOrNull()
                    break
                }
                sent++
            }
            runOnUiThread {
                done(
                    when {
                        failure == null && sent == 1 -> getString(R.string.link_sent)
                        failure == null -> resources.getQuantityString(R.plurals.links_sent, sent, sent)
                        // How many went first (N9 review: a retry would send them again).
                        sent > 0 -> getString(R.string.links_sent_then_failed, sent, urls.size, plainErrorText(failure.message))
                        else -> getString(R.string.share_failed, plainErrorText(failure.message))
                    },
                )
            }
        }.start()
    }

    private fun askFriday(host: HostStore.Host, text: String) {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.ask_friday_question_hint)
            isSingleLine = false
            maxLines = 4
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        var asking = false
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.ask_friday_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.ask_friday_action) { _, _ ->
                asking = true
                val question = input.text?.toString()?.takeIf { it.isNotBlank() }
                val progress = android.app.AlertDialog.Builder(this)
                    .setMessage(getString(R.string.ask_friday_asking, HostStore.shownName(this, host.address)))
                    .setCancelable(false)
                    .show()

                Thread {
                    val result = RustCore.sendAsk(host.address, host.controlPort, text, question)
                    runOnUiThread {
                        runCatching { progress.dismiss() }
                        if (result.isSuccess) {
                            showAnswerOrNotify(result.getOrThrow())
                        } else {
                            done(AskShare.words(this, HostStore.shownName(this, host.address), result.exceptionOrNull()?.message))
                        }
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                finish()
            }
            .setOnDismissListener {
                if (!asking) finish()
            }
            .create()

        dialog.show()
    }

    private fun showAnswerOrNotify(answer: String) {
        if (isFinishing || isDestroyed) {
            postAnswerNotification(answer)
            return
        }
        val scroll = android.widget.ScrollView(this).apply {
            val tv = android.widget.TextView(this@ShareActivity).apply {
                setText(answer)
                setTextIsSelectable(true)
                val padding = (16 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
            addView(tv)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.ask_friday_response_title)
            .setView(scroll)
            .setPositiveButton(R.string.ask_friday_copy) { _, _ ->
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("FRIDAY", answer))
                Toast.makeText(applicationContext, R.string.ask_friday_copied, Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.ask_friday_close) { _, _ ->
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .show()
    }

    private fun postAnswerNotification(answer: String) {
        createAskChannelIfNeeded()
        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        val copyIntent = Intent(this, dev.pocketdesk.android.ask.AskCopyReceiver::class.java).apply {
            putExtra(dev.pocketdesk.android.ask.AskCopyReceiver.EXTRA_ANSWER, answer)
            putExtra(dev.pocketdesk.android.ask.AskCopyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val copyPending = android.app.PendingIntent.getBroadcast(
            this,
            notificationId,
            copyIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, ASK_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }
            .setSmallIcon(R.drawable.ic_stat_pocketdesk)
            .setContentTitle(getString(R.string.ask_friday_response_title))
            .setContentText(answer)
            .setStyle(android.app.Notification.BigTextStyle().bigText(answer))
            .addAction(
                android.app.Notification.Action.Builder(
                    null,
                    getString(R.string.ask_friday_copy),
                    copyPending,
                ).build()
            )
            .setAutoCancel(true)

        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager?.notify(notificationId, builder.build())
    }

    private fun createAskChannelIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = getSystemService(android.app.NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(ASK_CHANNEL_ID) == null) {
                val channel = android.app.NotificationChannel(
                    ASK_CHANNEL_ID,
                    getString(R.string.channel_ask_friday),
                    android.app.NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = getString(R.string.channel_ask_friday_desc)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /** Say [message] where it outlives this screen, and close it. */
    private fun done(message: String) {
        // AA2: how a share left this screen (its toast's words: counts and a
        // computer's name, never a file's).
        android.util.Log.i("ShareActivity", "share: $message")
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private companion object {
        /** The manifest's "Open on computer" alias of this screen. */
        const val OPEN_ON_COMPUTER = "dev.pocketdesk.android.OpenOnComputer"
        const val ASK_CHANNEL_ID = "channel_ask_friday"
    }
}

package com.krishna.jarvis

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

class JarvisNotificationService : NotificationListenerService() {

    companion object {
        private var lastApp = ""
        private var lastSender = ""
        private var lastText = ""
        private var lastAction: Notification.Action? = null
        private var appContext: Context? = null

        private val dropWords = setOf(
            "do", "dena", "de", "karo", "kar", "ki", "bolo", "bhejo", "likho"
        )

        fun readLast(): String {
            if (lastSender.isEmpty()) return "कोई नया संदेश नहीं है"
            if (lastText.isEmpty()) return "$lastSender का संदेश पढ़ नहीं पाया"
            return "$lastSender ने लिखा है: $lastText"
        }

        fun sendReply(raw: String): String {
            val ctx = appContext ?: return "जवाब भेजने के लिए नोटिफिकेशन की अनुमति चाहिए"
            val action = lastAction ?: return "जवाब देने लायक कोई संदेश नहीं है"

            val words = raw.trim().split(" ").drop(1).toMutableList()
            while (words.isNotEmpty() && words[0] in dropWords) words.removeAt(0)
            val msg = words.joinToString(" ").trim()
            if (msg.isEmpty()) return "क्या जवाब भेजना है?"

            val inputs = action.remoteInputs ?: return "इस संदेश पर जवाब नहीं दे सकता"
            return try {
                val intent = Intent()
                val bundle = Bundle()
                for (ri in inputs) bundle.putCharSequence(ri.resultKey, msg)
                RemoteInput.addResultsToIntent(inputs, intent, bundle)
                action.actionIntent.send(ctx, 0, intent)
                "$lastSender को जवाब भेज दिया"
            } catch (e: Exception) {
                "जवाब भेज नहीं पाया"
            }
        }
    }

    private var tts: TextToSpeech? = null
    private var ready = false

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("hi", "IN"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("en", "IN"))
                }
                ready = true
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !ready) return
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notif", true)) return

        val pm = packageManager
        val app = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            return
        }
        val title = (n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "")
            .take(40)
        val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val replyAction = n.actions?.firstOrNull { a ->
            a.remoteInputs != null && a.remoteInputs.isNotEmpty()
        }

        val msg: String
        if (replyAction != null && title.isNotBlank()) {
            lastApp = app
            lastSender = title
            lastText = text
            lastAction = replyAction
            msg = "सर, $app पर $title का संदेश आया है। " +
                "जवाब देना हो तो जार्विस खोलकर कहिए, रिप्लाई, और अपना संदेश।"
        } else if (title.isNotBlank()) {
            msg = "सर, $app पर $title की सूचना आई है"
        } else {
            msg = "सर, $app पर नई सूचना आई है"
        }
        tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, "notif")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

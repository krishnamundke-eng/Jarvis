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
            "do", "dena", "de", "karo", "kar", "ki", "bolo", "bhejo", "likho",
            "saying", "say", "that"
        )

        private fun hindi(): Boolean =
            appContext?.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
                ?.getString("lang", "en") == "hi"

        private fun t(en: String, hi: String): String = if (hindi()) hi else en

        fun readLast(): String {
            if (lastSender.isEmpty()) return t("There is no new message.", "कोई नया संदेश नहीं है")
            if (lastText.isEmpty()) {
                return t("I couldn't read the message from $lastSender.", "$lastSender का संदेश पढ़ नहीं पाया")
            }
            return t("$lastSender wrote: $lastText", "$lastSender ने लिखा है: $lastText")
        }

        fun sendReply(raw: String): String {
            val ctx = appContext
                ?: return t("I need notification access to send replies.", "जवाब भेजने के लिए नोटिफिकेशन की अनुमति चाहिए")
            val action = lastAction
                ?: return t("There is no message to reply to.", "जवाब देने लायक कोई संदेश नहीं है")

            val words = raw.trim().split(" ").drop(1).toMutableList()
            while (words.isNotEmpty() && words[0] in dropWords) words.removeAt(0)
            val msg = words.joinToString(" ").trim()
            if (msg.isEmpty()) return t("What should I reply?", "क्या जवाब भेजना है?")

            val inputs = action.remoteInputs
                ?: return t("I can't reply to this message.", "इस संदेश पर जवाब नहीं दे सकता")
            return try {
                val intent = Intent()
                val bundle = Bundle()
                for (ri in inputs) bundle.putCharSequence(ri.resultKey, msg)
                RemoteInput.addResultsToIntent(inputs, intent, bundle)
                action.actionIntent.send(ctx, 0, intent)
                t("Reply sent to $lastSender.", "$lastSender को जवाब भेज दिया")
            } catch (e: Exception) {
                t("I couldn't send the reply.", "जवाब भेज नहीं पाया")
            }
        }
    }

    private var tts: TextToSpeech? = null
    private var ready = false

    private fun setLang() {
        val loc = if (hindi()) Locale("hi", "IN") else Locale("en", "IN")
        val r = tts?.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                setLang()
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
            msg = t(
                "Sir, you have a message from $title on $app. To reply, open Jarvis and say reply, followed by your message.",
                "सर, $app पर $title का संदेश आया है। जवाब देना हो तो जार्विस खोलकर कहिए, रिप्लाई, और अपना संदेश।"
            )
        } else if (title.isNotBlank()) {
            msg = t("Sir, there is a notification from $title on $app.", "सर, $app पर $title की सूचना आई है")
        } else {
            msg = t("Sir, there is a new notification on $app.", "सर, $app पर नई सूचना आई है")
        }
        setLang()
        tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, "notif")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

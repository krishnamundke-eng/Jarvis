
package com.krishna.jarvis
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ExifInterface
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private val CARD = Color.parseColor("#141414")
    private val EDGE = Color.parseColor("#2A2A2A")
    private val ACCENT = Color.parseColor("#2E8BFF")
    private val MUTED = Color.parseColor("#8A8A8A")

    private lateinit var prefs: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var homeView: View
    private lateinit var chatView: View
    private lateinit var settingsView: View
    private lateinit var homeStatus: TextView
    private lateinit var chatList: LinearLayout
    private lateinit var chatScroll: ScrollView
    private val navItems = mutableListOf<TextView>()

    private var tts: TextToSpeech? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var viaVoice = false
    private var torchOn = false
    private val history = mutableListOf<String>()

    private val appFillers = setOf(
        "jarvis", "open", "kholo", "khol", "do", "the", "app", "ko",
        "karo", "kar", "please", "launch", "start", "chalu", "a", "an"
    )
    private val callFillers = setOf(
        "jarvis", "call", "phone", "lagao", "dial", "ring", "to",
        "ko", "karo", "kar", "kro", "please", "a", "me", "the", "do"
    )
    private val playFillers = setOf(
        "jarvis", "play", "chalao", "chala", "gaana", "gana", "song", "video",
        "youtube", "on", "kar", "karo", "do", "ka", "ki", "ko", "the", "please"
    )
    private val greetings = setOf(
        "hi", "hello", "hey", "namaste", "hello jarvis", "hi jarvis", "hey jarvis"
    )

    private val monthsHi = arrayOf(
        "जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून",
        "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"
    )
    private val daysHi = arrayOf(
        "रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"
    )
    private val monthsEn = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    private val daysEn = arrayOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    )

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.lowercase()
        if (text != null) {
            viaVoice = true
            addUser(text)
            handle(text)
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) saveBackground(uri)
    }

    // ---------- helpers ----------

    private fun isHindi(): Boolean = prefs.getString("lang", "en") == "hi"

    private fun t(en: String, hi: String): String = if (isHindi()) hi else en

    private fun toast(m: String) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight)

    private fun round(color: Int, r: Int, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(r).toFloat()
        if (stroke > 0) d.setStroke(dp(stroke), strokeColor)
        return d
    }

    private fun greeting(): String =
        t("Hi! I'm Jarvis. How can I help you?", "नमस्ते! मैं जार्विस हूँ। बताइए, क्या करूँ?")

    private fun setTtsLanguage() {
        val loc = if (isHindi()) Locale("hi", "IN") else Locale("en", "IN")
        val r = tts?.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
        }
    }

    private fun applyBackground() {
        val f = File(filesDir, "bg.jpg")
        if (prefs.getBoolean("custombg", false) && f.exists()) {
            val bmp = BitmapFactory.decodeFile(f.absolutePath)
            if (bmp != null) {
                val layers = arrayOf<Drawable>(
                    BitmapDrawable(resources, bmp),
                    ColorDrawable(Color.parseColor("#99000000"))
                )
                root.background = LayerDrawable(layers)
                return
            }
        }
        root.setBackgroundColor(Color.BLACK)
    }

    private fun saveBackground(uri: Uri) {
        try {
            val dm = resources.displayMetrics
            val sw = dm.widthPixels
            val sh = dm.heightPixels

            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sw && bounds.outHeight / (sample * 2) >= sh) {
                sample *= 2
            }
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val decoded: Bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: run {
                toast(t("Could not open the picture", "फोटो नहीं खुल पाई"))
                return
            }

            val orientation = try {
                contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                } ?: 1
            } catch (e: Exception) {
                1
            }
            val deg = when (orientation) {
                6 -> 90f
                3 -> 180f
                8 -> 270f
                else -> 0f
            }
            var bmp: Bitmap = decoded
            if (deg != 0f) {
                val m = Matrix()
                m.postRotate(deg)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }

            val scale = maxOf(sw.toFloat() / bmp.width, sh.toFloat() / bmp.height)
            val nw = maxOf(sw, (bmp.width * scale).toInt() + 1)
            val nh = maxOf(sh, (bmp.height * scale).toInt() + 1)
            val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
            val cropped = Bitmap.createBitmap(scaled, (nw - sw) / 2, (nh - sh) / 2, sw, sh)

            FileOutputStream(File(filesDir, "bg.jpg")).use {
                cropped.compress(Bitmap.CompressFormat.JPEG, 85, it)
            }
            prefs.edit().putBoolean("custombg", true).apply()
            applyBackground()
            toast(t("Background updated", "बैकग्राउंड बदल गया"))
        } catch (e: Exception) {
            toast(t("Could not set the background", "बैकग्राउंड नहीं लग पाया"))
        }
    }

    private fun showTab(index: Int) {
        homeView.visibility = if (index == 0) View.VISIBLE else View.GONE
        chatView.visibility = if (index == 1) View.VISIBLE else View.GONE
        settingsView.visibility = if (index == 2) View.VISIBLE else View.GONE
        for ((i, nav) in navItems.withIndex()) {
            nav.setTextColor(if (i == index) Color.WHITE else MUTED)
        }
    }

    private fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, t("Speak now...", "बोलिए..."))
        voiceLauncher.launch(i)
    }

    private fun askPhonePermissions() {
        requestPermissions(
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.ANSWER_PHONE_CALLS
            ), 1
        )
    }

    // ---------- chat bubbles ----------

    private fun scrollChat() {
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addUser(msg: String) {
        val tv = TextView(this)
        tv.text = msg
        tv.textSize = 16f
        tv.setTextColor(Color.WHITE)
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.background = round(Color.parseColor("#262626"), 18)
        tv.maxWidth = dp(280)
        val p = LinearLayout.LayoutParams(WRAP, WRAP)
        p.gravity = Gravity.END
        p.setMargins(dp(40), dp(8), dp(12), dp(4))
        chatList.addView(tv, p)
        scrollChat()
    }

    private fun addAssistant(msg: String): TextView {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), dp(8))

        val name = TextView(this)
        name.text = "JARVIS"
        name.textSize = 12f
        name.setTypeface(null, Typeface.BOLD)
        name.setTextColor(MUTED)
        box.addView(name, lp(WRAP, WRAP))

        val tv = TextView(this)
        tv.text = msg
        tv.textSize = 16f
        tv.setTextColor(Color.WHITE)
        tv.setLineSpacing(0f, 1.15f)
        tv.setTextIsSelectable(true)
        val tp = lp(MATCH, WRAP)
        tp.topMargin = dp(2)
        box.addView(tv, tp)

        chatList.addView(box, lp(MATCH, WRAP))
        scrollChat()
        return tv
    }

    private fun speakOut(msg: String, speak: Boolean) {
        if (speak && prefs.getBoolean("voice", true)) {
            tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        }
    }

    private fun say(msg: String, speak: Boolean = viaVoice) {
        homeStatus.text = msg
        addAssistant(msg)
        speakOut(msg, speak)
    }

    private fun sendTyped(input: EditText) {
        val typed = input.text.toString().trim()
        if (typed.isEmpty()) return
        input.setText("")
        viaVoice = false
        addUser(typed)
        handle(typed.lowercase(), typed)
    }

    // ---------- screens ----------

    private fun buildHome(): View {
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.CENTER_HORIZONTAL
        v.setPadding(dp(16), dp(24), dp(16), dp(12))

        val title = TextView(this)
        title.text = "JARVIS"
        title.textSize = 38f
        title.setTypeface(null, Typeface.BOLD)
        title.letterSpacing = 0.3f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        val tp = lp(WRAP, WRAP)
        tp.topMargin = dp(40)
        v.addView(title, tp)

        val sub = TextView(this)
        sub.text = t("Your personal assistant", "आपका निजी सहायक")
        sub.textSize = 14f
        sub.setTextColor(MUTED)
        sub.gravity = Gravity.CENTER
        val sp = lp(WRAP, WRAP)
        sp.topMargin = dp(6)
        v.addView(sub, sp)

        val mic = TextView(this)
        mic.text = "🎙"
        mic.textSize = 56f
        mic.gravity = Gravity.CENTER
        val oval = GradientDrawable()
        oval.shape = GradientDrawable.OVAL
        oval.setColor(Color.parseColor("#0D0D0D"))
        oval.setStroke(dp(3), Color.WHITE)
        mic.background = oval
        mic.setOnClickListener { startVoice() }
        val mp = lp(dp(170), dp(170))
        mp.topMargin = dp(44)
        v.addView(mic, mp)

        homeStatus = TextView(this)
        homeStatus.text = t("Tap the mic and speak", "माइक दबाकर बोलिए")
        homeStatus.textSize = 16f
        homeStatus.setTextColor(Color.WHITE)
        homeStatus.gravity = Gravity.CENTER
        homeStatus.maxWidth = dp(320)
        val hp = lp(WRAP, WRAP)
        hp.topMargin = dp(26)
        v.addView(homeStatus, hp)

        v.addView(View(this), lp(MATCH, 0, 1f))

        fun tile(label: String, onTap: () -> Unit): TextView {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 14f
            tv.setTextColor(Color.WHITE)
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(6), dp(14), dp(6), dp(14))
            tv.background = round(CARD, 16, 1, EDGE)
            tv.setOnClickListener { onTap() }
            return tv
        }

        fun tileRow(vararg tiles: TextView) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (tv in tiles) {
                val p = lp(0, WRAP, 1f)
                p.setMargins(dp(4), dp(4), dp(4), dp(4))
                row.addView(tv, p)
            }
            v.addView(row, lp(MATCH, WRAP))
        }

        tileRow(
            tile("📱\n" + t("Apps", "ऐप्स")) {
                homeStatus.text = t("Say: open YouTube", "बोलिए: open YouTube")
            },
            tile("📞\n" + t("Call", "कॉल")) {
                homeStatus.text = t("Say: call Rahul", "बोलिए: call Rahul")
            },
            tile("⏰\n" + t("Alarm", "अलार्म")) {
                homeStatus.text = t("Say: set alarm 6 30", "बोलिए: set alarm 6 30")
            }
        )
        tileRow(
            tile("🔦\n" + t("Torch", "टॉर्च")) {
                viaVoice = false
                handle(if (torchOn) "torch off" else "torch on")
            },
            tile("🕒\n" + t("Time", "समय")) {
                viaVoice = false
                handle("what is the time")
            },
            tile("🔋\n" + t("Battery", "बैटरी")) {
                viaVoice = false
                handle("battery")
            }
        )
        return v
    }

    private fun buildChat(): View {
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(18), dp(16), dp(12), dp(10))

        val title = TextView(this)
        title.text = "JARVIS"
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        title.letterSpacing = 0.15f
        title.setTextColor(Color.WHITE)
        head.addView(title, lp(0, WRAP, 1f))

        val fresh = TextView(this)
        fresh.text = t("＋ New chat", "＋ नई चैट")
        fresh.textSize = 14f
        fresh.setTextColor(Color.WHITE)
        fresh.setPadding(dp(14), dp(8), dp(14), dp(8))
        fresh.background = round(CARD, 16, 1, EDGE)
        fresh.setOnClickListener {
            chatList.removeAllViews()
            history.clear()
            addAssistant(greeting())
        }
        head.addView(fresh, lp(WRAP, WRAP))
        v.addView(head, lp(MATCH, WRAP))

        chatScroll = ScrollView(this)
        chatList = LinearLayout(this)
        chatList.orientation = LinearLayout.VERTICAL
        chatScroll.addView(chatList, ViewGroup.LayoutParams(MATCH, WRAP))
        v.addView(chatScroll, lp(MATCH, 0, 1f))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(10), dp(8), dp(10), dp(8))

        val input = EditText(this)
        input.hint = t("Message JARVIS...", "JARVIS को संदेश...")
        input.setHintTextColor(MUTED)
        input.setTextColor(Color.WHITE)
        input.maxLines = 4
        input.setPadding(dp(16), dp(10), dp(16), dp(10))
        input.background = round(CARD, 24, 1, EDGE)
        bar.addView(input, lp(0, WRAP, 1f))

        val micBtn = TextView(this)
        micBtn.text = "🎙"
        micBtn.textSize = 22f
        micBtn.gravity = Gravity.CENTER
        micBtn.setOnClickListener { startVoice() }
        val mbp = lp(dp(44), dp(44))
        mbp.setMargins(dp(6), 0, 0, 0)
        bar.addView(micBtn, mbp)

        val send = TextView(this)
        send.text = "➤"
        send.textSize = 20f
        send.setTextColor(Color.BLACK)
        send.gravity = Gravity.CENTER
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Color.WHITE)
        send.background = circle
        send.setOnClickListener { sendTyped(input) }
        val sbp = lp(dp(44), dp(44))
        sbp.setMargins(dp(6), 0, 0, 0)
        bar.addView(send, sbp)

        v.addView(bar, lp(MATCH, WRAP))
        return v
    }

    private fun section(parent: LinearLayout, title: String) {
        val tv = TextView(this)
        tv.text = title.uppercase()
        tv.textSize = 12f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(MUTED)
        val p = lp(WRAP, WRAP)
        p.topMargin = dp(26)
        p.bottomMargin = dp(8)
        parent.addView(tv, p)
    }

    private fun actionButton(parent: LinearLayout, label: String, onTap: () -> Unit) {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = 16f
        tv.setTextColor(Color.WHITE)
        tv.setPadding(dp(16), dp(14), dp(16), dp(14))
        tv.background = round(CARD, 14, 1, EDGE)
        tv.setOnClickListener { onTap() }
        val p = lp(MATCH, WRAP)
        p.bottomMargin = dp(8)
        parent.addView(tv, p)
    }

    private fun switchRow(parent: LinearLayout, label: String, key: String, def: Boolean) {
        val sw = Switch(this)
        sw.text = label
        sw.setTextColor(Color.WHITE)
        sw.textSize = 16f
        sw.isChecked = prefs.getBoolean(key, def)
        sw.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(key, checked).apply()
        }
        sw.setPadding(dp(16), dp(14), dp(16), dp(14))
        sw.background = round(CARD, 14, 1, EDGE)
        val p = lp(MATCH, WRAP)
        p.bottomMargin = dp(8)
        parent.addView(sw, p)
    }

    private fun buildSettings(): View {
        val scroll = ScrollView(this)
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL
        v.setPadding(dp(18), dp(22), dp(18), dp(24))
        scroll.addView(v, ViewGroup.LayoutParams(MATCH, WRAP))

        val title = TextView(this)
        title.text = t("Settings", "सेटिंग्स")
        title.textSize = 24f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.WHITE)
        v.addView(title, lp(WRAP, WRAP))

        section(v, t("General", "सामान्य"))
        switchRow(v, t("Spoken replies when I speak", "बोलने पर बोलकर जवाब दें"), "voice", true)
        switchRow(v, t("Read notifications aloud", "नोटिफिकेशन बोलकर बताएं"), "notif", true)

        section(v, t("Language", "भाषा"))
        val langRow = LinearLayout(this)
        langRow.orientation = LinearLayout.HORIZONTAL
        for ((code, name) in listOf("en" to "English", "hi" to "हिन्दी")) {
            val tv = TextView(this)
            tv.text = name
            tv.textSize = 16f
            tv.gravity = Gravity.CENTER
            val selected = (prefs.getString("lang", "en") == code)
            tv.setTextColor(if (selected) Color.BLACK else Color.WHITE)
            tv.background = round(if (selected) Color.WHITE else CARD, 14, 1, EDGE)
            tv.setPadding(0, dp(14), 0, dp(14))
            tv.setOnClickListener {
                prefs.edit().putString("lang", code).putInt("starttab", 2).apply()
                recreate()
            }
            val p = lp(0, WRAP, 1f)
            p.setMargins(dp(4), 0, dp(4), 0)
            langRow.addView(tv, p)
        }
        v.addView(langRow, lp(MATCH, WRAP))

        section(v, t("Background", "बैकग्राउंड"))
        actionButton(v, "🖼  " + t("Choose from gallery", "गैलरी से चुनें")) {
            pickImage.launch("image/*")
        }
        actionButton(v, "⬛  " + t("Reset to black", "काला बैकग्राउंड")) {
            prefs.edit().putBoolean("custombg", false).apply()
            applyBackground()
            toast(t("Background reset", "बैकग्राउंड रीसेट हो गया"))
        }

        section(v, t("AI (Gemini)", "AI (Gemini)"))
        val keyInput = EditText(this)
        keyInput.hint = t("Paste your Gemini API key", "Gemini API key यहाँ डालिए")
        keyInput.setHintTextColor(MUTED)
        keyInput.setTextColor(Color.WHITE)
        keyInput.setSingleLine()
        keyInput.setText(prefs.getString("apikey", "") ?: "")
        keyInput.setPadding(dp(16), dp(14), dp(16), dp(14))
        keyInput.background = round(CARD, 14, 1, EDGE)
        val kp = lp(MATCH, WRAP)
        kp.bottomMargin = dp(8)
        v.addView(keyInput, kp)
        actionButton(v, "💾  " + t("Save key", "key सेव करें")) {
            prefs.edit().putString("apikey", keyInput.text.toString().trim()).apply()
            toast(t("Saved", "सेव हो गया"))
        }

        section(v, t("Phone control", "फोन कंट्रोल"))
        actionButton(v, "📞  " + t("Allow calls and contacts", "कॉल और कॉन्टैक्ट की अनुमति")) {
            askPhonePermissions()
        }
        actionButton(v, "🔔  " + t("Notification access", "नोटिफिकेशन एक्सेस")) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        actionButton(v, "🛡  " + t("App permissions", "ऐप की अनुमतियाँ")) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        actionButton(v, "📶  " + t("Wi-Fi settings", "वाई-फाई सेटिंग्स")) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        actionButton(v, "🔵  " + t("Bluetooth settings", "ब्लूटूथ सेटिंग्स")) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        section(v, t("Try saying", "ये बोलकर देखें"))
        val tips = TextView(this)
        tips.text = t(
            "call Rahul\nend call\nspeaker on\nset alarm 6 30\nplay Arijit Singh\nopen YouTube\ntorch on\nvolume up\nwifi settings\nbattery\nwhat is the time\nreply I am coming",
            "call Rahul\nend call\nspeaker on\nset alarm 6 30\nplay Arijit Singh\nopen YouTube\ntorch on\nvolume up\nwifi settings\nbattery\nwhat is the time\nreply I am coming"
        )
        tips.textSize = 14f
        tips.setTextColor(MUTED)
        tips.setLineSpacing(0f, 1.2f)
        tips.setPadding(dp(16), dp(14), dp(16), dp(14))
        tips.background = round(CARD, 14, 1, EDGE)
        v.addView(tips, lp(MATCH, WRAP))

        return scroll
    }

    private fun buildNav(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(Color.parseColor("#0A0A0A"))
        val names = listOf(
            "🏠\n" + t("Home", "होम"),
            "💬\n" + t("Chat", "चैट"),
            "⚙️\n" + t("Settings", "सेटिंग्स")
        )
        for ((i, n) in names.withIndex()) {
            val tv = TextView(this)
            tv.text = n
            tv.textSize = 13f
            tv.gravity = Gravity.CENTER
            tv.setPadding(0, dp(10), 0, dp(10))
            tv.setOnClickListener { showTab(i) }
            navItems.add(tv)
            bar.addView(tv, lp(0, WRAP, 1f))
        }
        return bar
    }

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)

        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) setTtsLanguage()
        }

        askPhonePermissions()

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val content = FrameLayout(this)
        homeView = buildHome()
        chatView = buildChat()
        settingsView = buildSettings()
        content.addView(homeView)
        content.addView(chatView)
        content.addView(settingsView)
        root.addView(content, lp(MATCH, 0, 1f))
        root.addView(buildNav(), lp(MATCH, WRAP))
        setContentView(root)

        applyBackground()
        showTab(prefs.getInt("starttab", 0))
        prefs.edit().putInt("starttab", 0).apply()
        addAssistant(greeting())

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                say(
                    t(
                        "Battery is low. Please plug in the charger.",
                        "बैटरी कम हो गई है, कृपया चार्जर लगा दीजिए"
                    ),
                    true
                )
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_LOW))
    }

    override fun onDestroy() {
        batteryReceiver?.let { unregisterReceiver(it) }
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    // ---------- commands ----------

    private fun has(p: String): Boolean =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun clean(text: String, fillers: Set<String>): String {
        val words = text.split(" ").filter { it.isNotBlank() && it !in fillers }
        val unique = mutableListOf<String>()
        for (w in words) {
            if (unique.isEmpty() || unique.last() != w) unique.add(w)
        }
        return unique.joinToString(" ")
    }

    private fun setTorch(on: Boolean) {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.setTorchMode(cm.cameraIdList[0], on)
        torchOn = on
    }

    private fun findNumber(name: String): String? {
        if (!has(Manifest.permission.READ_CONTACTS)) return null
        val cur = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )
        cur?.use {
            while (it.moveToNext()) {
                val n = it.getString(0)?.lowercase() ?: continue
                if (n.contains(name)) return it.getString(1)
            }
        }
        return null
    }

    private fun makeCall(text: String) {
        val name = clean(text, callFillers)
        if (name.isEmpty()) {
            say(t("Who should I call?", "किसको कॉल करना है?"))
            return
        }
        if (!has(Manifest.permission.CALL_PHONE)) {
            say(t("I don't have permission to make calls.", "कॉल की अनुमति नहीं मिली"))
            return
        }
        val digits = name.replace(" ", "")
        val number = if (digits.all { it.isDigit() }) digits else findNumber(name)
        if (number == null) {
            say(t("I couldn't find a contact named $name.", "$name नाम का कॉन्टैक्ट नहीं मिला"))
            return
        }
        say(t("Calling $name.", "$name को कॉल लगा रहा हूँ"))
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))))
    }

    @Suppress("DEPRECATION")
    private fun endCall() {
        if (Build.VERSION.SDK_INT >= 28 && has(Manifest.permission.ANSWER_PHONE_CALLS)) {
            val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.endCall()
            say(t("Call ended.", "कॉल काट दी"))
        } else {
            say(t("I don't have permission to end calls.", "कॉल काटने की अनुमति नहीं है"))
        }
    }

    @Suppress("DEPRECATION")
    private fun setSpeaker(on: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = on
        say(
            if (on) t("Speaker is on.", "स्पीकर चालू कर दिया")
            else t("Speaker is off.", "स्पीकर बंद कर दिया")
        )
    }

    private fun changeVolume(mode: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (mode) {
            1 -> {
                for (i in 1..3) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                say(t("Volume increased.", "आवाज़ बढ़ा दी"))
            }
            0 -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                say(t("Muted.", "आवाज़ बंद कर दी"))
            }
            else -> {
                for (i in 1..3) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                say(t("Volume decreased.", "आवाज़ कम कर दी"))
            }
        }
    }

    private fun setAlarm(text: String) {
        val m = Regex("(\\d{1,2})(?:[:. ](\\d{2}))?").find(text)
        if (m == null) {
            say(t("What time should I set the alarm for?", "कितने बजे का अलार्म लगाना है?"))
            return
        }
        var h = m.groupValues[1].toInt()
        val min = if (m.groupValues[2].isEmpty()) 0 else m.groupValues[2].toInt()
        val pm = text.contains("p.m") || text.contains("pm") ||
            text.contains("shaam") || text.contains("raat") || text.contains("evening")
        if (pm && h < 12) h += 12
        if (h > 23 || min > 59) {
            say(t("I didn't understand the time.", "समय समझ नहीं आया"))
            return
        }
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
        i.putExtra(AlarmClock.EXTRA_HOUR, h)
        i.putExtra(AlarmClock.EXTRA_MINUTES, min)
        i.putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        startActivity(i)
        val mm = min.toString().padStart(2, '0')
        say(t("Alarm set for $h:$mm.", "अलार्म $h बजकर $min मिनट पर लगा दिया"))
    }

    private fun playQuery(text: String) {
        val q = clean(text, playFillers)
        if (q.isEmpty()) {
            say(t("What should I play?", "क्या चलाना है?"))
            return
        }
        say(t("Playing $q.", "$q चला रहा हूँ"))
        try {
            val i = Intent(Intent.ACTION_SEARCH)
            i.setPackage("com.google.android.youtube")
            i.putExtra("query", q)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(i)
        } catch (e: Exception) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))
                )
            )
        }
    }

    private fun tellBattery() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        when {
            level < 20 -> say(
                t(
                    "Battery is only $level percent. Please plug in the charger.",
                    "बैटरी सिर्फ $level प्रतिशत है, कृपया चार्जर लगा दीजिए"
                )
            )
            level < 50 -> say(
                t(
                    "Battery is at $level percent. Charge it soon.",
                    "बैटरी $level प्रतिशत है, जल्दी चार्ज कर लीजिए"
                )
            )
            else -> say(
                t(
                    "Battery is at $level percent. All good.",
                    "बैटरी $level प्रतिशत है, सब ठीक है"
                )
            )
        }
    }

    private fun tellTime() {
        val c = Calendar.getInstance()
        val hh = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE)
        var h = c.get(Calendar.HOUR)
        if (h == 0) h = 12
        if (isHindi()) {
            val period = when {
                hh < 12 -> "सुबह"
                hh < 16 -> "दोपहर"
                hh < 20 -> "शाम"
                else -> "रात"
            }
            if (m == 0) say("अभी $period के $h बजे हैं")
            else say("अभी $period के $h बजकर $m मिनट हुए हैं")
        } else {
            val ap = if (hh < 12) "AM" else "PM"
            say("It's $h:${m.toString().padStart(2, '0')} $ap")
        }
    }

    private fun tellDate() {
        val c = Calendar.getInstance()
        val d = c.get(Calendar.DAY_OF_MONTH)
        val mi = c.get(Calendar.MONTH)
        val y = c.get(Calendar.YEAR)
        val wi = c.get(Calendar.DAY_OF_WEEK) - 1
        if (isHindi()) say("आज $d ${monthsHi[mi]} $y, ${daysHi[wi]} है")
        else say("Today is ${daysEn[wi]}, $d ${monthsEn[mi]} $y")
    }

    private fun openApp(text: String) {
        val name = clean(text, appFillers)
        if (name.isEmpty()) {
            say(t("Which app should I open?", "कौन सा ऐप खोलना है?"))
            return
        }
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val match = apps.firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase()
            label.contains(name) || (label.length >= 3 && name.contains(label))
        }
        val launch = match?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (launch != null) {
            say(t("Opening $name.", "$name खोल रहा हूँ"))
            startActivity(launch)
        } else {
            say(t("I couldn't find an app called $name.", "$name नाम का ऐप नहीं मिला"))
        }
    }

    private fun askAi(question: String) {
        val key = prefs.getString("apikey", "") ?: ""
        if (key.isEmpty()) {
            say(
                t(
                    "Please add your Gemini API key in Settings first.",
                    "पहले सेटिंग्स में Gemini API key डालिए।"
                )
            )
            return
        }
        val speak = viaVoice
        val view = addAssistant(t("Thinking...", "सोच रहा हूँ..."))
        val sb = StringBuilder()
        if (history.isNotEmpty()) {
            sb.append("Conversation so far:\n")
            for (h in history) sb.append(h).append("\n")
            sb.append("\nNow answer the latest user message below.\n")
        }
        sb.append("User: ").append(question)
        Ai.ask(key, sb.toString(), isHindi()) { reply ->
            runOnUiThread {
                view.text = reply
                homeStatus.text = reply
                history.add("User: $question")
                history.add("Jarvis: $reply")
                while (history.size > 12) history.removeAt(0)
                speakOut(reply, speak)
                scrollChat()
            }
        }
    }

    private fun handle(text: String, original: String = text) {
        val ws = text.split(" ")
        val words = ws.size

        when {
            text.startsWith("reply") || text.startsWith("jawab") ->
                say(JarvisNotificationService.sendReply(text))

            text.contains("message padho") || text.contains("read message") ->
                say(JarvisNotificationService.readLast())

            text in greetings ->
                say(t("Hello! I'm Jarvis. How can I help?", "नमस्ते, मैं जार्विस हूँ। बताइए, क्या करूँ?"))

            (text.startsWith("play ") && !text.contains("store")) ||
                text.contains("gaana") || text.contains("song chalao") -> playQuery(text)

            text.contains("alarm") && words <= 8 -> setAlarm(text)

            ("call" in ws && ("cut" in ws || "end" in ws || "kaat" in ws || "disconnect" in ws)) ||
                text.contains("hang up") -> endCall()

            text.contains("speaker") && words <= 5 ->
                setSpeaker(!(text.contains("off") || text.contains("band")))

            text.startsWith("call ") || text.startsWith("dial ") ||
                text.contains("phone lagao") -> makeCall(text)

            text.contains("volume") && words <= 5 -> {
                when {
                    text.contains("up") || text.contains("increase") || text.contains("badha") -> changeVolume(1)
                    text.contains("mute") -> changeVolume(0)
                    else -> changeVolume(-1)
                }
            }

            (text.contains("wifi") || text.contains("wi-fi") || text.contains("wi fi")) && words <= 5 -> {
                say(t("Opening Wi-Fi settings.", "वाई-फाई सेटिंग्स खोल रहा हूँ"))
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }

            text.contains("bluetooth") && words <= 5 -> {
                say(t("Opening Bluetooth settings.", "ब्लूटूथ सेटिंग्स खोल रहा हूँ"))
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }

            ("battery" in ws || "charge" in ws) && words <= 6 -> tellBattery()

            ("time" in ws || "samay" in ws || "baje" in ws) && words <= 6 -> tellTime()

            ("date" in ws || "today" in ws || "day" in ws || "tarikh" in ws) && words <= 6 -> tellDate()

            ("torch" in ws || "flashlight" in ws) && words <= 5 -> {
                if (text.contains("band") || text.contains("off")) {
                    setTorch(false)
                    say(t("Torch turned off.", "टॉर्च बंद कर दी"))
                } else {
                    setTorch(true)
                    say(t("Torch turned on.", "टॉर्च चालू कर दी"))
                }
            }

            ws.firstOrNull() == "open" || "kholo" in ws -> openApp(text)

            else -> askAi(original)
        }
    }
}

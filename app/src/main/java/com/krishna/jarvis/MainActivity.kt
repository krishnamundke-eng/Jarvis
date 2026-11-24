package com.krishna.jarvis

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private val CARD = Color.parseColor("#0F1B33")
    private val ACCENT = Color.parseColor("#1E8CFF")
    private val CYAN = Color.parseColor("#22D3EE")
    private val MUTED = Color.parseColor("#8FA3C0")
    private val EDGE = Color.parseColor("#1F4E8C")

    private lateinit var prefs: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var homeView: View
    private lateinit var chatView: View
    private lateinit var settingsView: View
    private lateinit var homeStatus: TextView
    private lateinit var chatList: LinearLayout
    private lateinit var chatScroll: ScrollView
    private val navItems = mutableListOf<TextView>()

    private var tts: TextToSpeech? = null
    private var batteryReceiver: BroadcastReceiver? = null

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

    private val months = arrayOf(
        "जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून",
        "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"
    )
    private val days = arrayOf(
        "रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"
    )

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.lowercase()
        if (text != null) {
            addBubble(text, true)
            handle(text)
        }
    }

    // ---------- helpers ----------

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

    private fun applyBackground() {
        val colors = when (prefs.getInt("bg", 0)) {
            1 -> intArrayOf(Color.parseColor("#020510"), Color.parseColor("#0B4DBB"))
            2 -> intArrayOf(Color.parseColor("#05010F"), Color.parseColor("#2B1055"))
            else -> intArrayOf(Color.parseColor("#050B1A"), Color.parseColor("#0A2A5E"))
        }
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
    }

    private fun showTab(index: Int) {
        homeView.visibility = if (index == 0) View.VISIBLE else View.GONE
        chatView.visibility = if (index == 1) View.VISIBLE else View.GONE
        settingsView.visibility = if (index == 2) View.VISIBLE else View.GONE
        for ((i, t) in navItems.withIndex()) {
            t.setTextColor(if (i == index) CYAN else MUTED)
        }
    }

    private fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye...")
        voiceLauncher.launch(i)
    }

    private fun addBubble(msg: String, user: Boolean) {
        val tv = TextView(this)
        tv.text = msg
        tv.textSize = 16f
        tv.setTextColor(Color.WHITE)
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.background = round(if (user) ACCENT else CARD, 18)
        tv.maxWidth = dp(270)
        val p = LinearLayout.LayoutParams(WRAP, WRAP)
        p.gravity = if (user) Gravity.END else Gravity.START
        p.setMargins(dp(8), dp(4), dp(8), dp(4))
        chatList.addView(tv, p)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun say(msg: String) {
        homeStatus.text = msg
        addBubble(msg, false)
        if (prefs.getBoolean("voice", true)) {
            tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        }
    }

    // ---------- screens ----------

    private fun buildHome(): View {
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.CENTER_HORIZONTAL
        v.setPadding(dp(16), dp(24), dp(16), dp(12))

        val title = TextView(this)
        val s = SpannableStringBuilder("Hi, I'm JARVIS")
        s.setSpan(ForegroundColorSpan(CYAN), 8, 14, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        title.text = s
        title.textSize = 30f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        val tp = lp(WRAP, WRAP)
        tp.topMargin = dp(30)
        v.addView(title, tp)

        val mic = TextView(this)
        mic.text = "🎙"
        mic.textSize = 64f
        mic.gravity = Gravity.CENTER
        val oval = GradientDrawable()
        oval.shape = GradientDrawable.OVAL
        oval.setColor(CARD)
        oval.setStroke(dp(4), CYAN)
        mic.background = oval
        mic.setOnClickListener { startVoice() }
        val mp = lp(dp(190), dp(190))
        mp.topMargin = dp(36)
        v.addView(mic, mp)

        homeStatus = TextView(this)
        homeStatus.text = "Ready to help"
        homeStatus.textSize = 16f
        homeStatus.setTextColor(Color.WHITE)
        homeStatus.gravity = Gravity.CENTER
        homeStatus.setPadding(dp(18), dp(8), dp(18), dp(8))
        homeStatus.background = round(CARD, 22, 1, CYAN)
        homeStatus.maxWidth = dp(320)
        val sp = lp(WRAP, WRAP)
        sp.topMargin = dp(24)
        v.addView(homeStatus, sp)

        v.addView(View(this), lp(MATCH, 0, 1f))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val tiles = listOf(
            "📱\nOpen Apps" to "Bolo: open YouTube",
            "📞\nPhone" to "Bolo: call Rahul",
            "🔔\nAlarm" to "Bolo: set alarm 6 30"
        )
        for ((label, hint) in tiles) {
            val t = TextView(this)
            t.text = label
            t.textSize = 14f
            t.setTextColor(Color.WHITE)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(8), dp(14), dp(8), dp(14))
            t.background = round(CARD, 16, 1, EDGE)
            t.setOnClickListener { homeStatus.text = hint }
            val p = lp(0, WRAP, 1f)
            p.setMargins(dp(4), 0, dp(4), 0)
            row.addView(t, p)
        }
        v.addView(row, lp(MATCH, WRAP))
        return v
    }

    private fun buildChat(): View {
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL

        val head = TextView(this)
        head.text = "JARVIS  🟢"
        head.textSize = 22f
        head.setTypeface(null, Typeface.BOLD)
        head.setTextColor(Color.WHITE)
        head.gravity = Gravity.CENTER
        head.setPadding(0, dp(18), 0, dp(14))
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
        input.hint = "Message JARVIS..."
        input.setHintTextColor(MUTED)
        input.setTextColor(Color.WHITE)
        input.setSingleLine()
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
        send.setTextColor(Color.WHITE)
        send.gravity = Gravity.CENTER
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(ACCENT)
        send.background = circle
        send.setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) {
                input.setText("")
                addBubble(t, true)
                handle(t.lowercase())
            }
        }
        val sbp = lp(dp(44), dp(44))
        sbp.setMargins(dp(6), 0, 0, 0)
        bar.addView(send, sbp)

        v.addView(bar, lp(MATCH, WRAP))
        return v
    }

    private fun buildSettings(): View {
        val scroll = ScrollView(this)
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL
        v.setPadding(dp(18), dp(22), dp(18), dp(18))
        scroll.addView(v, ViewGroup.LayoutParams(MATCH, WRAP))

        val title = TextView(this)
        title.text = "Settings"
        title.textSize = 24f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.WHITE)
        v.addView(title, lp(WRAP, WRAP))

        val sw = Switch(this)
        sw.text = "Jarvis bolkar jawab de"
        sw.setTextColor(Color.WHITE)
        sw.textSize = 16f
        sw.isChecked = prefs.getBoolean("voice", true)
        sw.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("voice", checked).apply()
        }
        sw.setPadding(dp(16), dp(14), dp(16), dp(14))
        sw.background = round(CARD, 14)
        val swp = lp(MATCH, WRAP)
        swp.topMargin = dp(18)
        v.addView(sw, swp)

        val keyLabel = TextView(this)
        keyLabel.text = "Gemini API Key"
        keyLabel.textSize = 15f
        keyLabel.setTextColor(MUTED)
        val klp = lp(WRAP, WRAP)
        klp.topMargin = dp(22)
        v.addView(keyLabel, klp)

        val keyInput = EditText(this)
        keyInput.hint = "Yahan key paste karo"
        keyInput.setHintTextColor(MUTED)
        keyInput.setTextColor(Color.WHITE)
        keyInput.setSingleLine()
        keyInput.setText(prefs.getString("apikey", "") ?: "")
        keyInput.setPadding(dp(16), dp(12), dp(16), dp(12))
        keyInput.background = round(CARD, 14, 1, EDGE)
        val kip = lp(MATCH, WRAP)
        kip.topMargin = dp(6)
        v.addView(keyInput, kip)

        val bgLabel = TextView(this)
        bgLabel.text = "Background"
        bgLabel.textSize = 15f
        bgLabel.setTextColor(MUTED)
        val blp = lp(WRAP, WRAP)
        blp.topMargin = dp(22)
        v.addView(bgLabel, blp)

        val bgRow = LinearLayout(this)
        bgRow.orientation = LinearLayout.HORIZONTAL
        val names = listOf("Default", "Blue Glow", "Space")
        for ((i, n) in names.withIndex()) {
            val t = TextView(this)
            t.text = n
            t.textSize = 14f
            t.setTextColor(Color.WHITE)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(6), dp(16), dp(6), dp(16))
            t.background = round(CARD, 14, 1, CYAN)
            t.setOnClickListener {
                prefs.edit().putInt("bg", i).apply()
                applyBackground()
                Toast.makeText(this, "Background badal gaya", Toast.LENGTH_SHORT).show()
            }
            val p = lp(0, WRAP, 1f)
            p.setMargins(dp(4), 0, dp(4), 0)
            bgRow.addView(t, p)
        }
        val brp = lp(MATCH, WRAP)
        brp.topMargin = dp(6)
        v.addView(bgRow, brp)

        val save = TextView(this)
        save.text = "💾  Save"
        save.textSize = 18f
        save.setTypeface(null, Typeface.BOLD)
        save.setTextColor(Color.WHITE)
        save.gravity = Gravity.CENTER
        save.setPadding(0, dp(14), 0, dp(14))
        save.background = round(ACCENT, 26)
        save.setOnClickListener {
            prefs.edit().putString("apikey", keyInput.text.toString().trim()).apply()
            Toast.makeText(this, "Save ho gaya", Toast.LENGTH_SHORT).show()
        }
        val svp = lp(MATCH, WRAP)
        svp.topMargin = dp(28)
        v.addView(save, svp)

        return scroll
    }

    private fun buildNav(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(CARD)
        val names = listOf("🏠\nHome", "💬\nChat", "⚙️\nSettings")
        for ((i, n) in names.withIndex()) {
            val t = TextView(this)
            t.text = n
            t.textSize = 13f
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(10), 0, dp(10))
            t.setOnClickListener { showTab(i) }
            navItems.add(t)
            bar.addView(t, lp(0, WRAP, 1f))
        }
        return bar
    }

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.statusBarColor = Color.parseColor("#050B1A")
        window.navigationBarColor = CARD
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)

        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("hi", "IN"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("en", "IN"))
                }
            }
        }

        requestPermissions(
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.ANSWER_PHONE_CALLS
            ), 1
        )

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        content = FrameLayout(this)
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
        showTab(0)
        addBubble("नमस्ते! मैं जार्विस हूँ। बताइए, क्या करूँ?", false)

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                say("बैटरी कम हो गई है, कृपया चार्जर लगा दीजिए")
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
            say("किसको कॉल करना है?")
            return
        }
        if (!has(Manifest.permission.CALL_PHONE)) {
            say("कॉल की अनुमति नहीं मिली")
            return
        }
        val digits = name.replace(" ", "")
        val number = if (digits.all { it.isDigit() }) digits else findNumber(name)
        if (number == null) {
            say("$name नाम का कॉन्टैक्ट नहीं मिला")
            return
        }
        say("$name को कॉल लगा रहा हूँ")
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))))
    }

    @Suppress("DEPRECATION")
    private fun endCall() {
        if (Build.VERSION.SDK_INT >= 28 && has(Manifest.permission.ANSWER_PHONE_CALLS)) {
            val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.endCall()
            say("कॉल काट दी")
        } else {
            say("कॉल काटने की अनुमति नहीं है")
        }
    }

    @Suppress("DEPRECATION")
    private fun setSpeaker(on: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = on
        say(if (on) "स्पीकर चालू कर दिया" else "स्पीकर बंद कर दिया")
    }

    private fun setAlarm(text: String) {
        val m = Regex("(\\d{1,2})(?:[:. ](\\d{2}))?").find(text)
        if (m == null) {
            say("कितने बजे का अलार्म लगाना है?")
            return
        }
        var h = m.groupValues[1].toInt()
        val min = if (m.groupValues[2].isEmpty()) 0 else m.groupValues[2].toInt()
        val pm = text.contains("p.m") || text.contains("pm") ||
            text.contains("shaam") || text.contains("raat") || text.contains("evening")
        if (pm && h < 12) h += 12
        if (h > 23 || min > 59) {
            say("समय समझ नहीं आया")
            return
        }
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
        i.putExtra(AlarmClock.EXTRA_HOUR, h)
        i.putExtra(AlarmClock.EXTRA_MINUTES, min)
        i.putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        startActivity(i)
        say("अलार्म $h बजकर $min मिनट पर लगा दिया")
    }

    private fun playQuery(text: String) {
        val q = clean(text, playFillers)
        if (q.isEmpty()) {
            say("क्या चलाना है?")
            return
        }
        say("$q चला रहा हूँ")
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
            level < 20 -> say("बैटरी सिर्फ $level प्रतिशत है, कृपया चार्जर लगा दीजिए")
            level < 50 -> say("बैटरी $level प्रतिशत है, जल्दी चार्ज कर लीजिए")
            else -> say("बैटरी $level प्रतिशत है, सब ठीक है")
        }
    }

    private fun tellTime() {
        val c = Calendar.getInstance()
        var h = c.get(Calendar.HOUR)
        if (h == 0) h = 12
        val m = c.get(Calendar.MINUTE)
        val hh = c.get(Calendar.HOUR_OF_DAY)
        val period = when {
            hh < 12 -> "सुबह"
            hh < 16 -> "दोपहर"
            hh < 20 -> "शाम"
            else -> "रात"
        }
        if (m == 0) say("अभी $period के $h बजे हैं")
        else say("अभी $period के $h बजकर $m मिनट हुए हैं")
    }

    private fun tellDate() {
        val c = Calendar.getInstance()
        val d = c.get(Calendar.DAY_OF_MONTH)
        val mo = months[c.get(Calendar.MONTH)]
        val y = c.get(Calendar.YEAR)
        val dn = days[c.get(Calendar.DAY_OF_WEEK) - 1]
        say("आज $d $mo $y, $dn है")
    }

    private fun openApp(text: String) {
        val name = clean(text, appFillers)
        if (name.isEmpty()) {
            say("कौन सा ऐप खोलना है?")
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
            say("$name खोल रहा हूँ")
            startActivity(launch)
        } else {
            say("$name नाम का ऐप नहीं मिला")
        }
    }

    private fun handle(text: String) {
        when {
            text.contains("hello") || text.contains("namaste") || text == "hi" ->
                say("नमस्ते, मैं जार्विस हूँ। बताइए, क्या करूँ?")

            (text.startsWith("play ") && !text.contains("store")) ||
                text.contains("gaana") || text.contains("song chalao") -> playQuery(text)

            text.contains("alarm") -> setAlarm(text)

            text.contains("call") &&
                (text.contains("cut") || text.contains("end") || text.contains("kaat") ||
                    text.contains("disconnect")) -> endCall()
            text.contains("hang up") -> endCall()

            text.contains("speaker") ->
                setSpeaker(!(text.contains("off") || text.contains("band")))

            text.contains("call") || text.contains("dial") || text.contains("phone lagao") ->
                makeCall(text)

            text.contains("battery") || text.contains("charge") -> tellBattery()

            text.contains("time") || text.contains("samay") || text.contains("baje") -> tellTime()

            text.contains("date") || text.contains("tarikh") || text.contains("today") -> tellDate()

            text.contains("torch") || text.contains("flashlight") -> {
                if (text.contains("band") || text.contains("off")) {
                    setTorch(false)
                    say("टॉर्च बंद कर दी")
                } else {
                    setTorch(true)
                    say("टॉर्च चालू कर दी")
                }
            }
            else -> {
                if (text.startsWith("open ") || text.contains("kholo")) {
                    openApp(text)
                } else {
                    val key = prefs.getString("apikey", "") ?: ""
                    if (key.isEmpty()) {
                        say("पहले सेटिंग्स में API key डालिए")
                    } else {
                        addBubble("सोच रहा हूँ...", false)
                        Ai.ask(key, text) { reply -> runOnUiThread { say(reply) } }
                    }
                }
            }
            

package com.krishna.jarvis
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private var tts: TextToSpeech? = null
    private var batteryReceiver: BroadcastReceiver? = null

    private val appFillers = setOf(
        "jarvis", "open", "kholo", "khol", "do", "the", "app", "ko",
        "karo", "kar", "please", "launch", "start", "chalu", "a", "an"
    )
    private val callFillers = setOf(
        "jarvis", "call", "phone", "lagao", "lagao", "dial", "ring", "to",
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
            status.text = "Suna: $text"
            handle(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                say("बैटरी कम हो गई है, कृपया चार्जर लगा दीजिए")
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_LOW))

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER

        val button = Button(this)
        button.text = "Jarvis se bolo"
        status = TextView(this)
        status.gravity = Gravity.CENTER
        status.textSize = 18f
        status.text = "Bolo: hello, time, date, battery, torch on, call Rahul, set alarm 6 30, play Arijit Singh, open YouTube"

        layout.addView(button)
        layout.addView(status)
        setContentView(layout)

        button.setOnClickListener {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye...")
            voiceLauncher.launch(i)
        }
    }

    override fun onDestroy() {
        batteryReceiver?.let { unregisterReceiver(it) }
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun say(msg: String) {
        status.text = msg
        tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

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

            else -> openApp(text)
        }
    }
}

package com.krishna.jarvis

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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

    private val fillers = setOf(
        "jarvis", "open", "kholo", "khol", "do", "the", "app", "ko",
        "karo", "kar", "please", "launch", "start", "chalu", "a", "an"
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

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER

        val button = Button(this)
        button.text = "Jarvis se bolo"
        status = TextView(this)
        status.gravity = Gravity.CENTER
        status.textSize = 18f
        status.text = "Bolo: hello, time, date, torch on, open YouTube"

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
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun say(msg: String) {
        status.text = msg
        tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun setTorch(on: Boolean) {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.setTorchMode(cm.cameraIdList[0], on)
    }

    private fun cleanName(text: String): String {
        val words = text.split(" ").filter { it.isNotBlank() && it !in fillers }
        val unique = mutableListOf<String>()
        for (w in words) {
            if (unique.isEmpty() || unique.last() != w) unique.add(w)
        }
        return unique.joinToString(" ")
    }

    private fun handle(text: String) {
        if (text.contains("hello") || text.contains("namaste") || text == "hi") {
            say("नमस्ते, मैं जार्विस हूँ। बताइए, क्या करूँ?")
            return
        }

        if (text.contains("time") || text.contains("samay") || text.contains("baje")) {
            val c = Calendar.getInstance()
            var h = c.get(Calendar.HOUR) 
            if (h == 0) h = 12
            val m = c.get(Calendar.MINUTE)
            if (m == 0) {
                say("अभी $h बजे हैं")
            } else {
                say("अभी $h बजकर $m मिनट हुए हैं")
            }
            return
        }

        if (text.contains("date") || text.contains("tarikh") || text.contains("today") || text.contains("day")) {
            val c = Calendar.getInstance()
            val d = c.get(Calendar.DAY_OF_MONTH)
            val mo = months[c.get(Calendar.MONTH)]
            val y = c.get(Calendar.YEAR)
            val dn = days[c.get(Calendar.DAY_OF_WEEK) - 1]
            say("आज $d $mo $y, $dn है")
            return
        }

        if (text.contains("torch") || text.contains("flashlight")) {
            if (text.contains("band") || text.contains("off")) {
                setTorch(false)
                say("टॉर्च बंद कर दी")
            } else {
                setTorch(true)
                say("टॉर्च चालू कर दी")
            }
            return
        }

        val name = cleanName(text)
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
}

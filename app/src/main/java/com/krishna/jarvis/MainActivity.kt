package com.krishna.jarvis
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val fillers = setOf(
        "jarvis", "open", "kholo", "khol", "do", "the", "app", "ko",
        "karo", "kar", "please", "launch", "start", "chalu", "a", "an"
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

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER

        val button = Button(this)
        button.text = "Jarvis se bolo"
        status = TextView(this)
        status.gravity = Gravity.CENTER
        status.textSize = 18f
        status.text = "Bolo: torch on, torch off, ya open YouTube"

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
        if (text.contains("torch") || text.contains("flashlight")) {
            if (text.contains("band") || text.contains("off")) {
                setTorch(false)
                status.text = "Torch band kar di"
            } else {
                setTorch(true)
                status.text = "Torch chalu kar di"
            }
            return
        }

        val name = cleanName(text)
        if (name.isEmpty()) {
            status.text = "Kaun sa app kholna hai?"
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
            status.text = "Khol raha hu: $name"
            startActivity(launch)
        } else {
            status.text = "App nahi mila: $name"
        }
    }
}

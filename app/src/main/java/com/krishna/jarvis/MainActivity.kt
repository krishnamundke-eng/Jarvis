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
        status.text = "Bolo: torch chalu, torch band, ya YouTube kholo"

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

        val name = text
            .replace("jarvis", "")
            .replace("kholo", "")
            .replace("open", "")
            .replace("khol do", "")
            .trim()

        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(launcher, 0).firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(name)
        }
        val launch = match?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (launch != null && name.isNotEmpty()) {
            status.text = "Khol raha hu: $name"
            startActivity(launch)
        } else {
            status.text = "App nahi mila: $name"
        }
    }
}

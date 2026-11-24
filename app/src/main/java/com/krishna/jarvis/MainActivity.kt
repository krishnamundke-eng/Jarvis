package com.krishna.jarvis

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var torchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.gravity = Gravity.CENTER

        val button = Button(this)
        button.text = "Torch Chalu"
        layout.addView(button)
        setContentView(layout)

        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        button.setOnClickListener {
            val id = cm.cameraIdList[0]
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            button.text = if (torchOn) "Torch Band" else "Torch Chalu"
        }
    }
}

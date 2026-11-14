package com.camerapro.v1

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.camerapro.v1.camera.Sensors
import com.camerapro.v1.ui.CameraApp
import com.camerapro.v1.ui.CameraProTheme

class MainActivity : ComponentActivity() {

    private lateinit var sensors: Sensors

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensors = Sensors(this)

        setContent {
            CameraProTheme {
                CameraApp(sensors)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensors.start()
    }

    override fun onPause() {
        sensors.stop()
        super.onPause()
    }
}

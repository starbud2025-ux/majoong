package kr.pleosapp.getoff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kr.pleosapp.getoff.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as GetOffApp
        setContent { AppRoot(app.controller, app.settings, app.speaker, app.backend) }
    }
}

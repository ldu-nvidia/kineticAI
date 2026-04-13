package com.kineticai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kineticai.app.ui.KineticAIAppRoot
import com.kineticai.app.ui.theme.KineticAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KineticAITheme {
                KineticAIAppRoot()
            }
        }
    }
}

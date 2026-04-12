package com.mycarv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mycarv.app.ui.MyCarvAppRoot
import com.mycarv.app.ui.theme.MyCarvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyCarvTheme {
                MyCarvAppRoot()
            }
        }
    }
}

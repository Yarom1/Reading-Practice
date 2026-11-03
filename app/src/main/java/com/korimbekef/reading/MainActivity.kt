package com.korimbekef.reading

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.korimbekef.reading.ui.AppRoot
import com.korimbekef.reading.ui.theme.KorimTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KorimTheme {
                AppRoot()
            }
        }
    }
}

package com.ttsreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ttsreader.ui.theme.TtsReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TtsReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Text("TTS Reader", modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

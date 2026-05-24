// FILE: MainActivity.kt
package com.chan.mimi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chan.mimi.navigation.ChanNavGraph
import com.chan.mimi.ui.theme.ChanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChanTheme {
                ChanNavGraph()  // ← entire app is just this one line
            }
        }
    }
}
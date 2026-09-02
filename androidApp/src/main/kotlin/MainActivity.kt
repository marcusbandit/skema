package com.marcusrosado.skemafordtu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.marcusrosado.skemafordtu.data.SkemaAndroid

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Storage lives in the shared module, which cannot see a Context. This
        // is the one place Android has to hand one over.
        SkemaAndroid.init(applicationContext)
        // The first, default call. The app makes it again with its own theme
        // once that is known; see SystemBars.
        enableEdgeToEdge()
        setContent { App() }
    }
}

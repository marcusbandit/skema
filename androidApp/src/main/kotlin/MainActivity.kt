package dk.bandit.skema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dk.bandit.skema.data.SkemaAndroid
import dk.bandit.skema.shared.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Storage lives in common code, which cannot see a Context. This is
        // the one place Android has to hand one over.
        SkemaAndroid.init(applicationContext)
        enableEdgeToEdge()
        setContent { App() }
    }
}

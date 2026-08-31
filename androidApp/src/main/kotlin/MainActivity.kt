package dk.bandit.skema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dk.bandit.skema.shared.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

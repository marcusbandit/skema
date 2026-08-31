package dk.bandit.skema.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun App() {
    val lines = unfold("BEGIN:VCALENDAR\r\nEND:VCALENDAR")
    MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            Text("parsed " + lines.size.toString() + " content lines")
        }
    }
}

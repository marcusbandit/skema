import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.marcusrosado.skemafordtu.shared.App

/**
 * The same App() the phone runs, in a window. Handy for working on the UI
 * without waiting for an emulator.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Skema",
        state = rememberWindowState(width = 460.dp, height = 860.dp),
    ) {
        App()
    }
}

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dk.bandit.skema.shared.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "calfeed") { App() }
}

package io.github.rudtjr1106.switchboard.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Switchboard") {
        MaterialTheme {
            Text("Switchboard ${BuildInfo.VERSION}")
        }
    }
}

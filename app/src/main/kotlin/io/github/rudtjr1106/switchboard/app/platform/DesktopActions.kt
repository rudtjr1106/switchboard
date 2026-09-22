package io.github.rudtjr1106.switchboard.app.platform

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

object DesktopActions {

    fun openUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            } else {
                // Linux 일부 환경은 AWT Desktop 이 없다
                ProcessBuilder("xdg-open", url).start()
            }
        }.onFailure { logger.warn(it) { "브라우저를 열지 못했어요: $url" } }
    }

    fun reveal(path: Path) {
        runCatching {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(path.toFile()) else desktop.open(path.parent.toFile())
        }.onFailure { logger.warn(it) { "Finder/탐색기를 열지 못했어요: $path" } }
    }

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}

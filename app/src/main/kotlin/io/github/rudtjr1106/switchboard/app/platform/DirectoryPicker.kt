package io.github.rudtjr1106.switchboard.app.platform

import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JFileChooser

/** 폴더 선택 창. macOS 는 네이티브 FileDialog 가 폴더를 고를 수 있고, 다른 OS 는 Swing 선택기를 쓴다 */
object DirectoryPicker {

    fun pick(parent: Frame?, title: String, start: Path? = null): Path? {
        if (AppPaths.os == OperatingSystem.MAC) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val dialog = FileDialog(parent, title, FileDialog.LOAD)
                start?.let { dialog.directory = it.toString() }
                dialog.isVisible = true
                val dir = dialog.directory ?: return null
                val file = dialog.file ?: return null
                return Paths.get(dir, file)
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        }
        val chooser = JFileChooser(start?.toFile()).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
    }
}

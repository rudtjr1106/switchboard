package io.github.rudtjr1106.switchboard.app.platform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

enum class OperatingSystem { MAC, WINDOWS, LINUX }

/** OS 별 앱 데이터 위치. 모델 파일·설정·토큰 폴백이 여기에 놓인다 */
object AppPaths {

    const val APP_NAME = "Switchboard"

    val os: OperatingSystem = System.getProperty("os.name").lowercase().let { name ->
        when {
            name.contains("mac") || name.contains("darwin") -> OperatingSystem.MAC
            name.contains("win") -> OperatingSystem.WINDOWS
            else -> OperatingSystem.LINUX
        }
    }

    val home: Path = Paths.get(System.getProperty("user.home"))

    val dataDir: Path = when (os) {
        OperatingSystem.MAC -> home.resolve("Library/Application Support").resolve(APP_NAME)
        OperatingSystem.WINDOWS -> (System.getenv("APPDATA")?.let(Paths::get) ?: home.resolve("AppData/Roaming")).resolve(APP_NAME)
        OperatingSystem.LINUX -> (System.getenv("XDG_DATA_HOME")?.let(Paths::get) ?: home.resolve(".local/share")).resolve(APP_NAME.lowercase())
    }

    val modelsDir: Path get() = dataDir.resolve("models")
    val settingsFile: Path get() = dataDir.resolve("settings.json")
    val credentialsFile: Path get() = dataDir.resolve("credentials.json")

    fun ensure() {
        Files.createDirectories(modelsDir)
    }
}

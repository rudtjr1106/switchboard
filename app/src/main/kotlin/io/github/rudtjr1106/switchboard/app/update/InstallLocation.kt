package io.github.rudtjr1106.switchboard.app.update

import io.github.rudtjr1106.switchboard.app.platform.AppPaths
import io.github.rudtjr1106.switchboard.app.platform.OperatingSystem
import java.nio.file.Path
import kotlin.io.path.name

/**
 * 설치된 앱의 자리. 제자리 업데이트를 하려면 지금 도는 실행 파일이 어디 있는지 알아야 한다
 *
 * Gradle 의 `run` 이나 IDE 에서 띄우면 실행 파일이 JDK 의 `java` 라 자리를 찾지 못한다. 그때는 업데이트 대신
 * 릴리즈 페이지를 열어 준다.
 */
sealed interface InstallLocation {

    /** macOS: `/Applications/스위치보드.app` 같은 앱 번들 */
    data class MacBundle(val bundle: Path) : InstallLocation

    /** Windows: `Switchboard.exe` 가 들어 있는 폴더 */
    data class WindowsFolder(val folder: Path) : InstallLocation

    companion object {
        /** jpackage 가 만든 실행 파일 이름. 한글 이름은 codesign·MSI 가 받지 못해 영문으로 둔다 */
        const val EXECUTABLE = "Switchboard"

        fun detect(): InstallLocation? =
            of(AppPaths.os, ProcessHandle.current().info().command().orElse(null)?.let(Path::of))

        /**
         * [command] 는 지금 도는 프로세스의 실행 파일 경로
         *
         * macOS 는 `<번들>/Contents/MacOS/Switchboard` 모양일 때만 인정한다. 단순히 조상 중에 `.app` 이 있는지만
         * 보면, Android Studio 안의 JDK 로 띄웠을 때 `Android Studio.app` 을 우리 앱으로 착각한다.
         */
        fun of(os: OperatingSystem, command: Path?): InstallLocation? {
            val exe = command?.normalize() ?: return null
            return when (os) {
                OperatingSystem.MAC -> {
                    val macOs = exe.parent ?: return null
                    val contents = macOs.parent ?: return null
                    val bundle = contents.parent ?: return null
                    val shaped = exe.name == EXECUTABLE && macOs.name == "MacOS" &&
                        contents.name == "Contents" && bundle.name.endsWith(".app")
                    if (shaped) MacBundle(bundle) else null
                }
                OperatingSystem.WINDOWS -> {
                    if (!exe.name.equals("$EXECUTABLE.exe", ignoreCase = true)) return null
                    exe.parent?.let(::WindowsFolder)
                }
                OperatingSystem.LINUX -> null
            }
        }
    }
}

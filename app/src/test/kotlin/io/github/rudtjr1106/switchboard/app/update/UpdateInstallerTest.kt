package io.github.rudtjr1106.switchboard.app.update

import io.github.rudtjr1106.switchboard.app.platform.OperatingSystem
import io.github.rudtjr1106.switchboard.app.platform.SafeFiles
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafeFilesTest {

    /** 업데이트는 /Applications 옆에서 도는 작업이라, 링크를 따라가면 남의 앱을 지운다 (실제로 났던 사고) */
    @Test
    fun `deleteTree 는 폴더 링크를 따라가지 않는다`() {
        val root = Files.createTempDirectory("switchboard-safe")
        val outside = root.resolve("건드리면-안-되는-곳").createDirectories()
        outside.resolve("소중한앱.txt").writeText("지우면 안 됨")
        val target = root.resolve("지울-폴더").createDirectories()
        target.resolve("안에-있는-파일.txt").writeText("지워도 됨")
        target.resolve("Applications").createSymbolicLinkPointingTo(outside)

        SafeFiles.deleteTree(target)

        assertFalse(target.exists(), "대상 폴더는 지워져야 한다")
        assertTrue(outside.exists(), "링크가 가리키던 폴더는 남아야 한다")
        assertTrue(outside.resolve("소중한앱.txt").exists(), "링크 안의 파일도 남아야 한다")
        SafeFiles.deleteTree(root)
    }

    @Test
    fun `deleteTree 는 없는 경로를 조용히 넘긴다`() {
        val root = Files.createTempDirectory("switchboard-safe")
        SafeFiles.deleteTree(root.resolve("없는-폴더"))
        assertTrue(root.exists())
        SafeFiles.deleteTree(root)
        assertFalse(root.exists())
    }

    @Test
    fun `deleteTree 에 링크를 바로 주면 링크만 지운다`() {
        val root = Files.createTempDirectory("switchboard-safe")
        val real = root.resolve("진짜").createDirectories()
        real.resolve("파일").writeText("남아야 함")
        val link = root.resolve("링크").createSymbolicLinkPointingTo(real)

        SafeFiles.deleteTree(link)

        assertFalse(link.exists(), "링크는 지워져야 한다")
        assertTrue(real.resolve("파일").exists(), "가리키던 폴더는 남아야 한다")
        SafeFiles.deleteTree(root)
    }
}

class InstallLocationTest {

    @Test
    fun `맥은 번들 안의 실행 파일일 때만 인정한다`() {
        val bundle = Path.of("/Applications/스위치보드.app")
        assertEquals(
            InstallLocation.MacBundle(bundle),
            InstallLocation.of(OperatingSystem.MAC, bundle.resolve("Contents/MacOS/Switchboard")),
        )
    }

    /** Android Studio 의 JDK 로 띄우면 조상에 .app 이 있지만 우리 앱이 아니다 */
    @Test
    fun `맥에서 다른 앱 안의 java 로 띄우면 설치 자리가 아니다`() {
        val java = Path.of("/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java")
        assertNull(InstallLocation.of(OperatingSystem.MAC, java))
    }

    @Test
    fun `맥에서 이름이 다른 실행 파일은 인정하지 않는다`() {
        val other = Path.of("/Applications/스위치보드.app/Contents/MacOS/다른것")
        assertNull(InstallLocation.of(OperatingSystem.MAC, other))
    }

    @Test
    fun `윈도우는 exe 가 있는 폴더를 쓴다`() {
        val exe = Path.of("C:/Users/seok/AppData/Local/Switchboard/Switchboard.exe")
        assertEquals(InstallLocation.WindowsFolder(exe.parent), InstallLocation.of(OperatingSystem.WINDOWS, exe))
    }

    @Test
    fun `실행 파일을 모르면 설치 자리도 없다`() {
        assertNull(InstallLocation.of(OperatingSystem.MAC, null))
        assertNull(InstallLocation.of(OperatingSystem.LINUX, Path.of("/opt/switchboard/bin/Switchboard")))
    }
}

class ChecksumAndScriptTest {

    private val sums = """
        3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b  Switchboard.dmg
        9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08  Switchboard-windows.zip
    """.trimIndent()

    @Test
    fun `체크섬 목록에서 파일 이름으로 해시를 찾는다`() {
        assertEquals(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            UpdateInstaller.expectedSha256(sums, "Switchboard-windows.zip"),
        )
        assertNull(UpdateInstaller.expectedSha256(sums, "Switchboard.msi"))
    }

    /** sha256sum 은 바이너리 모드에서 이름 앞에 * 를 붙인다 */
    @Test
    fun `바이너리 표시가 붙은 줄도 읽는다`() {
        val binary = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08 *Switchboard-windows.zip"
        assertEquals(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            UpdateInstaller.expectedSha256(binary, "Switchboard-windows.zip"),
        )
    }

    @Test
    fun `윈도우 교체 스크립트는 앱이 꺼진 뒤 폴더를 바꾸고 다시 띄운다`() {
        val script = UpdateInstaller.windowsSwapScript(
            pid = 4321,
            target = Path.of("C:/Users/seok/AppData/Local/Switchboard"),
            staging = Path.of("C:/Users/seok/AppData/Local/Switchboard.new"),
        )
        assertTrue("PID eq 4321" in script, script)
        // 기다리는 고리가 있어야 도는 중인 exe 를 지우려다 실패하지 않는다
        assertTrue(":wait" in script && "goto wait" in script, script)
        assertTrue("""rmdir /s /q "C:\Users\seok\AppData\Local\Switchboard"""" in script, script)
        // 경로만 역슬래시로 바뀌어야 한다 (cmd 옵션의 /FI, /s /q 는 그대로 둔다)
        assertTrue("""move "C:\Users\seok\AppData\Local\Switchboard.new"""" in script, script)
        assertTrue("/FI" in script && "/s /q" in script, "cmd 옵션은 그대로 남아야 한다")
        assertTrue("Switchboard.exe" in script, script)
        assertTrue(script.endsWith("""del "%~f0""""), script)
        assertTrue("\r\n" in script, "배치 파일은 CRLF 로 써야 한다")
    }
}

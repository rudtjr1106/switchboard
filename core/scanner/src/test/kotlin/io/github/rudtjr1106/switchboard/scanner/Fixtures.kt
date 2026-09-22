package io.github.rudtjr1106.switchboard.scanner

import io.github.rudtjr1106.switchboard.scanner.parse.SourceText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal object Fixtures {

    /** src/test/resources/fixtures/<name> — Gradle 이 복사한 테스트 리소스 위치를 클래스패스로 찾는다 */
    fun project(name: String): Path {
        val settings = checkNotNull(Fixtures::class.java.classLoader.getResource("fixtures/$name/settings.gradle.kts")) {
            "fixture 가 없어요: $name"
        }
        return Path.of(settings.toURI()).parent
    }

    fun tempDir(): Path = Files.createTempDirectory("switchboard-scanner-")

    fun Path.writeFile(relative: String, content: String): Path {
        val target = resolve(relative)
        target.parent.createDirectories()
        target.writeText(content.trimIndent())
        return target
    }

    fun deleteRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    /** 문자열·주석 속 괄호를 뺀 뒤 중괄호와 소괄호 짝이 맞는지 */
    fun bracesBalanced(code: String): Boolean {
        val masked = SourceText.maskStrings(SourceText.stripComments(code))
        return masked.count { it == '{' } == masked.count { it == '}' } && masked.count { it == '(' } == masked.count { it == ')' }
    }
}

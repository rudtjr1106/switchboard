package io.github.rudtjr1106.switchboard.scanner

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrationWriterTest {

    @Test
    fun `writes every file under the root creating directories`() {
        val root = Fixtures.tempDir()
        try {
            val plan = IntegrationPlan(
                files = listOf(
                    GeneratedFile(Path.of("app/src/main/java/com/x/A.kt"), "package com.x\n\nclass A\n", FileAction.CREATE),
                    GeneratedFile(Path.of("domain/src/main/java/com/x/B.kt"), "package com.x\n\nclass B\n", FileAction.MODIFY, original = "old"),
                ),
                notes = emptyList(),
                manualSteps = emptyList(),
            )

            assertEquals(listOf("app/src/main/java/com/x/A.kt", "domain/src/main/java/com/x/B.kt"), IntegrationWriter.preview(root, plan))

            IntegrationWriter.write(root, plan)

            assertEquals("package com.x\n\nclass A\n", Files.readString(root.resolve("app/src/main/java/com/x/A.kt")))
            assertEquals("package com.x\n\nclass B\n", Files.readString(root.resolve("domain/src/main/java/com/x/B.kt")))
            assertTrue(Files.isDirectory(root.resolve("domain/src/main/java/com/x")))
        } finally {
            Fixtures.deleteRecursively(root)
        }
    }

    @Test
    fun `preview relativizes absolute paths`() {
        // Windows 에서는 "/tmp/project" 가 절대 경로가 아니므로(드라이브 문자 없음) 실제 절대 경로를 쓴다
        val root = Path.of("").toAbsolutePath().resolve("project")
        val plan = IntegrationPlan(
            files = listOf(GeneratedFile(root.resolve("app/A.kt"), "", FileAction.CREATE)),
            notes = emptyList(),
            manualSteps = emptyList(),
        )
        assertEquals(listOf("app/A.kt"), IntegrationWriter.preview(root, plan))
    }
}

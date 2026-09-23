package io.github.rudtjr1106.switchboard.scanner

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 이미 자기 안내 화면을 만들어 둔 프로젝트는 그 파일만 빼고 받을 수 있어야 한다 */
class SkippedFilesTest {

    private fun file(name: String, content: String, action: FileAction = FileAction.CREATE) =
        GeneratedFile(Path.of("app/src/main/java/com/acme/remoteconfig/$name"), content, action)

    private val host = file(
        "RemoteNoticeHost.kt",
        """
        package com.acme.remoteconfig
        @Composable
        fun RemoteNoticeHost(currentRoute: String?, viewModel: RemoteNoticeViewModel) { }
        """.trimIndent(),
    )
    private val viewModel = file(
        "RemoteNoticeViewModel.kt",
        """
        package com.acme.remoteconfig
        class RemoteNoticeViewModel(private val getRemoteNotices: GetRemoteNoticesUseCase)
        """.trimIndent(),
    )
    private val useCase = file(
        "GetRemoteNoticesUseCase.kt",
        """
        package com.acme.remoteconfig
        class GetRemoteNoticesUseCase(private val repository: RemoteConfigRepository)
        """.trimIndent(),
    )
    private val plan = IntegrationPlan(listOf(host, viewModel, useCase), notes = emptyList(), manualSteps = emptyList())

    @Test
    fun `아무것도 빼지 않으면 경고가 없다`() {
        assertEquals(emptyList(), plan.missingWhenSkipped(emptySet()))
    }

    /** 화면(UI)만 빼는 건 안전하다. 아무도 RemoteNoticeHost 를 부르지 않는다 */
    @Test
    fun `아무도 쓰지 않는 화면 파일은 빼도 경고가 없다`() {
        assertEquals(emptyList(), plan.missingWhenSkipped(setOf(host.path)))
    }

    /** 남은 파일이 쓰는 선언을 빼면 컴파일이 깨지므로 미리 알려야 한다 */
    @Test
    fun `남은 파일이 쓰는 선언을 빼면 알려 준다`() {
        val missing = plan.missingWhenSkipped(setOf(viewModel.path))
        assertEquals(1, missing.size, missing.toString())
        assertEquals("RemoteNoticeViewModel", missing.single().name)
        assertEquals(listOf(host.path), missing.single().neededBy)
    }

    /** 이미 프로젝트에 있는 파일(MODIFY)을 빼는 건 문제가 아니다. 그 선언은 이미 프로젝트에 있다 */
    @Test
    fun `이미 있는 파일을 그대로 두는 건 경고하지 않는다`() {
        val mine = viewModel.copy(action = FileAction.MODIFY)
        val withMine = plan.copy(files = listOf(host, mine, useCase))
        assertEquals(emptyList(), withMine.missingWhenSkipped(setOf(mine.path)))
    }

    /** 빠진 것끼리만 쓰던 선언은 경고하지 않는다. 남는 파일이 아쉬워하는 것만 알려야 쓸모가 있다 */
    @Test
    fun `함께 뺀 파일끼리 쓰던 선언은 경고하지 않는다`() {
        val missing = plan.missingWhenSkipped(setOf(viewModel.path, useCase.path))
        // GetRemoteNoticesUseCase 는 RemoteNoticeViewModel 만 쓰는데 그것도 함께 빠졌다
        assertEquals(listOf("RemoteNoticeViewModel"), missing.map { it.name })
        assertEquals(listOf(host.path), missing.single().neededBy)
    }

    @Test
    fun `쓰기는 뺀 파일을 건드리지 않는다`() {
        val root = Fixtures.tempDir()
        val mine = "내가 만든 화면"
        root.resolve(host.path).toFile().apply { parentFile.mkdirs(); writeText(mine) }

        IntegrationWriter.write(root, plan.copy(files = plan.files.filterNot { it.path == host.path }))

        assertEquals(mine, root.resolve(host.path).toFile().readText(), "뺀 파일은 그대로여야 한다")
        assertTrue(root.resolve(viewModel.path).toFile().exists(), "나머지는 쓰여야 한다")
    }
}

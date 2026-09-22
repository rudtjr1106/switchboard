package io.github.rudtjr1106.switchboard.app.editor

import io.github.rudtjr1106.switchboard.app.testing.FakeConfigRepository
import io.github.rudtjr1106.switchboard.config.ChangeKind
import io.github.rudtjr1106.switchboard.config.ConfigCodec
import io.github.rudtjr1106.switchboard.config.ScreenCatalog
import io.github.rudtjr1106.switchboard.github.GitHubException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorModelTest {

    private val schemaText = """
        {
          "type": "object",
          "required": ["version", "minimumVersion", "notices"],
          "properties": {
            "${'$'}schema": { "type": "string" },
            "version": { "const": 1 },
            "minimumVersion": { "type": "string", "pattern": "^([0-9]+(\\.[0-9]+){0,2})?$" },
            "notices": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["screen", "enabled", "template", "title", "body"],
                "additionalProperties": false,
                "properties": {
                  "screen": { "enum": ["ALL", "Home"] },
                  "enabled": { "type": "boolean" },
                  "template": { "enum": ["INFO", "BLOCKING"] },
                  "title": { "type": "string", "minLength": 1, "maxLength": 40 },
                  "body": { "type": "string", "minLength": 1, "maxLength": 200 },
                  "until": { "type": "string", "format": "date" }
                }
              }
            }
          }
        }
    """.trimIndent()

    private val configText = """
        {
          "${'$'}schema": "./schema.json",
          "version": 1,
          "minimumVersion": "",
          "notices": [
            {
              "screen": "ALL",
              "enabled": false,
              "template": "BLOCKING",
              "title": "서비스 점검 중이에요",
              "body": "잠시 후 다시 이용해주세요."
            }
          ]
        }

    """.trimIndent()

    private fun TestScope.newModel(repo: FakeConfigRepository = FakeConfigRepository(configText = configText, schemaText = schemaText)): Pair<EditorModel, FakeConfigRepository> {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val model = EditorModel(repo, scope) { LocalDate.of(2026, 9, 22) }
        model.load()
        return model to repo
    }

    @Test
    fun `load selects the first notice and has no changes`() = runTest {
        val (model, _) = newModel()
        val state = model.current
        assertEquals(LoadState.Loaded, state.loadState)
        assertFalse(state.hasChanges)
        assertIs<Selection.NoticeItem>(state.selection)
        assertEquals("바뀐 내용이 없어요", state.applyBlockedReason)
        assertTrue(state.schema!!.supportsMinimumVersion)
    }

    @Test
    fun `editing produces changes and validation issues block apply`() = runTest {
        val (model, _) = newModel()
        val id = model.current.draft.notices.single().id
        model.updateNotice(id) { it.copy(enabled = true, title = "") }
        assertTrue(model.current.hasChanges)
        assertEquals("제목 없음: 제목을 입력하세요", model.current.applyBlockedReason)
        model.updateNotice(id) { it.copy(title = "점검") }
        assertNull(model.current.applyBlockedReason)
        assertEquals(listOf(ChangeKind.ENABLED, ChangeKind.MODIFIED), model.current.changes.map { it.kind })
    }

    @Test
    fun `apply runs the pipeline and promotes the draft to original`() = runTest {
        val (model, repo) = newModel()
        val id = model.current.draft.notices.single().id
        model.updateNotice(id) { it.copy(enabled = true, until = "2026-09-30") }
        model.beginApply()
        val confirming = assertIs<ApplyState.Confirming>(model.current.apply)
        assertTrue(confirming.isDangerous)
        assertEquals("원격 설정: 안내 켜짐 · 서비스 점검 중이에요 외 1건", confirming.commitMessage)
        assertNull(confirming.blockedReason)
        model.setMemo("9/30 까지 점검")
        model.confirmApply()

        val finished = assertIs<ApplyState.Finished>(model.current.apply)
        assertEquals("https://github.com/acme/app-config/pull/7", finished.result.pullRequestUrl)
        assertFalse(model.current.hasChanges)
        val request = repo.applied.single()
        assertEquals("sha-config-1", request.files.single().expectedSha)
        assertEquals(ConfigCodec.encode(model.current.draft), request.files.single().content)
        assertTrue(request.pullRequestBody.contains("9/30 까지 점검"))
        assertTrue(request.pullRequestBody.contains("- 안내 켜짐 · 서비스 점검 중이에요"))
        assertEquals("sha-after-app-config.json", model.fileSha("app-config.json"))
        model.closeApply()
        assertNull(model.current.apply)
    }

    @Test
    fun `failed apply keeps the draft and reports the message`() = runTest {
        val repo = FakeConfigRepository(configText = configText, schemaText = schemaText, failApplyWith = GitHubException.ValidationFailed("failure"))
        val (model, _) = newModel(repo)
        val id = model.current.draft.notices.single().id
        model.updateNotice(id) { it.copy(body = "새 본문") }
        model.beginApply()
        model.confirmApply()
        val failed = assertIs<ApplyState.Failed>(model.current.apply)
        assertTrue(failed.message.contains("validate"))
        assertTrue(model.current.hasChanges)
        assertEquals("새 본문", model.current.draft.notices.single().body)
    }

    @Test
    fun `add duplicate delete and revert`() = runTest {
        val (model, _) = newModel()
        model.addNotice()
        assertEquals(2, model.current.draft.notices.size)
        val added = assertIs<Selection.NoticeItem>(model.current.selection).id
        assertEquals(ScreenCatalog.ALL, model.current.draft.notice(added)!!.screen)
        model.duplicateNotice(added)
        assertEquals(3, model.current.draft.notices.size)
        model.deleteNotice(added)
        assertEquals(2, model.current.draft.notices.size)
        assertNotNull(model.current.selection)
        model.revert()
        assertFalse(model.current.hasChanges)
        assertEquals(1, model.current.draft.notices.size)
    }

    @Test
    fun `schema mismatch is caught locally before applying`() = runTest {
        val (model, _) = newModel()
        val id = model.current.draft.notices.single().id
        // 스키마에 없는 화면은 폼 검사에서 막힌다
        model.updateNotice(id) { it.copy(screen = "Nowhere") }
        assertTrue(model.current.applyBlockedReason!!.contains("schema.json 에 없어요"))
    }
}

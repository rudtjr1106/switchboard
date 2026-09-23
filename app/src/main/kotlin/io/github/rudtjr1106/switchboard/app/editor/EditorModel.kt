package io.github.rudtjr1106.switchboard.app.editor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.config.AppConfig
import io.github.rudtjr1106.switchboard.config.Change
import io.github.rudtjr1106.switchboard.config.ChangeKind
import io.github.rudtjr1106.switchboard.config.ConfigCodec
import io.github.rudtjr1106.switchboard.config.ConfigDiff
import io.github.rudtjr1106.switchboard.config.ConfigFormatException
import io.github.rudtjr1106.switchboard.config.ConfigSchema
import io.github.rudtjr1106.switchboard.config.ConfigValidator
import io.github.rudtjr1106.switchboard.config.Notice
import io.github.rudtjr1106.switchboard.config.NoticeId
import io.github.rudtjr1106.switchboard.config.SchemaRenderer
import io.github.rudtjr1106.switchboard.config.SchemaValidation
import io.github.rudtjr1106.switchboard.config.ValidationIssue
import io.github.rudtjr1106.switchboard.config.ValueSpec
import io.github.rudtjr1106.switchboard.config.templates.RepoTemplates
import io.github.rudtjr1106.switchboard.github.ApplyProgress
import io.github.rudtjr1106.switchboard.github.ApplyRequest
import io.github.rudtjr1106.switchboard.github.ApplyResult
import io.github.rudtjr1106.switchboard.github.ConfigRepository
import io.github.rudtjr1106.switchboard.github.FileChange
import io.github.rudtjr1106.switchboard.github.GitHubException
import io.github.rudtjr1106.switchboard.github.RepoRef
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

private val logger = KotlinLogging.logger {}

sealed interface LoadState {
    data object Loading : LoadState
    data object Loaded : LoadState
    data class Failed(val message: String) : LoadState
}

sealed interface Selection {
    data object MinimumVersion : Selection
    data object Values : Selection
    data class NoticeItem(val id: NoticeId) : Selection
}

/** 적용 시트의 상태. null 이면 시트가 닫혀 있다 */
sealed interface ApplyState {
    data class Confirming(
        val changes: List<Change>,
        val isDangerous: Boolean,
        val commitMessage: String,
        val memo: String,
        val blockedReason: String?,
    ) : ApplyState

    data class Running(val progress: ApplyProgress) : ApplyState

    data class Finished(val progress: ApplyProgress, val result: ApplyResult) : ApplyState

    data class Failed(val progress: ApplyProgress, val message: String) : ApplyState
}

data class EditorState(
    val ref: RepoRef,
    val loadState: LoadState = LoadState.Loading,
    val schema: ConfigSchema? = null,
    /** 값 정의를 고쳤을 때의 schema.json. null 이면 스키마는 그대로다 */
    val schemaDraft: ConfigSchema? = null,
    val original: AppConfig = AppConfig(),
    val draft: AppConfig = AppConfig(),
    val loadedAt: Instant? = null,
    val branch: String = "main",
    val selection: Selection? = null,
    val apply: ApplyState? = null,
) {
    val isLoaded: Boolean get() = loadState == LoadState.Loaded && schema != null
    val hasChanges: Boolean get() = !draft.contentEquals(original) || schemaDraft != null
    val isApplying: Boolean get() = apply is ApplyState.Running
    val selectedNotice: Notice? get() = (selection as? Selection.NoticeItem)?.let { draft.notice(it.id) }

    /** 값 편집에 쓰는 스키마. 정의를 고쳤으면 그 초안 */
    val effectiveSchema: ConfigSchema? get() = schemaDraft ?: schema

    val valueSpecs: List<ValueSpec> get() = effectiveSchema?.values.orEmpty()

    val hasSchemaChanges: Boolean get() = schemaDraft != null

    val issues: List<ValidationIssue> get() = effectiveSchema?.let { ConfigValidator(it).validate(draft) }.orEmpty()

    fun issuesFor(id: NoticeId): List<ValidationIssue> = issues.filter { it.noticeId == id }

    val changes: List<Change> get() = ConfigDiff.between(original, draft) + schemaChanges

    /** 값 정의(schema.json) 변화 */
    private val schemaChanges: List<Change>
        get() {
            val before = schema?.values.orEmpty().associateBy { it.key }
            val after = schemaDraft?.values?.associateBy { it.key } ?: return emptyList()
            val changes = mutableListOf<Change>()
            for ((key, spec) in after) {
                val old = before[key]
                when {
                    old == null -> changes += Change(ChangeKind.ADDED, "값 정의 $key 추가 (${spec.type.label})")
                    old != spec -> changes += Change(ChangeKind.MODIFIED, "값 정의 $key 수정")
                }
            }
            for (key in before.keys - after.keys) changes += Change(ChangeKind.REMOVED, "값 정의 $key 삭제")
            return changes
        }

    /** 적용 버튼이 꺼져 있는 이유. null 이면 적용할 수 있다 */
    val applyBlockedReason: String?
        get() = when {
            !isLoaded -> "아직 불러오지 않았어요"
            !hasChanges -> "바뀐 내용이 없어요"
            issues.isNotEmpty() -> issues.joinToString("\n") { issue ->
                val prefix = issue.noticeId?.let { id -> draft.notice(id)?.displayTitle }?.let { "$it: " } ?: ""
                prefix + issue.message
            }
            else -> null
        }
}

/**
 * 저장소 하나를 편집하는 상태 (iOS 편집 앱의 EditorModel 에 해당)
 *
 * 불러온 원본([EditorState.original])과 고치는 중인 초안([EditorState.draft])을 나눠 두고,
 * 적용할 때는 불러왔을 때의 파일 sha 로 충돌을 확인한다.
 */
class EditorModel(
    private val repository: ConfigRepository,
    private val scope: CoroutineScope,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    val ref: RepoRef get() = repository.ref

    private val _state = MutableStateFlow(EditorState(ref = repository.ref))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** 경로 → 불러왔을 때의 sha. 적용 후에는 머지된 sha 로 바뀐다 */
    private val fileShas = mutableMapOf<String, String>()

    val current: EditorState get() = _state.value

    fun load() {
        val selectedIndex = selectedNoticeIndex()
        _state.update { it.copy(loadState = LoadState.Loading) }
        scope.launch {
            try {
                val loaded = repository.load()
                val schema = ConfigSchema.parse(loaded.schema.content)
                val config = ConfigCodec.decode(loaded.config.content, schema)
                fileShas[RepoTemplates.CONFIG_PATH] = loaded.config.sha
                fileShas[RepoTemplates.SCHEMA_PATH] = loaded.schema.sha
                _state.update {
                    it.copy(
                        loadState = LoadState.Loaded,
                        schema = schema,
                        original = config,
                        draft = config,
                        loadedAt = loaded.loadedAt,
                        branch = loaded.branch,
                    )
                }
                restoreSelection(selectedIndex)
            } catch (e: GitHubException) {
                _state.update { it.copy(loadState = LoadState.Failed(e.message ?: "불러오지 못했어요")) }
            } catch (e: ConfigFormatException) {
                _state.update { it.copy(loadState = LoadState.Failed(e.message ?: "설정 파일을 읽지 못했어요")) }
            }
        }
    }

    fun revert() {
        val selectedIndex = selectedNoticeIndex()
        _state.update { it.copy(draft = it.original) }
        if (selectedNoticeIndex() == null) restoreSelection(selectedIndex)
    }

    fun select(selection: Selection?) {
        _state.update { it.copy(selection = selection) }
    }

    // ---- 자유 값 ----

    /** 값 하나를 고친다. 기본값과 같아져도 파일에는 남긴다 (다음 사람이 무엇을 정했는지 보이게) */
    fun setValue(spec: ValueSpec, value: JsonPrimitive) {
        _state.update { it.copy(draft = it.draft.setValue(spec.key, value)) }
    }

    /**
     * 값 정의를 새로 만든다. schema.json 이 함께 바뀌므로 적용할 때 두 파일이 한 PR 로 올라간다
     *
     * @return 만들지 못한 이유. null 이면 만들었다
     */
    fun addValueSpec(spec: ValueSpec): String? {
        val state = current
        val schema = state.effectiveSchema ?: return "스키마를 불러오지 못했어요"
        if (!ValueSpec.KEY_PATTERN.matches(spec.key)) return "키는 영문 소문자로 시작하고 영문·숫자만 쓸 수 있어요 (예: showEvent)"
        if (schema.values.any { it.key == spec.key }) return "'${spec.key}' 는 이미 있어요"
        val updated = ConfigSchema.parse(SchemaRenderer.withValues(schema.text, schema.values + spec))
        _state.update { it.copy(schemaDraft = updated, selection = Selection.Values) }
        return null
    }

    /** 값 정의를 지운다. 설정 파일의 값도 함께 뺀다 */
    fun removeValueSpec(key: String) {
        _state.update { state ->
            val schema = state.effectiveSchema ?: return@update state
            val updated = ConfigSchema.parse(SchemaRenderer.withValues(schema.text, schema.values.filterNot { it.key == key }))
            state.copy(schemaDraft = updated, draft = state.draft.removeValue(key))
        }
    }

    fun addNotice() {
        val notice = Notice()
        _state.update { it.copy(draft = it.draft.add(notice), selection = Selection.NoticeItem(notice.id)) }
    }

    fun duplicateNotice(id: NoticeId) {
        _state.update { state ->
            val (draft, newId) = state.draft.duplicate(id) ?: return@update state
            state.copy(draft = draft, selection = Selection.NoticeItem(newId))
        }
    }

    fun deleteNotice(id: NoticeId) {
        val index = current.draft.indexOf(id).takeIf { it >= 0 } ?: return
        val wasSelected = current.selection == Selection.NoticeItem(id)
        _state.update { it.copy(draft = it.draft.remove(id), selection = if (wasSelected) null else it.selection) }
        if (wasSelected) restoreSelection(index)
    }

    fun updateNotice(id: NoticeId, transform: (Notice) -> Notice) {
        _state.update { it.copy(draft = it.draft.update(id, transform)) }
    }

    fun setMinimumVersion(value: String) {
        _state.update { it.copy(draft = it.draft.copy(minimumVersion = value)) }
    }

    // ---- 적용 ----

    fun beginApply() {
        val state = current
        if (state.applyBlockedReason != null || state.apply != null) return
        val changes = state.changes
        _state.update {
            it.copy(
                apply = ApplyState.Confirming(
                    changes = changes,
                    isDangerous = ConfigDiff.isDangerous(it.original, it.draft, today()),
                    commitMessage = ConfigDiff.commitTitle(changes),
                    memo = "",
                    blockedReason = localSchemaCheck(it),
                ),
            )
        }
    }

    fun setCommitMessage(message: String) = updateConfirming { it.copy(commitMessage = message) }

    fun setMemo(memo: String) = updateConfirming { it.copy(memo = memo) }

    fun closeApply() {
        if (current.apply is ApplyState.Running) return
        _state.update { it.copy(apply = null) }
    }

    fun confirmApply() {
        val state = current
        val confirming = state.apply as? ApplyState.Confirming ?: return
        val title = confirming.commitMessage.trim()
        if (title.isEmpty() || confirming.blockedReason != null || state.applyBlockedReason != null) return

        val draft = state.draft
        val body = buildString {
            append("### 변경 사항\n\n")
            confirming.changes.forEach { append("- ").append(it.text).append('\n') }
            if (confirming.memo.isNotBlank()) append("\n### 메모\n\n").append(confirming.memo.trim()).append('\n')
            append("\n스위치보드에서 적용")
        }
        val schemaDraft = state.schemaDraft
        val files = buildList {
            add(FileChange(RepoTemplates.CONFIG_PATH, ConfigCodec.encode(draft), fileShas[RepoTemplates.CONFIG_PATH]))
            // 값 정의를 고쳤으면 schema.json 도 같은 PR 로 올린다 (따로 올리면 잠깐 서로 맞지 않는다)
            if (schemaDraft != null) add(FileChange(RepoTemplates.SCHEMA_PATH, schemaDraft.text, fileShas[RepoTemplates.SCHEMA_PATH]))
        }
        val request = ApplyRequest(
            files = files,
            commitTitle = title,
            pullRequestBody = body,
        )
        _state.update { it.copy(apply = ApplyState.Running(ApplyProgress())) }
        scope.launch {
            try {
                val result = repository.apply(request) { progress ->
                    _state.update { it.copy(apply = ApplyState.Running(progress)) }
                }
                // main 은 이미 바뀌었으니 배포가 실패해도 원본은 머지 결과로 맞춘다
                fileShas.putAll(result.fileShas)
                _state.update {
                    it.copy(
                        original = draft,
                        schema = schemaDraft ?: it.schema,
                        schemaDraft = null,
                        apply = ApplyState.Finished(lastProgress(), result),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "적용 실패" }
                _state.update { it.copy(apply = ApplyState.Failed(lastProgress(), e.message ?: "적용하지 못했어요")) }
            }
        }
    }

    /**
     * 설정 파일 외의 파일(schema.json, README)을 바꾸는 적용. 프로젝트 세팅에서 화면 목록을 올릴 때 쓴다
     *
     * 성공하면 저장소를 다시 불러온다. 초안에 적용하지 않은 변경이 있으면 호출하지 않아야 한다.
     */
    suspend fun applyFiles(files: Map<String, String>, commitTitle: String, body: String, onProgress: (ApplyProgress) -> Unit): ApplyResult {
        val changes = files.map { (path, content) -> FileChange(path, content, fileShas[path]) }
        val result = repository.apply(ApplyRequest(changes, commitTitle, body, branchPrefix = "setup"), onProgress)
        fileShas.putAll(result.fileShas)
        load()
        return result
    }

    fun fileSha(path: String): String? = fileShas[path]

    /** README 처럼 불러오지 않는 파일을 [applyFiles] 로 바꾸기 전에 현재 sha 를 등록한다 (null 이면 새 파일) */
    fun registerFileSha(path: String, sha: String?) {
        if (sha == null) fileShas.remove(path) else fileShas[path] = sha
    }

    private fun localSchemaCheck(state: EditorState): String? {
        val schema = state.schema ?: return "스키마를 불러오지 못했어요"
        val errors = runCatching { SchemaValidation.validate(ConfigCodec.encode(state.draft), schema.text) }
            .getOrElse { return null } // 로컬 검사기가 문제면 서버 검사에 맡긴다
        return errors.takeIf { it.isNotEmpty() }?.let { "schema.json 검사에 걸렸어요:\n" + it.joinToString("\n") }
    }

    private fun updateConfirming(transform: (ApplyState.Confirming) -> ApplyState.Confirming) {
        _state.update { state ->
            val confirming = state.apply as? ApplyState.Confirming ?: return@update state
            state.copy(apply = transform(confirming))
        }
    }

    private fun lastProgress(): ApplyProgress = (current.apply as? ApplyState.Running)?.progress ?: ApplyProgress()

    private fun selectedNoticeIndex(): Int? =
        (current.selection as? Selection.NoticeItem)?.let { current.draft.indexOf(it.id).takeIf { i -> i >= 0 } }

    private fun restoreSelection(noticeIndex: Int?) {
        val state = current
        if (state.selection == Selection.MinimumVersion) return
        val notices = state.draft.notices
        val selection = when {
            notices.isNotEmpty() -> Selection.NoticeItem(notices[minOf(noticeIndex ?: 0, notices.size - 1)].id)
            state.schema?.supportsMinimumVersion == true -> Selection.MinimumVersion
            else -> null
        }
        _state.update { it.copy(selection = selection) }
    }
}

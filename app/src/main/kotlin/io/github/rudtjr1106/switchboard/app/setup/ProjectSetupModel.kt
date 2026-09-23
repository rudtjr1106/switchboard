package io.github.rudtjr1106.switchboard.app.setup

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.ai.ScreenDrafts
import io.github.rudtjr1106.switchboard.ai.ScreenHint
import io.github.rudtjr1106.switchboard.app.ai.AiManager
import io.github.rudtjr1106.switchboard.app.editor.EditorModel
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import io.github.rudtjr1106.switchboard.config.ConfigSchema
import io.github.rudtjr1106.switchboard.config.SchemaRenderer
import io.github.rudtjr1106.switchboard.config.ScreenCatalog
import io.github.rudtjr1106.switchboard.config.ScreenInfo
import io.github.rudtjr1106.switchboard.config.templates.RepoTemplates
import io.github.rudtjr1106.switchboard.github.ApplyProgress
import io.github.rudtjr1106.switchboard.github.GitHubException
import io.github.rudtjr1106.switchboard.scanner.AndroidProject
import io.github.rudtjr1106.switchboard.scanner.AndroidProjectScanner
import io.github.rudtjr1106.switchboard.scanner.FileAction
import io.github.rudtjr1106.switchboard.scanner.IntegrationGenerator
import io.github.rudtjr1106.switchboard.scanner.IntegrationPlan
import io.github.rudtjr1106.switchboard.scanner.IntegrationTarget
import io.github.rudtjr1106.switchboard.scanner.IntegrationWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/** 계획 만들기 진행. [total] 이 0 이면 단계 수를 모르는 로딩 */
data class PlanningProgress(val message: String, val done: Int = 0, val total: Int = 0)

sealed interface SetupStep {
    data class PickFolder(val error: String? = null) : SetupStep

    data class Scanning(val path: Path) : SetupStep

    /** 스캔 결과 확인. [selected] 는 schema.json 에 넣을 화면 이름 */
    data class Review(val project: AndroidProject, val selected: Set<String>) : SetupStep

    data class Labeling(
        val project: AndroidProject,
        val screens: List<ScreenInfo>,
        val aiRunning: Boolean = false,
        val aiError: String? = null,
        /** 계획을 만드는 중이면 진행 상황. 화면 위에 로딩 창으로 띄운다 */
        val planning: PlanningProgress? = null,
    ) : SetupStep

    data class Plan(
        val project: AndroidProject,
        val screens: List<ScreenInfo>,
        val plan: IntegrationPlan,
        val writeFiles: Boolean = true,
        /** 쓰지 않고 그대로 둘 파일. 이미 자기 화면을 만들어 둔 프로젝트를 위해 둔다 */
        val skipped: Set<Path> = emptySet(),
        val updateRepo: Boolean,
        val updateReadme: Boolean,
        val repoBlockedReason: String?,
        val selectedFile: Int = 0,
    ) : SetupStep

    data class Running(val progress: ApplyProgress?, val phase: String) : SetupStep

    data class Done(val written: List<Path>, val pullRequestUrl: String?, val manualSteps: List<String>, val note: String?) : SetupStep
}

/**
 * Android 프로젝트 세팅 마법사
 *
 * 폴더 고르기 → 스캔 → 화면 고르기 → 라벨(AI) → 계획 확인 → 파일 쓰기 + 저장소 스키마 PR
 */
class ProjectSetupModel(
    private val session: SessionState.SignedIn,
    private val editor: EditorModel,
    private val scanner: AndroidProjectScanner,
    private val generator: IntegrationGenerator,
    private val ai: AiManager,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SetupStep>(SetupStep.PickFolder())
    val state: StateFlow<SetupStep> = _state.asStateFlow()

    private var aiJob: Job? = null

    val lastProjectPath: Path? get() = settings.current.lastProjectPath?.let { runCatching { Path.of(it) }.getOrNull() }

    fun pick(path: Path) {
        _state.value = SetupStep.Scanning(path)
        scope.launch {
            try {
                val project = scanner.scan(path)
                settings.update { it.copy(lastProjectPath = path.toString()) }
                val existing = editor.current.schema?.catalog?.ids.orEmpty().toSet()
                // 스플래시처럼 금방 지나가는 화면은 기본으로 빼 둔다
                val selected = project.destinationNames.filterNot { it.equals("Splash", ignoreCase = true) }.toSet() + (existing - ScreenCatalog.ALL)
                _state.value = SetupStep.Review(project, selected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "프로젝트 스캔 실패: $path" }
                _state.value = SetupStep.PickFolder(e.message ?: "프로젝트를 읽지 못했어요")
            }
        }
    }

    fun toggle(name: String) {
        _state.update { step ->
            (step as? SetupStep.Review)?.let { it.copy(selected = if (name in it.selected) it.selected - name else it.selected + name) } ?: step
        }
    }

    fun selectAll(all: Boolean) {
        _state.update { step ->
            (step as? SetupStep.Review)?.let { it.copy(selected = if (all) it.project.destinationNames.toSet() else emptySet()) } ?: step
        }
    }

    fun toLabeling() {
        val review = _state.value as? SetupStep.Review ?: return
        val existing = editor.current.schema?.catalog
        val ordered = listOf(ScreenCatalog.ALL) + review.project.destinationNames.filter { it in review.selected } +
            review.selected.filterNot { it in review.project.destinationNames || it == ScreenCatalog.ALL }
        val hints = hintsFor(review.project)
        // 저장소에 이미 이름이 있으면 그것을, 없으면 모델 없이 만든 초안(주석·구역·용어 사전)을 먼저 채워 둔다
        val screens = ordered.map { id ->
            existing?.find(id)?.takeIf { it.hasMetadata }
                ?: if (id == ScreenCatalog.ALL) ScreenInfo(id, ScreenCatalog.ALL_LABEL, ScreenCatalog.ALL_GROUP) else ScreenDrafts.draft(hints[id] ?: ScreenHint(id))
        }
        _state.value = SetupStep.Labeling(review.project, screens)
    }

    fun backToReview() {
        val labeling = _state.value as? SetupStep.Labeling ?: return
        aiJob?.cancel()
        _state.value = SetupStep.Review(labeling.project, labeling.screens.map { it.id }.filterNot { it == ScreenCatalog.ALL }.toSet())
    }

    fun runAiLabels() {
        val labeling = _state.value as? SetupStep.Labeling ?: return
        aiJob?.cancel()
        _state.value = labeling.copy(aiRunning = true, aiError = null)
        aiJob = scope.launch {
            try {
                val hints = hintsFor(labeling.project)
                val requested = labeling.screens.map { it.id }.filterNot { it == ScreenCatalog.ALL }.map { hints[it] ?: ScreenHint(it) }
                val labeled = ai.labeler.labelWithHints(requested, labeling.project.name).associateBy { it.id }
                _state.update { step ->
                    (step as? SetupStep.Labeling)?.copy(
                        screens = step.screens.map { screen -> labeled[screen.id]?.let { screen.copy(label = it.label, group = it.group) } ?: screen },
                        aiRunning = false,
                    ) ?: step
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "AI 라벨 실패" }
                _state.update { step -> (step as? SetupStep.Labeling)?.copy(aiRunning = false, aiError = e.message ?: "라벨을 만들지 못했어요") ?: step }
            }
        }
    }

    fun setScreen(id: String, label: String, group: String?) {
        _state.update { step ->
            (step as? SetupStep.Labeling)?.copy(
                screens = step.screens.map { if (it.id == id) it.copy(label = label.ifBlank { ScreenCatalog.defaultLabel(id) }, group = group?.ifBlank { null }) else it },
            ) ?: step
        }
    }

    private var planJob: Job? = null

    /**
     * 연동 코드 계획을 만든다. 템플릿은 바로 나오고, 스캐너가 알아보지 못한 부분이 있으면 AI 가 그 파일만 고쳐 쓴다
     *
     * AI 가 켜져 있지 않거나 결과가 검사를 통과하지 못하면 템플릿을 그대로 쓰고 계획의 메모에 적는다.
     */
    fun buildPlan() {
        val labeling = _state.value as? SetupStep.Labeling ?: return
        if (labeling.planning != null) return
        aiJob?.cancel()
        _state.value = labeling.copy(planning = PlanningProgress("연동 코드를 만드는 중…"))
        planJob = scope.launch {
            try {
                val ref = editor.ref
                val target = IntegrationTarget(
                    pagesBaseUrl = "https://${ref.owner.lowercase()}.github.io/",
                    configPath = "${ref.name}/${RepoTemplates.CONFIG_PATH}",
                    repoFullName = ref.fullName,
                    screens = labeling.screens,
                )
                val base = withContext(Dispatchers.Default) { generator.plan(labeling.project, target) }
                val plan = adaptWithAi(labeling, base)
                val editorState = editor.current
                val blocked = when {
                    !editorState.isLoaded -> "저장소를 아직 불러오지 못했어요"
                    editorState.hasChanges -> "편집기에 적용하지 않은 변경이 있어요. 먼저 적용하거나 되돌린 뒤 진행하세요"
                    else -> null
                }
                _state.value = SetupStep.Plan(
                    project = labeling.project,
                    screens = labeling.screens,
                    plan = plan,
                    // 프로젝트에 이미 있는 파일은 기본으로 빼 둔다. 직접 만든 화면을 말없이 덮어쓰면 안 된다
                    skipped = plan.files.filter { it.action == FileAction.MODIFY }.map { it.path }.toSet(),
                    updateRepo = blocked == null,
                    updateReadme = false,
                    repoBlockedReason = blocked,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "계획 만들기 실패" }
                _state.update { step -> (step as? SetupStep.Labeling)?.copy(planning = null, aiError = "계획을 만들지 못했어요: ${e.message}") ?: step }
            }
        }
    }

    fun cancelPlanning() {
        planJob?.cancel()
        planJob = null
        _state.update { step -> (step as? SetupStep.Labeling)?.copy(planning = null) ?: step }
    }

    private suspend fun adaptWithAi(labeling: SetupStep.Labeling, plan: IntegrationPlan): IntegrationPlan {
        val targets = AiAdaptation.targets(labeling.project, plan.files)
        if (targets.isEmpty()) return plan
        val reasons = targets.map { "${it.file.path.fileName}: ${it.reason}" }
        if (!ai.state.value.isReady) {
            return plan.copy(notes = plan.notes + "AI 를 켜면 스캐너가 알아보지 못한 부분을 프로젝트에 맞게 고쳐 줘요 (${targets.joinToString(", ") { it.file.path.fileName.toString() }})")
        }
        setPlanning(PlanningProgress("프로젝트 코드를 읽는 중…", 0, targets.size))
        val notes = AiAdaptation.projectNotes(labeling.project)
        val files = plan.files.toMutableList()
        val results = mutableListOf<String>()
        targets.forEachIndexed { i, target ->
            setPlanning(PlanningProgress("AI 로 ${target.file.path.fileName} 을 프로젝트에 맞추는 중…", i, targets.size))
            val adapted = AiAdaptation.adapt(ai.codeAdapter, target, notes)
            if (adapted != null) {
                files[target.index] = adapted
                results += "AI 가 ${target.file.path.fileName} 을 프로젝트에 맞게 고쳤어요 (${target.reason}). 저장 전에 꼭 읽어 보세요"
            } else {
                results += "AI 결과가 검사를 통과하지 못해 ${target.file.path.fileName} 은 템플릿을 그대로 뒀어요"
            }
        }
        setPlanning(PlanningProgress("마무리하는 중…", targets.size, targets.size))
        logger.info { "AI 코드 적응: ${reasons.joinToString()} → ${results.size}건" }
        return plan.copy(files = files, notes = plan.notes + results)
    }

    private fun setPlanning(progress: PlanningProgress) {
        _state.update { step -> (step as? SetupStep.Labeling)?.copy(planning = progress) ?: step }
    }

    /**
     * 파일 하나를 쓸지 말지 뒤집는다
     *
     * Path 는 Iterable<Path> 라서 `집합 + 경로` 를 쓰면 경로가 조각(app, src, main…)으로 들어간다. 그래서 직접 넣고 뺀다.
     */
    fun toggleFile(path: Path) {
        _state.update { step ->
            (step as? SetupStep.Plan)?.let { plan ->
                val next = plan.skipped.toMutableSet()
                if (!next.remove(path)) next.add(path)
                plan.copy(skipped = next)
            } ?: step
        }
    }

    fun updatePlan(transform: (SetupStep.Plan) -> SetupStep.Plan) {
        _state.update { step -> (step as? SetupStep.Plan)?.let(transform) ?: step }
    }

    fun backToLabeling() {
        val plan = _state.value as? SetupStep.Plan ?: return
        _state.value = SetupStep.Labeling(plan.project, plan.screens)
    }

    fun run() {
        val plan = _state.value as? SetupStep.Plan ?: return
        scope.launch {
            val written = mutableListOf<Path>()
            var prUrl: String? = null
            var note: String? = null
            try {
                val kept = plan.plan.files.filterNot { it.path in plan.skipped }
                if (plan.writeFiles && kept.isNotEmpty()) {
                    _state.value = SetupStep.Running(null, "프로젝트에 파일을 쓰는 중…")
                    withContext(Dispatchers.IO) { IntegrationWriter.write(plan.project.root, plan.plan.copy(files = kept)) }
                    written += kept.map { it.path }
                }
                if (plan.updateRepo && plan.repoBlockedReason == null) {
                    _state.value = SetupStep.Running(ApplyProgress(), "저장소에 화면 목록을 올리는 중…")
                    val schema = editor.current.schema ?: error("스키마가 없어요")
                    val newSchemaText = SchemaRenderer.withScreens(schema.text, plan.screens)
                    val files = linkedMapOf(RepoTemplates.SCHEMA_PATH to newSchemaText)
                    if (plan.updateReadme) {
                        val readmeSha = try {
                            session.api.getFile(editor.ref, RepoTemplates.README_PATH, editor.current.branch).sha
                        } catch (e: GitHubException.NotFound) {
                            null
                        }
                        editor.registerFileSha(RepoTemplates.README_PATH, readmeSha)
                        files[RepoTemplates.README_PATH] = RepoTemplates.readme(plan.project.name, editor.ref.owner, editor.ref.name, ConfigSchema.parse(newSchemaText))
                    }
                    if (newSchemaText == schema.text && !plan.updateReadme) {
                        note = "화면 목록이 이미 같아서 저장소는 바꾸지 않았어요."
                    } else {
                        val body = buildString {
                            append("### 화면 목록\n\n")
                            plan.screens.forEach { append("- `").append(it.id).append("` ").append(it.label).append(it.group?.let { g -> " ($g)" } ?: "").append('\n') }
                            append("\n프로젝트 ").append(plan.project.name).append(" 을 스캔해 스위치보드에서 적용")
                        }
                        val result = editor.applyFiles(files, "원격 설정: 화면 목록 갱신 (${plan.screens.size - 1}개)", body) { progress ->
                            _state.value = SetupStep.Running(progress, "저장소에 화면 목록을 올리는 중…")
                        }
                        prUrl = result.pullRequestUrl
                    }
                }
                _state.value = SetupStep.Done(written, prUrl, plan.plan.manualSteps, note)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "프로젝트 세팅 실패" }
                _state.value = SetupStep.Done(written, prUrl, plan.plan.manualSteps, "저장소 갱신에 실패했어요: ${e.message}")
            }
        }
    }

    /** 스캔한 목적지의 주석·구역을 AI 하네스 힌트로 바꾼다 */
    private fun hintsFor(project: AndroidProject): Map<String, ScreenHint> =
        project.destinations.associate { it.name to ScreenHint(it.name, it.comment, it.section) }

    fun restart() {
        aiJob?.cancel()
        planJob?.cancel()
        _state.value = SetupStep.PickFolder()
    }
}

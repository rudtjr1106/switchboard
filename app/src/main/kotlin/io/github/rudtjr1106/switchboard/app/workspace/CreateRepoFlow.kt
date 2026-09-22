package io.github.rudtjr1106.switchboard.app.workspace

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.config.ConfigSchema
import io.github.rudtjr1106.switchboard.config.SchemaRenderer
import io.github.rudtjr1106.switchboard.config.ScreenCatalog
import io.github.rudtjr1106.switchboard.config.templates.RepoTemplates
import io.github.rudtjr1106.switchboard.github.BootstrapProgress
import io.github.rudtjr1106.switchboard.github.BootstrapRequest
import io.github.rudtjr1106.switchboard.github.GitHubClientFactory
import io.github.rudtjr1106.switchboard.github.GitHubOwner
import io.github.rudtjr1106.switchboard.github.GitHubRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

sealed interface CreateRepoState {
    data class Form(
        val owner: GitHubOwner,
        val name: String = "",
        val description: String = "앱이 원격으로 읽어가는 설정 저장소",
        val includeMinimumVersion: Boolean = true,
        val error: String? = null,
    ) : CreateRepoState {
        val nameError: String?
            get() = when {
                name.isEmpty() -> null
                !NAME_PATTERN.matches(name) -> "영문·숫자·-·_·. 만 쓸 수 있어요"
                else -> null
            }
        val canSubmit: Boolean get() = name.isNotBlank() && nameError == null
    }

    data class Running(val progress: BootstrapProgress) : CreateRepoState
    data class Done(val repo: GitHubRepo, val progress: BootstrapProgress) : CreateRepoState
    data class Failed(val progress: BootstrapProgress, val message: String) : CreateRepoState
}

private val NAME_PATTERN = Regex("^[A-Za-z0-9_.-]+$")

/** "새 설정 저장소 만들기" 흐름. 저장소 생성 → 파일 커밋 → Pages → 보호 규칙까지 한 번에 */
class CreateRepoFlow(
    private val session: SessionState.SignedIn,
    private val clientFactory: GitHubClientFactory,
    private val owners: List<GitHubOwner>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<CreateRepoState>(CreateRepoState.Form(owner = owners.first()))
    val state: StateFlow<CreateRepoState> = _state.asStateFlow()

    fun update(transform: (CreateRepoState.Form) -> CreateRepoState.Form) {
        _state.update { (it as? CreateRepoState.Form)?.let(transform) ?: it }
    }

    fun submit() {
        val form = _state.value as? CreateRepoState.Form ?: return
        if (!form.canSubmit) return
        val schema = ConfigSchema.parse(SchemaRenderer.render(ScreenCatalog.starter().screens, includeMinimumVersion = form.includeMinimumVersion))
        val request = BootstrapRequest(
            owner = form.owner,
            name = form.name.trim(),
            description = form.description.trim(),
            files = RepoTemplates.files(appName = form.name.trim(), owner = form.owner.login, repo = form.name.trim(), schema = schema),
            topics = listOf(RepoTemplates.TOPIC, "remote-config", "android"),
            requiredCheck = RepoTemplates.CHECK_NAME,
        )
        _state.value = CreateRepoState.Running(BootstrapProgress())
        scope.launch {
            try {
                val repo = clientFactory.bootstrapper(session.api).bootstrap(request) { progress ->
                    _state.value = CreateRepoState.Running(progress)
                }
                _state.value = CreateRepoState.Done(repo, lastProgress())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "저장소 생성 실패" }
                _state.value = CreateRepoState.Failed(lastProgress(), e.message ?: "저장소를 만들지 못했어요")
            }
        }
    }

    fun backToForm() {
        val previous = _state.value
        if (previous is CreateRepoState.Running) return
        _state.value = CreateRepoState.Form(owner = owners.first())
    }

    private fun lastProgress(): BootstrapProgress = (_state.value as? CreateRepoState.Running)?.progress ?: BootstrapProgress()
}

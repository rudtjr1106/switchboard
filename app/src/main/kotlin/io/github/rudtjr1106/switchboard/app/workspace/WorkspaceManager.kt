package io.github.rudtjr1106.switchboard.app.workspace

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.app.editor.EditorModel
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import io.github.rudtjr1106.switchboard.config.templates.RepoTemplates
import io.github.rudtjr1106.switchboard.github.GitHubApi
import io.github.rudtjr1106.switchboard.github.GitHubClientFactory
import io.github.rudtjr1106.switchboard.github.GitHubException
import io.github.rudtjr1106.switchboard.github.GitHubOwner
import io.github.rudtjr1106.switchboard.github.GitHubRepo
import io.github.rudtjr1106.switchboard.github.OwnerType
import io.github.rudtjr1106.switchboard.github.RepoRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

sealed interface WorkspaceState {
    data object Idle : WorkspaceState

    /** 저장소 고르기 화면 */
    data class Browsing(
        val repos: List<GitHubRepo> = emptyList(),
        val owners: List<GitHubOwner> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val openError: String? = null,
    ) : WorkspaceState

    data class Opening(val ref: RepoRef) : WorkspaceState

    data class Open(val editor: EditorModel, val repo: GitHubRepo) : WorkspaceState
}

/** 어느 설정 저장소를 열어 두었는지. 로그인 상태가 바뀌면 [reset] 으로 비운다 */
class WorkspaceManager(
    private val settings: SettingsStore,
    private val clientFactory: GitHubClientFactory,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<WorkspaceState>(WorkspaceState.Idle)
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    val activeEditor: EditorModel? get() = (_state.value as? WorkspaceState.Open)?.editor

    /** 로그인 직후. 마지막에 열었던 저장소가 있으면 바로 연다 */
    fun start(session: SessionState.SignedIn) {
        if (_state.value !is WorkspaceState.Idle) return
        val last = settings.current.lastRepository
        if (last != null) open(session, last) else browse(session)
    }

    fun browse(session: SessionState.SignedIn) {
        _state.value = WorkspaceState.Browsing()
        scope.launch {
            try {
                val (owners, repos) = loadRepos(session)
                _state.update { (it as? WorkspaceState.Browsing ?: WorkspaceState.Browsing()).copy(repos = repos, owners = owners, loading = false, error = null) }
            } catch (e: GitHubException) {
                logger.warn(e) { "저장소 목록 실패" }
                _state.update { (it as? WorkspaceState.Browsing ?: WorkspaceState.Browsing()).copy(loading = false, error = e.message) }
            }
        }
    }

    fun open(session: SessionState.SignedIn, ref: RepoRef) {
        _state.value = WorkspaceState.Opening(ref)
        scope.launch {
            try {
                val repo = session.api.repository(ref)
                val repository = clientFactory.configRepository(session.api, ref, repo.defaultBranch)
                // 설정 저장소가 맞는지 두 파일의 존재로 확인한다
                repository.load()
                val editor = EditorModel(repository, scope)
                settings.update { it.copy(lastRepository = ref) }
                _state.value = WorkspaceState.Open(editor, repo)
                editor.load()
            } catch (e: GitHubException) {
                val message = when (e) {
                    is GitHubException.NotFound -> "${ref.fullName} 은 설정 저장소가 아니에요. ${RepoTemplates.CONFIG_PATH} 과 ${RepoTemplates.SCHEMA_PATH} 이 있어야 해요."
                    else -> e.message ?: "저장소를 열지 못했어요"
                }
                logger.warn(e) { "저장소 열기 실패: ${ref.fullName}" }
                settings.update { if (it.lastRepository == ref) it.copy(lastRepository = null) else it }
                browse(session)
                _state.update { (it as? WorkspaceState.Browsing)?.copy(openError = message) ?: it }
            }
        }
    }

    fun opened(session: SessionState.SignedIn, repo: GitHubRepo) = open(session, repo.ref)

    fun close(session: SessionState.SignedIn) {
        settings.update { it.copy(lastRepository = null) }
        browse(session)
    }

    fun reset() {
        _state.value = WorkspaceState.Idle
    }

    private suspend fun loadRepos(session: SessionState.SignedIn): Pair<List<GitHubOwner>, List<GitHubRepo>> = coroutineScope {
        val api: GitHubApi = session.api
        val orgs = async { runCatching { api.organizations() }.getOrElse { emptyList() } }
        val me = GitHubOwner(session.user.login, OwnerType.USER, session.user.avatarUrl)
        val owners = listOf(me) + orgs.await()
        val repos = api.repositoriesWithTopic(RepoTemplates.TOPIC, owners.map { it.login })
            .sortedWith(compareBy({ it.ref.owner != session.user.login }, { it.ref.fullName }))
        owners to repos
    }
}

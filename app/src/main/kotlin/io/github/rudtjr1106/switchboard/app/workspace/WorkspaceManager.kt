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
import kotlinx.coroutines.Job
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

    /**
     * 저장소 고르기 화면
     *
     * @property returnTo 편집기에서 '바꾸기' 로 왔으면 그 편집기. 뒤로가기로 고치던 값 그대로 돌아간다
     */
    data class Browsing(
        val repos: List<GitHubRepo> = emptyList(),
        val owners: List<GitHubOwner> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val openError: String? = null,
        val returnTo: Open? = null,
    ) : WorkspaceState

    data class Opening(val ref: RepoRef, val returnTo: Open? = null) : WorkspaceState

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

    private var browseJob: Job? = null
    private var openJob: Job? = null

    val activeEditor: EditorModel? get() = (_state.value as? WorkspaceState.Open)?.editor

    /** 화면에 보이든 저장소 고르기 뒤에 숨어 있든, 아직 살아 있는 편집기 */
    val editorInUse: EditorModel?
        get() = when (val s = _state.value) {
            is WorkspaceState.Open -> s.editor
            is WorkspaceState.Browsing -> s.returnTo?.editor
            is WorkspaceState.Opening -> s.returnTo?.editor
            WorkspaceState.Idle -> null
        }

    /** 로그인 직후. 마지막에 열었던 저장소가 있으면 바로 연다 */
    fun start(session: SessionState.SignedIn) {
        if (_state.value !is WorkspaceState.Idle) return
        val last = settings.current.lastRepository
        if (last != null) open(session, last) else browse(session)
    }

    fun browse(session: SessionState.SignedIn, returnTo: WorkspaceState.Open? = null, openError: String? = null) {
        browseJob?.cancel()
        _state.value = WorkspaceState.Browsing(returnTo = returnTo, openError = openError)
        browseJob = scope.launch {
            try {
                val (owners, repos) = loadRepos(session)
                updateBrowsing { it.copy(repos = repos, owners = owners, loading = false, error = null) }
            } catch (e: GitHubException) {
                logger.warn(e) { "저장소 목록 실패" }
                updateBrowsing { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /** 편집기에서 다른 저장소로 바꾸러 간다. 지금 편집기는 [back] 으로 돌아올 수 있게 남겨 둔다 */
    fun switchRepository(session: SessionState.SignedIn) {
        browse(session, returnTo = _state.value as? WorkspaceState.Open)
    }

    /** 저장소 고르기에서 원래 편집기로 돌아간다 */
    fun back() {
        val returnTo = (_state.value as? WorkspaceState.Browsing)?.returnTo ?: return
        browseJob?.cancel()
        _state.value = returnTo
    }

    fun open(session: SessionState.SignedIn, ref: RepoRef) {
        val previous = _state.value
        val returnTo = when (previous) {
            is WorkspaceState.Browsing -> previous.returnTo
            is WorkspaceState.Open -> previous
            else -> null
        }
        // 이미 열려 있는 저장소를 다시 고르면 고치던 값을 버리지 않고 그대로 돌아간다
        if (returnTo != null && returnTo.editor.ref == ref) {
            _state.value = returnTo
            return
        }
        browseJob?.cancel()
        openJob?.cancel()
        _state.value = WorkspaceState.Opening(ref, returnTo)
        openJob = scope.launch {
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
                browse(session, returnTo = returnTo, openError = message)
            }
        }
    }

    fun opened(session: SessionState.SignedIn, repo: GitHubRepo) = open(session, repo.ref)

    /** 여는 중에 취소하면 저장소 고르기(또는 원래 편집기)로 돌아간다 */
    fun cancelOpening(session: SessionState.SignedIn) {
        val opening = _state.value as? WorkspaceState.Opening ?: return
        openJob?.cancel()
        browse(session, returnTo = opening.returnTo)
    }

    fun reset() {
        browseJob?.cancel()
        openJob?.cancel()
        _state.value = WorkspaceState.Idle
    }

    private fun updateBrowsing(transform: (WorkspaceState.Browsing) -> WorkspaceState.Browsing) {
        _state.update { (it as? WorkspaceState.Browsing)?.let(transform) ?: it }
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

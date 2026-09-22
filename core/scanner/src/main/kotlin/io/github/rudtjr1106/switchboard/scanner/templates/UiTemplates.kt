package io.github.rudtjr1106.switchboard.scanner.templates

import io.github.rudtjr1106.switchboard.scanner.DiFramework

/** 안내 목록을 받아두고 닫은 안내를 기억하는 ViewModel. `key`·`toScreenName` 도 여기 둔다 */
internal object RemoteNoticeViewModelFile {
    fun render(ctx: TemplateContext): String {
        val hiltImports = if (ctx.isHilt) listOf("dagger.hilt.android.lifecycle.HiltViewModel", "javax.inject.Inject") else emptyList()
        val annotation = if (ctx.isHilt) "@HiltViewModel\n" else ""
        return ctx.source(
            ctx.uiPackage,
            listOf(
                "androidx.lifecycle.ViewModel",
                "androidx.lifecycle.viewModelScope",
                "${ctx.modelPackage}.RemoteNotice",
                "${ctx.useCasePackage}.GetRemoteNoticesUseCase",
                "kotlinx.coroutines.flow.MutableStateFlow",
                "kotlinx.coroutines.flow.StateFlow",
                "kotlinx.coroutines.flow.asStateFlow",
                "kotlinx.coroutines.flow.update",
                "kotlinx.coroutines.launch",
            ) + hiltImports,
            """
/**
 * 원격 설정 저장소(${ctx.repoFullName})의 화면별 안내를 받아두고, 이미 닫은 안내를 기억한다. 스위치보드가 만든 파일이다
 */
${annotation}class RemoteNoticeViewModel${ctx.injectConstructor}(
    private val getRemoteNoticesUseCase: GetRemoteNoticesUseCase,
) : ViewModel() {

    private val _notices = MutableStateFlow<List<RemoteNotice>>(emptyList())
    val notices: StateFlow<List<RemoteNotice>> = _notices.asStateFlow()

    // 이번 실행 중에 닫은 안내. 같은 화면에 다시 들어와도 또 띄우지 않는다
    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    /**
     * 앱이 화면에 올라올 때마다 부른다
     *
     * 클라이언트 캐시 덕분에 10분 안에는 네트워크를 쓰지 않는다.
     * 받아오지 못하면 마지막으로 받은 목록을 그대로 둔다.
     */
    fun refresh() {
        viewModelScope.launch {
            getRemoteNoticesUseCase().onSuccess { _notices.value = it }
        }
    }

    fun dismiss(notice: RemoteNotice) {
        _dismissed.update { it + notice.key }
    }
}

/** 안내를 구분하는 값. 운영진이 문구를 고치면 다른 안내로 보고 다시 띄운다 */
internal val RemoteNotice.key: String
    get() = "${'$'}screen|${'$'}title|${'$'}body"

/**
 * route 문자열에서 경로 이름만 꺼낸다
 *
 * `com.example.MainDestination.NoticeDetail/{noticeId}` → `NoticeDetail`, `home/{id}` → `home`
 *
 * 클래스 이름(`::class.simpleName`)은 release 빌드에서 R8 이 바꿔버리지만,
 * route 는 직렬화 이름에서 만들어지므로 난독화돼도 원래 이름이 남는다.
 */
internal fun String.toScreenName(): String =
    substringBefore('/').substringBefore('?').substringAfterLast('.')
""",
        )
    }
}

/** 현재 화면 위에 안내를 띄우는 Composable. 디자인 시스템에 기대지 않고 Material 기본 컴포넌트만 쓴다 */
internal object RemoteNoticeHostFile {
    fun render(ctx: TemplateContext): String {
        val (viewModelParam, viewModelImports) = when {
            ctx.di == DiFramework.HILT ->
                "viewModel: RemoteNoticeViewModel = hiltViewModel()," to listOf("androidx.hilt.navigation.compose.hiltViewModel")
            ctx.di == DiFramework.KOIN ->
                "viewModel: RemoteNoticeViewModel = koinViewModel()," to listOf(ctx.koinViewModelImport)
            ctx.hostHasDefaultViewModel ->
                "viewModel: RemoteNoticeViewModel = viewModel(factory = RemoteConfigContainer.viewModelFactory(LocalContext.current))," to
                    listOf("androidx.lifecycle.viewmodel.compose.viewModel", "${ctx.diPackage}.RemoteConfigContainer")
            else ->
                "viewModel: RemoteNoticeViewModel," to emptyList()
        }
        val m = ctx.material.pkg
        return ctx.source(
            ctx.uiPackage,
            listOf(
                "android.app.Activity",
                "androidx.compose.runtime.Composable",
                "androidx.compose.runtime.getValue",
                "androidx.compose.runtime.remember",
                "androidx.compose.ui.platform.LocalContext",
                "androidx.lifecycle.Lifecycle",
                "androidx.lifecycle.compose.LifecycleEventEffect",
                "androidx.lifecycle.compose.collectAsStateWithLifecycle",
                "$m.AlertDialog",
                "$m.Text",
                "$m.TextButton",
                "${ctx.modelPackage}.RemoteNoticeTemplate",
                "java.time.LocalDate",
            ) + viewModelImports,
            """
/**
 * 원격 설정으로 켠 안내를 현재 화면 위에 띄운다
 *
 * 앱 최상단(MainActivity)에 하나만 두면 각 화면은 이 기능을 몰라도 된다.
 * 어느 화면에 무엇을 띄울지는 원격 설정 저장소(${ctx.repoFullName})의 app-config.json 이 정한다. 스위치보드가 만든 파일이다.
 *
 * 이용을 막는 안내([RemoteNoticeTemplate.BLOCKING])는 하단바까지 덮어야 하므로 Scaffold 바깥에 두어야 한다.
 *
 * @param currentRoute 현재 내비게이션 route 문자열
 */
@Composable
fun RemoteNoticeHost(
    currentRoute: String?,
    $viewModelParam
) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.refresh()
    }

    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val dismissed by viewModel.dismissed.collectAsStateWithLifecycle()

    val screen = currentRoute?.toScreenName() ?: return
    val notice = remember(screen, notices, dismissed) {
        val today = LocalDate.now()
        val candidates = notices.filter { it.targets(screen) && it.isShowable(today) }

        // 이용을 막는 안내가 있으면 무엇보다 먼저 보여준다. 닫을 수 없는 안내라 '닫은 안내' 기록과는 상관없다
        candidates.firstOrNull { it.template == RemoteNoticeTemplate.BLOCKING }
            ?: candidates.firstOrNull { it.template == RemoteNoticeTemplate.INFO && it.key !in dismissed }
    } ?: return

    when (notice.template) {
        RemoteNoticeTemplate.INFO -> AlertDialog(
            onDismissRequest = { viewModel.dismiss(notice) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismiss(notice) }) {
                    Text(text = "확인")
                }
            },
            title = { Text(text = notice.title) },
            text = { Text(text = notice.body) },
        )

        RemoteNoticeTemplate.BLOCKING -> {
            val activity = LocalContext.current as? Activity
            RemoteBlockingScreen(
                title = notice.title,
                body = notice.body,
                onExitApp = { activity?.finish() },
            )
        }

        RemoteNoticeTemplate.UNKNOWN -> Unit
    }
}
""",
        )
    }
}

/** 앱 이용을 막는 전체 화면. 터치를 삼키고 뒤로 가기를 막아 아래 화면을 조작하지 못하게 한다 */
internal object RemoteBlockingScreenFile {
    fun render(ctx: TemplateContext): String {
        val m = ctx.material
        return ctx.source(
            ctx.uiPackage,
            listOf(
                "androidx.activity.compose.BackHandler",
                "androidx.compose.foundation.layout.Column",
                "androidx.compose.foundation.layout.Spacer",
                "androidx.compose.foundation.layout.fillMaxSize",
                "androidx.compose.foundation.layout.fillMaxWidth",
                "androidx.compose.foundation.layout.height",
                "androidx.compose.foundation.layout.padding",
                "androidx.compose.foundation.layout.safeDrawingPadding",
                "androidx.compose.runtime.Composable",
                "androidx.compose.ui.Alignment",
                "androidx.compose.ui.Modifier",
                "androidx.compose.ui.input.pointer.pointerInput",
                "androidx.compose.ui.text.style.TextAlign",
                "androidx.compose.ui.unit.dp",
                "${m.pkg}.Button",
                "${m.pkg}.MaterialTheme",
                "${m.pkg}.Surface",
                "${m.pkg}.Text",
            ),
            """
/**
 * 앱 이용을 막는 전용 화면
 *
 * 원격 설정 저장소(${ctx.repoFullName})의 BLOCKING 안내(점검 등)에 쓴다. 아래 화면을 조작할 수 없도록 모든 터치를 이 화면이 받아 삼키고
 * 뒤로 가기도 막는다. 사용자가 할 수 있는 건 앱 종료뿐이고, 운영진이 설정을 끄면 앱을 다시 열 때 사라진다. 스위치보드가 만든 파일이다.
 */
@Composable
fun RemoteBlockingScreen(
    title: String,
    body: String,
    onExitApp: () -> Unit,
) {
    // 뒤로 가기로 아래 화면에 돌아가지 못하게 한다
    BackHandler(enabled = true) {}

    Surface(
        modifier = Modifier
            .fillMaxSize()
            // 아래 화면으로 터치가 새지 않도록 모든 입력을 소비한다
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = title,
                style = MaterialTheme.typography.${m.titleStyle},
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = body,
                style = MaterialTheme.typography.${m.bodyStyle},
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onExitApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text(text = "앱 종료")
            }
        }
    }
}
""",
        )
    }
}

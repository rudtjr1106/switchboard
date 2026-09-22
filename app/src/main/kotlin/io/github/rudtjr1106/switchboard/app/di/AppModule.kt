package io.github.rudtjr1106.switchboard.app.di

import io.github.rudtjr1106.switchboard.ai.AiSupport
import io.github.rudtjr1106.switchboard.ai.FileModelStore
import io.github.rudtjr1106.switchboard.ai.LlamaCppEngine
import io.github.rudtjr1106.switchboard.ai.LlmCodeAdapter
import io.github.rudtjr1106.switchboard.ai.LlmEngine
import io.github.rudtjr1106.switchboard.ai.LlmNoticeCopywriter
import io.github.rudtjr1106.switchboard.ai.LlmScreenLabeler
import io.github.rudtjr1106.switchboard.ai.ModelStore
import io.github.rudtjr1106.switchboard.app.ai.AiManager
import io.github.rudtjr1106.switchboard.app.platform.AppPaths
import io.github.rudtjr1106.switchboard.app.session.SessionManager
import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import io.github.rudtjr1106.switchboard.app.update.UpdateChecker
import io.github.rudtjr1106.switchboard.app.workspace.WorkspaceManager
import io.github.rudtjr1106.switchboard.github.DefaultGitHubClientFactory
import io.github.rudtjr1106.switchboard.github.DeviceFlowAuthenticator
import io.github.rudtjr1106.switchboard.github.GhCliTokenProvider
import io.github.rudtjr1106.switchboard.github.GitHubClientFactory
import io.github.rudtjr1106.switchboard.github.GitHubHttp
import io.github.rudtjr1106.switchboard.github.GitHubToken
import io.github.rudtjr1106.switchboard.github.TokenSource
import io.github.rudtjr1106.switchboard.github.KtorDeviceFlowAuthenticator
import io.github.rudtjr1106.switchboard.github.ProcessGhCliTokenProvider
import io.github.rudtjr1106.switchboard.github.TokenStore
import io.github.rudtjr1106.switchboard.github.TokenStores
import io.github.rudtjr1106.switchboard.scanner.AndroidProjectScanner
import io.github.rudtjr1106.switchboard.scanner.GradleAndroidProjectScanner
import io.github.rudtjr1106.switchboard.scanner.IntegrationGenerator
import io.github.rudtjr1106.switchboard.scanner.TemplateIntegrationGenerator
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** 앱 전역 객체 조립. 화면은 [AppContainer] 만 받는다 */
val appModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    single { SettingsStore(AppPaths.settingsFile) }
    single<HttpClient> { GitHubHttp.client() }
    single<GitHubClientFactory> { DefaultGitHubClientFactory(get()) }
    single<TokenStore> { TokenStores.default(AppPaths.credentialsFile) }
    single<GhCliTokenProvider> { ProcessGhCliTokenProvider() }
    single<DeviceFlowAuthenticator> {
        val factory = get<GitHubClientFactory>()
        KtorDeviceFlowAuthenticator(
            client = get(),
            apiFactory = { token -> factory.api(GitHubToken(token, TokenSource.DEVICE_FLOW)) },
        )
    }
    single { SessionManager(get(), get(), get(), get(), get(), get()) }
    single { WorkspaceManager(get(), get(), get()) }
    single { UpdateChecker(get(), get()) }

    single<ModelStore> { FileModelStore(AppPaths.modelsDir, get()) }
    single<LlmEngine> { LlamaCppEngine() }
    single {
        AiManager(
            store = get(),
            engine = get(),
            settings = get(),
            scope = get(),
            supported = AiSupport.platformSupported(),
            platformDescription = AiSupport.describePlatform(),
            copywriterFactory = { engine -> LlmNoticeCopywriter(engine) },
            labelerFactory = { engine -> LlmScreenLabeler(engine) },
            codeAdapterFactory = { engine -> LlmCodeAdapter(engine) },
        )
    }
    single<AndroidProjectScanner> { GradleAndroidProjectScanner() }
    single<IntegrationGenerator> { TemplateIntegrationGenerator() }

    single { AppContainer(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

fun createAppContainer(): AppContainer {
    val koin = startKoin { modules(appModule) }.koin
    return koin.get()
}

package io.github.rudtjr1106.switchboard.scanner.templates

import io.github.rudtjr1106.switchboard.scanner.HttpStack

/**
 * HTTP 스택별 클라이언트·API 생성 코드. Hilt / Koin / 컨테이너 세 곳에서 같은 식을 쓴다
 *
 * [clientExpr] 와 [apiExpr] 는 들여쓰기 없는 여러 줄 식이고, 쓰는 쪽이 [indent] 로 자리에 맞춘다.
 */
private class ClientCode(
    val imports: List<String>,
    val clientType: String,
    val constants: String,
    val clientExpr: (contextExpr: String) -> String,
    val apiExpr: (clientExpr: String) -> String,
) {
    companion object {
        fun of(ctx: TemplateContext): ClientCode = if (ctx.isRetrofit) retrofit(ctx) else ktor(ctx)

        private fun retrofit(ctx: TemplateContext): ClientCode {
            val (converterImports, converter) = when (ctx.http) {
                HttpStack.RETROFIT_KOTLINX -> listOf(
                    "kotlinx.serialization.json.Json",
                    "okhttp3.MediaType.Companion.toMediaType",
                    ctx.kotlinxConverterImport,
                ) to "Json { ignoreUnknownKeys = true }.asConverterFactory(\"application/json\".toMediaType())"
                HttpStack.RETROFIT_MOSHI -> listOf("retrofit2.converter.moshi.MoshiConverterFactory") to "MoshiConverterFactory.create()"
                else -> listOf("retrofit2.converter.gson.GsonConverterFactory") to "GsonConverterFactory.create()"
            }
            return ClientCode(
                imports = listOf("okhttp3.Cache", "okhttp3.OkHttpClient", "retrofit2.Retrofit", "java.io.File", "java.util.concurrent.TimeUnit") + converterImports,
                clientType = "OkHttpClient",
                constants = """
private const val CACHE_DIR = "remote_config"
private const val CACHE_SIZE_BYTES = 1L * 1024 * 1024
private const val TIMEOUT_SECONDS = 10L
""".trim(),
                clientExpr = { context ->
                    """
OkHttpClient.Builder()
    .cache(Cache(File($context.cacheDir, CACHE_DIR), CACHE_SIZE_BYTES))
    .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .build()
""".trim()
                },
                apiExpr = { client ->
                    """
Retrofit.Builder()
    .baseUrl(RemoteConfigApi.BASE_URL)
    .client($client)
    .addConverterFactory($converter)
    .build()
    .create(RemoteConfigApi::class.java)
""".trim()
                },
            )
        }

        private fun ktor(ctx: TemplateContext): ClientCode = ClientCode(
            imports = listOfNotNull(
                "io.ktor.client.HttpClient",
                "io.ktor.client.plugins.HttpTimeout",
                "io.ktor.client.plugins.cache.HttpCache",
                "io.ktor.client.plugins.cache.storage.FileStorage",
                "io.ktor.client.plugins.contentnegotiation.ContentNegotiation",
                "io.ktor.serialization.kotlinx.json.json",
                "kotlinx.serialization.json.Json",
                "java.io.File",
                ctx.ktorEngine?.import,
            ),
            clientType = "HttpClient",
            constants = """
private const val CACHE_DIR = "remote_config"
private const val TIMEOUT_MILLIS = 10_000L
""".trim(),
            clientExpr = { context ->
                """
HttpClient(${ctx.ktorEngine?.name.orEmpty()}) {
    // 404 같은 실패도 예외로 받아 캐시 폴백으로 넘어가게 한다
    expectSuccess = true
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    // 디스크 캐시로 GitHub Pages 의 Cache-Control(10분)과 ETag 를 그대로 따른다
    install(HttpCache) {
        publicStorage(FileStorage(File($context.cacheDir, CACHE_DIR)))
    }
    install(HttpTimeout) {
        requestTimeoutMillis = TIMEOUT_MILLIS
        connectTimeoutMillis = TIMEOUT_MILLIS
    }
}
""".trim()
            },
            apiExpr = { client -> "RemoteConfigApi($client)" },
        )
    }
}

/** 첫 줄은 그대로 두고 나머지 줄 앞에 [indent] 를 붙인다. 식을 `return ` 이나 `val x = ` 뒤에 이어 붙일 때 쓴다 */
private fun String.indentContinuation(indent: String): String =
    lines().mapIndexed { i, line -> if (i == 0 || line.isBlank()) line else indent + line }.joinToString("\n")

private fun String.indentAll(indent: String): String =
    lines().joinToString("\n") { if (it.isBlank()) it else indent + it }

/** Hilt: `@Provides` 로 클라이언트와 API 를 준다. 구분자를 같이 정의해 앱의 다른 클라이언트와 섞이지 않게 한다 */
internal object HiltRemoteConfigModuleFile {
    fun render(ctx: TemplateContext): String {
        val code = ClientCode.of(ctx)
        val qualifier = if (ctx.isRetrofit) "RemoteConfigOkHttpClient" else "RemoteConfigHttpClient"
        val imports = code.imports + listOf(
            "android.content.Context",
            "${ctx.dataPackage}.RemoteConfigApi",
            "dagger.Module",
            "dagger.Provides",
            "dagger.hilt.InstallIn",
            "dagger.hilt.android.qualifiers.ApplicationContext",
            "dagger.hilt.components.SingletonComponent",
            "javax.inject.Qualifier",
            "javax.inject.Singleton",
        )
        return ctx.source(
            ctx.diPackage,
            imports,
            """
/** 원격 설정 전용 클라이언트 구분자. 앱의 다른 클라이언트(인증 토큰이 붙는)와 섞이지 않게 한다 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class $qualifier

/**
 * 원격 설정 저장소(${ctx.repoFullName}) 네트워크 구성
 *
 * 기존 DI 모듈을 고치지 않아도 되도록 필요한 것을 모두 여기서 제공한다. 스위치보드가 만든 파일이다.
 */
@Module
@InstallIn(SingletonComponent::class)
object RemoteConfigModule {

${code.constants.indentAll("    ")}

    /**
     * 설정 파일 전용 클라이언트
     *
     * - API 서버용 클라이언트를 쓰면 인증 토큰이 GitHub 으로 함께 나가므로 따로 둔다
     * - 디스크 캐시를 붙여 GitHub Pages 의 Cache-Control(10분)과 ETag 를 그대로 따른다.
     *   10분 안에는 네트워크를 쓰지 않고, 그 뒤에는 바뀐 게 없으면 304 로 끝난다
     */
    @Provides
    @Singleton
    @$qualifier
    fun provideRemoteConfigHttpClient(@ApplicationContext context: Context): ${code.clientType} {
        return ${code.clientExpr("context").indentContinuation("        ")}
    }

    @Provides
    @Singleton
    fun provideRemoteConfigApi(@$qualifier client: ${code.clientType}): RemoteConfigApi {
        return ${code.apiExpr("client").indentContinuation("        ")}
    }
}
""",
        )
    }
}

/** Hilt: 데이터 소스·저장소 구현을 인터페이스에 묶는다 */
internal object HiltRemoteConfigBindModuleFile {
    fun render(ctx: TemplateContext): String = ctx.source(
        ctx.diPackage,
        listOf(
            "${ctx.dataPackage}.RemoteConfigRemoteDataSource",
            "${ctx.dataPackage}.RemoteConfigRemoteDataSourceImpl",
            "${ctx.dataPackage}.RemoteConfigRepositoryImpl",
            "${ctx.repositoryPackage}.RemoteConfigRepository",
            "dagger.Binds",
            "dagger.Module",
            "dagger.hilt.InstallIn",
            "dagger.hilt.components.SingletonComponent",
            "javax.inject.Singleton",
        ),
        """
/** 원격 설정 저장소(${ctx.repoFullName}) 연동의 구현 바인딩. 기존 RepositoryModule 등은 건드리지 않는다. 스위치보드가 만든 파일이다 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteConfigBindModule {

    @Binds
    @Singleton
    abstract fun bindRemoteConfigRemoteDataSource(impl: RemoteConfigRemoteDataSourceImpl): RemoteConfigRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteConfigRepository(impl: RemoteConfigRepositoryImpl): RemoteConfigRepository
}
""",
    )
}

/** Koin: 모듈 하나. `startKoin { modules(remoteConfigModule) }` 에 넣는다 */
internal object KoinRemoteConfigModuleFile {
    fun render(ctx: TemplateContext): String {
        val code = ClientCode.of(ctx)
        val imports = code.imports + listOf(
            "${ctx.dataPackage}.RemoteConfigApi",
            "${ctx.dataPackage}.RemoteConfigRemoteDataSource",
            "${ctx.dataPackage}.RemoteConfigRemoteDataSourceImpl",
            "${ctx.dataPackage}.RemoteConfigRepositoryImpl",
            "${ctx.repositoryPackage}.RemoteConfigRepository",
            "${ctx.useCasePackage}.GetRemoteNoticesUseCase",
            "${ctx.uiPackage}.RemoteNoticeViewModel",
            "org.koin.android.ext.koin.androidContext",
            "org.koin.core.qualifier.named",
            "org.koin.dsl.module",
            ctx.koinViewModelDslImport,
        )
        return ctx.source(
            ctx.diPackage,
            imports,
            """
/**
 * 원격 설정 저장소(${ctx.repoFullName}) 연동 Koin 모듈
 *
 * `startKoin { androidContext(this); modules(remoteConfigModule) }` 처럼 등록하면 된다. 스위치보드가 만든 파일이다.
 */
val remoteConfigModule = module {
    // 설정 파일 전용 클라이언트. API 서버용을 쓰면 인증 토큰이 GitHub 으로 함께 나가므로 따로 두고,
    // 디스크 캐시로 GitHub Pages 의 Cache-Control(10분)과 ETag 를 그대로 따른다
    single(named(REMOTE_CONFIG_CLIENT)) {
        ${code.clientExpr("androidContext()").indentContinuation("        ")}
    }
    single {
        ${code.apiExpr("get<${code.clientType}>(named(REMOTE_CONFIG_CLIENT))").indentContinuation("        ")}
    }
    single<RemoteConfigRemoteDataSource> { RemoteConfigRemoteDataSourceImpl(get()) }
    single<RemoteConfigRepository> { RemoteConfigRepositoryImpl(get()) }
    factory { GetRemoteNoticesUseCase(get()) }
    viewModel { RemoteNoticeViewModel(get()) }
}

private const val REMOTE_CONFIG_CLIENT = "remoteConfigClient"
${code.constants}
""",
        )
    }
}

/** DI 없음: 싱글턴을 직접 들고 있는 컨테이너와 ViewModel 팩토리 */
internal object RemoteConfigContainerFile {
    fun render(ctx: TemplateContext): String {
        val code = ClientCode.of(ctx)
        val imports = code.imports + listOf(
            "android.content.Context",
            "androidx.lifecycle.ViewModelProvider",
            "androidx.lifecycle.viewmodel.initializer",
            "androidx.lifecycle.viewmodel.viewModelFactory",
            "${ctx.dataPackage}.RemoteConfigApi",
            "${ctx.dataPackage}.RemoteConfigRemoteDataSourceImpl",
            "${ctx.dataPackage}.RemoteConfigRepositoryImpl",
            "${ctx.repositoryPackage}.RemoteConfigRepository",
            "${ctx.useCasePackage}.GetRemoteNoticesUseCase",
            "${ctx.uiPackage}.RemoteNoticeViewModel",
        )
        return ctx.source(
            ctx.diPackage,
            imports,
            """
/**
 * 원격 설정 저장소(${ctx.repoFullName}) 연동 조립. DI 프레임워크가 없어 여기서 직접 만든다
 *
 * 처음 부를 때 한 번 만들고 앱이 살아 있는 동안 같은 것을 돌려준다. 스위치보드가 만든 파일이다.
 */
object RemoteConfigContainer {

${code.constants.indentAll("    ")}

    @Volatile
    private var repository: RemoteConfigRepository? = null

    fun repository(context: Context): RemoteConfigRepository =
        repository ?: synchronized(this) {
            repository ?: create(context.applicationContext).also { repository = it }
        }

    fun getRemoteNoticesUseCase(context: Context): GetRemoteNoticesUseCase =
        GetRemoteNoticesUseCase(repository(context))

    /** `viewModel(factory = RemoteConfigContainer.viewModelFactory(context))` 로 RemoteNoticeViewModel 을 만든다 */
    fun viewModelFactory(context: Context): ViewModelProvider.Factory {
        val useCase = getRemoteNoticesUseCase(context)
        return viewModelFactory {
            initializer { RemoteNoticeViewModel(useCase) }
        }
    }

    // 설정 파일 전용 클라이언트. API 서버용을 쓰면 인증 토큰이 GitHub 으로 함께 나가므로 따로 두고,
    // 디스크 캐시로 GitHub Pages 의 Cache-Control(10분)과 ETag 를 그대로 따른다
    private fun create(appContext: Context): RemoteConfigRepository {
        val client = ${code.clientExpr("appContext").indentContinuation("        ")}
        val api = ${code.apiExpr("client").indentContinuation("        ")}
        return RemoteConfigRepositoryImpl(RemoteConfigRemoteDataSourceImpl(api))
    }
}
""",
        )
    }
}

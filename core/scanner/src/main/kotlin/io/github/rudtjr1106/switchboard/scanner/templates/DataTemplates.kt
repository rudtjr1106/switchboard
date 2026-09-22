package io.github.rudtjr1106.switchboard.scanner.templates

import io.github.rudtjr1106.switchboard.scanner.HttpStack

/** 직렬화 라이브러리별 DTO 애노테이션. kotlinx 는 빠진 필드에 기본값이 있어야 해서 `= null` 을 붙인다 */
private class Serialization(val imports: List<String>, val classAnnotation: String, val field: (String) -> String, val default: String) {
    companion object {
        fun of(http: HttpStack): Serialization = when (http) {
            HttpStack.RETROFIT_KOTLINX, HttpStack.KTOR -> Serialization(
                imports = listOf("kotlinx.serialization.SerialName", "kotlinx.serialization.Serializable"),
                classAnnotation = "@Serializable\n",
                field = { "@SerialName(\"$it\") " },
                default = " = null",
            )
            HttpStack.RETROFIT_MOSHI -> Serialization(
                imports = listOf("com.squareup.moshi.Json", "com.squareup.moshi.JsonClass"),
                classAnnotation = "@JsonClass(generateAdapter = true)\n",
                field = { "@Json(name = \"$it\") " },
                default = "",
            )
            HttpStack.RETROFIT_GSON, HttpStack.NONE -> Serialization(
                imports = listOf("com.google.gson.annotations.SerializedName"),
                classAnnotation = "",
                field = { "@SerializedName(\"$it\") " },
                default = "",
            )
        }
    }
}

/** 설정 파일 조회 API. Retrofit 이면 인터페이스, Ktor 면 HttpClient 를 감싼 클래스. 시그니처는 같아서 데이터 소스는 구분하지 않는다 */
internal object RemoteConfigApiFile {
    fun render(ctx: TemplateContext): String = if (ctx.isRetrofit) retrofit(ctx) else ktor(ctx)

    private fun retrofit(ctx: TemplateContext): String = ctx.source(
        ctx.dataPackage,
        listOf("retrofit2.http.GET", "retrofit2.http.Header"),
        """
/**
 * 원격 설정 파일 조회
 *
 * API 서버가 아니라 GitHub Pages 에 올라간 정적 JSON 이라 서버 응답 봉투(success/code/result)가 없다.
 * 파일은 원격 설정 저장소(${ctx.repoFullName})가 올린다. 스위치보드가 만든 파일이다.
 */
interface RemoteConfigApi {

    @GET(APP_CONFIG_PATH)
    suspend fun getAppConfig(
        // 네트워크가 안 될 때만 캐시 강제 값을 넘긴다. 평소에는 null 이라 헤더가 붙지 않는다
        @Header("Cache-Control") cacheControl: String?,
    ): AppConfigResponse

    companion object {
        // 원격 설정 저장소(${ctx.repoFullName})가 올라가는 GitHub Pages
        const val BASE_URL = "${ctx.baseUrl}"
        const val APP_CONFIG_PATH = "${ctx.configPath}"
    }
}
""",
    )

    private fun ktor(ctx: TemplateContext): String = ctx.source(
        ctx.dataPackage,
        listOf(
            "io.ktor.client.HttpClient",
            "io.ktor.client.call.body",
            "io.ktor.client.request.get",
            "io.ktor.client.request.header",
            "io.ktor.http.HttpHeaders",
        ),
        """
/**
 * 원격 설정 파일 조회
 *
 * API 서버가 아니라 GitHub Pages 에 올라간 정적 JSON 이라 서버 응답 봉투(success/code/result)가 없다.
 * 파일은 원격 설정 저장소(${ctx.repoFullName})가 올린다. 스위치보드가 만든 파일이다.
 */
class RemoteConfigApi(
    private val client: HttpClient,
) {

    /** [cacheControl] 은 네트워크가 안 될 때만 캐시 강제 값을 넘긴다. 평소에는 null 이라 헤더가 붙지 않는다 */
    suspend fun getAppConfig(cacheControl: String?): AppConfigResponse {
        return client.get(BASE_URL + APP_CONFIG_PATH) {
            if (cacheControl != null) header(HttpHeaders.CacheControl, cacheControl)
        }.body()
    }

    companion object {
        // 원격 설정 저장소(${ctx.repoFullName})가 올라가는 GitHub Pages
        const val BASE_URL = "${ctx.baseUrl}"
        const val APP_CONFIG_PATH = "${ctx.configPath}"
    }
}
""",
    )
}

/** 응답 DTO. 모르는 스키마 버전이면 아무것도 띄우지 않고, 필수 값이 빠진 항목은 버린다 */
internal object AppConfigResponseFile {
    fun render(ctx: TemplateContext): String {
        val s = Serialization.of(ctx.http)
        return ctx.source(
            ctx.dataPackage,
            s.imports + listOf("${ctx.modelPackage}.RemoteNotice", "${ctx.modelPackage}.RemoteNoticeTemplate"),
            """
/** 원격 설정 저장소(${ctx.repoFullName})의 app-config.json 그대로. 스위치보드가 만든 파일이다 */
${s.classAnnotation}data class AppConfigResponse(
    ${s.field("version")}val version: Int?${s.default},
    ${s.field("notices")}val notices: List<RemoteNoticeResponse>?${s.default},
) {
    /**
     * 앱이 아는 스키마 버전일 때만 읽는다
     *
     * 설정 형식이 바뀐 파일을 구버전 앱이 잘못 해석해 엉뚱한 안내를 띄우지 않도록, 모르는 버전이면 하나도 띄우지 않는다.
     */
    fun toDomain(): List<RemoteNotice> {
        if (version != SUPPORTED_VERSION) return emptyList()
        return notices.orEmpty().mapNotNull { it.toDomain() }
    }

    companion object {
        private const val SUPPORTED_VERSION = 1
    }
}

${s.classAnnotation}data class RemoteNoticeResponse(
    ${s.field("screen")}val screen: String?${s.default},
    ${s.field("enabled")}val enabled: Boolean?${s.default},
    ${s.field("template")}val template: String?${s.default},
    ${s.field("title")}val title: String?${s.default},
    ${s.field("body")}val body: String?${s.default},
    ${s.field("until")}val until: String?${s.default},
) {
    // 필수 값이 빠진 항목은 버린다. 저장소의 스키마 검사를 통과한 파일이면 여기서 걸러질 일은 없다
    fun toDomain(): RemoteNotice? {
        if (screen.isNullOrBlank() || title.isNullOrBlank() || body.isNullOrBlank()) return null

        return RemoteNotice(
            screen = screen,
            enabled = enabled == true,
            template = RemoteNoticeTemplate.from(template),
            title = title,
            body = body,
            until = until,
        )
    }
}
""",
        )
    }
}

internal object RemoteConfigRemoteDataSourceFile {
    fun render(ctx: TemplateContext): String = ctx.source(
        ctx.dataPackage,
        emptyList(),
        """
/** 원격 설정 저장소(${ctx.repoFullName})의 설정 파일. 스위치보드가 만든 파일이다 */
interface RemoteConfigRemoteDataSource {

    // 원격 설정 파일 조회
    suspend fun getAppConfig(): Result<AppConfigResponse>
}
""",
    )
}

internal object RemoteConfigRemoteDataSourceImplFile {
    fun render(ctx: TemplateContext): String = ctx.source(
        ctx.dataPackage,
        listOf("kotlinx.coroutines.CancellationException") + ctx.injectImports,
        """
/** 네트워크로 받고, 안 되면 디스크 캐시로 버틴다. 원격 설정 저장소(${ctx.repoFullName}) 전용. 스위치보드가 만든 파일이다 */
class RemoteConfigRemoteDataSourceImpl${ctx.injectConstructor}(
    private val remoteConfigApi: RemoteConfigApi,
) : RemoteConfigRemoteDataSource {

    override suspend fun getAppConfig(): Result<AppConfigResponse> {
        return try {
            Result.success(remoteConfigApi.getAppConfig(cacheControl = null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 네트워크가 안 되면 마지막으로 받아둔 설정이라도 쓴다
            fromCache() ?: Result.failure(e)
        }
    }

    private suspend fun fromCache(): Result<AppConfigResponse>? {
        return try {
            Result.success(remoteConfigApi.getAppConfig(cacheControl = FORCE_CACHE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        // 기한이 지난 캐시라도 네트워크 없이 캐시에서만 읽는다. 캐시가 없으면 실패한다
        private const val FORCE_CACHE = "only-if-cached, max-stale=2147483647"
    }
}
""",
    )
}

internal object RemoteConfigRepositoryImplFile {
    fun render(ctx: TemplateContext): String = ctx.source(
        ctx.dataPackage,
        listOf("${ctx.modelPackage}.RemoteNotice", "${ctx.repositoryPackage}.RemoteConfigRepository") + ctx.injectImports,
        """
/** 원격 설정 저장소(${ctx.repoFullName}) 응답을 도메인 모델로 바꾼다. 스위치보드가 만든 파일이다 */
class RemoteConfigRepositoryImpl${ctx.injectConstructor}(
    private val remoteConfigRemoteDataSource: RemoteConfigRemoteDataSource,
) : RemoteConfigRepository {

    override suspend fun getNotices(): Result<List<RemoteNotice>> {
        return remoteConfigRemoteDataSource.getAppConfig().map { it.toDomain() }
    }
}
""",
    )
}

package io.github.rudtjr1106.switchboard.scanner

import io.github.rudtjr1106.switchboard.config.ScreenInfo
import java.nio.file.Path

data class IntegrationTarget(
    /** `https://owner.github.io/` */
    val pagesBaseUrl: String,
    /** `repo-name/app-config.json` */
    val configPath: String,
    /** `owner/repo` */
    val repoFullName: String,
    val screens: List<ScreenInfo>,
)

enum class FileAction { CREATE, MODIFY }

data class GeneratedFile(
    /** 프로젝트 루트 기준 상대 경로 */
    val path: Path,
    val content: String,
    val action: FileAction,
    val original: String? = null,
    /** 템플릿을 온디바이스 AI 가 프로젝트에 맞게 고쳐 쓴 파일 */
    val adaptedByAi: Boolean = false,
)

data class IntegrationPlan(
    val files: List<GeneratedFile>,
    /** 생성 근거·주의점. UI 에 보여준다 */
    val notes: List<String>,
    /** 자동으로 못 하는 일 (MainActivity 에 RemoteNoticeHost 붙이기 등) */
    val manualSteps: List<String>,
)

/** 스캔 결과에 맞춰 연동 코드를 만든다. 결정적 템플릿이 기본이고, AI 는 선택적으로 파일을 다듬는다 */
interface IntegrationGenerator {
    fun plan(project: AndroidProject, target: IntegrationTarget): IntegrationPlan
}

object IntegrationWriter {
    fun write(root: Path, plan: IntegrationPlan) {
        for (file in plan.files) {
            val target = root.resolve(file.path)
            target.parent?.toFile()?.mkdirs()
            target.toFile().writeText(file.content)
        }
    }

    /** 쓰게 될 파일들의 루트 기준 상대 경로 (`/` 구분). 적용 전에 목록으로 보여줄 때 쓴다 */
    fun preview(root: Path, plan: IntegrationPlan): List<String> = plan.files.map { file ->
        val relative = if (file.path.isAbsolute) root.relativize(file.path) else file.path
        relative.joinToString("/")
    }
}

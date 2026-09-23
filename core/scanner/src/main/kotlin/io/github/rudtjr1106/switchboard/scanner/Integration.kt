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

/**
 * 파일 하나를 빼면 남는 파일이 찾지 못하게 되는 선언
 *
 * @property skipped 빼기로 한 파일
 * @property name 그 파일이 만들던 선언 (`RemoteNoticeViewModel` 등)
 * @property neededBy 그 이름을 쓰는, 쓰기로 한 파일들
 */
data class MissingDeclaration(val skipped: Path, val name: String, val neededBy: List<Path>)

/**
 * 이미 자기 화면·다이얼로그를 갖고 있는 팀은 그 파일만 빼고 나머지를 받을 수 있다. 이때 빠진 선언을 남은 파일이
 * 쓰고 있으면 컴파일이 깨지므로 미리 알려 준다
 *
 * 프로젝트에 이미 있는 파일(MODIFY)을 빼는 건 문제가 아니다. 그 선언은 프로젝트에 이미 있다.
 */
fun IntegrationPlan.missingWhenSkipped(skipped: Set<Path>): List<MissingDeclaration> {
    val kept = files.filterNot { it.path in skipped }
    return files.filter { it.path in skipped && it.action == FileAction.CREATE }
        .flatMap { file ->
            declarations(file.content).mapNotNull { name ->
                val users = kept.filter { Regex("""\b$name\b""").containsMatchIn(it.content) }.map { it.path }
                if (users.isEmpty()) null else MissingDeclaration(file.path, name, users)
            }
        }
}

/** 파일이 만드는 최상위 선언 이름들 */
private fun declarations(content: String): List<String> =
    Regex("""(?m)^(?:internal\s+|public\s+|abstract\s+|sealed\s+|data\s+|open\s+|enum\s+)*(?:class|object|interface|fun)\s+(\w+)""")
        .findAll(content).map { it.groupValues[1] }.distinct().toList()

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

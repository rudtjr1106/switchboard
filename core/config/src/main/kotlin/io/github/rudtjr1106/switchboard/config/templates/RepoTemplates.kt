package io.github.rudtjr1106.switchboard.config.templates

import io.github.rudtjr1106.switchboard.config.AppConfig
import io.github.rudtjr1106.switchboard.config.ConfigCodec
import io.github.rudtjr1106.switchboard.config.ConfigSchema
import io.github.rudtjr1106.switchboard.config.Notice
import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import io.github.rudtjr1106.switchboard.config.ScreenCatalog

/** 새 설정 저장소에 넣을 파일들. 구조는 UMC-PRODUCT/umc-product-android-config 와 같다 */
object RepoTemplates {

    const val CONFIG_PATH = "app-config.json"
    const val SCHEMA_PATH = "schema.json"
    const val WORKFLOW_PATH = ".github/workflows/validate.yml"
    const val README_PATH = "README.md"

    /** 이 앱이 만든 저장소를 다시 찾을 때 쓰는 GitHub topic */
    const val TOPIC = "switchboard-config"

    /** validate 워크플로의 job 이름. 편집기가 검사 결과를 찾을 때와 main 보호 규칙의 필수 검사에 쓴다 */
    const val CHECK_NAME = "validate"

    fun pagesUrl(owner: String, repo: String): String = "https://${owner.lowercase()}.github.io/$repo/$CONFIG_PATH"

    fun initialConfig(schema: ConfigSchema): String {
        val maintenance = Notice(
            screen = ScreenCatalog.ALL,
            enabled = false,
            template = NoticeTemplate.BLOCKING.id,
            title = "서비스 점검 중이에요",
            body = "더 나은 서비스를 위해 점검하고 있어요. 잠시 후 다시 이용해주세요.",
        )
        return ConfigCodec.encode(AppConfig.empty(schema).add(maintenance))
    }

    fun validateWorkflow(): String = """
        |name: config 검증
        |on:
        |  pull_request:
        |  push:
        |    branches: [main]
        |
        |jobs:
        |  $CHECK_NAME:
        |    runs-on: ubuntu-latest
        |    steps:
        |      - uses: actions/checkout@v4
        |      - run: pip install jsonschema
        |      - name: JSON 문법·스키마 검사
        |        run: |
        |          python3 - <<'PY'
        |          import json, sys, jsonschema
        |          try:
        |              cfg = json.load(open("$CONFIG_PATH"))
        |          except Exception as e:
        |              sys.exit(f"JSON 문법 오류: {e}")
        |          # format_checker 가 없으면 "until": "2026/09/17" 같은 값이 통과한다
        |          jsonschema.validate(cfg, json.load(open("$SCHEMA_PATH")),
        |                              format_checker=jsonschema.FormatChecker())
        |          print(f"통과 — 안내 {len(cfg['notices'])}건")
        |          PY
        |""".trimMargin()

    fun readme(appName: String, owner: String, repo: String, schema: ConfigSchema, appRepoUrl: String? = null): String {
        val appLink = appRepoUrl?.let { "[$appName]($it)" } ?: appName
        val screenRows = schema.catalog.groups.joinToString("\n") { group ->
            group.screens.joinToString("\n") { screen ->
                val label = if (screen.isAll) "**모든 화면**. 앱을 켜는 순간부터 적용됩니다" else screen.label
                "| ${group.name.orEmpty()} | `${screen.id}` | $label |"
            }
        }
        val minimumVersionSection = if (schema.supportsMinimumVersion) {
            """
            |### `minimumVersion`
            |
            |이 버전보다 낮은 앱은 업데이트 화면으로 막힙니다. `""`(빈 값)이면 강제 업데이트를 하지 않습니다.
            |`2.3.0` 처럼 숫자와 점만 씁니다.
            |
            """.trimMargin()
        } else {
            ""
        }
        return """
            |# $repo
            |
            |$appLink 앱이 **원격으로 읽어가는 설정 저장소**입니다.
            |
            |앱을 새로 배포하지 않고도 특정 화면에 안내 다이얼로그를 켜거나 끄고, 문구를 바꿀 수 있습니다.
            |Play 스토어 심사와 사용자 업데이트를 기다릴 필요가 없습니다.
            |
            |설정은 데스크톱 편집 앱 **Switchboard** 로 고치는 방법을 추천합니다. JSON 을 직접 만지지 않아도 되고 PR 생성부터 배포 확인까지 앱이 대신 진행합니다.
            |
            |## 앱이 읽는 주소
            |
            |```
            |${pagesUrl(owner, repo)}
            |```
            |
            |GitHub Pages 로 서빙되며 캐시가 10분입니다. **머지 후 최대 10분 뒤에 앱에 반영**됩니다.
            |
            |## 파일
            |
            || 파일 | 설명 |
            ||---|---|
            || `$CONFIG_PATH` | 실제 설정. 이것만 고치면 됩니다 |
            || `$SCHEMA_PATH` | 값의 규칙. 오타·잘못된 값을 걸러냅니다 |
            || `$WORKFLOW_PATH` | PR 마다 위 규칙으로 검사 |
            |
            |## 설정 값
            |
            || 필드 | 필수 | 설명 |
            ||---|---|---|
            || `screen` | O | 어느 화면에 띄울지 |
            || `enabled` | O | `true` 면 노출, `false` 면 숨김. **평소에는 `false` 로 두고 필요할 때만 켭니다** |
            || `template` | O | 다이얼로그 모양 (`INFO` 안내 · `BLOCKING` 차단) |
            || `title` | O | 제목 (${schema.titleLimit}자 이내) |
            || `body` | O | 본문 (${schema.bodyLimit}자 이내) |
            || `until` | X | 이 날짜가 지나면 자동으로 안 뜹니다 (`YYYY-MM-DD`) |
            |
            |$minimumVersionSection
            |### `screen` 에 쓸 수 있는 값
            |
            |앱의 화면 경로 이름을 그대로 씁니다. 대소문자까지 똑같이 적어야 합니다.
            |
            || 구분 | 값 | 화면 |
            ||---|---|---|
            |$screenRows
            |
            |### `template` 에 쓸 수 있는 값
            |
            || 값 | 모양 |
            ||---|---|
            || `INFO` | 제목 + 본문 + 확인 버튼. 닫으면 앱을 다시 켜기 전까지 또 뜨지 않습니다 |
            || `BLOCKING` | 앱 전체를 덮는 전용 화면. **닫을 수 없고** "앱 종료" 버튼만 있습니다. 점검처럼 이용을 막아야 할 때 씁니다 |
            |
            |- `ALL` + `BLOCKING` 조합이 **서비스 점검(킬스위치)** 입니다
            |- 같은 화면에 `BLOCKING` 과 `INFO` 가 함께 걸리면 `BLOCKING` 이 먼저 뜹니다
            |- `BLOCKING` 은 사용자가 풀 수 없습니다. **점검이 끝나면 반드시 `enabled` 를 `false` 로 되돌리거나 `until` 을 걸어두세요**
            |
            |## 주의
            |
            |- **비밀값을 넣지 마세요.** 공개 저장소라 누구나 읽을 수 있습니다. 토큰·비밀번호·내부 주소는 금지입니다
            |- 위 표에 없는 값을 쓰면 `$CHECK_NAME` 검사에서 막힙니다
            |- `main` 은 보호돼 있어 직접 커밋할 수 없습니다. 항상 PR 로 올라갑니다
            |""".trimMargin()
    }

    /** 새 저장소에 커밋할 파일 전체 (경로 → 내용) */
    fun files(appName: String, owner: String, repo: String, schema: ConfigSchema, appRepoUrl: String? = null): Map<String, String> = linkedMapOf(
        SCHEMA_PATH to schema.text,
        CONFIG_PATH to initialConfig(schema),
        WORKFLOW_PATH to validateWorkflow(),
        README_PATH to readme(appName, owner, repo, schema, appRepoUrl),
    )
}

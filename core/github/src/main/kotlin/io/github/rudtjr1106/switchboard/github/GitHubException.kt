package io.github.rudtjr1106.switchboard.github

/** GitHub 호출·적용 과정의 실패. [message] 는 그대로 화면에 보여줄 수 있는 한국어 문장이다 */
sealed class GitHubException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class Http(val status: Int, val apiMessage: String?) : GitHubException(
        if (apiMessage.isNullOrBlank()) "GitHub 요청이 실패했어요 ($status)." else "GitHub 요청이 실패했어요 ($status): $apiMessage",
    )

    class NotFound(val what: String) : GitHubException("$what 을(를) 찾지 못했어요.")

    class Unauthorized : GitHubException("GitHub 로그인이 풀렸어요. 다시 로그인해 주세요.")

    class Forbidden(val apiMessage: String?) : GitHubException(
        "권한이 없어요." + (apiMessage?.let { " $it" } ?: ""),
    )

    class RateLimited(val resetEpochSeconds: Long?) : GitHubException("GitHub 요청 한도를 넘었어요. 잠시 뒤 다시 시도하세요.")

    class Network(cause: Throwable) : GitHubException("GitHub 에 연결하지 못했어요: ${cause.message ?: cause::class.simpleName}", cause)

    class InvalidResponse(detail: String? = null) : GitHubException(
        "GitHub 응답을 읽지 못했어요." + (detail?.let { " $it" } ?: ""),
    )

    class Conflict : GitHubException("불러온 뒤 다른 사람이 먼저 바꿨어요. 새로 불러온 뒤 다시 적용하세요.")

    class ValidationFailed(val conclusion: String) : GitHubException(
        "validate 검사를 통과하지 못했어요 ($conclusion). PR 에서 원인을 확인하세요.",
    )

    class PagesFailed(detail: String?) : GitHubException(
        "GitHub Pages 배포가 실패했어요." + (detail?.let { " $it" } ?: ""),
    )

    class TimedOut(val step: String) : GitHubException("$step 결과를 5분 안에 받지 못했어요.")

    class Cancelled : GitHubException("취소했어요.")
}

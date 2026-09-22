package io.github.rudtjr1106.switchboard.github

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * 단계별 상태를 기록하고 바뀔 때마다 스냅샷을 내보낸다
 *
 * UI 는 스냅샷만 받으므로 진행 중인 맵이 밖으로 새지 않는다. 실패한 단계는 [StepState.Failed] 로 남기고 예외는 그대로 다시 던진다.
 */
internal class StepTracker<S : Any>(private val publish: (Map<S, StepState>) -> Unit) {

    private val states = LinkedHashMap<S, StepState>()

    fun set(step: S, state: StepState) {
        states[step] = state
        publish(states.toMap())
    }

    suspend fun <T> runStep(step: S, block: suspend () -> T): T {
        set(step, StepState.Running)
        val value = try {
            block()
        } catch (e: Throwable) {
            set(step, StepState.Failed(e.userMessage()))
            throw e
        }
        set(step, StepState.Done)
        return value
    }
}

/** 화면에 그대로 보여줄 문장. [GitHubException] 은 이미 한국어라 그대로 쓰고, 취소는 예외 메시지가 영어라 바꿔 준다 */
internal fun Throwable.userMessage(): String = when (this) {
    is CancellationException -> GitHubException.Cancelled().message.orEmpty()
    else -> message?.takeIf { it.isNotBlank() } ?: "알 수 없는 오류 (${this::class.simpleName})"
}

/**
 * [isDone] 이 true 를 줄 때까지 [interval] 마다 다시 묻는다. [timeout] 안에 끝나지 않으면 [GitHubException.TimedOut]
 *
 * withTimeout 대신 [timeSource] 로 마감을 재는 이유: runTest 는 HTTP 호출처럼 가상 시간 밖에서 기다리는 동안
 * 예약된 타이머로 시계를 건너뛰어 버려 타임아웃이 곧바로 터진다. 테스트는 testScheduler.timeSource 를 넘긴다.
 */
internal suspend fun pollUntil(
    interval: Duration,
    timeout: Duration,
    step: String,
    timeSource: TimeSource = TimeSource.Monotonic,
    isDone: suspend () -> Boolean,
) {
    val started = timeSource.markNow()
    while (started.elapsedNow() < timeout) {
        if (isDone()) return
        delay(interval)
    }
    throw GitHubException.TimedOut(step)
}

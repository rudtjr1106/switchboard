package io.github.rudtjr1106.switchboard.ai

/**
 * java-llama.cpp 4.2.0 이 jar 에 네이티브 라이브러리를 묶어 둔 플랫폼인지 미리 가린다
 *
 * 로드 시점의 UnsatisfiedLinkError 보다 먼저 "이 컴퓨터에선 안 돼요" 를 보여 주려는 용도다.
 * jar 안 경로: `de/kherud/llama/{Mac,Windows,Linux}/{aarch64,x86_64}`.
 */
object AiSupport {

    fun platformSupported(os: String = osName(), arch: String = osArch()): Boolean {
        val name = os.lowercase()
        val arm64 = arch.equals("aarch64", ignoreCase = true) || arch.equals("arm64", ignoreCase = true)
        val x64 = arch.equals("x86_64", ignoreCase = true) || arch.equals("amd64", ignoreCase = true)
        return when {
            name.contains("mac") || name.contains("darwin") -> arm64 || x64
            name.contains("win") -> x64
            name.contains("linux") -> arm64 || x64
            else -> false
        }
    }

    /** 오류 메시지에 넣을 "macOS aarch64" 같은 짧은 설명 */
    fun describePlatform(): String = "${osName()} ${osArch()}"

    private fun osName(): String = System.getProperty("os.name") ?: "unknown"
    private fun osArch(): String = System.getProperty("os.arch") ?: "unknown"
}

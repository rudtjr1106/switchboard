package io.github.rudtjr1106.switchboard.ai

import java.nio.file.Path

/**
 * 내려받아 쓸 수 있는 GGUF 모델
 *
 * java-llama.cpp 4.2.0 이 묶은 llama.cpp(b4916) 가 아는 아키텍처만 넣는다. Qwen3 처럼 그 뒤에 나온 계열은 로드되지 않는다.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val description: String,
    val contextLength: Int = 8192,
    val recommended: Boolean = false,
) {
    val sizeLabel: String get() = "%.1f GB".format(sizeBytes / 1024.0 / 1024.0 / 1024.0)
}

object ModelCatalog {

    val GEMMA_3_4B = ModelSpec(
        id = "gemma-3-4b-it-q4_k_m",
        displayName = "Gemma 3 4B (권장)",
        fileName = "gemma-3-4b-it-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/unsloth/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
        sizeBytes = 2_489_894_016,
        description = "한국어 문구 다듬기와 코드 적응까지 무난하게 해내요. 메모리 16GB 이상을 권해요.",
        recommended = true,
    )

    val GEMMA_3_1B = ModelSpec(
        id = "gemma-3-1b-it-q4_k_m",
        displayName = "Gemma 3 1B (가벼움)",
        fileName = "gemma-3-1b-it-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
        sizeBytes = 806_000_000,
        description = "빠르지만 결과 품질은 낮아요. 화면 라벨 붙이기 정도에 알맞아요.",
    )

    val all: List<ModelSpec> = listOf(GEMMA_3_4B, GEMMA_3_1B)

    fun find(id: String): ModelSpec? = all.firstOrNull { it.id == id }
}

sealed interface ModelDownloadEvent {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadEvent {
        val fraction: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
    }

    data class Done(val path: Path) : ModelDownloadEvent
    data class Failed(val message: String, val cause: Throwable? = null) : ModelDownloadEvent
}

/** 모델 파일 보관 (~/Library/Application Support/Switchboard/models 등) */
interface ModelStore {
    val directory: Path
    fun installedPath(spec: ModelSpec): Path?
    fun isInstalled(spec: ModelSpec): Boolean = installedPath(spec) != null

    /** 이어받기를 지원한다. 중간에 취소하면 .part 파일이 남고 다음에 이어받는다 */
    fun download(spec: ModelSpec): kotlinx.coroutines.flow.Flow<ModelDownloadEvent>

    fun delete(spec: ModelSpec)
}

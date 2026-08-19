package com.tm.ordotakehome.llm

data class LlmUiState(
    val modelStatus: ModelStatus = ModelStatus.NotSelected,
    val modelName: String? = null,
    val isModelReady: Boolean = false,
    val isBusy: Boolean = false,
    val response: String = "",
    val stats: String = "",
    val error: LlmError? = null,
    val modelLoadTimeMs: Double? = null,
    val coldStartToReadyMs: Long? = null,
    val isSustainedBenchmarkRunning: Boolean = false,
    val sustainedBenchmarkRun: Int = 0,
    val sustainedBenchmarkResults: List<SustainedRunResult> = emptyList(),
    val sustainedBenchmarkElapsedMs: Long? = null,
)

enum class LlmError {
    EngineInitializationFailed,
    ModelLoadFailed,
    GenerationFailed,
    SustainedBenchmarkFailed,
}

data class SustainedRunResult(
    val run: Int,
    val promptTokens: Int,
    val prefillThroughput: Double,
    val ttftSeconds: Double,
    val generatedTokens: Int,
    val decodeThroughput: Double,
)

sealed interface ModelStatus {

    data object NotSelected : ModelStatus

    data object Preparing : ModelStatus

    data object Unloading : ModelStatus

    data class Loading(val modelName: String) : ModelStatus

    data object Ready : ModelStatus

    data object LoadFailed : ModelStatus
}
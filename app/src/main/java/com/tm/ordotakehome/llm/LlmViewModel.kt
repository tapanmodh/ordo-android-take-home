package com.tm.ordotakehome.llm

import android.app.Application
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.tm.ordotakehome.benchmark.BenchmarkPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LlmViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences =
        application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(LlmUiState())
    val uiState: StateFlow<LlmUiState> = _uiState.asStateFlow()

    private lateinit var engine: InferenceEngine

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        viewModelScope.launch(Dispatchers.Default) {

            val initialized = runCatching {
                engine = AiChat.getInferenceEngine(getApplication())

                val state = engine.state.first {
                    it is InferenceEngine.State.Initialized ||
                            it is InferenceEngine.State.Error
                }

                if (state is InferenceEngine.State.Error) {
                    throw state.exception
                }
            }.onFailure { throwable ->

                Log.e(TAG, "Failed to initialize inference engine", throwable)

                _uiState.value = _uiState.value.copy(
                    modelStatus = ModelStatus.NotSelected,
                    isModelReady = false,
                    isBusy = false,
                    error = LlmError.EngineInitializationFailed,
                )
            }.isSuccess

            if (!initialized) {
                return@launch
            }

            runCatching {
                loadRememberedModelIfAvailable()
            }.onFailure { throwable ->

                Log.e(TAG, "Failed to restore remembered model", throwable)

                _uiState.value = _uiState.value.copy(
                    modelStatus = ModelStatus.LoadFailed,
                    modelName = null,
                    isModelReady = false,
                    isBusy = false,
                    modelLoadTimeMs = null,
                    coldStartToReadyMs = null,
                    error = LlmError.ModelLoadFailed,
                )
            }
        }
    }

    private suspend fun loadRememberedModelIfAvailable() {
        val modelPath =
            preferences.getString(KEY_MODEL_PATH, null)
                ?: return

        val modelFile = File(modelPath)

        if (!modelFile.exists()) {
            preferences.edit {
                remove(KEY_MODEL_PATH)
            }

            _uiState.value = _uiState.value.copy(
                modelStatus = ModelStatus.NotSelected,
                modelName = null,
                isModelReady = false,
                isBusy = false,
                error = null,
            )

            return
        }

        _uiState.value = _uiState.value.copy(
            modelStatus = ModelStatus.Loading(
                modelName = modelFile.name,
            ),
            modelName = modelFile.name,
            isModelReady = false,
            isBusy = true,
            error = null,
        )

        val loadStartNs = SystemClock.elapsedRealtimeNanos()

        withContext(Dispatchers.IO) {
            engine.loadModel(modelFile.absolutePath)
        }

        val nativeLoadTimeMs =
            (SystemClock.elapsedRealtimeNanos() - loadStartNs) /
                    1_000_000.0

        val coldStartToReadyMs = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()

        _uiState.value = _uiState.value.copy(
            modelName = modelFile.name,
            modelStatus = ModelStatus.Ready,
            isModelReady = true,
            isBusy = false,
            modelLoadTimeMs = nativeLoadTimeMs,
            coldStartToReadyMs = coldStartToReadyMs,
            error = null
        )
    }

    fun selectModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBusy = true,
                modelStatus = ModelStatus.Preparing,
                response = "",
                stats = "",
                error = null,
            )

            runCatching {
                // Copy first, so a file-copy failure does not disturb
                // the currently loaded model.
                val modelFile = withContext(Dispatchers.IO) {
                    copyModelToPrivateStorage(uri)
                }

                // A model/context is already loaded.
                // llama.cpp must be cleaned up before loading another one.
                if (engine.state.value is InferenceEngine.State.ModelReady) {
                    _uiState.value = _uiState.value.copy(
                        modelStatus = ModelStatus.Unloading
                    )

                    withContext(Dispatchers.IO) {
                        engine.cleanUp()
                    }
                }

                _uiState.value = _uiState.value.copy(
                    modelStatus = ModelStatus.Loading(
                        modelName = modelFile.name,
                    ),
                    modelName = modelFile.name,
                    isModelReady = false,
                    error = null,
                )

                val loadStartNs = SystemClock.elapsedRealtimeNanos()

                withContext(Dispatchers.IO) {
                    engine.loadModel(modelFile.absolutePath)
                }

                val loadTimeMs =
                    (SystemClock.elapsedRealtimeNanos() - loadStartNs) /
                            1_000_000.0

                modelFile to loadTimeMs
            }.onSuccess { (modelFile, loadTimeMs) ->

                preferences.edit {
                    putString(KEY_MODEL_PATH, modelFile.absolutePath)
                }

                _uiState.value = _uiState.value.copy(
                    modelName = modelFile.name,
                    modelStatus = ModelStatus.Ready,
                    isModelReady = true,
                    isBusy = false,
                    modelLoadTimeMs = loadTimeMs,

                    // Manual model switch is not a cold-start measurement.
                    coldStartToReadyMs = null,

                    response = "",
                    stats = "",
                    error = null,
                )

            }.onFailure { throwable ->

                Log.e(TAG, "Failed to load selected model", throwable)

                _uiState.value = _uiState.value.copy(
                    modelStatus = ModelStatus.LoadFailed,
                    modelName = null,
                    isModelReady = false,
                    isBusy = false,
                    modelLoadTimeMs = null,
                    coldStartToReadyMs = null,
                    error = LlmError.ModelLoadFailed,
                )
            }
        }
    }

    fun generate(prompt: String) {
        if (prompt.isBlank() || !_uiState.value.isModelReady || _uiState.value.isBusy) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBusy = true,
                response = "",
                stats = "",
                error = null,
            )

            runCatching {

                // Important for controlled benchmark runs:
                // each Generate starts with an empty conversation/KV cache,
                // while keeping the model loaded.
                engine.resetConversation()

                val responseBuilder = StringBuilder()

                engine.sendUserPrompt(
                    message = prompt,
                    predictLength = 128,
                ).collect { text ->
                    responseBuilder.append(text)

                    _uiState.value = _uiState.value.copy(
                        response = responseBuilder.toString(),
                    )
                }

                engine.getGenerationStats()
            }.onSuccess { stats ->
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    modelStatus = ModelStatus.Ready,
                    stats = stats,
                    error = null,
                )
            }.onFailure { throwable ->

                Log.e(TAG, "Generation failed", throwable)

                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    modelStatus = ModelStatus.Ready,
                    error = LlmError.GenerationFailed,
                )
            }
        }
    }

    private suspend fun copyModelToPrivateStorage(uri: Uri): File = withContext(Dispatchers.IO) {

        val context = getApplication<Application>()

        val modelDirectory = File(context.filesDir, "models").apply {
            mkdirs()
        }

        val originalFileName = getDisplayName(uri) ?: "model-${System.currentTimeMillis()}.gguf"

        require(originalFileName.endsWith(".gguf", ignoreCase = true)) {
            "Selected file is not a GGUF model."
        }

        val destination = File(modelDirectory, originalFileName)

        if (!destination.exists()) {
            context.contentResolver
                .openInputStream(uri)
                ?.use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
                ?: error("Unable to open selected model.")
        }

        destination
    }

    private fun getDisplayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver

        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->

            if (!cursor.moveToFirst()) {
                return null
            }

            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

            if (index >= 0) {
                return cursor.getString(index)
            }
        }

        return null
    }

    override fun onCleared() {
        if (::engine.isInitialized) {
            engine.destroy()
        }

        super.onCleared()
    }

    private fun parseSustainedResult(
        run: Int,
        stats: String,
    ): SustainedRunResult {

        fun number(label: String): Double {
            val regex = Regex(
                """(?m)^${Regex.escape(label)}:\s*([0-9]+(?:\.[0-9]+)?)"""
            )

            return regex.find(stats)
                ?.groupValues
                ?.get(1)
                ?.toDoubleOrNull()
                ?: error("Unable to parse '$label' from:\n$stats")
        }

        return SustainedRunResult(
            run = run,
            promptTokens = number("Prompt tokens").toInt(),
            prefillThroughput = number("Prefill throughput"),
            ttftSeconds = number("TTFT"),
            generatedTokens = number("Generated tokens").toInt(),
            decodeThroughput = number("Decode throughput"),
        )
    }

    fun runSustainedBenchmark() {
        if (!_uiState.value.isModelReady) {
            return
        }

        if (_uiState.value.isBusy) {
            return
        }

        viewModelScope.launch {

            val benchmarkStartMs =
                SystemClock.elapsedRealtime()

            _uiState.value = _uiState.value.copy(
                isBusy = true,
                isSustainedBenchmarkRunning = true,
                sustainedBenchmarkRun = 0,
                sustainedBenchmarkResults = emptyList(),
                sustainedBenchmarkElapsedMs = null,
                response = "",
                stats = "",
                error = null,
            )

            try {
                repeat(BenchmarkPrompts.SUSTAINED_RUN_COUNT) { index ->

                    val runNumber = index + 1

                    _uiState.value = _uiState.value.copy(
                        sustainedBenchmarkRun = runNumber,
                    )

                    // Same model stays loaded.
                    // Only conversation/KV/sampler state is reset.
                    engine.resetConversation()

                    // We intentionally consume the output without rendering
                    // every token to the UI during this benchmark.
                    engine.sendUserPrompt(
                        message = BenchmarkPrompts.LONG_PROMPT,
                        predictLength = BenchmarkPrompts.PREDICT_LENGTH,
                    ).collect {
                        // Drain the Flow. Generation happens upstream.
                    }

                    val stats = engine.getGenerationStats()

                    val result = parseSustainedResult(run = runNumber, stats = stats)

                    _uiState.value = _uiState.value.copy(
                        sustainedBenchmarkResults =
                            _uiState.value.sustainedBenchmarkResults + result,
                        stats = stats,
                    )
                }

                val elapsedMs = SystemClock.elapsedRealtime() - benchmarkStartMs

                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    isSustainedBenchmarkRunning = false,
                    sustainedBenchmarkElapsedMs = elapsedMs,
                    error = null,
                )

            } catch (throwable: Throwable) {

                Log.e(TAG, "Sustained benchmark failed", throwable)

                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    isSustainedBenchmarkRunning = false,
                    error = LlmError.SustainedBenchmarkFailed,
                )
            }
        }
    }

    companion object {
        private const val TAG = "LlmViewModel"

        private const val PREFS_NAME = "llm_preferences"
        private const val KEY_MODEL_PATH = "model_path"
    }
}
package com.tm.ordotakehome.llm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.ordotakehome.R
import com.tm.ordotakehome.benchmark.BenchmarkPrompts

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            content()
        }
    }
}

@Composable
fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.llm_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = stringResource(R.string.llm_screen_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ModelSection(state: LlmUiState, onSelectModel: () -> Unit) {
    SectionCard(title = stringResource(R.string.model_section_title)) {
        state.modelName?.let { modelName ->
            Text(
                text = modelName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        StatusText(
            text = state.modelStatus.asText(),
            isReady = state.modelStatus is ModelStatus.Ready
        )

        state.modelLoadTimeMs?.let {
            MetricRow(
                label = stringResource(R.string.model_metric_native_load),
                value = "%.0f ms".format(it)
            )
        }

        state.coldStartToReadyMs?.let {
            MetricRow(label = stringResource(R.string.model_metric_cold_ready), value = "$it ms")
        }

        Button(
            onClick = onSelectModel,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(
                    if (state.modelName == null) {
                        R.string.model_select
                    } else {
                        R.string.model_change
                    }
                )
            )
        }
    }
}

@Composable
fun ErrorMessage(error: LlmError?, modifier: Modifier = Modifier) {
    if (error == null) {
        return
    }

    Text(
        text = error.asText(),
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun LlmError.asText(): String {
    return when (this) {
        LlmError.EngineInitializationFailed -> {
            stringResource(
                R.string.error_engine_initialization,
            )
        }

        LlmError.ModelLoadFailed -> {
            stringResource(
                R.string.error_model_load,
            )
        }

        LlmError.GenerationFailed -> {
            stringResource(
                R.string.error_generation,
            )
        }

        LlmError.SustainedBenchmarkFailed -> {
            stringResource(
                R.string.error_sustained_benchmark,
            )
        }
    }
}

@Composable
private fun ModelStatus.asText(): String {
    return when (this) {
        ModelStatus.NotSelected -> {
            stringResource(
                R.string.model_status_not_selected,
            )
        }

        ModelStatus.Preparing -> {
            stringResource(
                R.string.model_status_preparing,
            )
        }

        ModelStatus.Unloading -> {
            stringResource(
                R.string.model_status_unloading,
            )
        }

        is ModelStatus.Loading -> {
            stringResource(
                R.string.model_status_loading,
                modelName,
            )
        }

        ModelStatus.Ready -> {
            stringResource(
                R.string.model_status_ready,
            )
        }

        is ModelStatus.LoadFailed -> {
            stringResource(
                R.string.model_status_load_failed,
            )
        }
    }
}

@Composable
private fun StatusText(text: String, isReady: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color =
            if (isReady)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight =
            if (isReady)
                FontWeight.Medium
            else
                FontWeight.Normal,
    )
}

@Composable
fun PromptSection(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onUseBenchmarkPrompt: () -> Unit,
    onGenerate: () -> Unit,
    isModelReady: Boolean,
    isBusy: Boolean,
) {
    SectionCard(title = stringResource(R.string.prompt_section_title)) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            enabled = !isBusy,
            label = {
                Text(
                    text = stringResource(
                        R.string.prompt_label,
                    ),
                    fontSize = 14.sp,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor =
                    MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor =
                    MaterialTheme.colorScheme.onSurface,

                focusedLabelColor =
                    MaterialTheme.colorScheme.primary,
                unfocusedLabelColor =
                    MaterialTheme.colorScheme.onSurfaceVariant,

                focusedBorderColor =
                    MaterialTheme.colorScheme.primary,
                unfocusedBorderColor =
                    MaterialTheme.colorScheme.outline,

                cursorColor =
                    MaterialTheme.colorScheme.primary,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onUseBenchmarkPrompt,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        R.string.prompt_use_benchmark,
                    ),
                )
            }

            Button(
                onClick = onGenerate,
                enabled =
                    isModelReady &&
                            prompt.isNotBlank() &&
                            !isBusy,
                modifier = Modifier.weight(1f),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp),
                    )
                }

                Text(
                    text = stringResource(
                        if (isBusy) {
                            R.string.prompt_running
                        } else {
                            R.string.prompt_generate
                        },
                    ),
                )
            }
        }
    }
}

@Composable
fun GenerationResultSection(response: String, stats: String) {
    SectionCard(title = stringResource(R.string.generation_section_title)) {
        if (response.isNotBlank()) {
            Text(
                text = response,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 23.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (response.isNotBlank() && stats.isNotBlank()) {
            HorizontalDivider()
        }

        if (stats.isNotBlank()) {
            SelectionContainer {
                Text(
                    text = stats,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun BenchmarkSection(state: LlmUiState, onRunBenchmark: () -> Unit) {
    SectionCard(title = stringResource(R.string.benchmark_section_title)) {
        Text(
            text = stringResource(
                R.string.benchmark_description,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onRunBenchmark,
            enabled = state.isModelReady && !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.benchmark_run))
        }

        if (state.isSustainedBenchmarkRunning) {
            LinearProgressIndicator(
                progress = {
                    state.sustainedBenchmarkRun /
                            BenchmarkPrompts.SUSTAINED_RUN_COUNT.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text =
                    stringResource(
                        R.string.benchmark_run_progress,
                        state.sustainedBenchmarkRun,
                        BenchmarkPrompts.SUSTAINED_RUN_COUNT,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SustainedBenchmarkResults(
            state = state,
        )
    }
}

@Composable
private fun SustainedBenchmarkResults(state: LlmUiState) {
    if (state.sustainedBenchmarkResults.isEmpty()) {
        return
    }

    val resultText =
        remember(
            state.sustainedBenchmarkResults,
            state.sustainedBenchmarkElapsedMs,
        ) {
            buildString {
                appendLine("Run,Prompt,Prefill tok/s,TTFT,Generated,Decode tok/s")

                state.sustainedBenchmarkResults.forEach {
                    appendLine(
                        "${it.run}," +
                                "${it.promptTokens}," +
                                "%.2f".format(it.prefillThroughput) + "," +
                                "%.3f".format(it.ttftSeconds) + "," +
                                "${it.generatedTokens}," +
                                "%.2f".format(it.decodeThroughput)
                    )
                }

                state.sustainedBenchmarkElapsedMs?.let {
                    appendLine()
                    appendLine("Elapsed,%.1f s".format(it / 1000.0))
                }
            }
        }

    HorizontalDivider()

    Text(
        text = stringResource(R.string.benchmark_results),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )

    SelectionContainer {
        Text(
            text = resultText,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp,
        )
    }
}

@Composable
fun AccessibilitySection(
    onOpenAccessibilitySettings: () -> Unit,
    onRunAccessibilityDemo: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.accessibility_section_title)) {
        Text(
            text = stringResource(
                R.string.accessibility_description,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.accessibility_settings))
        }

        Button(
            onClick = onRunAccessibilityDemo,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.accessibility_bluetooth_action))
        }
    }
}
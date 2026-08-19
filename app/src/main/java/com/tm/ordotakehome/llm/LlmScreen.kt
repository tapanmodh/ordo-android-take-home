package com.tm.ordotakehome.llm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tm.ordotakehome.benchmark.BenchmarkPrompts

@Composable
fun LlmScreen(
    state: LlmUiState,
    onSelectModel: () -> Unit,
    onGenerate: (String) -> Unit,
    onRunSustainedBenchmark: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRunAccessibilityDemo: () -> Unit,
) {
    var prompt by rememberSaveable {
        mutableStateOf("")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            HeaderSection()
        }

        item {
            ModelSection(state = state, onSelectModel = onSelectModel)
        }

        if (state.error != null) {
            item {
                ErrorMessage(error = state.error)
            }
        }

        item {
            PromptSection(
                prompt = prompt,
                onPromptChange = { prompt = it },
                onUseBenchmarkPrompt = {
                    prompt = BenchmarkPrompts.LONG_PROMPT
                },
                onGenerate = {
                    onGenerate(prompt)
                },
                isModelReady = state.isModelReady,
                isBusy = state.isBusy,
            )
        }

        if (state.response.isNotBlank() || state.stats.isNotBlank()) {
            item {
                GenerationResultSection(response = state.response, stats = state.stats)
            }
        }

        item {
            BenchmarkSection(state = state, onRunBenchmark = onRunSustainedBenchmark)
        }

        item {
            AccessibilitySection(
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onRunAccessibilityDemo = onRunAccessibilityDemo
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
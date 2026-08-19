package com.tm.ordotakehome

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.tm.ordotakehome.accessibility.AccessibilityDemoCommand
import com.tm.ordotakehome.llm.LlmScreen
import com.tm.ordotakehome.llm.LlmViewModel
import com.tm.ordotakehome.ui.theme.OrdoTakeHomeTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: LlmViewModel

    private val modelPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let(viewModel::selectModel)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        viewModel =
            ViewModelProvider(this)[LlmViewModel::class.java]

        setContent {
            OrdoTakeHomeTheme {
                val state by
                viewModel.uiState.collectAsState()

                LlmScreen(
                    state = state,
                    onSelectModel = {
                        modelPicker.launch(arrayOf("*/*"))
                    },
                    onGenerate = viewModel::generate,
                    onRunSustainedBenchmark = viewModel::runSustainedBenchmark,
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },

                    onRunAccessibilityDemo = {
                        AccessibilityDemoCommand
                            .requestBluetoothNavigation(this)
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    })
            }
        }
    }
}
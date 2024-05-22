package me.nickclifford.mobilecomputingdemo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ResultsPage(
    viewModel: DenoiserViewModel,
    originalVm: PlayerViewModel = viewModel(key = "original"),
    denoisedVm: PlayerViewModel = viewModel(key = "denoised")
) {
    val inferenceTime by viewModel.inferenceTime.collectAsState()

    val inputLength by viewModel.inputLength.collectAsState()

    val inferenceSecs = inferenceTime / 1000f

    val originalRecording by viewModel.recordedData.collectAsState()
    val denoisedRecording by viewModel.denoisedData.collectAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        ColumnCenterLayout(300.dp) {
            ColumnCenterLayout(100.dp) {
                Text("Denoising complete!", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Inference time: %.2f s".format(inferenceSecs),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "(%.2f s of inference per second of input)".format(inferenceSecs / inputLength),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            RowCenterLayout(250.dp) {
                Player(data = originalRecording, viewModel = originalVm, label = "Original")
                Player(data = denoisedRecording, viewModel = denoisedVm, label = "Denoised")
            }
        }
    }
}
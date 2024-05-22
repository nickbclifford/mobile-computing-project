package me.nickclifford.mobilecomputingdemo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ResultsPage(viewModel: DenoiserViewModel) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val millisElapsed by viewModel.elapsedTime.collectAsState()
    val inferenceTime by viewModel.inferenceTime.collectAsState()

    val inputLength by viewModel.inputLength.collectAsState()

    val inferenceSecs = inferenceTime / 1000f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        ColumnCenterLayout(if (isPlaying) 320.dp else 240.dp) {
            ColumnCenterLayout(120.dp) {
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

            if (isPlaying) {
                ColumnCenterLayout(150.dp) {
                    OutlinedIconButton(
                        onClick = {
                            viewModel.stopPlaying()
                        },
                        modifier = Modifier.size(buttonSize),
                        border = BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
                        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "Stop Playing",
                            modifier = Modifier.size(iconSize)
                        )
                    }

                    val seconds = millisElapsed / 1000
                    Text(
                        "%02d:%02d.%d".format(
                            seconds / 60,
                            seconds % 60,
                            (millisElapsed % 1000) / 100
                        )
                    )
                }
            } else {
                FilledIconButton(
                    onClick = {
                        viewModel.startPlaying()
                    },
                    modifier = Modifier.size(buttonSize),
                    colors = IconButtonDefaults.filledIconButtonColors()
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}
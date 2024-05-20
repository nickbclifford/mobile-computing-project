package me.nickclifford.mobilecomputingdemo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults.filledIconButtonColors
import androidx.compose.material3.IconButtonDefaults.outlinedIconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val buttonSize = 100.dp
val iconSize = 60.dp

@Composable
fun RecordPage() {
    var recording by remember { mutableStateOf(false) }
    var millisElapsed by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()

    DisposableEffect(recording) {
        val timer = scope.launch {
            while (true) {
                delay(10)
                millisElapsed += 10
            }
        }

        // TODO: actually collect the audio

        onDispose {
            timer.cancel()
            millisElapsed = 0
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        if (recording) {
            Column(
                modifier = Modifier.height(150.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedIconButton(
                    onClick = {
                        recording = false
                    },
                    modifier = Modifier.size(buttonSize),
                    border = BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
                    colors = outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Stop Recording",
                        modifier = Modifier.size(iconSize)
                    )
                }

                val seconds = millisElapsed / 1000
                Text(
                    "%02d:%02d.%d".format(
                        seconds / 60,
                        seconds % 60,
                        (millisElapsed % 100) / 10
                    )
                )
            }
        } else {
            FilledIconButton(
                onClick = { recording = true },
                modifier = Modifier.size(buttonSize),
                colors = filledIconButtonColors()
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Start Recording",
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
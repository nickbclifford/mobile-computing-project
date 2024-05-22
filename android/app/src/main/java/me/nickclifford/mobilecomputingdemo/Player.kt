package me.nickclifford.mobilecomputingdemo

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.thread

class PlayerViewModel : ViewModel() {
    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime = _elapsedTime.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private var track: AudioTrack? = null
    private val bufferSize = AudioTrack.getMinBufferSize(
        16000,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var timer: Job? = null

    fun startPlaying(data: ByteArray) {
        track = AudioTrack(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        launch { _isPlaying.emit(true) }
        timer = launch {
            while (true) {
                delay(50)
                _elapsedTime.update { it + 50 }
            }
        }
        track?.play()

        thread {
            val chunkSize = bufferSize
            var offset = 0

            while (offset < data.size) {
                val size = Math.min(chunkSize, data.size - offset)
                track?.write(data, offset, size)
                offset += size
            }

            stopPlaying()
        }
    }

    fun stopPlaying() {
        launch { _elapsedTime.emit(0) }
        timer?.cancel()
        track?.apply {
            stop()
            release()
        }
        track = null

        launch { _isPlaying.emit(false) }
    }
}

@Composable
fun Player(data: ByteArray, viewModel: PlayerViewModel, label: String) {
    val buttonGroupSize = 130.dp

    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    ColumnCenterLayout(buttonGroupSize + if (isPlaying) 40.dp else 0.dp) {
        ColumnCenterLayout(buttonGroupSize) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (isPlaying) {
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
            } else {
                FilledIconButton(
                    onClick = {
                        viewModel.startPlaying(data)
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

        if (isPlaying) {
            val seconds = elapsedTime / 1000
            Text(
                "%02d:%02d.%d".format(
                    seconds / 60,
                    seconds % 60,
                    (elapsedTime % 1000) / 100
                )
            )
        }
    }
}
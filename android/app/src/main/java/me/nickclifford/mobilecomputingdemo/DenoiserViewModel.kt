package me.nickclifford.mobilecomputingdemo

import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val LOG_TAG = "DenoiserViewModel"

fun ViewModel.launch(block: suspend () -> Unit): Job {
    return viewModelScope.launch { block() }
}

fun ViewModel.launchIO(block: suspend () -> Unit): Job {
    return viewModelScope.launch {
        withContext(Dispatchers.IO) {
            block()
        }
    }
}

class DenoiserViewModel : ViewModel() {
    private val _elapsedTime = MutableStateFlow(0)
    val elapsedTime = _elapsedTime.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private var timer: Job? = null
    private var tempfile: File? = null
    private var recorder: MediaRecorder? = null

    fun startRecording() {
        timer = launch {
            while (true) {
                delay(10)
                _elapsedTime.update { it + 10 }
            }
        }

        // TODO: specify app files directory
        tempfile = File.createTempFile("recording", "amr")
        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
            setOutputFile(tempfile)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        }
        try {
            recorder.prepare()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "prepare() failed")
        }

        launch { _isRecording.emit(true) }
        recorder.start()
    }

    fun stopRecording() {
        timer?.cancel()

        recorder?.apply {
            stop()
            release()
        }
        recorder = null

        launch {
            _isRecording.emit(false)
            _elapsedTime.emit(0)
        }

        launchIO {
            val outputPath = tempfile?.parent.plus("/${tempfile?.nameWithoutExtension}.wav")

            val session = FFmpegKit.execute("-i ${tempfile?.path} -ar 16000 ${outputPath}")

            if (ReturnCode.isSuccess(session.getReturnCode())) {
                Log.i(LOG_TAG, "Command completed successfully.")
                denoise(File(outputPath))
            } else {
                // TODO: show error state in UI
                Log.e(
                    LOG_TAG,
                    String.format(
                        "Command failed with state %s and rc %s.%s",
                        session.getState(),
                        session.getReturnCode(),
                        session.getFailStackTrace()
                    )
                )
            }
        }
    }

    private fun denoise(wavOutput: File) {
        Log.i(LOG_TAG, "Beginning denoising of ${wavOutput.name}")

        // TODO: model stuff
    }
}
package me.nickclifford.mobilecomputingdemo

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.time.measureTime

private const val LOG_TAG = "DenoiserViewModel"

fun ViewModel.launch(block: suspend () -> Unit): Job {
    return viewModelScope.launch { block() }
}

class DenoiserViewModel : ViewModel() {
    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime = _elapsedTime.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _denoisedReady = MutableStateFlow(false)
    val denoisedReady = _denoisedReady.asStateFlow()

    private val _inferenceTime = MutableStateFlow(0L)
    val inferenceTime = _inferenceTime.asStateFlow()

    private val memMeasurements = mutableListOf<Measurement>()

    private var profiler: Job? = null

    private var timer: Job? = null
    private var tempfile: File? = null
    private var recorder: MediaRecorder? = null

    private var data = byteArrayOf()
    private val bufferSize = AudioTrack.getMinBufferSize(
        16000,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private var track: AudioTrack? = null

    private val _inputLength = MutableStateFlow(0f)
    val inputLength = _inputLength.asStateFlow()
    private val _outputLength = MutableStateFlow(0f)
    val outputLength = _outputLength.asStateFlow()

    lateinit var filesDir: File
    lateinit var torchModel: Module

    lateinit var outputDir: File

    private fun startTimer() {
        timer = launch {
            while (true) {
                delay(50)
                _elapsedTime.update { it + 50 }
            }
        }
    }

    private fun stopTimer() {
        launch { _elapsedTime.emit(0) }
        timer?.cancel()
    }

    fun startRecording() {
        launch { _denoisedReady.emit(false) }

        tempfile = File.createTempFile("recording", ".amr", filesDir)
        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
            setOutputFile(tempfile)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        }
        try {
            recorder.prepare()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "MediaRecorder.prepare() failed")
        }

        launch { _isRecording.emit(true) }
        recorder.start()
        startTimer()
    }

    fun stopRecording() {
        tempfile?.copyTo(File(outputDir, "input.amr"), overwrite = true)
        stopTimer()
        recorder?.apply {
            stop()
            release()
        }
        recorder = null

        launch {
            _isRecording.emit(false)
            _elapsedTime.emit(0)
        }

        thread {
            val outputPath =
                filesDir.path + File.separator + tempfile?.nameWithoutExtension + ".wav"

            val session =
                FFmpegKit.execute("-i ${tempfile?.path} -bitexact -acodec pcm_s16le -ac 1 -ar 16000 $outputPath")

            if (ReturnCode.isSuccess(session.returnCode)) {
                denoise(File(outputPath))
            } else {
                // TODO: show error state in UI
                Log.e(
                    LOG_TAG,
                    String.format(
                        "Command failed with state %s and rc %s.%s",
                        session.state,
                        session.returnCode,
                        session.failStackTrace
                    )
                )
            }
        }
    }

    private fun denoise(modelInput: File) {
        Log.i(LOG_TAG, "Beginning denoising of ${modelInput.name}")

        val samples = parseWavFile(modelInput)

        Log.d(
            LOG_TAG,
            "input: ${samples.size} samples @ 16 kHz = ${samples.size / 16000f} seconds"
        )

        launch {
            _inputLength.emit(samples.size / 16000f)
        }

        val inputTensor = Tensor.fromBlob(samples, longArrayOf(1, samples.size.toLong()))

        val outputTensor: Tensor
        val elapsed = measureTime {
            outputTensor = torchModel.forward(IValue.from(inputTensor)).toTensor()
        }

        val outputSamples = outputTensor.dataAsFloatArray

        Log.d(
            LOG_TAG,
            "output: ${outputSamples.size} samples @ 16 kHz = ${outputSamples.size / 16000f} seconds"
        )
        launch {
            _outputLength.emit(outputSamples.size / 16000f)
        }
        data = buildWavData(outputSamples)

        File(outputDir, "denoised.wav").writeBytes(data)

        launch {
            _inferenceTime.emit(elapsed.inWholeMilliseconds)
            _denoisedReady.emit(true)
        }
    }

    fun startPlaying() {
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
        track?.play()
        startTimer()

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
        stopTimer()
        track?.apply {
            stop()
            release()
        }
        track = null

        launch { _isPlaying.emit(false) }
    }

    fun startProfiling() {
        profiler = launch {
            while (true) {
                memMeasurements.add(currentMemUsage())
                delay(5)
            }
        }
    }

    fun stopProfiling() {
        profiler?.cancel()

        File(outputDir, "memory.csv").outputStream().use { stream ->
            stream.write("time,mem_usage\n".toByteArray())
            for (measurement in memMeasurements) {
                stream.write("${measurement.time},${measurement.mem}\n".toByteArray())
            }
        }
    }
}
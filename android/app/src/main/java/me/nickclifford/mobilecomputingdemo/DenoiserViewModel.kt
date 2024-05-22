package me.nickclifford.mobilecomputingdemo

import android.content.res.AssetManager
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
import org.pytorch.LiteModuleLoader
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
    private val _currentModel = MutableStateFlow(Model.STATIC_AND_DYNAMIC)
    val currentModel = _currentModel.asStateFlow()

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime = _elapsedTime.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _denoisedReady = MutableStateFlow(false)
    val denoisedReady = _denoisedReady.asStateFlow()

    private val _inferenceTime = MutableStateFlow(0L)
    val inferenceTime = _inferenceTime.asStateFlow()

    private val memMeasurements = mutableListOf<MemoryUsage>()

    private var profiler: Job? = null

    private var timer: Job? = null
    private var recordedFile: File? = null
    private var recorder: MediaRecorder? = null

    private var _recordedData = MutableStateFlow(byteArrayOf())
    val recordedData = _recordedData.asStateFlow()

    private var _denoisedData = MutableStateFlow(byteArrayOf())
    val denoisedData = _denoisedData.asStateFlow()

    private val _inputLength = MutableStateFlow(0f)
    val inputLength = _inputLength.asStateFlow()

    lateinit var filesDir: File
    private var torchModel: Module? = null

    lateinit var outputDir: File

    lateinit var assets: AssetManager

    private fun loadModel() {
        if (torchModel == null) {
            val model = _currentModel.value
            val key = model.name.lowercase()
            Log.d(LOG_TAG, "Loading model $key from assets into memory")
            val modelFile = File.createTempFile(key, ".ptl", filesDir)
            modelFile.outputStream().use { temp ->
                model.loadAsset(assets).use { asset ->
                    asset.copyTo(temp)
                }
            }
            torchModel = LiteModuleLoader.load(modelFile.path)
        }
    }

    fun setModel(model: Model) {
        launch {
            _currentModel.emit(model)
            torchModel = null
            loadModel()
        }
    }

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
        startProfiling()

        launch { _denoisedReady.emit(false) }

        recordedFile = File.createTempFile("recording", ".amr", filesDir)
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
            setOutputFile(recordedFile)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        }
        try {
            recorder?.prepare()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "MediaRecorder.prepare() failed")
        }

        launch { _isRecording.emit(true) }
        recorder?.start()
        startTimer()
    }

    fun stopRecording() {
        recordedFile?.copyTo(File(outputDir, "input.amr"), overwrite = true)
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
                filesDir.path + File.separator + recordedFile?.nameWithoutExtension + ".wav"

            val session =
                FFmpegKit.execute("-i ${recordedFile?.path} -bitexact -acodec pcm_s16le -ac 1 -ar 16000 $outputPath")

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

        loadModel()

        launch {
            _recordedData.emit(modelInput.readBytes())
        }

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
            outputTensor = torchModel!!.forward(IValue.from(inputTensor)).toTensor()
        }

        val outputSamples = outputTensor.dataAsFloatArray
        val outputData = buildWavData(outputSamples)

        File(outputDir, "denoised.wav").writeBytes(outputData)

        launch {
            _denoisedData.emit(outputData)
            _inferenceTime.emit(elapsed.inWholeMilliseconds)
            _denoisedReady.emit(true)
        }
    }

    private fun startProfiling() {
        profiler = launch {
            while (true) {
                memMeasurements.add(MemoryUsage.getCurrent())
                delay(5)
            }
        }
    }

    fun stopProfiling() {
        profiler?.cancel()

        File(outputDir, "memory.csv").outputStream().use { MemoryUsage.dump(it, memMeasurements) }
    }
}
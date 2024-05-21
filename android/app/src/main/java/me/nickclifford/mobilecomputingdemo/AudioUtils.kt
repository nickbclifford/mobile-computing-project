package me.nickclifford.mobilecomputingdemo

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// --- begin code from Claude.ai ---
fun ByteArray.decodeToString(startIndex: Int, length: Int): String {
    return this.slice(startIndex until startIndex + length).toByteArray().toString(Charsets.UTF_8)
}

fun ByteArray.decodeInt(startIndex: Int): Int {
    return ByteBuffer.wrap(this, startIndex, 4).order(ByteOrder.LITTLE_ENDIAN).int
}

fun ByteArray.decodeShort(startIndex: Int): Short {
    return ByteBuffer.wrap(this, startIndex, 2).order(ByteOrder.LITTLE_ENDIAN).short
}

fun parseWavFile(file: File): FloatArray {
    val inputStream = FileInputStream(file)

    // Read the WAV file header
    val header = ByteArray(44)
    inputStream.read(header)

    // Check if the file is a valid WAV file
    if (header.decodeToString(0, 4) != "RIFF" ||
        header.decodeToString(8, 4) != "WAVE") {
        throw IllegalArgumentException("Invalid WAV file format")
    }

    // Get the sample rate, number of channels, and sample data size
    val sampleRate = header.decodeInt(24)
    val numChannels = header.decodeShort(22).toInt()
    val bitsPerSample = header.decodeShort(34).toInt()
    val dataSize = header.decodeInt(40)

    // Ensure the file is mono and 16-bit
    if (numChannels != 1 || bitsPerSample != 16) {
        throw IllegalArgumentException("Unsupported WAV file format (must be mono, 16-bit)")
    }

    val buffer = ByteArray(dataSize)
    inputStream.read(buffer)
    inputStream.close()

    // Convert the sample data to a ShortArray
    val samples = ShortArray(dataSize / 2)
    val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
    for (i in samples.indices) {
        samples[i] = byteBuffer.short
    }

// --- end code from Claude.ai ---

    // normalize samples to [-1, 1] for input to the model
    return samples.map { it / 32768.0f }.toFloatArray()
}

// --- begin code from Claude.ai ---

fun addWavHeader(
    pcmData: ShortArray,
    sampleRate: Int = 16000,
    bitsPerSample: Int = 16,
    numChannels: Int = 1
): ByteArray {
    val byteData = pcmData.flatMap {
        val it = it.toInt()
        listOf(
            (it and 0xFF).toByte(),
            (it.shr(8) and 0xFF).toByte()
        )
    }.toByteArray()

    val dataSize = byteData.size
    val byteRate = sampleRate * numChannels * bitsPerSample / 8
    val blockAlign = numChannels * bitsPerSample / 8
    val header = ByteArray(44)

    // RIFF chunk descriptor
    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    header[4] = (dataSize + 36).toByte()
    header[5] = (dataSize + 36 shr 8).toByte()
    header[6] = (dataSize + 36 shr 16).toByte()
    header[7] = (dataSize + 36 shr 24).toByte()

    // WAVE chunk descriptor
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()

    // FMT subchunk
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    header[16] = 16 // subchunk1Size (PCM = 16)
    header[17] = 0
    header[18] = 0
    header[19] = 0
    header[20] = 1.toByte() // audioFormat (PCM = 1)
    header[21] = numChannels.toByte()
    header[22] = (sampleRate and 0xFF).toByte()
    header[23] = (sampleRate shr 8).toByte()
    header[24] = (sampleRate shr 16).toByte()
    header[25] = (sampleRate shr 24).toByte()
    header[26] = (byteRate and 0xFF).toByte()
    header[27] = (byteRate shr 8).toByte()
    header[28] = (byteRate shr 16).toByte()
    header[29] = (byteRate shr 24).toByte()
    header[30] = blockAlign.toByte()
    header[31] = 0
    header[32] = bitsPerSample.toByte()
    header[33] = 0

    // DATA subchunk
    header[34] = 'd'.code.toByte()
    header[35] = 'a'.code.toByte()
    header[36] = 't'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = (dataSize and 0xFF).toByte()
    header[39] = (dataSize shr 8).toByte()
    header[40] = (dataSize shr 16).toByte()
    header[41] = (dataSize shr 24).toByte()

    return header + byteData
}

// --- end code from Claude.ai ---

fun buildWavData(samples: FloatArray): ByteArray {
    val data = samples.map { (it * 32767).toInt().toShort() }.toShortArray()

    return addWavHeader(data)
}
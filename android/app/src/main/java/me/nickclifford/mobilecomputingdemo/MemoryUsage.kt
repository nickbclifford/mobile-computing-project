package me.nickclifford.mobilecomputingdemo

import java.io.FileOutputStream

data class MemoryUsage(val time: Long, val memUsage: Long) {
    companion object {
        fun getCurrent(): MemoryUsage {
            val runtime = Runtime.getRuntime()
            return MemoryUsage(
                System.currentTimeMillis(),
                runtime.totalMemory() - runtime.freeMemory()
            )
        }

        fun dump(output: FileOutputStream, measurements: List<MemoryUsage>) {
            output.write("time,mem_usage\n".toByteArray())
            val startTime = measurements.first().time
            for (measurement in measurements) {
                output.write("${measurement.time - startTime},${measurement.memUsage}\n".toByteArray())
            }
        }
    }
}
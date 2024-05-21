package me.nickclifford.mobilecomputingdemo

data class Measurement(val time: Long, val mem: Long)

fun currentMemUsage(): Measurement {
    val runtime = Runtime.getRuntime()
    return Measurement(System.currentTimeMillis(), runtime.totalMemory() - runtime.freeMemory())
}


package me.nickclifford.mobilecomputingdemo

import android.content.res.AssetManager
import java.io.InputStream

enum class Model {
    PRETRAINED,
    DYNAMIC_ONLY,
    STATIC_ONLY,
    STATIC_ENCODER_ONLY,
    STATIC_LSTM_ONLY,
    STATIC_AND_DYNAMIC;

    fun loadAsset(assets: AssetManager): InputStream {
        return assets.open("${this.name.lowercase()}.ptl")
    }
}
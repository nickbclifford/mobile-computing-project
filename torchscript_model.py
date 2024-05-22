import torch
import torchaudio
from torchaudio.functional import resample
from torch.utils.mobile_optimizer import optimize_for_mobile
import sys

from denoising import demucs, quantized_demucs

noisy, fs = torchaudio.load("alex_noisy.mp3")
noisy = resample(noisy, fs, 16000)


all_models = {
    "pretrained": lambda: demucs.load_pretrained_demucs(
        "dns48"
    ),  # no quantization, base model
    "dynamic_only": lambda: quantized_demucs.QuantizedDemucs.from_facebook_pretrained(
        "dns48", dict(encoder=False, lstm=False), dynamic=torch.qint8
    ),  # dynamic lstm
    "static_only": lambda: quantized_demucs.QuantizedDemucs.from_facebook_pretrained(
        "dns48", dict(encoder=True, lstm=True), dynamic=None, sample_audio=noisy
    ),  # static encoder + static lstm
    "static_encoder_only": lambda: quantized_demucs.QuantizedDemucs.from_facebook_pretrained(
        "dns48", dict(encoder=True, lstm=False), dynamic=None, sample_audio=noisy
    ),  # static encoder
    "static_lstm_only": lambda: quantized_demucs.QuantizedDemucs.from_facebook_pretrained(
        "dns48", dict(encoder=False, lstm=True), dynamic=None, sample_audio=noisy
    ),  # static lstm
    "static_and_dynamic": lambda: quantized_demucs.QuantizedDemucs.from_facebook_pretrained(
        "dns48", dict(encoder=True, lstm=False), dynamic=torch.qint8, sample_audio=noisy
    ),  # static encoder + dynamic lstm
}

variant = sys.argv[1]
as_script = torch.jit.script(all_models[variant]())
optimized = optimize_for_mobile(as_script)
optimized._save_for_lite_interpreter(f"android/app/src/main/assets/{variant}.ptl")

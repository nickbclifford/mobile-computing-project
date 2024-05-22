import torch
import torchaudio 
from torchaudio.functional import resample
from torch.utils.mobile_optimizer import optimize_for_mobile

from denoising import demucs, quantized_demucs

#pretrained = demucs.load_pretrained_demucs('dns48')
#quantized = quantized_demucs.QuantizedDemucs(hidden=48, quantize_opts=dict(encoder=True, lstm=False))
#quantized_demucs.load_model_state_to_quantized(pretrained, quantized)

noisy, fs = torchaudio.load('alex_noisy.mp3')
noisy = resample(noisy, fs, 16000)

quantized = quantized_demucs.QuantizedDemucs.from_facebook_pretrained(
    'dns48',
    dict(
        encoder=True,
        lstm=False,
    ),
    dynamic=torch.qint8,
    sample_audio=noisy
)

as_script = torch.jit.script(quantized)
optimized = optimize_for_mobile(as_script)
optimized._save_for_lite_interpreter("android/app/src/main/assets/demucs.ptl")
import torch
from torch.utils.mobile_optimizer import optimize_for_mobile

from denoising import demucs, quantized_demucs

pretrained = demucs.load_pretrained_demucs('dns64')
quantized = quantized_demucs.QuantizedDemucs(hidden=64, quantize_opts=dict(encoder=True, lstm=False))
quantized_demucs.load_model_state_to_quantized(pretrained, quantized)

as_script = torch.jit.script(quantized)
optimized = optimize_for_mobile(as_script)
optimized._save_for_lite_interpreter("demucs.ptl")
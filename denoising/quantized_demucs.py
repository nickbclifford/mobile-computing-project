""" 
DEMUCS model taken from facebookresearch/denoiser;; We need the exact class
definition so we can easily load in pretrained models, if needed.
"""
import torch
from torch.quantization import QuantStub, DeQuantStub, get_default_qat_qconfig 
from torch import hub
from torch import nn
from torch.nn import functional as F
from torch.utils.data import DataLoader
from torchaudio.functional import resample
import math

from demucs import BLSTM, Demucs

class QuantizedDemucs(nn.Module):
    def __init__(self,
                 hidden=48,
                 depth=5,
                 kernel_size=8,
                 stride=4,
                 causal=True,
                 resample=4,
                 growth=2,
                 max_hidden=10_000,
                 normalize=True,
                 rescale=0.1,
                 floor=1e-3,
                 sample_rate=16_000,
                 quantize_opts = {}):

        super().__init__()
        if resample not in [1, 2, 4]:
            raise ValueError("Resample should be 1, 2 or 4.")

        chin = 1
        chout = 1

        self.hidden = hidden
        self.depth = depth
        self.kernel_size = kernel_size
        self.stride = stride
        self.causal = causal
        self.floor = floor
        self.resample = resample
        self.normalize = normalize
        self.sample_rate = sample_rate

        self.encoder = nn.ModuleList()
        self.decoder = nn.ModuleList()
        activation = nn.GLU(1)
        ch_scale = 2
        
        # Quantization code
        self.qconfig = get_default_qat_qconfig('qnnpack')
        self.quantize_opts = quantize_opts

        for index in range(depth):
            encode = []
            encode += [
                nn.Conv1d(chin, hidden, kernel_size, stride),
                nn.ReLU(),
                nn.Conv1d(hidden, hidden * ch_scale, 1), activation,
            ]
            if self.quantize_opts['encoder']:
                # Reorder;; GLU cannot be quantized

                encode = encode[:-1]
                encode = [QuantStub(qconfig=self.qconfig)] + encode + [DeQuantStub(qconfig=self.qconfig)]
                encode += [activation]

            self.encoder.append(nn.Sequential(*encode))

            decode = []
            decode += [
                nn.Conv1d(hidden, ch_scale * hidden, 1), activation,
                nn.ConvTranspose1d(hidden, chout, kernel_size, stride),
            ]
            if index > 0:
                decode.append(nn.ReLU())
            self.decoder.insert(0, nn.Sequential(*decode))
            chout = hidden
            chin = hidden
            hidden = min(int(growth * hidden), max_hidden)

        self.lstm = BLSTM(chin, bi=not causal)

        if not self.quantize_opts['encoder']:
            for encoder in self.encoder:
                encoder.qconfig = None
        # TODO: add option to statically quantize decoder?
        for decoder in self.decoder:
            decoder.qconfig = None
        if not self.quantize_opts['lstm']:
            self.lstm.qconfig = None
        # if rescale:
        #     rescale_module(self, reference=rescale)

    def valid_length(self, length):
        """
        Return the nearest valid length to use with the model so that
        there is no time steps left over in a convolutions, e.g. for all
        layers, size of the input - kernel_size % stride = 0.

        If the mixture has a valid length, the estimated sources
        will have exactly the same length.
        """
        length = math.ceil(length * self.resample)
        for idx in range(self.depth):
            length = math.ceil((length - self.kernel_size) / self.stride) + 1
            length = max(length, 1)
        for idx in range(self.depth):
            length = (length - 1) * self.stride + self.kernel_size
        length = int(math.ceil(length / self.resample))
        return int(length)

    @property
    def total_stride(self):
        return self.stride ** self.depth // self.resample

    def forward(self, mix):
        if mix.dim() == 2:
            mix = mix.unsqueeze(1)

        if self.normalize:
            mono = mix.mean(dim=1, keepdim=True)
            std = mono.std(dim=-1, keepdim=True)
            mix = mix / (self.floor + std)
        else:
            std = 1
        length = mix.shape[-1]
        x = mix
        x = F.pad(x, (0, self.valid_length(length) - length))
        if self.resample == 2:
          x = resample(x, self.sample_rate, self.sample_rate * 2)
        elif self.resample == 4:
          x = resample(x, self.sample_rate, self.sample_rate * 4)

        skips = []
        for encode in self.encoder:
            x = encode(x)
            skips.append(x)
        x = x.permute(2, 0, 1)
        x, _ = self.lstm(x)
        x = x.permute(1, 2, 0)
        for decode in self.decoder:
            skip = skips.pop(-1)
            x = x + skip[..., :x.shape[-1]]
            x = decode(x)
        if self.resample == 2:
          x = resample(x, self.sample_rate *2, self.sample_rate)
        elif self.resample == 4:
          x = resample(x, self.sample_rate *4, self.sample_rate)
        x = x[..., :length]
        return std * x

def load_model_state_to_quantized(src: Demucs, dst: QuantizedDemucs) -> None:
    # load encoder weights
    for i, (se, de) in enumerate(zip(src.encoder, dst.encoder)):
        dst_conv = filter(lambda x: isinstance(x,nn.Conv1d), de)
        src_conv = filter(lambda x: isinstance(x,nn.Conv1d), se)

        for x, y in zip(dst_conv, src_conv):
            x.load_state_dict(y.state_dict())

    for i, (se, de) in enumerate(zip(src.decoder, dst.decoder)):
        dst_conv = filter(lambda x: isinstance(x,nn.Conv1d) or isinstance(x,nn.ConvTranspose1d), de)
        src_conv = filter(lambda x: isinstance(x,nn.Conv1d) or isinstance(x,nn.ConvTranspose1d), se)

        for x, y in zip(dst_conv, src_conv):
            x.load_state_dict(y.state_dict())

    src.lstm.load_state_dict(dst.lstm.state_dict())

def prepare_and_convert(model: QuantizedDemucs, 
                        data: DataLoader | torch.Tensor, 
                        engine: str = 'qnnpack') -> QuantizedDemucs:
    torch.backends.quantized.engine = engine
    prepared = torch.ao.quantization.prepare(model)
    prepared.eval()

    if isinstance(data, torch.Tensor):
        prepared(data)
    else:
        for x, _ in data:
            prepared(x)
    
    converted = torch.ao.quantization.convert(prepared)

    return converted

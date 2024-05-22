""" 
DEMUCS model taken from facebookresearch/denoiser;; We need the exact class
definition so we can easily load in pretrained models, if needed.
"""
import torch
from torch import hub, nn, Tensor
from torch.nn import functional as F
from torchaudio.functional import resample
import math
from typing import Optional, Tuple

# BEGIN (mostly) UNORIGINAL CODE

class BLSTM(nn.Module):
    def __init__(self, dim, layers=2, bi=True):
        super().__init__()
        self.lstm = nn.LSTM(bidirectional=bi, num_layers=layers, hidden_size=dim, input_size=dim)
        self.linear = nn.Linear(2 * dim, dim) if bi else None

    def forward(self, x: Tensor, hidden: Optional[Tuple[Tensor, Tensor]] = None):
        x, hidden = self.lstm(x, hidden)
        if self.linear is not None:
            x = self.linear(x)
        return x, hidden

class Demucs(nn.Module):
    """
    Demucs speech enhancement model.
    Args:
        - chin (int): number of input channels.
        - chout (int): number of output channels.
        - hidden (int): number of initial hidden channels.
        - depth (int): number of layers.
        - kernel_size (int): kernel size for each layer.
        - stride (int): stride for each layer.
        - causal (bool): if false, uses BiLSTM instead of LSTM.
        - resample (int): amount of resampling to apply to the input/output.
            Can be one of 1, 2 or 4.
        - growth (float): number of channels is multiplied by this for every layer.
        - max_hidden (int): maximum number of channels. Can be useful to
            control the size/speed of the model.
        - normalize (bool): if true, normalize the input.
        - glu (bool): if true uses GLU instead of ReLU in 1x1 convolutions.
        - rescale (float): controls custom weight initialization.
            See https://arxiv.org/abs/1911.13254.
        - floor (float): stability flooring when normalizing.
        - sample_rate (float): sample_rate used for training the model.

    """
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
                 sample_rate=16_000):

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

        for index in range(depth):
            encode = []
            encode += [
                nn.Conv1d(chin, hidden, kernel_size, stride),
                nn.ReLU(),
                nn.Conv1d(hidden, hidden * ch_scale, 1), activation,
            ]
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
        # if rescale:
        #     rescale_module(self, reference=rescale)

    def valid_length(self, length: int):
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
            std = torch.tensor([1])
        length = mix.shape[-1]
        x = mix
        x = F.pad(x, (0, self.valid_length(length) - length))
        # if self.resample == 2:
        #     x = upsample2(x)
        # elif self.resample == 4:
        #     x = upsample2(x)
        #     x = upsample2(x)
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
        # if self.resample == 2:
        #     x = downsample2(x)
        # elif self.resample == 4:
        #     x = downsample2(x)
        #     x = downsample2(x)
        if self.resample == 2:
          x = resample(x, self.sample_rate *2, self.sample_rate)
        elif self.resample == 4:
          x = resample(x, self.sample_rate *4, self.sample_rate)
        x = x[..., :length]
        return std * x

# END UNORIGINAL CODE

ROOT = "https://dl.fbaipublicfiles.com/adiyoss/denoiser/"
PRETRAINED_URLS = {
    'dns48': ROOT + "dns48-11decc9d8e3f0998.th",
    'dns64': ROOT + "dns64-a7761ff99a7d5bb6.th",
    'master64': ROOT + "master64-8a5dfb4bb92753dd.th",
    'valentini': ROOT + 'valentini_nc-93fc4337.th',  # Non causal
}

def load_pretrained_demucs(name: str):
    if name not in PRETRAINED_URLS:
        raise ValueError(f'pretrained name needs to be in {list(PRETRAINED_URLS.keys())}')
    if name == 'dns48':
        kwargs = dict(hidden=48)
    elif name == 'dns64' or name == 'master64':
        kwargs = dict(hidden=64)
    else:
        kwargs = dict(hidden=64, causal=False, stride=2, resample=2)
    state= hub.load_state_dict_from_url(PRETRAINED_URLS[name], map_location='cpu')
    model = Demucs(**kwargs, sample_rate = 16000)
    model.load_state_dict(state)

    return model
    


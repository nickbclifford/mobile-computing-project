# takes .wav files and returns (1) audio samples (2) frame rate
# uses torchaudio

import torch
import torchaudio
import sys

def parse_wav_file(file_path):
    try:
        waveform, sample_rate = torchaudio.load(file_path)
        return waveform, sample_rate

    except Exception as e:
        print(f"Error parsing file.")
        return None, None

# sample usage
file_path = 'data/nick/street_1.wav'
samples, frame_rate = parse_wav_file(file_path)
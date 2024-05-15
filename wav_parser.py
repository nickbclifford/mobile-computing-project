# takes .wav files and returns (1) audio samples (2) frame rate

import wave
import numpy as np

def parse_wav_file(file_path):
    try:
        # Open the WAV file
        with wave.open(file_path, 'rb') as wav_file:
            # Get audio file properties
            num_channels = wav_file.getnchannels()
            sample_width = wav_file.getsampwidth()
            frame_rate = wav_file.getframerate()
            num_frames = wav_file.getnframes()

            # Read audio frames
            raw_frames = wav_file.readframes(num_frames)

        # Convert raw data to numpy array based on sample width
        if sample_width == 1:
            dtype = np.uint8
        elif sample_width == 2:
            dtype = np.int16
        elif sample_width == 3:
            dtype = np.int32  # Special case for 24-bit audio (not common)
        else:
            raise ValueError("Unsupported sample width")

        # Convert raw data to numpy array
        samples = np.frombuffer(raw_frames, dtype=dtype)

        # Reshape array based on number of channels
        if num_channels > 1:
            samples = samples.reshape(-1, num_channels)

        return samples, frame_rate

    except Exception as e:
        print(f"Error parsing {file_path}: {e}")
        return None, None

# sample usage
file_path = 'data/nick/street_1.wav'
samples, frame_rate = parse_wav_file(file_path)
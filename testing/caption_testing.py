## Takes two arguments: path to audio (wav file) and path to reference transcript
## Prints out accuracy of the trascript generated from DeepSpeech pre-trained model
## To format wav files, run "ffmpeg -i input.wav -ac 1 -ar 16000 -sample_fmt s16 input_formatted.wav"

import sys
import os
import requests
import numpy as np
import deepspeech
import wave
import difflib

def load_model(model_path):
    model = deepspeech.Model(model_path)
    return model

def transcribe_audio(model, file_path):
    with wave.open(file_path, 'rb') as wf:
        rate = wf.getframerate()
        frames = wf.readframes(wf.getnframes())
        audio = np.frombuffer(frames, dtype=np.int16)

    text = model.stt(audio)
    return text

def load_reference_text(file_path):
    with open(file_path, 'r') as file:
        reference_text = file.read().strip()
    return reference_text

def calculate_accuracy(transcribed_text, reference_text):
    transcribed_words = transcribed_text.split()
    reference_words = reference_text.split()
    
    matcher = difflib.SequenceMatcher(None, transcribed_words, reference_words)
    matching_blocks = matcher.get_matching_blocks()
    
    match_count = sum(tr.size for tr in matching_blocks)
    accuracy = (match_count / len(reference_words)) * 100 if reference_words else 0

    return accuracy

if __name__ == "__main__":
    audio_path = sys.argv[1]
    reference_text_path = sys.argv[2]

    model_url = 'https://github.com/mozilla/DeepSpeech/releases/download/v0.9.3/deepspeech-0.9.3-models.pbmm'
    model_path = 'deepspeech-0.9.3-models.pbmm'

    if not os.path.exists(model_path):
        response = requests.get(model_url)
        with open(model_path, 'wb') as f:
            f.write(response.content)

    model = load_model(model_path)
    transcribed_text = transcribe_audio(model, audio_path)
    
    reference_text = load_reference_text(reference_text_path)
    
    accuracy = calculate_accuracy(transcribed_text, reference_text)
    
    print(f"Accuracy: {accuracy:.2f}%")
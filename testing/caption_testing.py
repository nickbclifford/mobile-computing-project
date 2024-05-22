## Takes 3 arguments: path to denoised audio (wav file), path to original audio (wav file), path to reference transcript
## Prints out accuracy of the trascript generated from DeepSpeech pre-trained model
## To format wav files, run "ffmpeg -i input.wav -ac 1 -ar 16000 -sample_fmt s16 input_formatted.wav"

import sys
sys.path.append('../')
import os
import requests
import numpy as np
import deepspeech
import wave
import torch
import torchaudio
from denoising import quantized_demucs
import difflib
from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor

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

def transcribe_wav2vec(model, file_path):
    waveform, sample_rate = torchaudio.load(file_path)
    waveform = waveform.squeeze(0)
    inputs = processor(waveform, sampling_rate=sample_rate, return_tensors="pt", padding=True)
    with torch.no_grad():
        logits = model(inputs.input_values).logits
    predicted_ids = torch.argmax(logits, dim=-1)
    transcription = processor.decode(predicted_ids[0])
    return transcription

def calculate_wer(transcribed_text, reference_text):
    transcribed_words = transcribed_text.split()
    reference_words = reference_text.split()
    d = np.zeros((len(reference_words) + 1, len(transcribed_words) + 1), dtype=np.uint8)
    for i in range(1, len(reference_words) + 1):
        d[i][0] = i
    for j in range(1, len(transcribed_words) + 1):
        d[0][j] = j
    for i in range(1, len(reference_words) + 1):
        for j in range(1, len(transcribed_words) + 1):
            if reference_words[i - 1] == transcribed_words[j - 1]:
                d[i][j] = d[i - 1][j - 1]
            else:
                d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + 1)
    wer = d[len(reference_words)][len(transcribed_words)] / float(len(reference_words))
    return wer

def tensor_to_audio(x, s):
  if len(x.shape) != 2:
    x = x.squeeze(1)
  torchaudio.save('denoised.wav', x, s)
  print("denoised audio saved")

def calculate_accuracy(transcribed_text, reference_text):
    transcribed_words = transcribed_text.split()
    reference_words = reference_text.split()
    
    matcher = difflib.SequenceMatcher(None, transcribed_words, reference_words)
    matching_blocks = matcher.get_matching_blocks()
    
    match_count = sum(tr.size for tr in matching_blocks)
    accuracy = (match_count / len(reference_words)) * 100 if reference_words else 0

    return accuracy

if __name__ == "__main__":
    denoised_audio_path = "denoised.wav"
    original_audio_path = sys.argv[1]
    reference_text_path = sys.argv[2]

    ## denoise
    # format file first
    os.system("ffmpeg -i {0} -ac 1 -ar 16000 -sample_fmt s16 formated_{0}".format(original_audio_path))
    denoise_model = quantized_demucs.QuantizedDemucs.from_facebook_pretrained('dns48', dict(encoder=False, lstm=False), dynamic=torch.qint8)
    input, rate = torchaudio.load("formated_"+original_audio_path)
    tensor_to_audio(denoise_model(input), 16000)

    ## Speech-to-text WER
    # format file first
    os.system("ffmpeg -i {0} -ac 1 -ar 16000 -sample_fmt s16 formated_{0}".format(denoised_audio_path))
    ds_model_url = 'https://github.com/mozilla/DeepSpeech/releases/download/v0.9.3/deepspeech-0.9.3-models.pbmm'
    ds_model_path = 'deepspeech-0.9.3-models.pbmm'

    if not os.path.exists(ds_model_path):
        response = requests.get(ds_model_url)
        with open(ds_model_path, 'wb') as f:
            f.write(response.content)

    model = load_model(ds_model_path)
    reference_text = load_reference_text(reference_text_path)

    print("#####DEEPSPEECH#####")
    original_transcribed_text = transcribe_audio(model, "formated_"+original_audio_path)
    original_wer = calculate_wer(original_transcribed_text.lower(), reference_text.lower())
    original_accuracy = calculate_accuracy(original_transcribed_text.lower(), reference_text.lower())
    print(f"original WER: {original_wer:.2f}%, original accuracy: {original_accuracy:.2f}%")

    denoised_transcribed_text = transcribe_audio(model, "formated_"+denoised_audio_path)
    denoised_wer = calculate_wer(denoised_transcribed_text.lower(), reference_text.lower())
    denoised_accuracy = calculate_accuracy(denoised_transcribed_text.lower(), reference_text.lower())
    print(f"denoised WER: {denoised_wer:.2f}%, denoised accuracy: {denoised_accuracy:.2f}%")

    print()
    print("#####WAV2VEC#####")
    model_name = "facebook/wav2vec2-base-960h"
    processor = Wav2Vec2Processor.from_pretrained(model_name)
    model = Wav2Vec2ForCTC.from_pretrained(model_name)
    original_transcribed_text = transcribe_wav2vec(model, "formated_"+original_audio_path)
    original_wer = calculate_wer(original_transcribed_text.lower(), reference_text.lower())
    original_accuracy = calculate_accuracy(original_transcribed_text.lower(), reference_text.lower())
    print(f"original WER: {original_wer:.2f}%, original accuracy: {original_accuracy:.2f}%")

    denoised_transcribed_text = transcribe_wav2vec(model, "formated_"+denoised_audio_path)
    denoised_wer = calculate_wer(denoised_transcribed_text.lower(), reference_text.lower())
    denoised_accuracy = calculate_accuracy(denoised_transcribed_text.lower(), reference_text.lower())
    print(f"denoised WER: {denoised_wer:.2f}%, denoised accuracy: {denoised_accuracy:.2f}%")
    
    os.remove("formated_"+original_audio_path)
    os.remove("formated_"+denoised_audio_path)
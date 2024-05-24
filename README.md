# SoundScape Saviors: On-Device Audio ML Denoising

This is the repo for the code on SoundScape Saviors, the final project for the
mobile computing class at UChicago.


Group Members: Nick Clifford, David Suh, Patricia Zhou.

Most explaination of the project is in the accompanying paper.

## Model
All the model definitions, including quantized models, are contained in the `denoising` directory.

## Testing
The code that was used produce the PESQ, STOI, SNR, and WER values are in the
`testing` directory.

## Android
The android build used to build our app is contained in `android`. You can use
the `torchscript.py` file in the root to see how we compiled models into
Android comptable TorchScript models. 

#!/bin/bash

download() {
    wget -O "$1" "$2"
    unzip "$1"
    rm "$1"
}

download clean_testset_wav.zip "https://datashare.ed.ac.uk/bitstream/handle/10283/2791/clean_testset_wav.zip?sequence=1&isAllowed=y"
download clean_trainset_28spk_wav.zip "https://datashare.ed.ac.uk/bitstream/handle/10283/2791/clean_trainset_28spk_wav.zip?sequence=2&isAllowed=y"
download noisy_testset_wav.zip "https://datashare.ed.ac.uk/bitstream/handle/10283/2791/noisy_testset_wav.zip?sequence=5&isAllowed=y"
download noisy_trainset_28spk_wav.zip "https://datashare.ed.ac.uk/bitstream/handle/10283/2791/noisy_trainset_28spk_wav.zip?sequence=6&isAllowed=y"
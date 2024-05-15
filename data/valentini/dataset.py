from torch.utils.data import IterableDataset, get_worker_info
import torchaudio

from glob import glob
import math
import os.path

class ReadWavsDataset(IterableDataset):
    files: list[str]

    def __init__(self, directory: str):
        assert os.path.isdir(directory), "ReadWavsDataset requires a directory as input"
        self.files = glob(os.path.join(directory, '*.wav'))

    def __iter__(self):
        start = 0
        end = len(self.files)
        
        # worker splitting code sourced from PyTorch docs
        if worker := get_worker_info():  # in a worker process
            # split workload
            per_worker = int(math.ceil((end - start) / float(worker.num_workers)))
            worker_id = worker.id
            iter_start = start + worker_id * per_worker
            iter_end = min(iter_start + per_worker, end)
        else:  # single-process data loading, return the full iterator
            iter_start = start
            iter_end = end

        return iter(map(torchaudio.load, self.files[iter_start:iter_end]))
            

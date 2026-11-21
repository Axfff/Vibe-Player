# Beat Runner scene model

Beat Runner uses multinomial logistic regression to choose a scene from each eight-second audio window. **Drift**, **Groove**, and **Surge** represent lower, middle, and higher perceived musical energy. The browser uses the supplied audio samples and the same [feature extractor](../public/style-model.js) used during training. Beat timing runs separately, so changing the scene does not move the beat grid.

## Data and evaluation

The source is the public [Database for Emotional Analysis of Music (DEAM)](https://cvml.unige.ch/databases/DEAM/). The dataset [manual](https://cvml.unige.ch/databases/DEAM/manual.pdf) permits noncommercial use under CC BY-NC. Use this model for noncommercial coursework. The repository contains the fitted weights and aggregate evaluation. The downloaded tracks and labels remain in the local cache. Per-track features are computed locally during training.

Cite Anna Aljanaki, Yi-Hsuan Yang, and Mohammad Soleymani (2017), “Developing a benchmark for emotional analysis of music,” *PLOS ONE*, 12(3), e0173392. [DOI](https://doi.org/10.1371/journal.pone.0173392)

- Select 360 track excerpts from the 2013 and 2014 data. Join track-level mean arousal ratings to metadata, remove entries without usable artist identity, and deduplicate normalized artist and title pairs. The training script assigns the Drift label below 4.25, Groove from 4.25 to below 5.75, and Surge at or above 5.75 on DEAM's 1–9 scale.
- Assign normalized artist names to training, validation, or test by deterministic hashing before sampling tracks by class. Keep all windows from each artist in one partition. Use up to two nonoverlapping windows from the first 16 seconds of each excerpt. Report total tracks and window counts because a truncated or silent window can be skipped.
- Downmix and resample into 8 kHz mono PCM. Summarize loudness, dynamics, spectral centroid and flatness, positive spectral flux, and relative frequency-band power in 17 numeric features. Fit the scaler and classifier on the training split. Clip scaled features to ±5 in both training and browser inference.
- The fixed baseline predicts the most frequent training class. The acceptance criterion is at least 45% balanced accuracy on held-out test windows against a 33.3% baseline. The evaluation includes a track-level metric computed by pooling the two window predictions and a window-level confusion matrix. Rows are true labels and columns are predicted labels, ordered Drift, Groove, Surge. [Evaluation results](evaluation.json)

DEAM annotators rated the complete excerpts, so individual windows inherit a track-level label. The held-out result supports classification of this dataset and these three energy buckets. Section-by-section evaluation and testing on the Android audio pipeline are later validation work.

## Reproduce on macOS

The initial run used Python 3.12, NumPy 1.26.4, scikit-learn 1.5.2, Node 25, FFmpeg 8, and the system `curl`. The training command pulls roughly 100 MB of selected audio prefixes from the official archive via HTTPS range requests. Keep the cache outside the repository.

From the Beat Runner directory:

```bash
python3 -m venv /tmp/beat-runner-train-env
source /tmp/beat-runner-train-env/bin/activate
python3 -m pip install numpy==1.26.4 scikit-learn==1.5.2
python3 model/train.py --cache /tmp/beat-runner-deam
node --test model/style-model.test.mjs
```

The training command writes this directory's `evaluation.json`. When the held-out result passes, it also writes `public/model.json`. The ordered class labels, model identifier, standardization values, and logistic coefficients define the portable inference contract. The browser loads the artifact from the local server and performs inference on the Mac. A window with missing samples, less than 0.65 seconds of audio, or silence yields Groove with zero confidence.

"""Reproduce the local-only Beat Runner scene model from DEAM."""
import argparse
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
import csv
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import subprocess
import unicodedata
from zipfile import ZipFile
import zlib


ROOT = Path(__file__).resolve().parent
BASE = "https://cvml.unige.ch/databases/DEAM/"
PLAYABLE_STYLES = ("drift", "groove", "surge")
SPLIT_LIMIT = {"train": 72, "validation": 24, "test": 24}
MODEL_ACCEPTANCE = 0.45
AUDIO_SIZE = 1_343_203_527
AUDIO_TAIL_BYTES = 400_000
AUDIO_PREFIX_BYTES = 280_000
ARCHIVES = {
    "annotations.zip": ("DEAM_Annotations.zip", "809b0feb4ba6196c1eda9b2b7b33b892b11fa71457cfceb0d47e832ed1a5f15f"),
    "metadata.zip": ("metadata.zip", "3d1d5ab42e852803a770f2cfbd685c6b464e1ebe76f26b0e10954597902b90f8"),
}


def request(path, byte_range=None):
    command = [
        "curl", "--fail", "--silent", "--show-error", "--location", "--retry", "2",
        "--connect-timeout", "20", "--max-time", "180",
        "--user-agent", "Vibe-Player-coursework/1",
        "--write-out", "\n%{http_code}\n%header{content-range}", BASE + path,
    ]
    if byte_range:
        first, last = byte_range
        command.extend(["--range", f"{first}-{last}", "--max-filesize", str(last - first + 1)])
    payload, status, content_range = subprocess.check_output(command).rsplit(b"\n", maxsplit=2)
    if byte_range:
        expected = f"bytes {first}-{last}/{AUDIO_SIZE}".encode()
        if status != b"206" or content_range != expected or len(payload) != last - first + 1:
            raise ValueError("DEAM server returned an unexpected audio range")
    return payload


def checked_archive(cache, name):
    path = cache / name
    remote, expected = ARCHIVES[name]
    data = path.read_bytes() if path.exists() else request(remote)
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError(f"DEAM archive checksum mismatch: {path}")
    if not path.exists():
        path.write_bytes(data)
    return ZipFile(io.BytesIO(data))


def extract_metadata(cache):
    def normalized_name(text):
        folded = unicodedata.normalize("NFKC", text).strip().casefold()
        return " ".join(re.findall(r"\w+", folded))

    artists_tracks = {}
    with checked_archive(cache, "metadata.zip") as archive:
        for year, artist_column, title_column in [(2013, 2, 3), (2014, 1, 3)]:
            with archive.open(f"metadata/metadata_{year}.csv") as contents:
                rows = csv.reader(io.TextIOWrapper(contents, encoding="utf-8-sig", errors="replace"))
                next(rows)
                for row in rows:
                    if len(row) > title_column and row[0].strip().isdigit():
                        artist = normalized_name(row[artist_column])
                        title = normalized_name(row[title_column])
                        if artist and artist not in {"n a", "unknown", "various", "various artists"}:
                            artists_tracks[int(row[0])] = artist, title

    candidates = defaultdict(list)
    seen = set()
    with checked_archive(cache, "annotations.zip") as archive:
        path = next(name for name in archive.namelist() if name.endswith("averaged_songs_1_2000.csv"))
        with archive.open(path) as contents:
            rows = csv.DictReader(io.TextIOWrapper(contents, encoding="utf-8-sig"), skipinitialspace=True)
            for row in rows:
                song_id = int(row["song_id"])
                arousal = float(row["arousal_mean"])
                if song_id not in artists_tracks or not 1 <= arousal <= 9:
                    continue
                artist, title = artists_tracks[song_id]
                if title and (artist, title) in seen:
                    continue
                seen.add((artist, title))
                split_value = digest_value("split", artist) % 10
                split = "train" if split_value < 6 else "validation" if split_value < 8 else "test"
                style = "drift" if arousal < 4.25 else "surge" if arousal >= 5.75 else "groove"
                candidates[split, style].append({
                    "song": song_id, "arousal": arousal, "artist": artist, "split": split, "style": style
                })

    selected = []
    for (split, style), rows in sorted(candidates.items()):
        rows.sort(key=lambda row: digest_value("sample", str(row["song"])))
        selected.extend(rows[:SPLIT_LIMIT[split]])
    return selected


def digest_value(purpose, value):
    digest = hashlib.sha256(f"beat-runner-{purpose}-v1:{value}".encode()).hexdigest()
    return int(digest[:8], 16)


def download_audio(cache, selected, workers):
    tail_path = cache / "tail.bin"
    tail_start = AUDIO_SIZE - AUDIO_TAIL_BYTES
    if not tail_path.exists():
        tail_path.write_bytes(request("DEAM_audio.zip", (tail_start, AUDIO_SIZE - 1)))
    with ZipFile(io.BytesIO(tail_path.read_bytes())) as audio_index:
        members = {
            int(Path(member.filename).stem): member
            for member in audio_index.infolist() if member.filename.endswith(".mp3")
        }

    def get_prefix(song):
        number = song["song"]
        destination = cache / f"{number}.mp3"
        if destination.exists():
            return
        member = members[number]
        start = member.header_offset + tail_start
        prefix_bytes = min(AUDIO_PREFIX_BYTES, member.compress_size)
        payload = request("DEAM_audio.zip", (start, start + prefix_bytes - 1))
        if payload[:4] != b"PK\x03\x04" or member.compress_type != 8:
            raise ValueError(f"Unexpected DEAM ZIP entry for song {number}")
        filename_size, extra_size = struct.unpack_from("<2H", payload, 26)
        offset = 30 + filename_size + extra_size
        if payload[30:30 + filename_size].decode() != member.filename:
            raise ValueError(f"DEAM ZIP entry name mismatch for {number}")
        prefix = zlib.decompressobj(-zlib.MAX_WBITS).decompress(payload[offset:])
        if not prefix:
            raise ValueError(f"No DEAM audio for {number}")
        destination.write_bytes(prefix)

    missing = [row for row in selected if not (cache / f"{row['song']}.mp3").is_file()]
    print(f"Downloading {len(missing)} of {len(selected)} selected audio prefixes", flush=True)
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for index, _ in enumerate(pool.map(get_prefix, missing), 1):
            if index % 24 == 0 or index == len(missing):
                print(f"Audio prefixes ready: {index}/{len(missing)}", flush=True)


def fit(cache, selected):
    import numpy as np
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import accuracy_score, balanced_accuracy_score, confusion_matrix
    from sklearn.preprocessing import StandardScaler

    catalog = cache / "selected.json"
    catalog.write_text(json.dumps(selected))
    result = subprocess.run(
        ["node", str(ROOT / "extract.mjs"), str(catalog)], capture_output=True, text=True, check=True
    )
    rows = json.loads(result.stdout)
    partitions = defaultdict(list)
    skipped = []
    for song, feature_windows in zip(selected, rows, strict=True):
        if not feature_windows:
            skipped.append(song["song"])
        for features in feature_windows:
            partitions[song["split"]].append({**song, "features": features})

    grouped_artists = {
        split: {row["artist"] for row in tracks} for split, tracks in partitions.items()
    }
    for split, artists in grouped_artists.items():
        if any(artists & other for name, other in grouped_artists.items() if name != split):
            raise ValueError("Artist leakage across splits")

    def arrays(partition):
        return (
            [row["features"] for row in partition],
            [row["style"] for row in partition],
        )

    x_train, y_train = arrays(partitions["train"])
    scaler = StandardScaler().fit(x_train)
    def scaled(features):
        return np.clip(scaler.transform(features), -5, 5)

    classifier = LogisticRegression(C=0.5, max_iter=1000, random_state=2044)
    classifier.fit(scaled(x_train), y_train)
    majority = Counter(y_train).most_common(1)[0][0]
    logical_order = [list(classifier.classes_).index(style) for style in PLAYABLE_STYLES]
    metrics = {}
    for split, tracks in sorted(partitions.items()):
        features, labels = arrays(tracks)
        predictions = classifier.predict(scaled(features))
        track_probabilities = defaultdict(list)
        track_labels = {}
        for row, probabilities in zip(tracks, classifier.predict_proba(scaled(features)), strict=True):
            track_probabilities[row["song"]].append(probabilities)
            track_labels[row["song"]] = row["style"]
        track_predictions = [
            classifier.classes_[np.argmax(np.mean(probabilities, axis=0))]
            for probabilities in track_probabilities.values()
        ]
        metrics[split] = {
            "artists": len(grouped_artists[split]),
            "tracks": len(track_probabilities),
            "windowCounts": dict(sorted(Counter(labels).items())),
            "windowAccuracy": round(accuracy_score(labels, predictions), 6),
            "windowBalancedAccuracy": round(balanced_accuracy_score(labels, predictions), 6),
            "baselineWindowBalancedAccuracy": round(
                balanced_accuracy_score(labels, [majority] * len(labels)), 6
            ),
            "trackBalancedAccuracy": round(
                balanced_accuracy_score(list(track_labels.values()), track_predictions), 6
            ),
            "windowConfusionMatrix": confusion_matrix(
                labels, predictions, labels=PLAYABLE_STYLES
            ).tolist(),
        }

    artifact = {
        "format": "beat-runner-style-v1",
        "styleNames": PLAYABLE_STYLES,
        "windowSeconds": 8,
        "mean": scaler.mean_.tolist(),
        "scale": scaler.scale_.tolist(),
        "weights": classifier.coef_[logical_order].tolist(),
        "bias": classifier.intercept_[logical_order].tolist(),
    }
    serialized = json.dumps(artifact, separators=(",", ":")) + "\n"
    accepted = metrics["test"]["windowBalancedAccuracy"] >= MODEL_ACCEPTANCE
    print(json.dumps(metrics, indent=2))
    evidence = {
        "dataset": BASE,
        "styleOrder": PLAYABLE_STYLES,
        "minimumTestWindowBalancedAccuracy": MODEL_ACCEPTANCE,
        "baselineStyle": majority,
        "promotedModelSha256": hashlib.sha256(serialized.encode()).hexdigest() if accepted else None,
        "skippedTracks": skipped,
        "selectionSha256": hashlib.sha256(catalog.read_bytes()).hexdigest(),
        "metrics": metrics,
    }
    (ROOT / "evaluation.json").write_text(json.dumps(evidence, indent=2) + "\n")
    if not accepted:
        raise SystemExit("Held-out model fell below the promotion threshold")
    (ROOT.parent / "public" / "model.json").write_text(serialized)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", required=True, type=Path, help="Untracked DEAM cache directory")
    parser.add_argument("--workers", default=12, type=int, help="Parallel HTTP requests")
    parser.add_argument("--download-only", action="store_true")
    args = parser.parse_args()
    cache = args.cache.resolve()
    cache.mkdir(exist_ok=True, parents=True)
    selected = extract_metadata(cache)
    download_audio(cache, selected, args.workers)
    if not args.download_only:
        fit(cache, selected)


if __name__ == "__main__":
    main()

# Fixture validation — 2026-11-07

This is host-side asset validation, not Android acceptance or a recommendation
accuracy claim. Exact asset metadata is in the manifest and linked reports.

## Actual source and integrity work

- Base main: `844caf7cd1cfb3348cdd130136763528c3274573`.
- 20 complete original MP3s, 33 unique normalized JPEGs, 80 candidate references.
- Media: 118,158,915 bytes / 112.685 MiB. No LFS, paid storage, APK, ZIP or model.
- 53 item-specific license records: 20 audio CC BY 4.0, 31 images CC0 1.0,
  2 images CC BY 4.0. Original and processed SHA-256 retained.
- HTTP download success was not assumed to mean complete media: initial
  downloads of Hitman, Chase Pulse and Crusade were truncated. Full decoding
  and comparison to official duration/Content-Length caught them (Crusade
  contained only 7,732 bytes at the repair audit). Resumed/replaced **before freeze**,
  then checked all 20 complete original lengths and fully decoded them again.
  Final byte counts: Hitman 8,035,387; Chase Pulse 4,788,279; Crusade 7,951,802.
- MP3 original album/ID3 metadata retained; some include attached album artwork.
  ffprobe validation permits that existing attached picture and requires exactly
  one MP3 audio stream. It does not mistake cover artwork for a second song.
- Selected images viewed by the agent individually/in contact sheets. Rejected
  branded, visibly soft and near-redundant candidates are not committed. Crops
  on img_0029/0030/0039 remove visible faces/foreground people and are recorded
  per item. No human review or listening is claimed.
- SHA duplicate checks, dHash screening and actual visual review complement
  each other. dHash is not a perceptual-identity guarantee; consult the reported
  candidate list and the mixed galleries.

## Reproduction (repository root; Python from an isolated venv)

```text
python -m unittest discover -s scripts/fixtures/tests -v
python scripts/fixtures/validate_music_photo_fixtures.py --output ../fixture-validation.json
python scripts/fixtures/probe_audio_fixtures.py --ffprobe <ffprobe-path> --output ../fixture-ffprobe.json
python scripts/fixtures/download_music_photo_fixtures.py --cache ../fixture-download-cache
python scripts/fixtures/materialize_music_photo_fixtures.py --output ../music-photo-export
```

The first three commands are offline once dependencies are installed. Download
only fetches missing locked assets; valid existing files are verified and skipped.
Materialization is run twice against the same external destination to check
idempotence. Different existing bytes fail rather than being overwritten.
Tests use mocked network responses, not paid or live APIs.

Results: [offline test log](offline-tests.log), [validation summary](validation-summary.json),
[full-file decode records](audio-decode.json), [actual ffprobe output](audio-ffprobe.json),
[export/reconstruction check](export-validation.json),
[frozen candidate/label hashes](../evaluation/freeze.json),
[complete package checksums](../checksums.sha256).

The freeze and checksum commands are **authoring** steps, not ways to repair a
failed validation. Frozen inputs/labels must never be rewritten after ranking.
Validation recomputes every package checksum and exits nonzero on extra,
missing or altered files. The checksum file excludes only itself. All final
report files are included. Source hashes here refer to the checked base; the
PR commit is reported by Git/GitHub rather than embedding a circular self-SHA.

## Actual tooling

- Python version and repository/device snapshot: [repository-audit.json](repository-audit.json).
- Pinned tool dependencies: Pillow 12.3.0, av 19.0.2, mutagen 1.48.1, numpy 2.3.5.
- ffprobe 6.1.1-essentials_build-www.gyan.dev, obtained from the
  [ffmpeg-static b6.1.1 release](https://github.com/eugeneware/ffmpeg-static/releases/tag/b6.1.1).
  Downloaded `ffprobe-win32-x64.gz` SHA-256 matched the published GitHub asset
  digest `f309e6223ad89d2fe54bccd420a7709b66fd27540674e92309578ed491a43c8d`.
  Executable SHA-256:
  `3a7e2dc003dc2cd1472827e4c7c4f056ae1ae0ae7c5bbc580c99b49827351ba4`.
  Neither executable nor archive is committed.
- PyAV decodes every frame of all 20 files, checks finite/non-silent PCM,
  sample count and duration agreement with Mutagen and the official catalogue.
  First-30-second RMS is measured, not an emotion prediction.

## Uncompleted acceptance

`APP_PLAYLISTS_PENDING`, `PHOTO_IMPORT_PENDING`: no running ADB device.
No playback/Next/persistence/picker acceptance was performed. Recommendation
is **Blocked**: no connected image-content ranking implementation; no actual
Top-5/Precision@5 is reported. All 80 labels await human review. See
[device-import.md](device-import.md) and [review status](../evaluation/review-status.md).

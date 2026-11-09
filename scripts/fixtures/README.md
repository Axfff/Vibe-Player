# Fixture tools and review fixes

Use **Python 3.12 or newer**, matching pinned PyAV 19.0.2. The older minimum in the frozen v1 README is historical and does not apply to this setup. Install `requirements.txt` in an isolated environment.

The default package is [music-photo-v2](../../test-data/music-photo-v2/README.md). Frozen v1 remains unchanged for audit. V2 keeps every media hash and evaluation CSV byte, uses ordering independent of annotation-row order, and carries the original Mayon JPEG copyright notice into offline manifests and attribution.

`create_music_photo_v2.py` reproduces the new directory from v1 and refuses existing destinations. The evaluator accepts zero through five unique in-scope IDs, reports the returned count, and always keeps five as the P@5 denominator. The macOS temporary-directory alias assertion resolves both paths.

Validation on Windows, Python 3.12.14: 30 boundary tests passed; complete media decoding, hashes, freeze, galleries and all 150 package checksums passed. A native macOS run remains unperformed. This validates packaging and metrics, not the subjective photo labels.

Issue #51 findings addressed by this version require reviewer confirmation. No relevance relabeling or claim of held-out accuracy is made.

Student ID (CroissantXinny): 50027204

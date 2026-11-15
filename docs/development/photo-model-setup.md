# Local model setup

The current experimental photo slot uses **tinyclip-atmospheres-v4.onnx** (159 MB); see the
[pinned artifact and preparation instructions](photo-scene-model.md). Existing
MobileNet, scenes-v2 and atmospheres-v3 imports show an update-required message; the music slots and saved
covers are retained. The earlier setup verification below used the original
MobileNet version and does not certify the new model's recommendation quality.

Entry: **封面照片来源 → 配置本地分析模型**, or the same button in **推荐当前照片范围**. Available without granting a photo source first.

1. Review each model's filename, size, preparation/source instructions and license under **文件与来源说明**. External links open the user's browser; the app does not download weights itself. The new photo model is exported from pinned official TinyCLIP weights; it is not an official upstream ONNX file.
2. Select **导入音乐特征模型**, **导入音乐氛围模型**, or **导入照片内容模型**, then choose the corresponding file in Android's document picker. The filename may differ; exact bytes and SHA-256 establish identity. No archive or executable is accepted as a substitute.
3. Observe per-model validation and progress. Three ready models enable the complete music-to-photo flow. Manually choosing an atmosphere still requires the photo model. Missing or invalid models lead back to setup or manual cover selection.
4. Return to recommendations, choose a representative track and analyze. Model readiness certifies the installed version, not runtime support or the relevance of the resulting recommendations.

## Storage and recovery

- All three pinned versions are listed in `PhotoModel`. Music hashes are shared with `MoodAnalyzer`; photo hash is shared with `OnnxPhotoFeatureExtractor`. No model weights enter Git or the production APK.
- Files are streamed on the I/O dispatcher into bounded private staging files. Exact size and digest are checked, data is synced, then `android.system.Os.rename` atomically replaces the destination on the same filesystem (API 21+). Wrong, truncated, oversized, unreadable or cancelled imports do not replace the existing model. No file is accepted based on its display name or MIME type.
- A process-wide mutex coordinates imports, inspection and cleanup. Staging files left by process death are removed during the next inspection. Existing models remain inspectable and import can be retried. Closing the setup screen requests cancellation; a commit already completed remains valid and is reflected by the next inspection.
- A fragment-scoped ViewModel preserves work and its result across rotation. The pending model role is saved while DocumentsUI is open. Source URI access is temporary; the app retains its own validated copy and does not persist file-selection grants or URI history.
- Storage is `no_backup/photo-models/`: shared by the app's playlists/albums, excluded from app backup, removed with app data/uninstall. Model setup neither changes photo scopes nor sets a cover. Importing a model never grants access to photographs.
- Progress cancellation is cooperative between reads. Some external document providers may delay opening/reading or closing a stream; an incomplete import remains inactive and bounded. Cloud-provider outages and actual API 21–23 runtime behavior remain separately unverified.

## Scope and licensing

No Manifest, SDK/Gradle version, network permission, player behavior or public coverage exclusion changed. The two music models retain CC BY-NC-SA 4.0 terms. The current photo model is exported from MIT-licensed TinyCLIP weights; the earlier MobileNet artifact retains its Apache 2.0 terms. The setup screen exposes the current terms and sources. Redistribution/production-use review remains a human task; this feature does not bundle or redistribute weights.

Installer regression tests use isolated tiny known byte sequences to verify atomic replacement and failures, not to claim model inference. Actual official-file imports and system-search behavior are recorded separately in the device verification report.

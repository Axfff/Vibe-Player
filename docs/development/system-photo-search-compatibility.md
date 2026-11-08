# System picker search compatibility

Checked official references on 2026-11-07: [MediaStore](https://developer.android.com/reference/android/provider/MediaStore), [new photopicker Builder](https://developer.android.com/reference/androidx/photopicker/activity/PickVisualMediaRequest.Builder), [legacy activity Builder](https://developer.android.com/reference/androidx/activity/result/PickVisualMediaRequest.Builder), [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker), [SAF](https://developer.android.com/training/data-storage/shared/documents-files).

The project retains `androidx.activity:activity-ktx:1.7.2`, including the legacy `androidx.activity.result.PickVisualMediaRequest`. The new `androidx.photopicker.activity.PickVisualMediaRequest.Builder.setHighlightSearchResultsParams(HighlightSearchResultsParams)` was added in 1.0.0-alpha03; it is not invoked on the old class and no alpha dependency is added.

Platform docs list search extra/query in 36.1; AndroidX docs identify API 33 / T extension 19 and say R/S ignore this feature. `SystemPhotoSearchLauncher` conservatively attempts only API >=33 and actual T extension >=19. API version/name or intent resolution alone is never treated as support. Unknown extension returns UNKNOWN and uses ordinary selection. No supported-device/provider behavior is inferred from platform version.

The isolated adapter copies these public documented values verbatim because compileSdk 33 lacks symbols:

- `android.provider.extra.PICK_IMAGES_HIGHLIGHT_SEARCH_RESULTS`: Bundle
- `android.provider.media.key.PICK_IMAGES_HIGHLIGHT_SEARCH_TEXT_QUERY`: non-empty user-edited string (max 200 chars)
- `android.provider.media.key.PICK_IMAGES_HIGHLIGHT_TYPE`: 1 (expanded)

Only ACTION_PICK_IMAGES receives the bundle; album highlight is never also set. Multi-select limit is min(100, platform maximum). Read-only user selection returns through the ordinary confirmation/binding logic. No hidden API, provider query scraping, clipboard operation or location access request.

Entry 从系统搜索添加照片 is explicitly range expansion. Show/edit suggested words from actual preceding analysis or an explicitly chosen atmosphere; disclose system/media-provider handling before the user's button click. The local recommendation path never opens a system picker to expand scope.

Unsupported/unknown/launch exceptions fall back to the existing multi-select contract (which falls back to ACTION_OPEN_DOCUMENT where needed). The query remains in restorable state. The editor always offers 普通选图 and the source screen offers folders. Provider ignoring extras or returning no results has no trustworthy machine-readable detection API; users receive an explicit notice and can choose ordinary selection. Cancellation performs no mutation and does not automatically reopen a picker. “Requested” never means “search succeeded”.

Verification layers: JVM capability/query tests; instrumentation intent/fallback checks; real ordinary picker on API 33; actual query highlight on a T extension >=19 system with a supporting media provider. The final layer remains separately Pending without such a device. See results matrix, not a version-number assumption.

2026-11-08 follow-up: a fresh official API 36 / T extension 19 Google APIs emulator was tested. The real `beach` request opened the ordinary Photos / Collections grid without query highlighting. Read-only provider configuration showed `highlight_search_results_feature=false`; no flags were overridden. Explicit ordinary selection and confirmation worked. The remaining limitation is actual provider feature support, not lack of an extension-19 emulator. See the [newer-picker verification](../testing/photo-model-setup-results.md).
## Physical Redmi follow-up — 2026-11-08

The user's REDMI K90 Pro Max (Android 16 / API 36, `OS3.0.310.0.WPMCNXM`) reports T extension **17**, below the public search-highlighting requirement. The app correctly displays unsupported search enhancement, falls back to ordinary selection, and preserves the keyword after cancellation without adding a source. This is not the API-36-emulator case in which extension 19 allowed a request that the provider ignored. The Xiaomi picker did not expose the newly seeded fixture album during this session, while SAF directory authorization and full real recommendation/crop/save succeeded. See the [physical-device evidence and limitations](../testing/phone-photo-verification-2026-11-08.md).

### Later investigation on the same phone

The standard system update screen was opened and **Check for update** returned
“系统已是最新版本”. The tested security settings page did not expose a Google Play
system-module update entry, and `android.settings.MODULE_UPDATE_SETTINGS` had no
handler. No firmware, picker APK, module, developer feature flag, or security
setting was installed/overridden to fake extension support. As documented in
[SDK Extensions](https://developer.android.com/guide/sdk-extensions), module updates
are delivered by the system; changing this app's compile/target SDK cannot raise
the phone's extension. The UI now states the observed extension and required 19,
explains the official update paths, and retains ordinary selection.

All six test images were present in MediaStore and had no `.nomedia` ancestor.
After opening Xiaomi Gallery, the directory appeared in **影集 → 相册 → 其他相册**.
Reopening the app's ordinary picker exposed the same folder under **影集 → 相册
(swipe horizontally) → 其他相册**. A fixture was selected, confirmed for QA1108,
and read successfully as a one-photo source. That temporary source was then
removed through the app; the six-photo directory and saved cover were retained.
No file timestamps, gallery database, or personal album settings were altered.
This resolves the observed selection gap, but the session does not isolate
gallery startup from elapsed indexing time as the exact cause.

The new **找不到相册？从文件选择照片** action uses public SAF
`OpenMultipleDocuments` restricted to `image/*`. It retains explicit target
confirmation and persisted read grants, rejects more than 100 selections as a
whole, and changes nothing on cancellation. It is an explicit alternative when
an OEM gallery omits an album, independent of search-extension support. See the
[follow-up evidence](../testing/photo-quality-and-picker-2026-11-08.md).

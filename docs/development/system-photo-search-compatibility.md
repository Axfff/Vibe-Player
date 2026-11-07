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

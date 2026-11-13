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

## Upgraded Redmi: query handoff investigation — 2026-11-09

After the user's system update, the same phone reports Android 17 / API 37,
T extension **22** and `OS4.0.5.0.XPMCNXM`. The former extension-17 blocker is
resolved. The unchanged production APK from `1b10cc42` correctly allows the
request, but real `flowers` and `花卉` requests still open **全部**. Opening the
picker's search page then reveals an empty input. Manual `flowers` entry does
produce a result grid; this establishes manual UI availability, not relevance
accuracy or automatic handoff.

A separate temporary app with **no requested permissions** reproduced the
failure using only public Android APIs. These independent cases exclude the
Vibe-Player editor, model and saved state as the cause:

| Entry | Public request | Observed initial UI |
| --- | --- | --- |
| Default Xiaomi picker | `flowers`, expanded, max 100 | 全部; search input empty when opened |
| Default Xiaomi picker | `flowers`, collapsed, max 100 | 全部; search input empty when opened |
| Default Xiaomi picker | `flowers`, expanded, single selection | 全部; search input empty when opened |
| Default Xiaomi picker | `花卉`, expanded, max 100 | 全部; search input empty when opened |
| Enabled MediaProvider picker | `flowers`, expanded, max 100 | Ordinary 照片 / 相册; no search control |
| Enabled MediaProvider picker | `flowers`, expanded, single selection | Ordinary 照片 / 相册; no search control |
| Embedded system picker | `flowers`, expanded, max 100 | Session opens successfully; 全部照片 grid, no query results section |

The first six cases use `ACTION_PICK_IMAGES` and the documented nested Bundle.
The alternate handler is the already enabled/exported
`com.android.providers.media.module/com.android.providers.media.photopicker.PhotoPickerActivity`;
it was selected by public `Intent.setPackage` in the probe only. It is distinct
from the **disabled** `com.android.photopicker.MainActivity`, which was neither
enabled nor launched. No production routing was changed.

The seventh case uses the public
[EmbeddedPhotoPickerProvider](https://developer.android.com/reference/android/widget/photopicker/EmbeddedPhotoPickerProvider)
and [feature builder](https://developer.android.com/reference/android/widget/photopicker/EmbeddedPhotoPickerFeatureInfo.Builder):
`setHighlightSearchMediaTextQuery("flowers")`, `setHighlightType(1)` and
`setPickerLaunchedInExpandedState(true)`. Runtime U extension is also 22.
`onSessionOpened` succeeds and the surface renders, but it displays the ordinary
all-photos grid. Only the disposable probe compiles against the installed API-37
SDK; the product's SDK, dependencies and minimum version are unchanged.

Read-only inspection of the installed picker package explains the black-box
behavior. The shared intent parser recognizes the exact public Bundle keys and
stores the highlight query in configuration. The active
`com.android.photopicker/.hyper.HyperMainActivity` startup chooses the image or
album tab; its search navigation opens a page without forwarding that query.
The inspected search page does not consume the highlight configuration. The
embedded service uses a separate Xiaomi UI that also starts on its grid. These
are implementation findings for this installed build, corroborated by the
device tests, not a claim about every Xiaomi release. Partial decompiler errors
in unrelated methods were retained locally; the inspected parser/startup paths
were readable. No vendor source or APK is redistributed.

**Decision:** no working public adapter was found on this firmware. Do not
hardcode a component switch that still ignores the query, treat an ADB-generated
tap/keystroke as an app capability, enable disabled system components, call hidden
interfaces, or change system feature flags. The public MediaStore contract makes
highlighting conditional on provider feasibility. Keep the standard request,
truthful explanation, ordinary picker / SAF alternatives and explicit selection
confirmation. A provider update that connects this parameter to its search UI
is needed for the requested automatic behavior on the tested paths.

The probe was removed after testing and USB stay-awake restored to 0. No personal
photo was selected, no result URI was retained and no source was added. The two
original playlists, ten fixture files and saved cover hash remain unchanged.
Raw UI dumps, screenshots containing personal media, system APKs and decompiled
files stay outside the repository. See the
[sanitized device receipt](../testing/evidence/xiaomi-search-2026-11-09.json).
This investigation changes documentation only; automatic search remains
unverified/unsupported on this provider, and formal AC remain unchecked.

## November 10: usable manual album bridge

The app now offers **小米相册搜索后添加** alongside ordinary photo selection. It
explains search → select results → Add to / New album → Copy, then opens ordinary
selection or the existing folder-authorization confirmation. Creating an album
alone grants the app nothing. Selected groups are fixed snapshots; directory
sources can include new files only through the documented refresh behavior.

The search-results Add to / New album menu was observed separately from a full
two-fixture test. The latter copied known licensed flower images from their QA
album into a new Xiaomi Gallery album, which appeared in the system picker. Both
explicitly selected images became readable in the target scope. The temporary
binding and copies were then removed, preserving originals and the saved cover.
Xiaomi's delete confirmation warned that deletion also applies to cloud/other
synced devices; the guide therefore tells users to confirm only temporary copies.
No automatic download, copy, delete, hidden API or permission expansion was added.
See [the detailed limits and receipt](../testing/photo-atmosphere-and-review-2026-11-10.md).

## Android 5.0 / 6.0 ordinary-selection fallback

On November 10, the unchanged production APK was exercised on isolated official
AOSP x86_64 API 21 and API 23 images. Both show the unsupported-search explanation;
requesting system search opens actual `android.intent.action.OPEN_DOCUMENT`
(`image/*`) in `com.android.documentsui/.DocumentsActivity`. Both displayed the
six licensed fixture photos and returned to the source screen on cancellation
without changing its scope or cover. This verifies the real document-picker
fallback on these two systems, not search highlighting or query retention after
cancellation. Normal folder consent and all three model imports also worked
through DocumentsUI. See the [runtime evidence and limits](../testing/photo-atmosphere-and-review-2026-11-10.md#android-50-and-60-runtime-follow-up).

# Target-scoped photo sources

Formal requirements: [#41](https://github.com/Axfff/Vibe-Player/issues/41), manual cover dependency [#40](https://github.com/Axfff/Vibe-Player/issues/40). The source/ranking/search AC extension was published to #41 on November 8 using the existing authorized GitHub CLI account after the user explicitly requested PowerShell. The connector's earlier 403 is historical; [PR #50](https://github.com/Axfff/Vibe-Player/pull/50) and the Issue updates now exist. Publication is distinct from human acceptance. The retained [original publication payload](photo-requirements-publication-pending.md) is an archive, not a second requirements entry.

Baseline: main `844caf7c`; cover foundation `25849a42`; mood PR #46 `4776e4e1`. Dependency merge `d987943b` integrates both existing contributions. No main/tag rewrite. Integration owner for this worktree is this task; existing member attribution is preserved. Shared CI scripts/workflows are unchanged; scope inventory is regenerated with the existing cumulative script, including all new Kotlin UI/permission/error paths and Release 1.

Entry: Playlist detail / Album detail overflow → **选择封面**. The first screen has
four actions: **推荐封面**, **直接选一张**, **添加照片**, **更多**, with the target name
and current photo sources above the secondary action row. Empty, unavailable and
unknown counts remain distinct. Loading cancellation appears only during loading.

**添加照片** groups selected photos, folders, file-picker fallback, system search,
Xiaomi album guidance and explicit source sharing. **更多** groups refresh, excluded
candidate recovery, model setup, restoring the default cover (with confirmation)
and photo-use information. A source row opens previews, removal or source details.
Recommendation details (audio duration, uncertainty, failures) and model setup are
under its own **更多** menu; cancellation appears during analysis. No authorization
or manual-cover capability was removed. Directory reauthorization still requires
explicit re-selection/addition; cancellation preserves existing sources.

`CoverTarget` typed stable keys are reused. Album artist/title snapshots are length-delimited and checked. A renamed playlist retains its UUID. Restorable fragment arguments fix the target before launching a picker; callbacks never consult the currently selected playlist. Request UUID plus durable scope revision reject stale attachment/recommendation/save. Deletion keeps a revision tombstone and never deletes source images. Target existence is checked through the existing PlaylistRepository or MediaStore audio album identity.

Schema 1 JSON uses AtomicFile under noBackupFilesDir; corruption/future schema fails closed rather than replacing state. Binding updates prune unreferenced source metadata. Atomic write failure retains old data. No grant is released: this module cannot prove exclusive ownership across the whole app. Removing a binding is not a complete OS revocation; users can revoke app access in system settings. No original images or persistent thumbnails/features are copied. Final confirmed cover storage remains the pre-existing CustomCoverRepository and is unaffected by source removal.

Both selectors request read access. Fixed selections are deduplicated and persisted only after explicit local-analysis confirmation; inability to take a persistent grant is an explicit error. There is no READ_MEDIA_IMAGES/MANAGE_EXTERNAL_STORAGE addition. Cloud-provider fetches may require connectivity; neither URI possession nor a grant proves offline availability. Missing/moved/deleted vs offline cannot always be distinguished by ContentResolver; the UI states this uncertainty.

Directories default to direct children. Explicit recursion is capped at depth 3; each traversal examines at most 500 rows within a 10-second cooperative budget. A cancellation signal is passed to directory queries and read probes. Providers may delay cancellation; no claim of a hard wall-clock guarantee. Non-images are ignored, failures are isolated, and partial/budget states are displayed. Each target allows at most 20 sources and each recommendation at most 500 unique URIs. Candidate thumbnails are paged in batches of 30. All traversal/model/decoding uses IO dispatchers; dialog rendering uses the main thread.

JPEG/PNG/WebP only: bounded stream read <=20 MiB (also bounds unknown-length providers), positive image dimensions <=40 million pixels, sampled decode, all eight EXIF orientations, no requested GPS. Candidate crops use the same bounded decoder. Before preview/save, re-enumerate the issuing scope, check target/revision/grant, and compare SHA-256 of actual current bytes. The serialized final save guard prevents app-scope mutations from interleaving. External provider revocation after the final check cannot be made atomic with local cover storage.

Known verification gaps must be read with the results document: comprehensive provider/offline behavior, rotation during confirmation, and target deletion racing external library changes are not implied by JVM tests. The [November 10 API 21/23 runtime checks](../testing/photo-atmosphere-and-review-2026-11-10.md#android-50-and-60-runtime-follow-up) verify normal model import, six-photo directory consent, actual inference, crop cancellation/save, ordinary document-picker fallback and full device-reboot persistence on isolated official x86_64 emulators. This is basic runtime evidence, not exhaustive legacy-device or provider coverage. Shared source removal is documented separately in the earlier device results.

Student ID (CroissantXinny): 50027204

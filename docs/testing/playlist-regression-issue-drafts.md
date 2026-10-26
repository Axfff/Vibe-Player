# Playlist regression issue drafts

These are prepared issue bodies, not claims that GitHub issues exist. The connected
GitHub integration returned HTTP 403 when creating both an issue and a Draft PR.
Git pushes through the repository's existing Git remote succeed. No alternate
credential or UI path was used to bypass the denied API operations.

The user requested completing RG01–RG07 and committing/pushing meaningful increments.
Discovered RG defects belong to that verification scope. Additional workflow work
should be tracked and reviewed before it is added. In particular, the optional
regression-branch CI change has been removed from this contribution and retained as
an unapplied local proposal.

## Tracking issue: Complete playlist integration regressions RG01–RG07

Related story: #2. Feature PR: #3. Contribution branch: `test/playlist-regressions`.
TC01–TC13 are owned by collaborators on `test/playlist-acceptance`.

Tasks:

- [ ] Execute RG01 old Queue clearing and list-end behavior.
- [ ] Execute RG02 saved edits versus active playback snapshots.
- [ ] Execute RG03 playing/paused rotation and background return.
- [ ] Execute RG04 stale details and repeated scans.
- [ ] Execute RG05 real file failures, finite recovery and media commands.
- [ ] Execute RG06 failed/denied versus successful-empty scans.
- [ ] Execute RG07 hiding, ordinary playback, Favorites and Queue.
- [ ] Link verified defects, commits, tested revisions and device evidence.
- [ ] Obtain a human teammate review before merge.

Use [the existing plan](custom-playlists.md) as the case definitions and
[the result record](custom-playlists-regression-results.md) for actual results.
AI assistance is disclosed in that record; it is not human teammate review.

## Defect: Explicit media Pause resumes a paused playlist

**Found by:** RG05 on Android 17 / API 37, production source `785e0863`.

1. Play a saved playlist using real local audio.
2. Pause the player.
3. Deliver an explicit `KEYCODE_MEDIA_PAUSE` through the service's MediaSession
   media-button route.

Expected: the player remains paused and repeated explicit commands do not skip.
Actual: `PlayerService.handleMediaIntent` routes Play/Pause through the toggle /
headset double-click branch, so Pause resumes an already paused player.

The device test reproduced actual `MediaPlayer.isPlaying == true`; its failed and
passing runner outputs are retained in the result record. Fix commit: `734cf90c`.
Regression tests cover both service and receiver routes, repeated commands and
queued recovery cancellation.

## Defect: Denied audio access is classified as missing playlist files

**Found by:** RG06 actual runtime permission revocation with external audio owned by
`com.android.shell`, production source `734cf90c`.

1. Scan an external audio file and add it to a saved playlist.
2. Revoke `READ_MEDIA_AUDIO`, reopen and deny the permission rationale.
3. Open the saved playlist from the permission-error screen.

Expected: failed/unknown availability; Play/Add disabled; management and saved
membership preserved. Actual: MediaStore returns a permission-filtered empty
query, and the still-existing file is marked Unavailable.

Before/after device screenshots, UI hierarchies and file/permission evidence are
retained. The fix checks media permission before querying. Verification includes
actual revoke/grant and live UI scanner-error regression tests.

## Separate proposal: Run CI for regression branches and compile their test APK

This is additional workflow scope, not shipped in the RG fix. Create an issue and
agree its scope first. The existing workflow runs on `main` and `feat/**` pushes
and PRs to `main`; `test/**` pushes are not covered. A future change can add the
chosen branch/PR triggers and instrumentation-APK compilation. Device execution
on hosted CI would be a further, separately specified requirement.

# Android Discovery CI integration — 2026-11-03

This is preparation for the Android feature, not a new coverage measurement. The feature owner edits Discovery production code; the CI owner maintains shared Gradle/workflows/coverage scripts. Android Discovery implementation is now in progress; its final committed source and coverage have not been supplied yet.

## Verified Git starting points

- Latest remote main: `dc17523be591a72f9fefa98536c61689e4d99ad1`.
- Initial local `feat/ai-photo-discovery`: `fa86950d0f8955a8b0718de95f7a0509dedee832`.
- At that initial revision, its merge base with main is `947709e4c798a103b9fcc5962172f2e85d349196`; main has 38 exclusive commits and Discovery has 4. That revision lacks Release 1 and its JVM/instrumentation configuration.
- The feature owner has since merged main in `f59e830ab31ac368268775813a9ddb47e1cc1238`. Release 1 and its existing test dependencies are therefore present now. Android and backend source edits are ongoing in that owner's worktree; this CI work does not alter them.
- Public CI branch: `26d5c3fc68075bd9d3470135b9160520655ed29c`, based on current main and including the tested playback progress race fix. The readiness branch containing this document is a descendant of it.
- Reusable backend/web coverage commit: `c87d4e6d64433dbcdda908a2b10740d30c851021`. Its sole parent is exactly `fa86950d0f8955a8b0718de95f7a0509dedee832`. Its 8 changed files are backend/web tests and coverage/package configuration; production Python/static source trees are identical to fa86950d.
- A three-way merge dry run of public CI 26d5c3fc and c87d4e6d completed without conflicts: tree `53035b73417590fbdfb261ad9cd3bfcb2074e8c7`. This is a tree object, not a tested commit SHA.

The feature owner can first integrate c87d4e6d and the readiness branch in their own checkout after preserving their current edits. If still clean at fa86950d, c87d4e6d can fast-forward; otherwise merge it to preserve provenance. Merge the public/readiness branch rather than copying only Gradle dependencies: Release 1 and its regressions must be present. This does not merge any GitHub PR. Do not take the whole PR #32 validation branch as the feature base: it also contains separate pending fixes #21/#26/#28/#30/#34.

After combining source, CI scope validation is expected to require a regenerated manifest. The CI owner will review this after the final feature SHA, then collect fresh coverage on the resulting committed tree. Do not reuse percentages or execution data from 26d5c3fc/d22fabe2.

## Current toolchain and available test APIs

Keep Kotlin 1.8.21, AGP 8.0.2, Gradle Wrapper 8.0, JDK 17, compile/target SDK 33 and minSdk 21. ViewBinding is already enabled. Shared Gradle on the public/readiness branch already contains:

```groovy
testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
testImplementation 'junit:junit:4.13.2'
androidTestImplementation 'androidx.test:core:1.5.0'
androidTestImplementation 'androidx.test:runner:1.5.2'
androidTestImplementation 'androidx.test.ext:junit:1.1.5'
```

These provide ActivityScenario and InstrumentationRegistry. Existing production UI tests use real activities, views, SharedPreferences, MediaStore audio and the player. Plain Kotlin policy/state logic belongs in src/test. Android JSON/ContentResolver/Activity/UI/storage behavior belongs in src/androidTest unless a separately verified equivalent is introduced; the local JVM android.jar stubs do not execute those Android methods.

The project already resolves `androidx.activity:activity-ktx:1.7.2`. Local javap inspection of the actual 1.7.2 API JAR confirms `PickVisualMedia`, `PickMultipleVisualMedia(int)` and `PickVisualMediaRequest` support. Official [Photo picker guidance](https://developer.android.com/training/data-storage/shared/photo-picker) supports Activity 1.7.0+, with ACTION_OPEN_DOCUMENT fallback when the system picker is absent. No Activity/AGP/SDK upgrade is needed just to compile these contracts. Validate the actual picker/URI/cancellation behavior on the selected device; do not copy new-version-only examples from the latest reference.

No extra runtime dependency is needed for HttpURLConnection, SharedPreferences or org.json on Android. No new test dependency has been added speculatively. If the feature tests require controlling the external system picker UI, name that requirement before adding a shared UI Automator dependency; the app's existing test libraries can exercise its own UI and returned-URI behavior.

For the feature owner's requested local EXIF orientation handling, shared app Gradle now explicitly declares `implementation 'androidx.exifinterface:exifinterface:1.3.6'`. Before the declaration, Gradle dependencyInsight already resolved exactly 1.3.6 through Coil 2.4.0; this makes the direct feature dependency explicit without upgrading it or another Android component. [Official 1.3.6 release](https://developer.android.com/jetpack/androidx/releases/exifinterface#1.3.6). Image orientation/re-encoding/metadata-removal behavior still needs the feature's own tests on its final source.

After the declaration, `:app:dependencyInsight --dependency androidx.exifinterface --configuration debugRuntimeClasspath :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` succeeded on the unchanged toolchain. Dependency resolution shows the direct root plus Coil path both selecting 1.3.6. The JVM task executed 31 tests, with zero failures/errors/skips; app and instrumentation APK build tasks passed. No device tests or new feature coverage are claimed by this dependency-only validation. Local logs/JUnit XML are retained under `verification/sprint3-coverage/exif-*-20261103*` outside the repository.

## Network configuration ownership and acceptance

The feature owner owns the Discovery Activity declarations, INTERNET permission, endpoint policy and debug/main network-security resources. The CI owner will review merged manifests and test results and change shared Gradle only if needed.

The feature owner's latest declared resource name is `discovery_network_security.xml`. Use a main resource with cleartext disabled by default and a debug resource override permitting only the exact hosts `10.0.2.2`, `127.0.0.1` and `localhost` (no subdomains). Keep release's resource strict. The client-side allowlist and tests must agree with those three explicitly named hosts. For example, the debug resource can contain:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

Reference the resource from the application manifest. `debug-overrides` configures trust anchors; it is not a host-specific HTTP switch. API 21–23 cannot rely on the API 24+ network-security XML for host restriction, so endpoint/request code must independently enforce release HTTPS and debug HTTP only for the exact allowed hosts. If debug uses a manifest cleartext opt-in for older API support, the client-side host policy is essential. Reject disallowed schemes/hosts after each redirect too, or disable automatic redirects and handle validated redirects explicitly. Never disable TLS/certificate checks. See [Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config).

Tests should check the pure endpoint/redirect policy, allowed loopback HTTP with a fixture service, denied other HTTP hosts, malformed/empty responses, error statuses, timeouts/cancellation/stale responses, and saved-state behavior. On API 33, verify NetworkSecurityPolicy allows the three debug hosts and denies others. Inspect both debug and release merged manifests/resources to ensure the release artifact has no debug-only HTTP exception. This is acceptance guidance; those new feature checks have not run yet.

## Local Windows execution and device ownership

Installed paths:

- JDK: `C:/Users/ASUS/Documents/ChatGPT/dsa project/toolchain/jdk-17.0.20.1+1`.
- Android build SDK: `C:/Users/ASUS/Documents/ChatGPT/dsa project/toolchain/sdk`.
- Shared Gradle cache: `C:/Users/ASUS/Documents/ChatGPT/dsa project/toolchain/gradle-home`.
- Python for scope scripts: `C:/Users/ASUS/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`.
- Node: `C:/Users/ASUS/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe`.
- pnpm: `C:/Users/ASUS/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin/fallback/pnpm.cmd`.
- ADB: `C:/Users/ASUS/AppData/Local/Android/Sdk/platform-tools/adb.exe`.
- Emulator: `C:/Users/ASUS/AppData/Local/Android/Sdk/emulator/emulator.exe`.

The previous CI disposable device is stopped; `adb devices -l` returned no devices during this audit. Its task-owned AVD is `Coverage_API_33` (API 33 google_apis x86_64), configured under `C:/Users/ASUS/Documents/ChatGPT/dsa project/verification/sprint3-coverage/avd`. The system image path in config.ini is absolute. A single agent may use it at a time; do not run two test suites on the same serial or mutate the same worktree build outputs concurrently.

To start this specific disposable AVD, after confirming nobody else is using it:

```powershell
$env:ANDROID_AVD_HOME = 'C:\Users\ASUS\Documents\ChatGPT\dsa project\verification\sprint3-coverage\avd'
Start-Process -FilePath 'C:\Users\ASUS\AppData\Local\Android\Sdk\emulator\emulator.exe' -WindowStyle Hidden -ArgumentList @('-avd', 'Coverage_API_33', '-port', '5582', '-wipe-data', '-no-snapshot', '-no-window', '-no-boot-anim', '-gpu', 'swiftshader_indirect')
$env:ANDROID_SERIAL = 'emulator-5582'
$env:DISPOSABLE_EMULATOR = '1'
```

The wipe above applies only to this task-owned disposable AVD. Never substitute a personal phone or personal AVD. Wait for boot completion using explicitly targeted adb commands, then run `python scripts/require-disposable-emulator.py` before tests that reset app preferences. Add the ADB/JDK/Node directories to the shell's PATH; set JAVA_HOME, ANDROID_HOME and GRADLE_USER_HOME to the paths above. Backend dependencies belong in a feature-local venv, not the bundled Python environment.

Tests may use a fixture backend bound to host loopback and accessed through emulator `10.0.2.2`; an instrumentation-local fixture server uses device `127.0.0.1`. Use mocked/local providers, never paid APIs or user credentials. The functional owner can validate the device now; the final coverage run must be coordinated after a committed feature SHA.

## Final collection contract

1. Feature owner commits the Android/backend source and meaningful tests, then provides the full SHA and exact test commands/server requirements.
2. CI owner integrates it in an independent worktree and reviews `python scripts/coverage/coverage.py scope --write`. New production files are fully counted; cumulative modified executable lines still start at upstream 947709e4, including Release 1. All present Android/backend/web modules must pass separately.
3. Commit the reviewed scope/config changes before measurement. Run Gradle clean, then coverage.py start.
4. Run JVM tests and debug APK, disposable-emulator Android tests, pytest-cov and pnpm web coverage on the same committed source. Generate teamCoverageReport, then coverage.py report. Require class-ID compatibility, both fresh JVM/device streams, nonzero tests, full fresh HTML/XML and exact >=80% line coverage.
5. Verify the current SHA's cloud build, PR bot comment, coverage check, report artifacts and APK. Revise this handoff's absent-feature statements only from observed code; historical reports retain their original SHA.

The readiness change removes hardcoded “Android Discovery is not implemented” text from future generated summaries and bot comments, replacing it with a scope-manifest statement valid before and after feature integration. It does not claim the feature exists. Validation: 11 coverage contract tests, unchanged 22-file public-main manifest validation, both publisher JavaScript bodies syntax-checked, git diff --check. An initial sandbox run failed due temporary-directory ACLs; rerunning with an explicit writable workspace temp directory passed. No new Android or final feature coverage run is claimed for this preparation.

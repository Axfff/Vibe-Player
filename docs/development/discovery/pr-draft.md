# Draft PR: Deliver Android Vibe Discovery manual increment with a real catalog and persistent Collection

Android users can now confirm scene/music intent, retrieve validated live iTunes songs, open their official links and save discoveries in an independent Collection that survives app restart. The existing local Python/provider work is retained and extended with deterministic metadata ranking, source identity checks, exclusions, artist diversity and explicit failures. The new native Kotlin/XML screen includes a consent-gated single-photo branch, bounded cancellable HTTP and durable AtomicFile storage.

Published as [draft PR #37](https://github.com/Axfff/Vibe-Player/pull/37), linked parent [Story #36](https://github.com/Axfff/Vibe-Player/issues/36). [Requirements, Sprint 3/4 AC and task ownership](../../requirements/vibe-discovery.md), [run instructions](android-sprint3.md), and [dated device/AC evidence](sprint3-delivery-2026-11-03.md) are included. Complete hybrid discovery remains the overall goal; legal previews and audio exclusivity are explicitly retained in Sprint 4.

## Validation

- Full clean local candidate `00bad97b`: 37 JVM, 49 Android, 166 Python and 22 web tests passed, with no failures/errors/skips. Cumulative coverage: Android 1727/1952 (88.47%), backend 954/992 (96.17%), web 812/825 (98.42%); all pass. All 46 production files, Release 1 and the 336-class matching audit are included. Final PR cloud results are separately attributed to their head SHA.
- Actual disposable API 33 run: confirm `city / focused / piano / US` → eight live iTunes results → launch Chrome → save → force-stop/reopen → restored Collection. APK hash and screenshots are recorded in the delivery evidence.
- Two bounded live catalog smoke runs each used four requests and passed result identity, nonrepeat replacement, lookup and external-only checks. Real model calls: zero. Automated transport fixtures are separate from this live evidence.

## Acceptance limits

- **S3-AC2 Blocked:** no authorized real vision configuration. Manual mode is usable; fixture photo tests do not satisfy recognition acceptance.
- **S3-AC5 partial:** the actual browser opened the validated Apple URL but was redirected to `/cn/new`; target-song page availability remains unpassed.
- Jamendo live authorization, legal in-app previews, audio mutual exclusion and full hybrid device acceptance remain unfinished and explicitly assigned to Sprint 4.
- Human review and acceptance remain pending. Story #2 and `release1` are unchanged; no release artifact is replaced and no merge is requested automatically.

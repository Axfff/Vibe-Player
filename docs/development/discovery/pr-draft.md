# Draft PR: Deliver Android Vibe Discovery manual increment with a real catalog and persistent Collection

Android users can now confirm scene/music intent, retrieve validated live iTunes songs, open their official links and save discoveries in an independent Collection that survives app restart. The existing local Python/provider work is retained and extended with deterministic metadata ranking, source identity checks, exclusions, artist diversity and explicit failures. The new native Kotlin/XML screen includes a consent-gated single-photo branch, bounded cancellable HTTP and durable AtomicFile storage.

Linked parent: [Story #36](https://github.com/Axfff/Vibe-Player/issues/36). [Requirements, Sprint 3/4 AC and task ownership](../../requirements/vibe-discovery.md), [run instructions](android-sprint3.md), and [dated device/AC evidence](sprint3-delivery-2026-11-03.md) are included. Complete hybrid discovery remains the overall goal; legal previews and audio exclusivity are explicitly retained in Sprint 4.

## Validation

- Feature source `8ef69e0b`: six new JVM tests, twelve Android Discovery tests, 166 Python tests and 22 web tests passed. Full cumulative coverage is being collected by the CI owner against the committed 46-file scope, including Release 1 and every new production file.
- Actual disposable API 33 run: confirm `city / focused / piano / US` → eight live iTunes results → launch Chrome → save → force-stop/reopen → restored Collection. APK hash and screenshots are recorded in the delivery evidence.
- Two bounded live catalog smoke runs each used four requests and passed result identity, nonrepeat replacement, lookup and external-only checks. Real model calls: zero. Automated transport fixtures are separate from this live evidence.

## Acceptance limits

- **S3-AC2 Blocked:** no authorized real vision configuration. Manual mode is usable; fixture photo tests do not satisfy recognition acceptance.
- **S3-AC5 partial:** the actual browser opened the validated Apple URL but was redirected to `/cn/new`; target-song page availability remains unpassed.
- Jamendo live authorization, legal in-app previews, audio mutual exclusion and full hybrid device acceptance remain unfinished and explicitly assigned to Sprint 4.
- Human review and acceptance remain pending. Story #2 and `release1` are unchanged; no release artifact is replaced and no merge is requested automatically.

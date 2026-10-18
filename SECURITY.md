# Weglet Security

Revision 1, 13 August 2026

## Protection Layers

The official Android build uses:

- strict TLS error blocking with no ordinary bypass;
- an explicit HTTP warning and one-session, exact-URL approval;
- JavaScript and DOM storage disabled for an approved HTTP page;
- mixed-content denial;
- Android Safe Browsing in WebView and engine protections in Gecko;
- local OpenPhish hash matching for main-frame navigation;
- URL parsing, normalization, hidden-character, userinfo, punycode, mixed-script,
  IP-address, shortener, risky-port, risky-path, and brand-lookalike checks;
- known tracker-domain blocking and an in-memory session report;
- a user blocklist;
- local structural analysis of HTTPS login forms in WebView;
- restricted file access, no remote WebView debugging, and confirmation before
  sensitive site permissions and downloads.

These controls reduce risk. They cannot prove that a page, person, payment, download,
or future redirect is safe.

## Local Form Analysis

The HTML heuristic returns four result classes:

- normal or no relevant login structure;
- caution for a cross-origin form action or login-page refresh;
- block for a password form submitting over HTTP;
- block for a high-confidence combination of login structure, brand headings, brand
  image cues, and a domain outside that brand's known roots.

No input value is read. No HTML, screenshot, URL, or result is uploaded by this feature.
The heuristic is deliberately conservative, but both false positives and misses remain
possible.

## Reporting

Do not post passwords, tokens, victim data, private history, exploit secrets that would
create immediate harm, or signing keys in a public issue. A useful report includes:

- affected Weglet version and engine;
- Android and WebView/Gecko version;
- exact steps using the smallest safe reproduction;
- expected and actual behavior;
- whether the issue reproduces after clearing site data;
- logs with secrets, personal data, and unrelated URLs removed.

The source archive does not declare a staffed private security mailbox. Until an
official private channel is announced, provide a minimal non-sensitive report to the
maintainer and ask for a protected channel before sharing sensitive evidence.

## Release Verification

An official release page should publish the APK SHA-256 and signing-certificate
fingerprint. A test-signed build cannot update an APK signed by a different certificate.
Do not disable Android's signature checks or install an unknown root certificate to
force an update.

Third-party forks must use distinct identity, package ID, signature, privacy notice,
and support contacts. See `FORK_POLICY.md`.

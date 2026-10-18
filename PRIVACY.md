# Weglet Privacy Architecture

Revision 1, 13 August 2026

This summary describes the official Android build. It is not a substitute for the
complete Russian and English notices bundled under
`app/src/main/assets/legal/terms_privacy_*.txt`.

## What Weglet Does Not Send To Its Developers

The official build has no Weglet account, first-party cloud history, advertising ID,
developer analytics SDK, or Weglet-owned telemetry endpoint. It does not intentionally
send opened URLs, search queries, local tracker counters, HTML, entered passwords, or
form values to the Developers.

## Local Data

Android private storage can contain settings, accepted-terms revision, restorable tabs,
bookmarks, quick-launch choices, blocklists, appearance settings, download identifiers,
wallpaper, local threat hashes, cookies, cache, and normal engine storage. Protection
counters live only in process memory and reset when Weglet closes.

Local storage is not immunity from a compromised or unlocked device, root access,
malware, backups, or OS vulnerabilities. Clearing Weglet data cannot erase information
already sent to an independent Website or a file already copied elsewhere.

## Necessary Third-Party Connections

- A selected search provider receives the search query and ordinary connection data.
- A Website receives requested URLs, IP address, headers, cookies, and information a
  user submits to it.
- DNS, ISP, Wi-Fi, VPN, device, Android, and enterprise components can process network
  metadata according to their roles.
- Safe Browsing and engine security features may communicate according to the installed
  WebView or Gecko implementation and provider policy.
- OpenPhish receives an update request with ordinary connection metadata. User-opened
  URLs are matched against local hashes and are not submitted by Weglet's matcher.
- Android DownloadManager, external apps, and media providers receive data when the
  user invokes their corresponding features.

These parties are independently controlled and can operate in other jurisdictions.
"Independent" does not create universal legal immunity: responsibility follows actual
control, decisions, conduct, causation, and mandatory law.

## Local HTML Protection

In WebView mode, Weglet inspects a limited page structure after an HTTPS page loads:
password-field presence, form-action protocol and origin, limited headings and controls,
brand cues in image metadata, and meta refresh. It does not request input values, retain
page HTML, capture screenshots, or upload the analysis. The result is a small local code.

The feature can miss attacks and can warn about legitimate cross-domain authentication.
Gecko mode uses its own content blocking and safe-browsing stack and is not represented
fully in the WebView counters.

## Forks

A fork that adds analytics, ads, sync, crash reporting, push, a proxy, or another
endpoint has a different privacy architecture. Its operator must replace this notice
with an accurate one and must not claim that the official Developers control or can
erase data from the fork's servers.

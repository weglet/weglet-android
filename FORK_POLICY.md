# Weglet Fork Policy

Revision 1, 13 August 2026

This document is a developer-facing summary. The Apache License 2.0 in `LICENSE`
controls rights in Weglet's own source code. The complete user-facing rules are in
`app/src/main/assets/legal/terms_privacy_*.txt`. Mandatory law always applies.

## Open Source And Identity

- You may inspect, use, modify, and redistribute covered code under Apache License 2.0.
- Preserve the license and applicable copyright, patent, trademark, NOTICE, and
  attribution notices. Mark modified files prominently.
- Apache License 2.0 does not grant a general trademark license or make a modified
  build official.
- Describe origin truthfully, for example, "Project Name, a fork of Weglet."
- Do not claim official status, endorsement, warranty, support, or common ownership
  without written authorization.

## Required Fork Identity

A publicly distributed, unofficial modified build must be visibly distinct:

- choose a new primary product name;
- change Android application ID `app.weglet.browser.v2` and, where practical,
  the `app.weglet.browser` code namespace;
- change deep-link schemes, APK filename, update channel, and other unique IDs;
- use your own signing key and publish its certificate fingerprint;
- replace the icon or otherwise make the build conspicuously distinguishable;
- identify the actual fork maintainer in About, support, and privacy screens;
- label the build as modified in store listings, release notes, and archives.

Do not request, extract, copy, publish, or use an official private signing key.
Attribution to original authors must remain even when the product identity changes.

## Prohibited Modifications

Never add or distribute:

- trojans, spyware, ransomware, hidden miners, botnet clients, or malware loaders;
- keyloggers or stealers of passwords, cookies, tokens, seed phrases, payment data,
  private messages, or files;
- intentional backdoors, secret remote commands, or authentication bypasses;
- covert APK installation or execution of arbitrary remotely supplied code;
- hidden page, download, DNS, proxy, certificate, search-result, or recipient changes;
- mass-phishing, unauthorized-access, stalking, exploitation, or attack capabilities;
- deceptive permission prompts, covert accessibility services, VPNs, root features,
  certificate installation, or traffic interception;
- false security indicators after TLS, Safe Browsing, mixed-content, or tracker
  protections have been removed;
- undisclosed analytics, advertising, fingerprinting, sync, search proxies, or
  remote-configuration systems.

An "educational" label, disclaimer, or open-source license does not authorize unlawful
conduct. Security research belongs in a lawful, controlled environment, not in a build
given deceptively to ordinary users.

## Privacy Changes

The official build intentionally sends no browsing history or search queries to a
Weglet-owned analytics backend. A fork may not repeat that statement if it adds a data
channel.

Before release, document:

- every added endpoint, SDK, analytics system, ad network, feed, and update host;
- data categories, purposes, recipients, retention, and transfer countries;
- whether collection is required or optional and how a person can disable it;
- permissions and what triggers their use;
- deletion, access, correction, and objection procedures required by applicable law;
- the identity and contact details of the fork operator.

Do not read or transmit password fields, payment fields, private messages, or arbitrary
page content without a lawful, strictly necessary, and conspicuously disclosed feature.
Do not use manipulative consent controls.

## Build And Release Security

Before publishing:

1. Pin dependencies and obtain them from verifiable sources.
2. Protect signing keys with least privilege and never embed them in source or APKs.
3. Review manifest permissions, exported components, deep links, and network policy.
4. Test HTTP warnings, invalid TLS, mixed content, Safe Browsing, URL heuristics,
   tracker blocking, downloads, permission denial, data clearing, and renderer failure.
5. Confirm that disabled protection is accurately reflected in the UI and documentation.
6. Publish release date, supported Android versions, architecture, commit identifier,
   source link, APK SHA-256, and signing-certificate fingerprint.
7. Verify clean installation and upgrades from earlier releases signed by the same key.
8. Keep a vulnerability-reporting channel and an incident-response procedure.

Successful compilation, one antivirus scan, or app-store acceptance does not prove that
a build is secure or lawful. Never promise perfect security, anonymity, or phishing
detection.

## Incident Response

For a credible critical vulnerability, signing-key leak, malicious dependency, or
hidden-data report:

1. preserve relevant evidence without collecting unrelated user content;
2. pause affected releases and update distribution pages;
3. identify impacted versions, keys, endpoints, and data;
4. remediate and independently review the fix;
5. notify users and authorities where law or reasonable security requires;
6. rotate keys or migrate signatures through a documented, verifiable process;
7. publish a truthful post-incident summary when doing so will not expose victims or
   create an immediate exploitation risk.

The official Weglet maintainers may decline to list or support a fork and may publish
certificate fingerprints or counterfeit warnings. That does not cancel Apache-licensed
code rights and does not make them the operator of the fork.

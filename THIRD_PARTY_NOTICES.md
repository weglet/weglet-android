# Third-party notices

## Mozilla GeckoView

Weglet includes Mozilla GeckoView 153 for ARM64 Android devices. GeckoView and
the Mozilla code it contains are available under the Mozilla Public License 2.0.

- Project: https://mozilla.github.io/geckoview/
- License: https://www.mozilla.org/MPL/2.0/
- Corresponding Mozilla source revision is identified in the GeckoView Maven POM.

## Onest and JetBrains Mono

The bundled Onest and JetBrains Mono fonts are licensed under the SIL Open Font
License 1.1. Full license texts are stored in `app/src/main/assets/fonts/`.

## Tabler Icons

Weglet uses Tabler Icons 3.46.0 by Paweł Kuna for interface controls. The official
TrueType font is bundled without changing the icon outlines. Tabler Icons is
licensed under the MIT License. The full license text is
stored in `app/src/main/assets/fonts/Tabler-Icons-MIT.txt`.

- Project: https://tabler.io/icons
- Source: https://github.com/tabler/tabler-icons

## AndroidX, Google Play services, Media3, Kotlin, and SnakeYAML

GeckoView has runtime dependencies on AndroidX, Google Play services FIDO,
AndroidX Media3, Kotlin, and SnakeYAML. Their artifacts retain their respective
license notices. The exact pinned dependency graph is generated in
`.deps/gecko-runtime.txt` by `scripts/fetch-gecko-deps.py`.

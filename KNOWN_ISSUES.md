# Known Issues

This file tracks lint issues intentionally left out of the current high-impact stability fix.

## AndroidManifest file import intent-filter lint

- Lint: `AppLinkUrlError`
- Location: `ft8cn/app/src/main/AndroidManifest.xml`
- Current state: known issue, not fixed in this pass.
- Impact range: affects static lint only for the local file import `VIEW` intent-filter that handles `file:` and `content:` URIs. It is not a web App Link flow. Runtime impact is expected to be low, but the manifest shape should be reviewed before suppressing or restructuring.
- Fix complexity: low to medium. The safest fix is to split or annotate the local file import intent-filter without changing supported `.txt` and `.adi` import behavior.

## Missing translations

- Lint: `MissingTranslation`
- Strings: `sync_time`, `syncing`, `qsl_success`, `transmitting_msg`
- Missing locales: `el`, `ja`, `es`
- Current state: known issue, not fixed in this pass.
- Impact range: users in Greek, Japanese, and Spanish locales may see fallback English text for these messages. No runtime crash is expected.
- Fix complexity: low. Add translations to the affected locale resource files, or mark the strings as non-translatable if English fallback is intentional.

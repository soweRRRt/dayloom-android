# Dayloom release checklist

## Before building

- Update `versionCode` and `versionName`; the release tag must be exactly `v<versionName>`.
- Confirm the production signing variables are provided by the CI secret store. Never copy the keystore or its passwords into the repository.
- Export a backup from the latest published build and keep a second copy for the migration test.
- Run `./gradlew --no-daemon ktlintCheck testDebugUnitTest lintDebug validateDebugScreenshotTest assembleDebug bundleRelease`.

## Clean installation

- Install the release APK on a phone with no Dayloom data.
- Add a habit, recurring plan, each list style, a wishlist item with category/link, a template set, an attachment, settings, and a Vault entry.
- Reboot the phone and verify reminders, notification permission guidance, locale, theme, navigation order, and Vault authentication.
- Verify TalkBack order, 1.3× font, compact phone, normal phone, and wide/tablet layout.

## Update without data loss

- Install the oldest supported signed APK, populate every data type, and record stable entity IDs plus attachment hashes.
- Install the new APK with `adb install -r`; never clear package data.
- Verify habits, plans, lists, wishes, categories, presets, template sets, attachments, settings, and Vault entries.
- Verify archived entries retain their own timestamps and only entries older than seven days disappear.
- Export from the updated app, clear a separate test installation, import the export, and compare counts, IDs, and attachment hashes.
- Import a schema-v1 backup and verify the missing template-set field defaults to an empty list.

## Device QA

- Measure cold start and primary navigation with the macrobenchmark build on a dedicated disposable physical device (`DAYLOOM_ALLOW_DESTRUCTIVE_BENCHMARK=true`); it must never target a working installation because the test runner can replace the APK and clear app data.
- Exercise large data sets in lists, calendar, search, filters, and archives; record frame timing and any visible jank.
- Test reminders after reboot, manual clock change, time-zone change, Doze, and battery restrictions.
- Confirm logs and Android backup contain no Vault plaintext; confirm `allowBackup=false` and data-extraction exclusions remain present.

## Publish

- Inspect the signed APK/AAB with `apksigner verify --verbose --print-certs` and compare the certificate fingerprint with the previous production release.
- Complete the clean-install and update paths above, then create the GitHub release from the matching tag.
- Download the published artifact once, verify its checksum/signature, and perform a final smoke test.

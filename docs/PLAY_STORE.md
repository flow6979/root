# Deploy Root to the Google Play Store

> Do this only once the app is genuinely testable. For the first release use the
> **Internal testing** track (up to 100 testers, no public review lag).

## 0. Prerequisites (one-time)
- A Google Play Developer account: **$25 one-time fee**, at
  https://play.google.com/console . Approval can take a day or two.
- App content ready: name, short/long description, an icon (512x512), a feature
  graphic (1024x500), at least 2 screenshots, and a **privacy policy URL**
  (mandatory - especially since Root touches usage/health/location later).

## 1. Create an upload signing key (one-time, keep it safe forever)

```bash
keytool -genkey -v -keystore root-upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias root-upload
```

Store `root-upload.jks` and its passwords OUTSIDE the repo (gitignored). If you
lose this key you cannot update the app. (Play App Signing manages the final
distribution key; this is just your upload key.)

## 2. Wire signing into Gradle

Add to `local.properties` (gitignored):

```properties
RELEASE_STORE_FILE=/absolute/path/root-upload.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=root-upload
RELEASE_KEY_PASSWORD=...
```

Then in `app/build.gradle.kts` add a `signingConfigs.release` block reading those
properties and reference it from `buildTypes.release`. (We'll add this when the
first release is actually cut - not needed for internal debug testing.)

## 3. Build a signed release bundle (AAB - Play requires .aab, not .apk)

```bash
./gradlew bundleRelease
# output: app/build/outputs/bundle/release/app-release.aab
```

## 4. Upload

1. Play Console -> Create app -> fill the form.
2. Complete: App content (privacy policy, data safety form, ads declaration,
   target audience). **Data safety must honestly declare** usage/health/location
   access when those features ship.
3. Release -> Testing -> **Internal testing** -> Create release -> upload the `.aab`.
4. Add tester emails (or a Google Group), save, roll out.
5. Testers install via the opt-in link.

## 5. Promote later
Internal -> Closed (beta) -> Open (beta) -> Production. Production submissions get a
review (hours to days).

## POLICY WARNING (read before building Shield)
The app-blocking feature will use `AccessibilityService` and/or
`PACKAGE_USAGE_STATS`. Google scrutinises these hard:
- You must submit a **Permissions Declaration** justifying the use case.
- AccessibilityService is only allowed for genuine accessibility/'well-being'
  uses - screen-time control qualifies, but the declaration must be precise.
- Prefer the official **UsageStatsManager** + a custom overlay over
  AccessibilityService where possible; it draws less policy risk.
This is flagged as a top risk in ROADMAP - validate before investing in the build.

## Rigorous device testing before any release
Use an emulator or **BrowserStack App Automate/App Live** to test on real Android
versions/devices - essential for the native blocking/geofencing features.

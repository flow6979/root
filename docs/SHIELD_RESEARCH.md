# Shield interrupt engine - Play Store policy reality-check

Goal: when the user opens a "junk" app (Instagram/YouTube Shorts), Root detects it
and shows a friendly full-screen "pause" interrupt. Android has **no official
third-party app-blocking API** (unlike iOS Screen Time), so we must choose between
two mechanisms. This doc records the policy research and the chosen approach BEFORE
we build, so we don't build on a foundation Google will reject.

## The two mechanisms

### A. UsageStatsManager + overlay  (RECOMMENDED)
- Detect the foreground app by polling `UsageStatsManager.queryEvents()` on a short
  rolling window (~700ms cadence) from a foreground service.
- Draw the interrupt with a `SYSTEM_ALERT_WINDOW` ("Display over other apps") overlay.
- Permissions: `PACKAGE_USAGE_STATS` (special "Usage access", user grants in Settings)
  + `SYSTEM_ALERT_WINDOW` (user grants in Settings) + a foreground service.
- Trade-off: detection is near-real-time but not instant (sub-second to ~1s delay).
  For a "pause and breathe" nudge that delay is completely acceptable.
- Policy risk: **LOW.** These are standard special-access permissions widely used by
  screen-time / wellbeing apps. No AccessibilityService policy minefield.

### B. AccessibilityService + overlay
- Observe window-state changes in real time (instant detection), then overlay.
- Policy risk: **HIGH.** See findings below.

## Official Google Play policy findings (2025-2026)

From the Play Console AccessibilityService policy + sensitive-permissions policy:

- **`isAccessibilityTool` is NOT allowed for us.** It is only for apps whose primary
  purpose is helping people with disabilities (screen readers, switch/voice input,
  braille). Monitoring/wellbeing/automation apps explicitly do NOT qualify.
- A digital-wellbeing / app-blocking app using AccessibilityService therefore must:
  1. Complete Google Play's **Permission Declaration Form** (required for Android 12+
     targets using AccessibilityService).
  2. Show a **prominent in-app disclosure** during normal use (not buried in menus)
     explaining why the access is needed and what data is touched.
  3. Obtain **affirmative user consent** (explicit checkbox/tap).
- Key rule that actually helps us: "any use of the Accessibility API that enables an
  app to autonomously initiate, plan, and execute actions is strictly prohibited",
  but "deterministic, rule-based automation ... a static, human-defined script" is
  permitted. A simple "if app X is foreground, show pause screen" is rule-based, so
  it is not auto-disqualified - BUT it still carries the declaration/disclosure burden
  and heavier review scrutiny.
- **Non-compliance = app suspension and possible developer-account termination.**

## Decision

**Build Shield on Approach A (UsageStatsManager + SYSTEM_ALERT_WINDOW overlay +
foreground service).** Only consider AccessibilityService later IF instant detection
proves necessary, and only with the full declaration + disclosure + consent flow.

Rationale: A gives us ~80-95% of the UX at a fraction of the policy risk, and keeps
our first Play submission clean. The interrupt is a gentle pause, not a hard
millisecond-perfect block, so polling latency is fine.

## Permissions + onboarding UX (both are user-granted in Settings, not auto)
- `PACKAGE_USAGE_STATS` -> Settings > Special app access > Usage access.
- `SYSTEM_ALERT_WINDOW` -> Settings > Display over other apps.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` (Android 14+) for the watcher.
- Onboarding must clearly explain WHY each is needed (this doubles as the Play
  "prominent disclosure"). A confusing permission flow is the #1 way we lose users here.

## Technical design sketch (for the build)
- A `ForegroundService` ("Root is watching your back") holding a low-frequency poller.
- Poller reads recent usage events; when a monitored package becomes foreground and a
  cooldown has elapsed, launch the overlay Activity/`SYSTEM_ALERT_WINDOW`.
- Overlay = our friendly interrupt (orb + personal line + "pause" / "open anyway (10s)").
- Strict Mode (premium) removes the escape and adds a timed lockout.
- Battery: poll only while a monitored app category is likely in use; back off when the
  screen is off; use `UsageStatsManager` (cheap) not continuous sensors. Measure on device.

## Play Store submission checklist (Shield-specific)
- [ ] Data safety form declares usage-access + overlay honestly.
- [ ] Prominent in-app disclosure before requesting Usage access.
- [ ] Privacy policy covers what usage data is read and that it stays on-device / how it's used.
- [ ] If we ever add AccessibilityService: submit the Permission Declaration Form + video.
- [ ] Test on a real device / BrowserStack across Android 12, 13, 14 (permission UX shifts per version).

## Open questions to validate during build
- Android 14/15 tightened foreground-service types and full-screen-intent rules -
  confirm our FGS type and overlay launch still work on the latest targets.
- Confirm `SYSTEM_ALERT_WINDOW` is grantable on the target min/target SDK without extra review.

## Sources
- [Use of the AccessibilityService API - Play Console Help](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)
- [Permissions and APIs that Access Sensitive Information - Play Console Help](https://support.google.com/googleplay/android-developer/answer/16558241)
- [Google Play policy about use of the Accessibility API - OMA support](https://orangeoma.zendesk.com/hc/en-us/articles/4407888308242-Google-Play-policy-about-use-of-the-Accessibility-API)
- [Inquiry about policy compliance for an app with app-usage-blocking features - Play Developer Community](https://support.google.com/googleplay/android-developer/thread/319193084)
- [Impact of Accessibility Permission in Android Apps - BrowserStack](https://www.browserstack.com/guide/accessibility-permission-in-android)

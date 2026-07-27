# Root - project brief (read this first)

> Working name: **Root**. Placeholder, not final.
> This file is the portable memory for the project. Any new Claude Code session
> should read this file and everything in `docs/` before writing code.

## What Root is

An Android app that helps youth break an unhealthy digital-lifestyle loop
(irregular sleep, junk food, no exercise, high screen time, short-form-content
addiction, "manifesting" goals instead of acting, compulsive/provoking content).

The core insight: these are not 7 separate problems, they are one self-feeding
dopamine/self-regulation loop. Root's job is to **catch the person at the moment
of the bad decision and interrupt it with something personal enough to make them
pause** - as a trusted friend, never a warden.

The companion is a **celestial orb** (sun by day, moon by night) that reflects the
real time of day. The whole UI colour follows the sky (see DECISIONS).

## Non-negotiable product principles

1. **Friend, not warden.** Gentle, personal, one well-timed nudge beats many nags.
   No spam. Every interruption has a ceiling and an escape (except paid Strict Mode).
2. **No background surveillance.** No always-on mic/camera. It is banned by the OS,
   rejected by Play Store, and users will not grant it. Use OS-sanctioned signals
   (UsageStats, Health Connect, geofencing) + consented in-app sessions instead.
3. **The Stories feed must never become the disease.** Finite (3-5/day), slow,
   enriching, no auto-play. The ending is a feature.
4. **Android first.** iOS is parked until Android finds product-market fit.
5. **Personalisation via RAG, not per-user model training.** One model + each
   user's own history/context retrieved at inference.

## App structure (5 tabs + interrupt)

| Tab | Purpose | Root cause it attacks |
|-----|---------|----------------------|
| Home | AI friend: daily check-in + reflection sessions | Emotional |
| Shield | Screen-time insights, charts, AI analysis + suggestions, app-interrupt config, Strict Mode (premium) | Screen time |
| Moments | Geofence + food/sleep logging, caught in real life gently | Physical health |
| Stories | Finite, immersive, calming short stories (audio = premium) | The scroll habit, redirected |
| You | Settings, appearance (time-adaptive / minimalist), personality, accountability, subscription | - |
| (Interrupt overlay) | Appears the instant a junk app opens; friend-style pause | Screen time |

## Monetisation

Free tier genuinely useful (drives PMF + word of mouth). Paid wall:
**Strict Mode + unlimited/deep AI + weekly insights + story audio.**
Add-ons: custom personality/voice, cosmetics, multiple geofences, data export.
Payments via Google Play Billing. Monetise only after PMF.

## Repo conventions

- Docs live in `docs/` as markdown. No Confluence.
- `docs/DECISIONS.md` is the source of truth for *why*. Append, do not rewrite history.
- `docs/SPEC.md` is the product spec. `docs/ROADMAP.md` is sequencing.
- Design mockups live in `design/` (currently `design/mockup-android.html`).
- Update this file with `/revise-claude-md` (or manually) at the end of significant sessions.

## Tech stack (locked - see DECISIONS D17-D23)

- **App:** Native Kotlin + Jetpack Compose (Android only)
- **Backend/hosting:** Supabase (Postgres + pgvector, Auth, Storage, Edge Functions)
- **AI:** free LLM tiers (Gemini free tier + Groq), server-side; RAG via pgvector.
  Route sensitive reflections through Groq (no data training). On-device Gemini Nano = private upgrade path.
- **Voice:** on-device SpeechRecognizer (STT) + on-device TextToSpeech (TTS), both free
- **Payments:** Google Play Billing
- **Cost principle:** free/lowest-cost tiers everywhere; launch target ~$0/month

## Status

Vertical slice BUILDS and is TESTED on this machine.
- Toolchain: JDK 17 + Gradle 8.7 (wrapper) + Android SDK 34. See docs/BUILD_AND_TEST.md.
- `./gradlew testDebugUnitTest` -> 15 unit tests, 0 failures.
- `./gradlew connectedDebugAndroidTest` -> 2 Compose UI tests, 0 failures (emulator).
- `./gradlew assembleDebug` -> app/build/outputs/apk/debug/app-debug.apk (16 MB).
- Verified live on an AVD (root_pixel, API 34 arm64): Home, reflection chat loop,
  minimalist toggle, time-adaptive night theme all working.
- Home + AI reflection session working (offline demo mode until a Groq key is set).
- Shield: insights UI (charts + Root's read) + WORKING interrupt engine
  (UsageStatsManager foreground poller + SYSTEM_ALERT_WINDOW overlay + FGS), verified
  on emulator. See com.rootapp.shield.*.
- Stories: immersive finite-scroll screen with ending state, verified on emulator.
- Moments: geofence card + food logging (persisted), verified on emulator.
- Persistence: local-first LocalStore (mood, streak, food, interrupt stats) -
  verified to survive force-stop/restart.
- Cloud: Supabase LIVE. Anonymous auth on start + RLS-scoped cloud sync of mood/food
  (SupabaseRepository, OkHttp). Verified end-to-end on emulator (row landed in cloud).
  AI (Groq) LIVE and verified. Keys in gitignored local.properties.
- Analytics: swappable Track/Analytics layer (Logcat now, PostHog later), content-safe;
  key events instrumented. See docs/ANALYTICS.md.
- Onboarding: first-run permission flow (usage/overlay/notifications), persists via SettingsStore.
- You/settings: premium card, appearance (minimalist toggle, persisted + drives whole UI),
  friend personality, permission status. All 5 tabs are now real screens (no placeholders).
Next: push to GitHub (docs/GITHUB.md), device-test, then build the Shield interrupt
engine (validate Play Store policy first - see docs/PLAY_STORE.md + ROADMAP risks).

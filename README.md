# Root

A friendly Android app that helps youth break the unhealthy digital-lifestyle loop
(irregular sleep, junk food, doomscrolling, low focus, compulsive content) by
catching the moment of decision and interrupting it gently - as a trusted friend,
never a warden. The UI colour follows the real sky (time-adaptive), with a
minimalist black-&-white option.

> Working name. Android first; iOS parked. See `CLAUDE.md` and `docs/` for the full
> product + decisions record.

## Status
Vertical slice: navigation shell, time-adaptive + minimalist theme, Home, and an
AI reflection session wired to a free LLM (Groq, with offline demo fallback).
Shield / Moments / Stories / You are placeholders for later builds.

## Stack
Kotlin + Jetpack Compose · Supabase (planned) · free LLM tiers (Groq/Gemini) ·
on-device STT/TTS · Google Play Billing (planned). Launch cost target ~$0/month.

## Quick start
```bash
# 1. Toolchain + SDK: see docs/BUILD_AND_TEST.md
# 2. Run the tests
./gradlew testDebugUnitTest
# 3. Build & install
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Docs
- `CLAUDE.md` - project brief + rules (read first)
- `docs/SPEC.md` - product spec
- `docs/DECISIONS.md` - decisions + reasoning
- `docs/ROADMAP.md` - sequencing + risks
- `docs/BUILD_AND_TEST.md` · `docs/GITHUB.md` · `docs/PLAY_STORE.md` - runbooks
- `design/mockup-android.html` - interactive design mockups

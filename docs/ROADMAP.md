# Root - roadmap

## Done
- Concept + product principles agreed.
- Android UI mockups: 5 tabs + interrupt, time-adaptive theme, minimalist mode,
  animated celestial-orb companion, immersive Stories, Shield insights.
  (`design/mockup-android.html`)
- Doc set seeded (CLAUDE.md, SPEC, DECISIONS, ROADMAP).
- Tech stack + hosting decided (D17-D24).
- Vertical slice CODE written (Kotlin + Compose): nav shell, time-adaptive +
  minimalist theme engine, celestial Orb, Home, AI reflection session (Groq client
  + ViewModel + prompts), placeholders for the other 4 tabs.
- Unit-test suite written: TimeOfDay, Prompts, GroqClient (MockWebServer),
  ReflectionViewModel state machine.
- Runbooks: BUILD_AND_TEST, GITHUB, PLAY_STORE. README.

- Toolchain + emulator set up (JDK17, Gradle 8.7, SDK 34, AVD root_pixel).
- Slice verified: 15 unit tests + 2 Compose UI tests green; run live on emulator.

- Pushed to GitHub: github.com/flow6979/root-app (private).
- Shield reality-check done + engine BUILT & verified (poller + overlay + FGS).
- Shield insights UI + immersive Stories screen BUILT & verified on emulator.

## Distance to a real launch (honest)
Internal-testing-ready in DAYS (needs: app icon, privacy policy URL, release signing
config, honest Data Safety form). Public MVP is ~4-8 weeks: auth + persistence (Supabase),
route AI via a backend (hide key + RAG), Moments tab, Play Billing/premium, Stories content
pipeline, onboarding polish, multi-device testing. See ANALYTICS.md for owner metrics.

## Next up
- Set a real GROQ_API_KEY in local.properties to exit offline demo mode.
- Auth + persistence (Supabase): accounts, streaks, mood/logs, RAG memory.
- Build Moments (geofence + food log) and the You/settings screen.
- Premium + Play Billing.
- Analytics instrumentation (PostHog + Crashlytics) - see docs/ANALYTICS.md.
- Store assets (icon, listing, privacy policy) + release signing + internal-testing upload.

## Next (in order)
1. **Tech stack + hosting decisions** (in progress). Record picks in DECISIONS "Tech".
2. **Reality-check the hard Android bits:** app-blocking (UsageStats +
   AccessibilityService overlay), geofencing, Health Connect, Play Store policy.
3. **Set up GitHub repo** with CLAUDE.md + docs committed (the portable memory).
4. **Thin vertical slice first:** one tab end-to-end (recommended: Home + one
   reflection session wired to the AI backend) to prove the loop before breadth.
5. Onboarding + permissions flow.
6. Shield interrupt engine (the technical centrepiece).
7. Moments (geofence + food log).
8. Stories (content pipeline + finite guardrails).
9. Premium + Play Billing.
10. Closed beta with real youth users -> measure retention -> PMF call.

## Known risks / open questions
- Play Store policy on AccessibilityService for app-blocking (used by real apps, but
  Google scrutinises it - must justify the use case clearly).
- Battery impact of geofencing + usage monitoring.
- Cost per active user of LLM calls (mitigate with free = short interactions).
- Content sourcing/quality + safety for Stories.
- PRIVACY vs cost: free LLM tiers may train on submitted data - sensitive reflection
  content must go through a no-training provider (Groq) or on-device (Gemini Nano). Revisit
  as a first-class privacy decision before any beta with real users.
- Free-tier rate limits (Gemini/Groq/Supabase) may throttle under load - watch as users grow.
- Getting real users to grant Usage Access + Health + Location (onboarding UX is critical).

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

- Pushed to GitHub: github.com/flow6979/root-app (private). Releases up to **v0.2.4**.
- Shield reality-check done + engine BUILT & verified (poller + overlay + FGS).
- Shield insights UI + immersive Stories screen BUILT & verified on emulator.

- All 5 tabs BUILT (Home, Shield, Moments, Stories, You) - see docs/screenshots/.
- Groq AI: LIVE + verified (chat + reflection + Whisper speech-to-text).
- Voice: voice-only session; TTS fallback chain ElevenLabs -> Google Translate TTS -> system.
- Supabase: LIVE - email/password + anonymous auth, RLS cloud sync of mood/food/reflections.
- Moments: nearby eating spots via OSM Overpass, tagged healthy/junk; eating score + explainer;
  food log with add / voice / per-day history / delete.
- Stories: AI "For you" + public-domain "Classics", refreshable, optional cloud narration.
- Scores: deterministic wellbeing/eating/mood/screen scores + Home tap-to-explain dialog.
- Personality (Gentle / Tough-love) applied to the friend's tone.
- **All features free** - premium tier + Play Billing removed; every feature is unlocked for
  everyone (v0.3.0).
- **AI engine choice** - built-in free (Groq) by default, or the user's own **Gemini key**
  (You -> AI). Provider picked in `AppModule`; future: OpenAI/Anthropic via the same slot.

## Distance to a real launch (honest)
Internal-testing-ready in DAYS (needs: app icon, privacy policy URL, release signing
config, honest Data Safety form). There is no paid tier - all features are free - so no
billing work is needed. Public MVP still wants: route AI + writes via a backend (hide keys
+ RAG), two-way sync/restore across devices, onboarding polish, and multi-device testing.
See ANALYTICS.md for owner metrics.

## Next up
- More AI providers behind the same slot (OpenAI, Anthropic) + a picker in You -> AI.
- Route AI + writes via a backend so the anon/Groq/ElevenLabs keys aren't shipped in the app;
  rotate the current keys before going public.
- Two-way sync/restore on a new device; sync interrupt stats.
- RAG memory for the friend (embed reflections -> pgvector, retrieve as context).
- Geofencing + Health Connect for Moments (real GPS triggers, sleep/steps).
- Analytics live (PostHog + Crashlytics) - see docs/ANALYTICS.md.
- Store assets (icon, feature graphic, listing) + release signing + internal-testing upload.

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

# Root - decisions log

Append-only record of decisions and the reasoning behind them, so future sessions
inherit the "why" instead of re-litigating. Newest at the bottom of each section.

## Product

- **D1. Attack the loop, not 7 separate habits.** The unhealthy behaviours share one
  root (dopamine / self-regulation). Interventions target the loop.
- **D2. Interrupt at the moment of decision, friend-style.** Existing apps log after
  the fact; Root's edge is the in-the-moment, personal pause. Relationship = trusted
  friend the user chose, never a warden.
- **D3. Dropped the "quote every 1 minute" nagging.** Punishment/spam -> uninstall.
  One well-timed nudge with a ceiling instead.
- **D4. No 24/7 mic/camera surveillance.** REASON: OS sandbox cuts background mic/camera;
  Play Store rejects it; users won't grant it; and it doesn't even work technically
  (phone can't see the burger, mic can't tell "provoking content" from a movie).
  REPLACEMENT: OS-sanctioned signals (UsageStats, Health Connect, geofencing) +
  consented in-app reflection sessions. This keeps ~80% of the intended signal, legally.
- **D5. Stories feed is finite by design.** An anti-short-form app must not ship an
  infinite feed. Guardrails: 3-5/day, slow, enriching, no auto-play, deliberate ending.
  Framing: "methadone for the scroll" - hijack the habit toward something healing.
- **D6. Personalisation via RAG, not per-user training.** Same effect, ~1000x cheaper,
  actually buildable.
- **D7. Android first, iOS parked.** Focus; validate PMF on one platform. (Note: iOS
  Screen Time API is actually more powerful, revisit for iOS later.)
- **D8. Free tier genuinely useful; paid wall = Strict Mode + unlimited/deep AI +
  weekly insights + story audio.** Desperate users pay for control, depth, accountability.
- **D9. Additional target problems folded in:** procrastination, doomscroll/comparison
  anxiety, impulse spending, loneliness, compulsive/provoking content.

## Design / UI

- **D10. Time-adaptive UI colour.** The whole UI follows the real sky (midnight black,
  night blue, dusk amber->indigo, dawn soft, day sky-blue). Fits the "feel of the day"
  goal and is rare in the market. Base stays monochrome so the time reads instantly.
- **D11. Companion = celestial orb (sun/moon), not a plant/creature.** Chosen over a
  Finch-style creature because it ties directly to the time-of-day concept. It is
  "alive": breathes, gets shaded by passing clouds, and changes moon phase at night.
  Production: drive from real weather + lunar data, not just a timer.
- **D12. Minimalist B&W mode.** User option for pure black & white, ignoring time colour.
  Serves low-distraction / accessibility preference. Available as a live setting.
- **D13. Stories screen is full-bleed immersive** (animated scene + scrim) rather than
  a card, to make the calming redirect feel worth choosing over Reels.
- **D14. Shield screen is data + AI** (charts, "Root's read" analysis, suggestion chips),
  not just a block-config list.

## Process / tooling

- **D15. Docs are local markdown only. No Confluence.**
- **D16. Repo (CLAUDE.md + docs/ + git history) is the portable memory** that future /
  separate Claude sessions read to catch context. Local auto-memory does not travel via
  GitHub, so all durable context must be committed here.

## Tech / hosting

- **D17. App framework: Native Kotlin + Jetpack Compose.** REASON: every hard feature
  (app-block overlay via AccessibilityService, UsageStats, geofencing, Health Connect)
  is Android-native regardless of framework; Flutter/RN would need native plugins + a
  bridge on top. Compose is also clean for the custom time-adaptive animated UI. Trade-off
  accepted: a future iOS version is a separate build, but iOS is parked (D7).
- **D18. Backend + hosting: Supabase.** Postgres + pgvector (RAG store), Auth (incl.
  Google Sign-In), Storage (food photos), Edge Functions (AI orchestration) in one managed
  platform. Least ops, cheap to start, SQL suits the insights/charts queries.
- **D19. [REVISED] AI: free LLM tiers, not Claude API.** Cost is a hard constraint (see D24).
  Primary: Google **Gemini API free tier** (Gemini Flash). Fallback: **Groq** (free, Llama,
  fast). Called from the backend, keys never in the app. PRIVACY CAVEAT: free tiers may train
  on submitted data - a real problem for sensitive reflection content. Therefore route the
  **private reflection sessions through Groq (states no training on data)**, keep Gemini for
  lower-sensitivity tasks, and treat on-device **Gemini Nano (AICore)** as the private,
  zero-cost upgrade path where device support exists.
- **D20. RAG: embeddings via Gemini free embedding API, stored in pgvector** over the user's
  own logs/reflections; retrieved at inference. No per-user fine-tuning (see D6).
- **D21. [REVISED] Voice: on-device Android SpeechRecognizer (STT) + on-device Android
  TextToSpeech (TTS)** - both free, no cloud cost. Premium "nicer voice" can use a cloud TTS
  later; base narration stays free.
- **D25. Shield uses UsageStatsManager + SYSTEM_ALERT_WINDOW overlay, NOT
  AccessibilityService.** REASON (see docs/SHIELD_RESEARCH.md): Android has no official
  third-party app-blocking API. AccessibilityService carries high Play Store policy risk
  (isAccessibilityTool is disallowed for wellbeing/monitoring apps; requires Permission
  Declaration Form + prominent disclosure + consent; misuse = suspension/account
  termination). UsageStatsManager ("Usage access") + overlay + foreground service gives a
  sub-second detection delay that is fine for a gentle "pause" nudge, at LOW policy risk.
  Revisit AccessibilityService only if instant blocking proves necessary, with full
  declaration/disclosure/consent.
- **D24. Cost principle: prefer free / lowest-cost tiers everywhere** (Supabase free tier,
  free LLM tiers, on-device voice) until free limits are hit and revenue covers the overage.
  Running cost at launch target ~$0.
- **D22. Payments: Google Play Billing** for subscriptions.
- **D23. Build order: one thin vertical slice first** (Home + a working reflection session
  wired to the Claude backend) to prove the core loop before building breadth or the harder
  Shield interrupt engine.

# Root - product spec (v0.1)

Working name: **Root**. Android only for v1.

## 1. Problem

Youth are stuck in a self-feeding loop: irregular sleep, junk food (delivery /
eating out), little physical activity, high screen time, fractured focus from
short-form content, goal-fantasising ("manifesting") instead of acting, and
consumption of provoking/compulsive content. These are symptoms of one underlying
dopamine + self-regulation problem, not seven independent issues.

## 2. Approach

Catch the user at the moment of decision and interrupt gently, as a friend.
Personalise everything to the individual via their own logged history (RAG).
Make the app's own presence honest about time and finite by design.

## 3. Signal layers (how Root "knows" things - all consent-based, no surveillance)

- **Layer 1 - Passive (OS-granted):** app usage & pickups (UsageStats / Digital
  Wellbeing), sleep + steps + activity (Health Connect / Google Fit), location
  geofencing (near fast-food / restaurants).
- **Layer 2 - Active self-log:** notification prompts ("what did you eat?") with
  photo (AI reads it), one-tap mood/energy, quick voice logs (foreground).
- **Layer 3 - Reflective:** the in-app AI reflection session (foreground, mic
  consented) - the richest emotional signal, feeds the RAG memory.

## 4. Screens

### Home - the AI friend (the soul)
- Greeting + streak, celestial orb companion (reflects time of day).
- Companion message (warm, personal, from RAG context).
- Daily mood check-in (one tap).
- "Start a reflection session" (5 min guided AI conversation).

### Interrupt overlay (the key moment)
- Fires the instant a configured junk app opens.
- Orb + a personal, contextual line ("It's 11:48pm, last night this kept you up till 2").
- "Okay, let's pause" / "Open anyway (10s)" on free tier.
- Strict Mode (premium) removes the escape.

### Shield - screen-time insights + AI
- Weekly screen-time bar chart, trend vs last week.
- "Root's read": AI analysis of patterns (worst windows, triggers) + tappable suggestions.
- Donut breakdown by app.
- App-interrupt toggles.
- Strict Mode (premium).

### Moments - real-world catcher
- Live geofence card ("you're near Burger Point, pause 10 seconds?").
- Today's log: meals (photo), sleep (from Health Connect), flagged items.
- "+ Log what you ate".

### Stories - the healthy, finite scroll
- Immersive full-bleed animated scene, story text over a scrim.
- Segment progress bar (N of 5 today).
- Content tied to the user's goals (e.g. "someone who fixed their sleep").
- "Listen instead" (audio narration = premium).
- Guardrails: finite (3-5/day), slow (read/reflect, not swipe), enriching, no auto-play.
- Deliberate ending screen: "That's enough for today, go live your real one."

### You - settings & premium
- Premium upsell (Strict + unlimited AI + insights + audio).
- Appearance: time-adaptive colour vs minimalist B&W.
- Your friend: personality (gentle / tough-love), voice (premium), accountability partner.
- Permissions: usage access, Health Connect, location.

## 5. UI system

- **Time-adaptive theme:** UI colour follows the real sky - midnight (black),
  night (night-blue), dusk (amber->indigo), dawn (soft), day (sky-blue). Base is
  monochrome; the time tint + orb are the only colour.
- **Minimalist mode:** user option for pure black & white, ignores time colour.
- **Companion orb:** sun/moon that breathes, gets shaded by passing clouds, and
  changes moon phase at night. Production version should use real weather + lunar data.
- **Motion:** staggered entrance, chart draw-in, immersive story ken-burns + drifting light.

## 6. Personalisation

One LLM + RAG over the user's own history (logs, reflections, patterns). No
per-user fine-tuning. Memory of past conversations gates behind premium (free =
short check-ins, premium = long sessions with full recall).

## 7. Monetisation

Free tier is genuinely useful. Paid: Strict Mode, unlimited/deep AI, weekly
insights, story audio. Add-ons: personality/voice, cosmetics, multi-geofence,
export. Google Play Billing. Monetise after PMF.

## 8. Out of scope for v1

iOS. Always-on mic/camera surveillance. Social/feed features. Per-user model training.

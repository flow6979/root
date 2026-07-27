# Supabase - cloud auth + sync (setup handoff)

Persistence today is **local-first** (`com.rootapp.data.LocalStore`, survives restarts,
no network). Supabase adds **accounts** (so data follows the user across devices),
**cloud backup**, and the **pgvector RAG store** for the AI friend's memory. This step
needs a free Supabase project that only you can create (like the Groq key).

## What Supabase gives us (all on the free tier)
- **Auth** (email / Google / anonymous) - user accounts.
- **Postgres** - store mood, food, reflection, interrupt-stats rows per user.
- **pgvector** - embeddings of the user's own reflections for RAG memory.
- **Storage** - food photos later.

## Your one-time setup (~5 min)
1. Go to https://supabase.com -> sign in -> New project (free tier). Pick a region near you.
2. Project Settings -> API. Copy:
   - **Project URL** (e.g. https://abcd.supabase.co)
   - **anon public key**
3. Paste into `local.properties` (gitignored):
   ```properties
   SUPABASE_URL=https://abcd.supabase.co
   SUPABASE_ANON_KEY=eyJ...
   ```
4. Tell me "supabase ready" and I'll wire the client + auth + sync.

## Integration plan (what I'll build once keys exist)
- Add `supabase-kt` (auth + postgrest + realtime) deps; read URL/key from BuildConfig.
- `AuthRepository`: anonymous sign-in on first launch (zero-friction), upgradeable to
  email/Google later. App still runs offline if unconfigured (graceful fallback).
- Sync `LocalStore` <-> Postgres tables (`moods`, `foods`, `reflections`, `interrupt_events`)
  keyed by user id. Local-first: write locally, sync in background.
- RAG: on reflection, embed the turn (Gemini free embeddings) -> pgvector; retrieve top-k
  as context for the friend prompt. Route sensitive reflections through Groq (D19).
- Row Level Security so each user only sees their own rows.

## Privacy (mandatory for this app)
- RLS on every table; a user can never read another user's data.
- Reflection *content* is sensitive - store it encrypted-at-rest (Supabase default) and
  keep it out of analytics (see ANALYTICS.md).
- Declare cloud storage honestly in the Play Data Safety form + privacy policy.

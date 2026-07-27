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

## Status
- Project created; URL + anon key are in local.properties. Connectivity verified (auth health 200).
- TWO dashboard actions remain (I can't do these with the anon key) before I can wire + test:

### Action 1 - enable anonymous sign-ins (1 toggle)
Supabase Dashboard -> Authentication -> Sign In / Providers -> find **Anonymous Sign-Ins**
-> enable -> Save. (Gives zero-friction accounts; users don't type an email.)

### Action 2 - create the tables + RLS (paste this in the SQL Editor -> Run)
```sql
create extension if not exists vector;

create table if not exists public.moods (
  id bigint generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  epoch_day bigint not null, mood int not null,
  created_at timestamptz not null default now());

create table if not exists public.foods (
  id bigint generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  label text, healthy boolean not null default true,
  created_at timestamptz not null default now());

create table if not exists public.reflections (
  id bigint generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  message_count int not null default 0,
  created_at timestamptz not null default now());

alter table public.moods enable row level security;
alter table public.foods enable row level security;
alter table public.reflections enable row level security;

create policy "own moods" on public.moods for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own foods" on public.foods for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own reflections" on public.reflections for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
```

After both actions, tell me "supabase configured" and I'll wire the client + anonymous
auth + local->cloud sync and verify it end to end.

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

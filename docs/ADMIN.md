# Admin - tracking users & granting premium

Root's backend is Supabase, which gives you a built-in admin dashboard plus SQL. This
doc covers (1) granting time-limited premium and (2) tracking user progress.

## One-time setup - run in Supabase SQL Editor
```sql
-- Entitlements: server-controlled, time-limited premium
create table if not exists public.entitlements (
  user_id uuid primary key references auth.users(id) on delete cascade,
  premium_until timestamptz,
  updated_at timestamptz not null default now()
);
alter table public.entitlements enable row level security;
-- users may READ their own entitlement; only you (dashboard/service role) can write it
create policy "read own entitlement" on public.entitlements
  for select using (auth.uid() = user_id);

-- Progress view: one row per user with their activity
create or replace view public.user_progress as
select
  u.id                      as user_id,
  u.created_at              as joined,
  u.last_sign_in_at         as last_seen,
  (select count(*) from public.moods m       where m.user_id = u.id) as moods,
  (select count(*) from public.foods f       where f.user_id = u.id) as foods,
  (select count(*) from public.reflections r where r.user_id = u.id) as reflections,
  (select max(created_at) from public.moods m where m.user_id = u.id) as last_mood_at,
  e.premium_until
from auth.users u
left join public.entitlements e on e.user_id = u.id;
```

The app already reads `entitlements` on launch: if `premium_until` is in the future, the
user gets premium automatically, and it expires on its own. (Wired in MainActivity.)

## Grant premium to a user (for a period)
Find the user's id in **Authentication -> Users** (or the `user_progress` view), then run:
```sql
-- 30 days of premium
insert into public.entitlements (user_id, premium_until)
values ('USER-UUID-HERE', now() + interval '30 days')
on conflict (user_id) do update
  set premium_until = excluded.premium_until, updated_at = now();
```
Change `30 days` to `7 days`, `1 year`, etc. To **revoke**: set `premium_until = now()`.
The change takes effect the next time that user opens the app.

## Track user progress (dashboard)
Fastest, free, no build - use the Supabase dashboard:
- **Table Editor** -> `user_progress` view: per-user moods/foods/reflections, last seen, premium status.
- **Authentication -> Users**: signups, last sign-in, total users.
- **SQL Editor** for ad-hoc metrics, e.g. daily active users:
  ```sql
  select date_trunc('day', last_mood_at) d, count(*) users
  from public.user_progress where last_mood_at is not null
  group by 1 order by 1 desc;
  ```

For **behavioural** analytics (retention, interrupt->pause conversion, funnels), wire
PostHog (see ANALYTICS.md) - richer than SQL for engagement questions.

## Note on identifying users
Right now sign-in is **anonymous** - users are UUIDs with no name/email, so the admin
views show IDs, not people. When we add email/Google sign-in, the dashboard becomes
person-friendly (and premium grants can be keyed to an email).

## Optional: a custom admin web dashboard
If you want a branded UI (charts + a "grant premium" button) instead of the Supabase
console, we can build a small web app (e.g. Next.js) using the Supabase service key.
That is a separate build - the SQL + Supabase console above cover the same needs for now.

# Admin (no deployment) - use the Supabase dashboard

> **Note (v0.3.0):** the app no longer has a premium tier - all features are free - so the
> `entitlements` table and grant/revoke functions below are **no longer used by the app**.
> They're kept only if you ever reintroduce paid features. For plain user-activity viewing,
> you only need the `user_progress` view.

Forget the local HTML file. The easiest admin, with nothing to host, is **Supabase's own
dashboard** (supabase.com → your project). Run the one-time SQL below and you get:
- a **`premium_users`** table view (open it to see who has premium),
- a **`user_progress`** view (per-user activity),
- **one-line functions** to grant/revoke premium.

## Step 1 - one-time setup (paste in Supabase → SQL Editor → Run)

> Run the **whole block top-to-bottom in one go**. Statement 1 creates the `entitlements`
> table; everything after references it, so a partial paste fails with
> `relation "public.entitlements" does not exist`. The `user_progress` view also needs the
> `moods` / `foods` / `reflections` tables from `docs/SUPABASE.md` - if you only want premium
> management, you can skip that view and run the rest.

```sql
-- Entitlements: server-controlled premium (app reads this on launch)
create table if not exists public.entitlements (
  user_id uuid primary key references auth.users(id) on delete cascade,
  premium_until timestamptz,
  updated_at timestamptz not null default now()
);
alter table public.entitlements enable row level security;
drop policy if exists "read own entitlement" on public.entitlements;
create policy "read own entitlement" on public.entitlements
  for select using (auth.uid() = user_id);

-- Who has premium (open this in Table Editor to see the list)
create or replace view public.premium_users as
  select e.user_id, u.email, e.premium_until, (e.premium_until > now()) as active
  from public.entitlements e
  left join auth.users u on u.id = e.user_id
  order by e.premium_until desc nulls last;

-- Per-user activity
create or replace view public.user_progress as
  select u.id as user_id, u.email, u.created_at as joined, u.last_sign_in_at as last_seen,
    (select count(*) from public.moods m       where m.user_id = u.id) as moods,
    (select count(*) from public.foods f       where f.user_id = u.id) as foods,
    (select count(*) from public.reflections r where r.user_id = u.id) as reflections,
    e.premium_until
  from auth.users u
  left join public.entitlements e on e.user_id = u.id;

-- One-line grant by user id
create or replace function public.grant_premium(uid uuid, days int)
returns void language sql security definer as $$
  insert into public.entitlements (user_id, premium_until)
  values (uid, now() + make_interval(days => days))
  on conflict (user_id) do update set premium_until = excluded.premium_until, updated_at = now();
$$;

-- One-line grant by email (works once a user signs in with email)
create or replace function public.grant_premium_email(user_email text, days int)
returns void language sql security definer as $$
  insert into public.entitlements (user_id, premium_until)
  select id, now() + make_interval(days => days) from auth.users where email = user_email
  on conflict (user_id) do update set premium_until = excluded.premium_until, updated_at = now();
$$;

-- IMPORTANT security: only you (dashboard / service role) may call these, never the app's users
revoke execute on function public.grant_premium(uuid, int) from public, anon, authenticated;
revoke execute on function public.grant_premium_email(text, int) from public, anon, authenticated;
```

## Step 2 - day-to-day (all in the Supabase dashboard, no deployment)

**See who has premium** - Table Editor → open **`premium_users`**. (`active = true` means live.)
Or SQL Editor:
```sql
select * from public.premium_users where active;
```

**See user activity** - Table Editor → open **`user_progress`**.

**Grant premium** (30 days) - SQL Editor, one line:
```sql
select public.grant_premium('USER-UUID-HERE', 30);
-- or, once they sign in with email:
select public.grant_premium_email('user@example.com', 30);
```
Find the UUID/email under **Authentication → Users**.

**Revoke** - grant 0 days (expires immediately):
```sql
select public.grant_premium('USER-UUID-HERE', 0);
```

The app reads this on next launch (`isPremiumFromServer`) and unlocks/locks premium automatically.

## Why this beats the HTML file
- Nothing to host or open locally; you just log into Supabase.
- No pasting the service_role key into a browser (safer).
- Views render as clean tables; granting is a single line.

(The old `admin/dashboard.html` still exists if you ever want a branded UI, but this is the easy path.)

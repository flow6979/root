-- Root leaderboard - Phase A schema + RPCs.
-- Run this once in the Supabase SQL editor (Dashboard -> SQL -> New query -> Run).
-- Safe to re-run: everything is idempotent (create if not exists / create or replace).

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

-- Public display name chosen by the user. One per account.
create table if not exists public.profiles (
    user_id    uuid primary key references auth.users(id) on delete cascade,
    username   text unique not null,
    created_at timestamptz default now()
);

-- One row per user per ISO week. effort_points is the league currency; growth_delta
-- is the change in wellbeing average vs the previous week (used for "most improved").
create table if not exists public.weekly_scores (
    user_id       uuid references auth.users(id) on delete cascade,
    week_start    date not null,
    effort_points int not null default 0,
    wellbeing_avg numeric,
    growth_delta  numeric default 0,
    updated_at    timestamptz default now(),
    primary key (user_id, week_start)
);

-- ---------------------------------------------------------------------------
-- Row-level security: users touch only their own rows. Cross-user reads for the
-- board go through SECURITY DEFINER functions below (which expose no private data).
-- ---------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.weekly_scores enable row level security;

drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles for select to authenticated using (true);
drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own on public.profiles for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles for update to authenticated using (auth.uid() = user_id);

drop policy if exists ws_select_own on public.weekly_scores;
create policy ws_select_own on public.weekly_scores for select to authenticated using (auth.uid() = user_id);
drop policy if exists ws_insert_own on public.weekly_scores;
create policy ws_insert_own on public.weekly_scores for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists ws_update_own on public.weekly_scores;
create policy ws_update_own on public.weekly_scores for update to authenticated using (auth.uid() = user_id);

-- ---------------------------------------------------------------------------
-- RPCs
-- ---------------------------------------------------------------------------

-- Monotonic upsert of this week's score. effort_points only ever goes up (guards
-- against a stale client lowering it); growth_delta is derived from last week.
create or replace function public.submit_score(p_week date, p_effort int, p_wellbeing numeric)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    prev numeric;
begin
    select wellbeing_avg into prev
      from public.weekly_scores
     where user_id = auth.uid() and week_start = p_week - 7;

    insert into public.weekly_scores (user_id, week_start, effort_points, wellbeing_avg, growth_delta, updated_at)
    values (auth.uid(), p_week, greatest(p_effort, 0), p_wellbeing,
            coalesce(p_wellbeing, 0) - coalesce(prev, p_wellbeing), now())
    on conflict (user_id, week_start) do update
        set effort_points = greatest(public.weekly_scores.effort_points, excluded.effort_points),
            wellbeing_avg = excluded.wellbeing_avg,
            growth_delta  = excluded.growth_delta,
            updated_at    = now();
end;
$$;

-- My rank, the field size, and my effort + growth percentiles for the week.
create or replace function public.get_my_standing(p_week date)
returns table (my_rank int, players int, effort_percentile int, my_points int, growth_percentile int)
language sql
security definer
set search_path = public
as $$
    with w as (
        select user_id,
               effort_points,
               growth_delta,
               percent_rank() over (order by effort_points) as epr,
               percent_rank() over (order by growth_delta)  as gpr,
               rank()        over (order by effort_points desc) as rnk
          from public.weekly_scores
         where week_start = p_week
    )
    select rnk::int,
           (select count(*) from w)::int,
           round(epr * 100)::int,
           effort_points,
           round(gpr * 100)::int
      from w
     where user_id = auth.uid();
$$;

-- Top players for the week (username + points + rank). Flags the caller's own row.
create or replace function public.get_leaderboard(p_week date, p_limit int default 50)
returns table (username text, effort_points int, rnk int, is_me boolean)
language sql
security definer
set search_path = public
as $$
    select coalesce(p.username, 'anon'),
           s.effort_points,
           rank() over (order by s.effort_points desc)::int,
           (s.user_id = auth.uid())
      from public.weekly_scores s
      left join public.profiles p on p.user_id = s.user_id
     where s.week_start = p_week
     order by s.effort_points desc
     limit p_limit;
$$;

grant execute on function public.submit_score(date, int, numeric)   to authenticated;
grant execute on function public.get_my_standing(date)              to authenticated;
grant execute on function public.get_leaderboard(date, int)         to authenticated;

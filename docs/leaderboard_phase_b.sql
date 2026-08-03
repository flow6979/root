-- Root leaderboard - Phase B: weekly leagues, tiers, promotion/relegation.
-- Run AFTER docs/leaderboard.sql, once, in the Supabase SQL editor.
-- Safe to re-run (idempotent). No cron or external job needed: the weekly rollover
-- (promotion/relegation + regrouping) runs lazily, once, the first time anyone touches
-- the league in a new week, guarded by a run-once lock.
--
-- Tiers: 0 Ember, 1 Dawn, 2 Sky, 3 Aurora, 4 Zenith. Each week, within a tier, players
-- are grouped into leagues of up to 25. Top 5 promote, bottom 5 relegate.

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

-- A player's current tier, carried across weeks.
create table if not exists public.user_league (
    user_id    uuid primary key references auth.users(id) on delete cascade,
    tier       int not null default 0,
    updated_at timestamptz default now()
);

-- One division for a given week + tier.
create table if not exists public.leagues (
    id         bigserial primary key,
    week_start date not null,
    tier       int not null,
    created_at timestamptz default now()
);

-- Fixed weekly membership: who is in which division this week.
create table if not exists public.league_assignments (
    week_start date not null,
    user_id    uuid references auth.users(id) on delete cascade,
    league_id  bigint not null references public.leagues(id) on delete cascade,
    tier       int not null,
    primary key (week_start, user_id)
);
create index if not exists league_members_idx on public.league_assignments (week_start, league_id);

-- Marks that a week's rollover has been processed (run-once lock).
create table if not exists public.rollover_log (
    week_start date primary key,
    done_at    timestamptz default now()
);

-- Read only via SECURITY DEFINER functions below; deny direct table access.
alter table public.user_league        enable row level security;
alter table public.leagues            enable row level security;
alter table public.league_assignments enable row level security;
alter table public.rollover_log       enable row level security;

-- ---------------------------------------------------------------------------
-- Rollover: promote/relegate based on last week, once per week.
-- ---------------------------------------------------------------------------
create or replace function public.ensure_rollover(p_week date)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_count int;
begin
    -- Claim the week; if another call already claimed it, do nothing.
    insert into public.rollover_log (week_start) values (p_week) on conflict do nothing;
    get diagnostics v_count = row_count;
    if v_count = 0 then
        return;
    end if;

    -- Rank last week's members within each league; promote top 5, relegate bottom 5.
    with prev as (
        select a.user_id,
               a.tier,
               row_number() over (partition by a.league_id order by coalesce(s.effort_points, 0) desc) as rnk,
               count(*)     over (partition by a.league_id)                                            as sz
          from public.league_assignments a
          left join public.weekly_scores s
                 on s.user_id = a.user_id and s.week_start = p_week - 7
         where a.week_start = p_week - 7
    )
    update public.user_league u
       set tier = greatest(0, least(4,
               case
                   when prev.rnk <= 5            and prev.tier < 4 then prev.tier + 1
                   when prev.rnk >  prev.sz - 5  and prev.tier > 0 then prev.tier - 1
                   else prev.tier
               end)),
           updated_at = now()
      from prev
     where prev.user_id = u.user_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Membership: make sure the caller is in a league this week (forms groups of 25).
-- ---------------------------------------------------------------------------
create or replace function public.ensure_membership(p_user uuid, p_week date)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    t   int;
    lid bigint;
begin
    perform public.ensure_rollover(p_week);
    insert into public.user_league (user_id) values (p_user) on conflict do nothing;

    if exists (select 1 from public.league_assignments where week_start = p_week and user_id = p_user) then
        return;
    end if;

    select tier into t from public.user_league where user_id = p_user;

    select l.id into lid
      from public.leagues l
     where l.week_start = p_week and l.tier = t
       and (select count(*) from public.league_assignments a where a.league_id = l.id) < 25
     order by l.id
     limit 1;

    if lid is null then
        insert into public.leagues (week_start, tier) values (p_week, t) returning id into lid;
    end if;

    insert into public.league_assignments (week_start, user_id, league_id, tier)
    values (p_week, p_user, lid, t)
    on conflict do nothing;
end;
$$;

-- ---------------------------------------------------------------------------
-- submit_score: same as Phase A, plus it ensures league membership.
-- ---------------------------------------------------------------------------
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

    perform public.ensure_membership(auth.uid(), p_week);
end;
$$;

-- ---------------------------------------------------------------------------
-- My division's board, with tier + size so the app can draw promote/relegate zones.
-- ---------------------------------------------------------------------------
create or replace function public.get_my_division(p_week date)
returns table (username text, effort_points int, rnk int, is_me boolean, tier int, league_size int)
language plpgsql
security definer
set search_path = public
as $$
begin
    perform public.ensure_membership(auth.uid(), p_week);

    return query
    with mine as (
        select league_id from public.league_assignments
         where week_start = p_week and user_id = auth.uid()
    )
    select coalesce(p.username, 'anon'),
           coalesce(s.effort_points, 0),
           rank() over (order by coalesce(s.effort_points, 0) desc)::int,
           (a.user_id = auth.uid()),
           a.tier,
           count(*) over ()::int
      from public.league_assignments a
      join mine m on m.league_id = a.league_id
      left join public.weekly_scores s on s.user_id = a.user_id and s.week_start = p_week
      left join public.profiles p on p.user_id = a.user_id
     where a.week_start = p_week
     order by coalesce(s.effort_points, 0) desc;
end;
$$;

grant execute on function public.submit_score(date, int, numeric) to authenticated;
grant execute on function public.get_my_division(date)            to authenticated;

# Admin analytics - how you (the owner) track app usage

Root itself must never feel surveilled, but as the owner you need aggregate,
privacy-respecting product analytics to find product-market fit. Three layers:

## 1. Product analytics (behaviour) - PostHog (free tier) or Firebase Analytics (free)
Instrument events in the app and view dashboards in a web console. Recommended: **PostHog**
(generous free tier, self-serve funnels/retention, EU hosting option for privacy).

Key events to track for Root:
- `app_open`, `screen_view` (which tab)
- `reflection_started`, `reflection_message_sent`, `reflection_completed`
- `interrupt_shown`, `interrupt_paused`, `interrupt_opened_anyway`  <- the core signal
- `story_read`, `stories_finished_for_day`
- `permission_granted` (usage / overlay), `protection_enabled`
- `premium_viewed`, `trial_started`, `subscribed`

The metrics that actually decide PMF:
- **Retention D1 / D7 / D30** (do people come back? this is THE signal)
- **Interrupt -> pause conversion** (are we actually changing the moment?)
- **Reflection sessions per active user per week**
- **Screen-time trend for retained users** (are we helping?)

## 2. Crash / stability - Firebase Crashlytics or Sentry (free tiers)
Auto-captures crashes + ANRs with stack traces. Non-negotiable before public launch.

## 3. Store + revenue - Google Play Console (built in, free)
Once published, Play Console gives you: installs / uninstalls, active devices, ratings &
reviews, crash/ANR rates, country + device breakdowns, and (for paid) revenue + subscriber
counts. For richer subscription analytics add **RevenueCat** (free under a revenue threshold).

## 4. Your own data - Supabase dashboard
When we add Supabase, you get SQL access to user counts, signups over time, and can build
custom queries/dashboards on top of the app's own data.

## Privacy guardrails (important for THIS app)
- Analytics events must be **aggregate + non-content**: track that a reflection happened,
  never the words. Never send reflection text or logged mood content to analytics.
- Declare analytics honestly in the Play Data Safety form + privacy policy.
- Prefer anonymous / pseudonymous IDs; offer an opt-out.

## Suggested first setup (all free)
PostHog (product) + Firebase Crashlytics (stability) + Play Console (store). Add later:
RevenueCat (subscriptions), Supabase dashboards (own data).

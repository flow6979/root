# Crash reporting + product analytics (optional, off by default)

Root wires **Sentry** (crashes) and **PostHog** (aggregate product events). Both stay OFF until you
add keys to `local.properties`, then rebuild. These are **client keys** designed to ship in the app
(not the paid/privileged kind), so they're kept in every build.

## Sentry (crash reporting)
1. sentry.io -> create an **Android** project -> copy the **DSN**.
2. `local.properties`: `SENTRY_DSN=https://<key>@<org>.ingest.sentry.io/<id>`
3. Rebuild. Crashes now report automatically. (Sentry's auto-init ContentProvider is disabled in
   the manifest; `RootApp` inits it only when the DSN is present, so a missing DSN never crashes
   launch.)

## PostHog (product analytics)
1. posthog.com -> your project -> **Project API Key** (`phc_...`).
2. `local.properties`:
   ```
   POSTHOG_KEY=phc_xxx
   POSTHOG_HOST=https://us.i.posthog.com   # or https://eu.i.posthog.com
   ```
3. Rebuild. The canonical events in `analytics/Events.kt` send via a tiny fire-and-forget HTTP
   capture with an **anonymous per-install id**.

Privacy: only aggregate event names + non-content properties are sent - never reflection text or
mood/journal content (see `docs/ANALYTICS.md`). Leave both blank and Root just logs to Logcat.

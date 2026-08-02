# Root AI proxy (Cloudflare Worker)

Holds the paid API keys (Groq, ElevenLabs) **server-side** so the app never ships them.
The published APK only contains this Worker's public URL, not any secret. Free tier:
100,000 requests/day, no credit card, no Docker.

## Deploy (about 2 minutes)

```bash
# 1. Install the CLI (once)
npm install -g wrangler

# 2. Log in (opens a browser; create a free Cloudflare account if needed)
wrangler login

# 3. From this folder, set your keys as Worker secrets (paste each when prompted)
cd proxy
wrangler secret put GROQ_API_KEY
wrangler secret put ELEVENLABS_API_KEY   # optional; skip to use free TTS
wrangler secret put APP_TOKEN            # optional; a random string to gate the proxy

# 4. Deploy
wrangler deploy
```

`wrangler deploy` prints a URL like:

```
https://root-proxy.<your-subdomain>.workers.dev
```

## Point the app at it

Add to the app's `local.properties` (gitignored), then rebuild:

```properties
PROXY_BASE_URL=https://root-proxy.<your-subdomain>.workers.dev
PROXY_APP_TOKEN=the-random-string-you-set-for-APP_TOKEN   # only if you set APP_TOKEN
```

Then a **public** build gets full built-in AI with no keys inside the APK:

```bash
./gradlew assembleRelease -PpublicBuild
```

Now the app calls `PROXY_BASE_URL/...`, the Worker adds the real key, and forwards to
Groq / ElevenLabs. Provider selection stays: a user's own Gemini key (You -> AI) still wins;
otherwise the app uses the proxy.

## Abuse protection
- Set `APP_TOKEN` so only builds carrying the matching header are served (stops casual scraping).
- Set spend caps / rate limits in the Groq and ElevenLabs dashboards (the real backstop).
- Optionally add a Cloudflare rate-limiting rule on the Worker route (free).

## Test it
```bash
curl -X POST https://root-proxy.<your-subdomain>.workers.dev/openai/v1/chat/completions \
  -H "content-type: application/json" \
  -H "x-root-key: <APP_TOKEN if set>" \
  -d '{"model":"llama-3.1-8b-instant","messages":[{"role":"user","content":"say hi"}]}'
```

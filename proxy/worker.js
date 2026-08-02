// Root AI proxy - a Cloudflare Worker that holds the paid API keys server-side so the app
// never ships them. The app calls this Worker's URL (which is public and not a secret); the
// Worker injects the real keys and forwards to Groq / ElevenLabs.
//
// Secrets (set with `wrangler secret put ...`, never in code):
//   GROQ_API_KEY        - required for chat + speech-to-text
//   ELEVENLABS_API_KEY  - required for premium voice (optional; app falls back to free TTS)
//   APP_TOKEN           - optional. If set, requests must send header  x-root-key: <APP_TOKEN>
//
// Routes (all POST):
//   /openai/v1/chat/completions      -> Groq chat (OpenAI-compatible JSON passthrough)
//   /openai/v1/audio/transcriptions  -> Groq Whisper (multipart passthrough)
//   /tts   { "text": "..." }         -> ElevenLabs, returns audio/mpeg

const GROQ_BASE = "https://api.groq.com/openai/v1";
const ELEVEN_VOICE = "EXAVITQu4vr4xnSDxMaL"; // "Sarah"

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/") {
      return new Response("Root AI proxy is running.", { status: 200 });
    }
    if (request.method !== "POST") {
      return new Response("Not found", { status: 404 });
    }

    // Optional lightweight app check (stops casual scraping; provider spend caps are the real backstop).
    if (env.APP_TOKEN && request.headers.get("x-root-key") !== env.APP_TOKEN) {
      return new Response("Forbidden", { status: 403 });
    }

    try {
      if (url.pathname === "/openai/v1/chat/completions") {
        const body = await request.text();
        return forward(`${GROQ_BASE}/chat/completions`, body, {
          Authorization: `Bearer ${env.GROQ_API_KEY}`,
          "Content-Type": "application/json",
        });
      }

      if (url.pathname === "/openai/v1/audio/transcriptions") {
        // Keep the original multipart body + boundary; only swap in the real auth.
        return forward(`${GROQ_BASE}/audio/transcriptions`, request.body, {
          Authorization: `Bearer ${env.GROQ_API_KEY}`,
          "Content-Type": request.headers.get("content-type") || "",
        });
      }

      if (url.pathname === "/tts") {
        if (!env.ELEVENLABS_API_KEY) return new Response("TTS not configured", { status: 501 });
        const { text } = await request.json();
        if (!text) return new Response("Missing text", { status: 400 });
        const r = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${ELEVEN_VOICE}`, {
          method: "POST",
          headers: {
            "xi-api-key": env.ELEVENLABS_API_KEY,
            "Content-Type": "application/json",
            Accept: "audio/mpeg",
          },
          body: JSON.stringify({
            text: String(text).slice(0, 2500),
            model_id: "eleven_multilingual_v2",
          }),
        });
        return new Response(r.body, {
          status: r.status,
          headers: { "Content-Type": "audio/mpeg" },
        });
      }

      return new Response("Not found", { status: 404 });
    } catch (e) {
      return new Response("Proxy error: " + (e && e.message), { status: 502 });
    }
  },
};

async function forward(target, body, headers) {
  const r = await fetch(target, { method: "POST", headers, body });
  return new Response(r.body, {
    status: r.status,
    headers: { "Content-Type": r.headers.get("content-type") || "application/json" },
  });
}

/**
 * Duo Status Bar relay.
 *
 * Two jobs:
 *
 *  1. `POST /` — receive a log upload and forward it to Telegram's `sendDocument`, so the bot token is a
 *     Worker secret and never ships in the APK.
 *  2. `GET /releases` — serve the newest GitHub release (stable or pre-release) as a small normalized
 *     JSON, cached in KV. This exists so devices never hit GitHub's API: at most one GitHub call per
 *     cache window serves every user, which keeps us far under the unauthenticated rate limit.
 *     `GET /download` streams the APK only as a fallback for networks where GitHub's CDN is blocked.
 *
 * Rate limiting on the log upload is enforced by a Durable Object (one instance per caller IP), which is
 * strongly consistent. The release routes are read-only and cheap; the KV cache is what protects GitHub.
 *
 * Required secrets (set with `wrangler secret put`, never in this file):
 *   TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID, RELAY_KEY, TELEGRAM_WEBHOOK_SECRET
 *   (RELAY_KEY is mandatory: log callers must send `X-Duo-Key: <value>`.)
 *   (TELEGRAM_WEBHOOK_SECRET is mandatory: Telegram webhook deliveries must carry it.)
 * Required binding:
 *   RELEASES   - a KV namespace holding the cached release JSON
 *   LIMITER    - the RateLimiter Durable Object (rate limit + ticket counter)
 */

const MAX_BYTES = 5 * 1024 * 1024;
const COOLDOWN_SECONDS = 60;

const REPO = "kvmy666/duoStatusBar";
const GITHUB_RELEASES = `https://api.github.com/repos/${REPO}/releases?per_page=20`;
const RELEASES_KEY = "releases-v1";
// One GitHub fetch per window, shared by every device. GitHub allows 60 requests/hour/IP; 6 h gives a
// huge margin even with several Cloudflare locations.
const CACHE_TTL_SECONDS = 6 * 60 * 60;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/releases") {
      return handleReleases(env);
    }
    if (request.method === "GET" && url.pathname === "/download") {
      return handleDownload(env);
    }
    if (request.method === "GET" && url.pathname === "/setup") {
      return handleSetup(url, env);
    }
    if (request.method === "POST" && url.pathname === "/tg") {
      return handleTelegramUpdate(request, url, env);
    }
    if (request.method !== "POST") return json({ ok: false, error: "method" }, 405);

    return handleUpload(request, env);
  },
};

/** The log upload path (unchanged behaviour). */
async function handleUpload(request, env) {
  if (!env.RELAY_KEY) {
    return json({ ok: false, error: "relay_key_missing" }, 503);
  }
  if (request.headers.get("x-duo-key") !== env.RELAY_KEY) {
    return json({ ok: false, error: "forbidden" }, 403);
  }

  const ip = request.headers.get("cf-connecting-ip") || "unknown";

  // Global limiter first: rotating IPs cannot flood by spreading requests across addresses.
  const globalGate = await env.LIMITER.get(env.LIMITER.idFromName("global"))
    .fetch("https://do/", {
      method: "POST",
      body: JSON.stringify({ cooldownMs: 2000 }),
    });
  if (globalGate.status === 429) return json({ ok: false, error: "rate_limited" }, 429);

  // Reserve the slot first: the Durable Object is single-threaded per instance, so two concurrent
  // requests from the same IP cannot both pass.
  const gate = await env.LIMITER.get(env.LIMITER.idFromName(ip))
    .fetch("https://do/", {
      method: "POST",
      body: JSON.stringify({ cooldownMs: COOLDOWN_SECONDS * 1000 }),
    });
  if (gate.status === 429) return json({ ok: false, error: "rate_limited" }, 429);

  let form;
  try {
    form = await request.formData();
  } catch {
    return json({ ok: false, error: "bad_form" }, 400);
  }

  const document = form.get("document");
  if (!(document instanceof File)) return json({ ok: false, error: "no_document" }, 400);
  if (document.size > MAX_BYTES) return json({ ok: false, error: "too_large" }, 413);

  const caption = String(form.get("caption") || "").slice(0, 200);
  const ticket = await nextTicket(env);
  const cleanCaption = caption.replace(/^Ticket #\S+/, `Ticket #${ticket}`);

  const telegram = new FormData();
  // The ZIP goes to the owner's private chat with the log bot. It is never posted to the public
  // group/channel. The group gets only a ticket card below.
  telegram.append("chat_id", env.TELEGRAM_CHAT_ID);
  const privateCaption = ticketCard(cleanCaption, true, true) || cleanCaption;
  if (privateCaption) telegram.append("caption", privateCaption);
  telegram.append("document", document, document.name || "duo-log.txt");

  const res = await fetch(
    `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendDocument`,
    { method: "POST", body: telegram }
  );
  if (!res.ok) {
    const detail = (await res.text()).slice(0, 200);
    return json({ ok: false, error: "telegram", status: res.status, detail }, 502);
  }

  const card = ticketCard(cleanCaption, false, false);
  if (env.TELEGRAM_GROUP_ID && card) {
    const ticket = ticketId(cleanCaption);
    await sendText(env, env.TELEGRAM_GROUP_ID, env.TELEGRAM_GROUP_THREAD_ID, card, ticket, 'open');
  }

  return json({ ok: true, ticket });
}

/** Sequential ticket number via the RateLimiter Durable Object (strongly consistent, no KV). */
async function nextTicket(env) {
  const res = await env.LIMITER.get(env.LIMITER.idFromName("ticket-seq"))
    .fetch("https://do/", {
      method: "POST",
      body: JSON.stringify({ action: "next" }),
    });
  const n = Number(await res.text());
  return String(n).padStart(6, "0");
}

/** The app's upload caption, reduced to a ticket card. Description stays private unless includeRequest. */
function ticketCard(caption, includeUser = false, includeRequest = false) {
  const match = /^Ticket (#\S+) · \[([^\]]+)\](?: (@[A-Za-z0-9_]+))? · ([\s\S]*)$/.exec(caption || "");
  if (!match) return "";
  const user = includeUser && match[3] ? `\n👤 User: ${match[3]}` : "";
  let card = `🎫 Ticket ${match[1]}\n🏷️ Label: ${match[2]}${user}\n🟢 Status: Open`;
  if (includeRequest) card += `\n📝 Request: ${match[4]}`;
  return card;
}

function ticketId(caption) {
  const match = /^Ticket (#\S+)/.exec(caption || "");
  return match ? match[1] : "";
}

async function sendText(env, chatId, threadId, text, ticket = "", state = "open") {
  const form = new FormData();
  form.append("chat_id", chatId);
  if (threadId) form.append("message_thread_id", threadId);
  form.append("text", text);
  if (ticket) {
    const actions = state === "open"
      ? [[{ text: "Close", callback_data: `close|${ticket}` }, { text: "Not planned", callback_data: `not|${ticket}` }]]
      : state === "closed"
        ? [[{ text: "Open", callback_data: `open|${ticket}` }, { text: "Not planned", callback_data: `not|${ticket}` }]]
        : [[{ text: "Open", callback_data: `open|${ticket}` }, { text: "Close", callback_data: `close|${ticket}` }]];
    form.append("reply_markup", JSON.stringify({ inline_keyboard: actions }));
  }
  const res = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
    method: "POST",
    body: form,
  });
  return res.ok;
}

async function handleSetup(url, env) {
  if (!env.SETUP_KEY || url.searchParams.get("key") !== env.SETUP_KEY) {
    return json({ ok: false, error: "forbidden" }, 403);
  }
  if (!env.TELEGRAM_WEBHOOK_SECRET) {
    return json({ ok: false, error: "webhook_secret_missing" }, 503);
  }
  const target = new URL(url);
  target.pathname = "/tg";
  const res = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/setWebhook`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ url: target.toString(), secret_token: env.TELEGRAM_WEBHOOK_SECRET }),
  });
  return json({ ok: res.ok, detail: (await res.text()).slice(0, 200) }, res.ok ? 200 : 502);
}

async function handleTelegramUpdate(request, url, env) {
  if (!env.TELEGRAM_WEBHOOK_SECRET) {
    return json({ ok: false, error: "webhook_secret_missing" }, 503);
  }
  const provided = request.headers.get("x-telegram-bot-api-secret-token") || "";
  if (!secretEquals(provided, env.TELEGRAM_WEBHOOK_SECRET)) {
    return json({ ok: false, error: "forbidden" }, 403);
  }
  let update;
  try { update = await request.json(); } catch { return json({ ok: false }, 400); }
  const cb = update.callback_query;
  if (!cb) return json({ ok: true });
  const from = cb.from?.id;
  if (!(await isAllowed(env, from))) {
    await answerCallback(env, cb.id, "Not allowed");
    return json({ ok: true });
  }
  const [action, ticket] = String(cb.data || "").split("|");
  const state = action === "not" ? "not" : action === "close" ? "closed" : "open";
  const labels = { open: "🟢 Status: Open", closed: "🔴 Status: Closed", not: "🔘 Status: Not planned" };
  const status = labels[state];
  const thread = state === "open"
    ? env.TELEGRAM_GROUP_THREAD_ID
    : state === "not" ? env.TELEGRAM_NOT_PLANNED_THREAD_ID : env.TELEGRAM_CLOSED_THREAD_ID;
  const message = cb.message;
  if (message && env.TELEGRAM_GROUP_ID && String(message.chat?.id) !== String(env.TELEGRAM_GROUP_ID)) {
    await answerCallback(env, cb.id, "Mismatched chat");
    return json({ ok: false, error: "chat_mismatch" }, 403);
  }
  const original = message?.text || `🎫 Ticket ${ticket}`;
  let text = original.replace(/(?:🟢|🔴|🔘) Status: .*$/m, status);
  if (!text.includes("Status:")) text += `\n${status}`;

  let moved = false;
  if (message && thread) {
    moved = await sendText(env, message.chat.id, thread, text, ticket, state);
  }
  if (message) {
    if (moved) {
      const deleted = await deleteMessage(env, message.chat.id, message.message_id);
      if (!deleted) {
        // Keep exactly one controllable card: strip buttons and annotate the duplicate.
        await editMessage(env, message.chat.id, message.message_id, `${text}\n⚠️ Duplicate: could not delete.`);
      }
    } else {
      await editMessage(env, message.chat.id, message.message_id, text);
    }
  }
  await answerCallback(env, cb.id, moved ? status : "Could not move");
  return json({ ok: true });
}

/** Group admins may control tickets; OWNER_ID remains a fallback for a private group. */
async function isAllowed(env, userId) {
  if (!userId) return false;
  if (env.OWNER_ID && String(userId) === String(env.OWNER_ID)) return true;
  if (!env.TELEGRAM_GROUP_ID) return false;
  try {
    const res = await fetch(
      `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/getChatMember?chat_id=${env.TELEGRAM_GROUP_ID}&user_id=${userId}`
    );
    const data = await res.json();
    const status = data?.result?.status;
    return status === "creator" || status === "administrator";
  } catch {
    return false;
  }
}

async function answerCallback(env, id, text) {
  const form = new FormData();
  form.append("callback_query_id", id);
  form.append("text", text);
  await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/answerCallbackQuery`, {
    method: "POST", body: form,
  });
}

async function deleteMessage(env, chatId, messageId) {
  const form = new FormData();
  form.append("chat_id", chatId);
  form.append("message_id", messageId);
  const res = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/deleteMessage`, {
    method: "POST", body: form,
  });
  return res.ok;
}

async function editMessage(env, chatId, messageId, text) {
  const form = new FormData();
  form.append("chat_id", chatId);
  form.append("message_id", messageId);
  form.append("text", text);
  form.append("reply_markup", JSON.stringify({ inline_keyboard: [] }));
  await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/editMessageText`, {
    method: "POST", body: form,
  });
}

/** Serves the normalized newest release, refreshing the KV cache when it is stale. */
async function handleReleases(env) {
  const cached = await env.RELEASES.get(RELEASES_KEY, "json");
  if (cached && Date.now() - (cached.fetchedAt || 0) < CACHE_TTL_SECONDS * 1000) {
    return json(cached, 200, CACHE_TTL_SECONDS);
  }

  let payload;
  try {
    const res = await fetch(GITHUB_RELEASES, {
      headers: {
        Accept: "application/vnd.github+json",
        "User-Agent": "duoStatusBar-relay",
      },
    });
    if (!res.ok) throw new Error(`github ${res.status}`);
    const list = await res.json();
    const releases = list.filter((r) => !r.draft).map(normalize);
    const latest = releases.reduce((best, r) => (compareVersions(r.version, best?.version) > 0 ? r : best), null);
    payload = { ok: true, fetchedAt: Date.now(), latest, releases };
    await env.RELEASES.put(RELEASES_KEY, JSON.stringify(payload), { expirationTtl: CACHE_TTL_SECONDS });
  } catch (e) {
    // A stale cache is still better than nothing; only fail when there is no cache at all.
    if (cached) return json(cached, 200, 60);
    return json({ ok: false, error: "github", detail: String(e).slice(0, 120) }, 502);
  }
  return json(payload, 200, CACHE_TTL_SECONDS);
}

/** Streams the newest APK from GitHub. Used only when a device cannot reach GitHub's CDN directly. */
async function handleDownload(env) {
  const cached = await env.RELEASES.get(RELEASES_KEY, "json");
  const apkUrl = cached?.latest?.apkUrl;
  if (!apkUrl || !/^https:\/\/github\.com\//.test(apkUrl)) {
    return json({ ok: false, error: "no_apk" }, 404);
  }
  const upstream = await fetch(apkUrl, { redirect: "follow" });
  if (!upstream.ok || !upstream.body) {
    return json({ ok: false, error: "upstream", status: upstream.status }, 502);
  }
  return new Response(upstream.body, {
    status: 200,
    headers: {
      "content-type": "application/vnd.android.package-archive",
      "cache-control": "public, max-age=3600",
    },
  });
}

/** GitHub release -> the compact shape the app reads. */
function normalize(r) {
  const apk = (r.assets || []).find((a) => (a.name || "").toLowerCase().endsWith(".apk"));
  return {
    version: parseVersion(r.name || r.tag_name || ""),
    tag: r.tag_name || "",
    prerelease: !!r.prerelease,
    pageUrl: r.html_url || `https://github.com/${REPO}/releases`,
    apkUrl: apk?.browser_download_url || "",
    sha256: digestHex(apk?.digest),
    publishedAt: r.published_at || "",
  };
}

/** `sha256:abc…` -> `abc…`, or "" when absent. */
function digestHex(digest) {
  const m = /^sha256:([0-9a-f]{64})$/i.exec(digest || "");
  return m ? m[1].toLowerCase() : "";
}

/** The `x.y.z` (and optional `-pre`) in a tag/name, or "" when there is none. */
function parseVersion(raw) {
  const m = /(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.]+))?/.exec(raw || "");
  return m ? m[0] : "";
}

function parseSemver(raw) {
  const m = /(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.]+))?/.exec(raw || "");
  if (!m) return null;
  return { nums: [Number(m[1]), Number(m[2]), Number(m[3] || 0)], pre: m[4] || "" };
}

/** True when [a] is a strictly higher version than [b]; pre-release aware. */
function compareVersions(a, b) {
  const x = parseSemver(a);
  const y = parseSemver(b);
  if (!x) return y ? -1 : 0;
  if (!y) return 1;
  for (let i = 0; i < 3; i++) {
    if (x.nums[i] !== y.nums[i]) return x.nums[i] - y.nums[i];
  }
  // Same x.y.z: a release beats any pre-release of it.
  if (!x.pre && y.pre) return 1;
  if (x.pre && !y.pre) return -1;
  return comparePre(x.pre, y.pre);
}

/** Semver pre-release ordering: numeric identifiers sort before alphanumeric, absent is lower. */
function comparePre(a, b) {
  const xs = a.split(".");
  const ys = b.split(".");
  for (let i = 0; i < Math.max(xs.length, ys.length); i++) {
    const x = xs[i];
    const y = ys[i];
    if (x === undefined) return -1;
    if (y === undefined) return 1;
    const xn = /^\d+$/.test(x);
    const yn = /^\d+$/.test(y);
    if (xn && yn) {
      if (Number(x) !== Number(y)) return Number(x) - Number(y);
    } else if (xn) {
      return -1;
    } else if (yn) {
      return 1;
    } else if (x !== y) {
      return x < y ? -1 : 1;
    }
  }
  return 0;
}

/**
 * One instance per caller IP, holding the last accepted send time. Returns 429 while the caller is inside
 * the cooldown, 200 otherwise.
 */
export class RateLimiter {
  constructor(state) {
    this.state = state;
  }

  async fetch(request) {
    let body = {};
    try { body = await request.json(); } catch { body = {}; }
    if (body && body.action === "next") {
      const current = Number(await this.state.storage.get("ticket")) || 0;
      const next = current + 1;
      await this.state.storage.put("ticket", next);
      return new Response(JSON.stringify(next), { status: 200, headers: { "content-type": "application/json" } });
    }
    const cooldownMs = Number(body?.cooldownMs) || 0;
    const now = Date.now();
    const last = (await this.state.storage.get("last")) || 0;
    if (cooldownMs > 0 && now - last < cooldownMs) return new Response("", { status: 429 });
    await this.state.storage.put("last", now);
    return new Response("", { status: 200 });
  }
}

function secretEquals(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function json(body, status = 200, maxAge = 0) {
  const headers = { "content-type": "application/json" };
  if (maxAge > 0) headers["cache-control"] = `public, max-age=${maxAge}`;
  return new Response(JSON.stringify(body), { status, headers });
}

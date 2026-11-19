# Duo relay (Cloudflare Worker)

`relay/src/index.js` is the whole Worker (`duo-log-relay`); `relay/wrangler.toml` declares its
bindings. It has three jobs:

1. **Log upload** (`POST /`) — forwards a bug report to Telegram, so the bot token is a Worker secret
   and never ships in the APK.
2. **Release feed** (`GET /releases`, `GET /download`) — serves the newest GitHub release (stable
   **or** pre-release) as a small normalized JSON, cached in KV. Devices read this instead of GitHub's
   API, so at most one GitHub call per cache window serves every user and we stay far under GitHub's
   unauthenticated rate limit. `GET /download` streams the APK only as a fallback for networks where
   GitHub's CDN is blocked.
3. **Ticket control** (`GET /setup`, `POST /tg`) — one-time Telegram webhook registration and the
   webhook that moves ticket cards between group topics.

There is no server to maintain — Cloudflare runs it on the free tier.

## Routes

| Route | Method | Purpose |
|---|---|---|
| `/releases` | GET | Normalized newest release, refreshed from GitHub at most once per 6 h cache window. |
| `/download` | GET | Streams `latest.apkUrl` from GitHub as `application/vnd.android.package-archive` (fallback path only). |
| `/setup` | GET | Registers the Telegram webhook; requires `?key=<SETUP_KEY>`. |
| `/tg` | POST | Telegram webhook: validates the secret token and handles ticket status buttons. |
| `/` | POST | Log upload; requires the shared `RELAY_KEY` key. |

### `POST /` — upload

- `multipart/form-data`: `document` (the log file; required) and optional `caption`.
- **Mandatory key:** the request must carry `X-Duo-Key: <RELAY_KEY>`; a missing or wrong value returns
  `403 forbidden`, and an unset `RELAY_KEY` returns `503 relay_key_missing`.
- Rate limiting: a global gate of **2 s** first (rotating IPs cannot flood), then a **60 s per-IP
  cooldown**. Both are enforced by the `LIMITER` Durable Object; a rejected request returns
  `{"ok":false,"error":"rate_limited"}` (HTTP 429).
- File cap: **5 MB** (`413 too_large`).
- A sequential **6-digit ticket number** is allocated by the `ticket-seq` Durable Object instance
  (`000001`, `000002`, …) and substituted into the caption.
- The document goes to the owner's private chat (`TELEGRAM_CHAT_ID`) only. If `TELEGRAM_GROUP_ID` is
  set and the caption parses as an app ticket, a status card is posted to that group (and
  `TELEGRAM_GROUP_THREAD_ID` for the Open topic). Nothing else is stored.

### `GET /releases` and `GET /download`

- `GET /releases` returns `{ ok, fetchedAt, latest, releases }`; each release is
  `{ version, tag, prerelease, pageUrl, apkUrl, sha256, publishedAt }`. `latest` is the highest
  version, including pre-releases. The payload is cached in `RELEASES` for 6 h, then served with
  `Cache-Control: max-age=21600`. A stale cache is served if GitHub fails.
- `GET /download` streams `latest.apkUrl` only when it is an `https://github.com/` URL; otherwise it
  returns `404 no_apk`. Used by the app only when a direct GitHub download fails.

### `GET /setup`

Registers the Telegram webhook at this Worker's `/tg` path. Requires the query parameter
`?key=<SETUP_KEY>` (`403` otherwise) and the `TELEGRAM_WEBHOOK_SECRET` secret (`503` if unset). It
calls `setWebhook` with that secret as `secret_token`, so the webhook can reject forged deliveries.

### `POST /tg` — Telegram webhook

- Telegram must send `X-Telegram-Bot-Api-Secret-Token` equal to `TELEGRAM_WEBHOOK_SECRET`; a mismatch
  returns `403 forbidden`.
- Handles `callback_query` buttons with data `open|<ticket>`, `close|<ticket>` and `not|<ticket>`
  (open / closed / not planned), updating the card's status line and its buttons.
- Moves the ticket card between `TELEGRAM_GROUP_THREAD_ID` (open), `TELEGRAM_CLOSED_THREAD_ID`
  (closed) and `TELEGRAM_NOT_PLANNED_THREAD_ID` (not planned). The moved card is posted to the target
  topic and the original is deleted; if deletion fails the original is edited to a duplicate with
  its buttons removed. If the target thread is unset, the card is edited in place.
- Authorisation: the acting user must be `OWNER_ID`, or a creator/administrator of
  `TELEGRAM_GROUP_ID` (checked with `getChatMember`). Others get "Not allowed".

## Bindings (`wrangler.toml`)

- `RELEASES` — KV namespace holding the cached release JSON (key `releases-v1`, 6 h TTL).
- `LIMITER` — Durable Object, class `RateLimiter` (SQLite-backed, migration tag `v1`). It enforces the
  global/per-IP upload cooldowns and holds the sequential ticket counter.

Create the KV namespace once with `npx wrangler kv namespace create RELEASES` and paste the printed id
into `wrangler.toml`.

## Secrets (names only)

Set these with `wrangler secret put`; none is stored in the repository or the APK.

```
TELEGRAM_BOT_TOKEN
TELEGRAM_CHAT_ID
RELAY_KEY
TELEGRAM_WEBHOOK_SECRET
TELEGRAM_GROUP_ID
TELEGRAM_GROUP_THREAD_ID
TELEGRAM_CLOSED_THREAD_ID
TELEGRAM_NOT_PLANNED_THREAD_ID
OWNER_ID
SETUP_KEY
```

`TELEGRAM_CHAT_ID` is the owner's private chat (log document only). `TELEGRAM_GROUP_ID` is the ticket
group; the three thread ids are its Open / Closed / Not planned topics. `RELAY_KEY` is mandatory for
uploads; `TELEGRAM_WEBHOOK_SECRET` is mandatory for `/setup` and `/tg`; `SETUP_KEY` gates `/setup`;
`OWNER_ID` is a fallback for a private group.

## Deploy (one time, ~2 minutes)

```bash
cd relay
npx wrangler login
npx wrangler kv namespace create RELEASES     # paste the printed id into wrangler.toml
npx wrangler secret put TELEGRAM_BOT_TOKEN    # the @BotFather token
npx wrangler secret put TELEGRAM_CHAT_ID
npx wrangler secret put RELAY_KEY
npx wrangler secret put TELEGRAM_WEBHOOK_SECRET
npx wrangler secret put TELEGRAM_GROUP_ID
npx wrangler secret put TELEGRAM_GROUP_THREAD_ID
npx wrangler secret put TELEGRAM_CLOSED_THREAD_ID
npx wrangler secret put TELEGRAM_NOT_PLANNED_THREAD_ID
npx wrangler secret put OWNER_ID
npx wrangler secret put SETUP_KEY
npx wrangler deploy
```

`wrangler deploy` prints the URL, e.g. `https://duo-log-relay.<your-subdomain>.workers.dev`.

## Point the app at it

Add the URL to the git-ignored `.env` at the repo root, then rebuild:

```
TELEGRAM_RELAY_URL=https://duo-log-relay.<your-subdomain>.workers.dev
```

When `TELEGRAM_RELAY_URL` is set the app uses the relay for logs (and for update checks), and the bot
token can be removed from `.env` entirely, so no secret is compiled into the APK. Leave
`TELEGRAM_BOT_TOKEN` empty for public builds.

## Test without the app

```bash
curl -H "X-Duo-Key: $RELAY_KEY" -F "caption=hello" \
  -F "document=@README.md;type=text/plain" https://duo-log-relay.<sub>.workers.dev
curl https://duo-log-relay.<sub>.workers.dev/releases
```

A second log POST inside the global 2 s gate (or the same IP's 60 s cooldown) returns
`{"ok":false,"error":"rate_limited"}` (HTTP 429).

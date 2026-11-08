# BLACKOUT

**Android Telephony Research Framework**

A native Android client built with Kotlin and Jetpack Compose. Runs fully offline by default, with an optional Cloudflare Workers backend for subscription and credit management.

---

## Operating Modes

BLACKOUT supports two mutually exclusive modes, controlled by the `SUBSCRIPTION_ENABLED` flag.

| | Offline (default) | Subscription |
|---|---|---|
| `SUBSCRIPTION_ENABLED` | `false` | `true` |
| Backend required | No | Yes |
| Network calls | None | HTTPS to Cloudflare Worker |
| Credits required | No | Yes |
| Admin panel | Hidden | Available |
| NOWPayments | Inactive | Active |
| Telephony, history, language, panic wipe | Full | Full |

The flag must be set consistently on **both** sides:

- `android/local.properties` → injected into `BuildConfig`
- `backend/wrangler.local.jsonc` → exposed as `env.SUBSCRIPTION_ENABLED`

---

## Requirements

**Android (always)**

- Android Studio with Gradle 9.5+ support
- JDK 17+
- Android SDK Platform 35

**Backend (subscription mode only)**

- Node.js 20+, npm
- Cloudflare account (Workers + D1)
- NOWPayments API key and IPN secret

---

## Quick Start — Offline Mode

1. Clone the repository and open `android/` in Android Studio.

2. Create `android/local.properties` from the template:

```
cp local.properties.example local.properties
```

3. Set the SDK path (escape every backslash on Windows):

```
sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk
SUBSCRIPTION_ENABLED=false
BACKEND_URL=https://configure-me.workers.dev
```

4. Build and run. The app works fully offline; no backend is needed.

---

## Enabling Subscription Mode

Update **both** sides and rebuild/redeploy.

**Backend** — `backend/wrangler.local.jsonc`:

```jsonc
"vars": { "SUBSCRIPTION_ENABLED": "true" }
```

**Android** — `android/local.properties`:

```
SUBSCRIPTION_ENABLED=true
BACKEND_URL=https://your-worker.workers.dev
```

Rebuild the app and redeploy the worker. The home screen will switch from `OFFLINE MODE` to `Credits: N`, and the admin panel becomes accessible via 5 taps on the Device ID in Settings.

---

## Backend Setup

Skip this section if you are running in offline mode.

```powershell
cd backend
npm install
npx wrangler login
npx wrangler d1 create blackout-db
```

Then:

```powershell
cp .dev.vars.example .dev.vars
cp wrangler.local.jsonc.example wrangler.local.jsonc
```

Configure:

- `wrangler.local.jsonc` — set your D1 `database_id` and `SUBSCRIPTION_ENABLED`
- `.dev.vars` — set `NOWPAYMENTS_API_KEY`, `NOWPAYMENTS_IPN_SECRET`, `ADMIN_SECRET`

Generate types, initialize the database, upload production secrets, and deploy:

```powershell
npm run types
npm run db:init
npx wrangler secret put NOWPAYMENTS_API_KEY --config wrangler.local.jsonc
npx wrangler secret put NOWPAYMENTS_IPN_SECRET --config wrangler.local.jsonc
npx wrangler secret put ADMIN_SECRET --config wrangler.local.jsonc
npm run deploy
```

Wrangler prints the deployed Worker URL — use it as `BACKEND_URL` on the Android side.

---

## API Reference

All endpoints are served by the Cloudflare Worker. None are contacted when `SUBSCRIPTION_ENABLED=false`.

**Public**

| Method | Path | Description |
|---|---|---|
| GET | `/api/get-plans` | Active credit plans |
| POST | `/api/create-payment` | Create a NOWPayments invoice |
| GET | `/api/get-credits?device_id=X` | Device credits and ban status |
| POST | `/api/consume-credits` | Consume credits |
| POST | `/api/nowpayments-webhook` | Payment notification (HMAC-SHA512 verified) |

**Admin** — require `Authorization: Bearer <ADMIN_SECRET>`

| Method | Path | Description |
|---|---|---|
| GET | `/api/admin/stats` | Usage, user, and revenue statistics |
| GET | `/api/admin/users` | List registered devices |
| GET | `/api/admin/transactions` | Recent transactions |
| POST | `/api/admin/user-action` | Ban, unban, delete, set credits |
| GET | `/api/admin/plans` | All credit plans |
| POST | `/api/admin/plans/update` | Update a credit plan |

---

## Testing

Backend tests run against the Cloudflare Workers test environment with Vitest.

```powershell
cd backend
npm test
```

Tests force `SUBSCRIPTION_ENABLED=true` during setup, so both subscription and offline code paths are exercised explicitly.

---

## Security

- `ADMIN_SECRET`, `NOWPAYMENTS_API_KEY`, and `NOWPAYMENTS_IPN_SECRET` never reach the Android client.
- Webhook notifications are verified with HMAC-SHA512 over a recursively-sorted JSON body before any state changes.
- Secrets and local configuration (`local.properties`, `.dev.vars`, `wrangler.local.jsonc`, keystores) are excluded via `.gitignore`.
- In offline mode, the client makes zero network requests — no fallback, no telemetry.

---

## Project Structure

```
BLACKOUT/
├── android/
│   ├── app/                    # Android application module
│   ├── local.properties.example
│   └── ...
├── backend/                    # Optional. Only for subscription mode.
│   ├── src/index.ts
│   ├── test/
│   ├── schema.sql
│   ├── wrangler.local.jsonc.example
│   └── ...
├── screenshots/
├── LICENSE
└── README.md
```

---

## Contributing

Contributions are welcome. Please ensure:

- Backend tests pass (`npm test` in `backend/`).
- Offline mode remains fully functional without any backend.
- Configuration changes are reflected in both `*.example` templates and this README.
- No secrets or `local.properties` files are committed.

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.

Copyright © 2026 Arashxzo
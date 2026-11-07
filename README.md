# BLACKOUT

**Android telephony research framework — Jetpack Compose client + Cloudflare Workers backend.**

[![Android](https://img.shields.io/badge/Android-API%2029%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Cloudflare Workers](https://img.shields.io/badge/Backend-Cloudflare%20Workers-F38020?logo=cloudflare&logoColor=white)](https://workers.cloudflare.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

BLACKOUT pairs a native Android application with a serverless Cloudflare Worker API for controlled telephony research. The Android client provides a SIM-aware interface for sending data SMS during authorized tests; the backend manages device credits, payment transactions, and administrative operations.

> **Authorized security research and laboratory testing only.** Using this tool against devices you do not own or lack explicit permission to test is illegal. Sending messages can incur carrier charges and affect recipients or services. You are responsible for complying with applicable laws and the scope of your authorization.

## Contents

- [Features](#features)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Backend setup](#backend-setup)
- [Android setup](#android-setup)
- [Testing](#testing)
- [API](#api)
- [Security notes](#security-notes)
- [License](#license)

## Features

**Android client**

- Kotlin + Jetpack Compose UI with English and Persian locales.
- SIM selection, telephony permission handling, and data SMS dispatch via a foreground service.
- Credit balance checks, consumption tracking, and payment plan display.
- Local operation history, settings, panic wipe, and an admin panel.

**Cloudflare Worker backend**

- Cloudflare D1 persistence for licenses, transactions, and plans.
- NOWPayments invoice creation and HMAC-SHA512 verified webhook processing.
- Admin-protected routes for user, plan, and revenue management.
- Full backend test suite powered by Vitest and the Cloudflare Workers test environment.

## Architecture

The Android app calls the Worker over HTTPS for plans, credit operations, and payment invoices. The Worker stores application state in D1 and processes payment notifications after signature verification. The Worker URL is injected into the Android app at build time from `local.properties`.

## Requirements

| Component | Requirement |
|---|---|
| Android build | Android Studio, JDK 17+, Android SDK Platform 35 |
| Android device | Android 10 (API 29) or later with a compatible SIM |
| Backend | Node.js 20+ and npm |
| Cloud deployment | Cloudflare account with Workers and D1 access |
| Payment processing | NOWPayments account with API and IPN credentials |

## Backend setup

Run from the `backend/` directory.

1. Install dependencies:
   `npm install`

2. Log in to Cloudflare:
   `npx wrangler login`

3. Create the D1 database:
   `npx wrangler d1 create blackout-db`

4. Copy the local configuration templates:
   `Copy-Item .dev.vars.example .dev.vars`
   `Copy-Item wrangler.local.jsonc.example wrangler.local.jsonc`
   (On macOS/Linux use `cp`.)

5. Set your D1 database ID in `wrangler.local.jsonc` and fill in the three secrets in `.dev.vars`. Both files are gitignored.

6. Generate Cloudflare Worker types:
   `npm run types`

7. Apply the database schema to D1:
   `npm run db:init`

8. Upload production secrets to Cloudflare:
   `npx wrangler secret put NOWPAYMENTS_API_KEY --config wrangler.local.jsonc`
   `npx wrangler secret put NOWPAYMENTS_IPN_SECRET --config wrangler.local.jsonc`
   `npx wrangler secret put ADMIN_SECRET --config wrangler.local.jsonc`

9. Deploy:
   `npm run deploy`

The Worker URL printed by Wrangler is the value to use as `BACKEND_URL` in the Android configuration.

## Android setup

1. Copy the template:
   `cd ..\android`
   `Copy-Item local.properties.example local.properties`

2. Edit `android/local.properties` and set:
   - `sdk.dir` — path to your local Android SDK.
   - `BACKEND_URL` — your deployed Worker URL, without a trailing slash.

3. Open the `android` directory in Android Studio, let Gradle sync complete, and run the `app` configuration on an Android 10+ device.

`local.properties` is gitignored. If `BACKEND_URL` is missing or still set to the placeholder, the app displays a configuration error instead of starting normally.

To produce a signed release APK, use Android Studio's **Build → Generate Signed Bundle / APK** flow with your own keystore. Keep the keystore and its credentials private.

## Testing

From `backend/`:

`npm test`

The test suite requires `wrangler.local.jsonc` to be present. Copy it from `wrangler.local.jsonc.example` before running tests for the first time.

Watch mode:

`npm run test:watch`

## API

All routes are served by the Worker.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/get-plans` | List active credit plans |
| POST | `/api/create-payment` | Create a NOWPayments invoice |
| GET | `/api/get-credits?device_id=X` | Read a device's credits and ban status |
| POST | `/api/consume-credits` | Consume credits after an operation |
| POST | `/api/nowpayments-webhook` | Receive and verify NOWPayments notifications |
| GET | `/api/admin/stats` | Read user, usage, and revenue totals |
| GET | `/api/admin/users` | List registered devices |
| GET | `/api/admin/transactions` | List recent transactions |
| POST | `/api/admin/user-action` | Ban, unban, delete, or set credits for a device |
| GET | `/api/admin/plans` | List all plans, including inactive |
| POST | `/api/admin/plans/update` | Update a plan |

Admin routes require the configured `ADMIN_SECRET` via the `Authorization: Bearer <secret>` header. The webhook route validates the NOWPayments HMAC-SHA512 signature with recursively sorted keys before processing.

## Security notes

- Never commit `.dev.vars`, `wrangler.local.jsonc`, `local.properties`, keystore files, or private keys. All are gitignored by default.
- Store production secrets only in Cloudflare via `wrangler secret put`.
- Rotate `ADMIN_SECRET`, NOWPayments keys, and D1 credentials immediately if exposure is suspected.
- Enable Cloudflare WAF or rate limiting for `/api/admin/*` and webhook endpoints.
- Credits are consumed server-side before the Android foreground service begins sending.
- Operation history is stored locally in app preferences; the settings wipe action clears preferences and cache but does not delete backend or carrier records.
- BLACKOUT is a research project. Review its behavior, legal requirements, and security posture before any deployment.

## License

Distributed under the Apache License 2.0. See [LICENSE](LICENSE) for the full text.
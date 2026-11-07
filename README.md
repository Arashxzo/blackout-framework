<div align="center">

# 🕶️ BLACKOUT

### Android Security Research Framework for Telephony Testing

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Cloudflare Workers](https://img.shields.io/badge/Cloudflare_Workers-F38020?style=for-the-badge&logo=cloudflare&logoColor=white)](https://workers.cloudflare.com)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue?style=for-the-badge)](LICENSE)

</div>

---

## ⚠️ Legal and Ethical Notice

This project is developed for authorized security research, laboratory testing, and defensive evaluation only.

It must only be used on systems, devices, or networks where you have explicit authorization to test. Any unauthorized use is illegal and strictly prohibited.

The author assumes no responsibility for misuse, consequences, legal exposure, or damage caused by this software.

---

## Overview

BLACKOUT is a research-oriented Android and Cloudflare Worker project focused on controlled evaluation of telephony-side behavior involving SMS and WAP Push traffic. The project includes:

- an Android client built with Jetpack Compose
- a backend service for licensing, payments, admin controls, and reporting
- a proof-of-concept flow for testing malformed or crafted telecom payloads in a lab environment
- secure configuration patterns for secrets, D1 storage, and webhook validation

This repository is intended as a security research and red-team lab project. It is not a production application and should not be deployed in real production environments without explicit review, approvals, and operational safeguards.

---

## Purpose and Scope

The repository demonstrates a controlled PoC for investigating how the Android telephony stack reacts to crafted WAP Push / SMS payloads under specific conditions. It is structured to help researchers:

- validate reproducible payload behavior in a controlled environment
- manage experiment flows from a client app
- track usage and credits via a backend service
- review logs and historical execution data
- manage administrative actions through a protected backend interface

The project is intentionally modular and uses local secret files rather than hardcoded credentials.

---

## Architecture

### Android client

The Android app provides the user interface for:

- selecting a SIM card
- entering target numbers
- choosing payload quantity
- checking and consuming credits
- reviewing history and logs
- opening an admin-protected flow for operational management

### Backend service

The Cloudflare Worker backend includes:

- D1 database storage for plans, users, licenses, and transactions
- public and admin API endpoints
- NOWPayments integration for credit purchases
- webhook verification and idempotent payment handling
- CORS-enabled access for the Android client

---

## Repository Structure

```text
blackout-framework/
├── android/                             # Android application
│   ├── app/
│   │   ├── build.gradle
│   │   ├── proguard-rules.pro
│   │   └── src/main/java/com/arashxzo/blackout/
│   ├── local.properties.example
│   ├── local.properties                 # local secrets (gitignored)
│   ├── build.gradle
│   ├── gradle.properties
│   ├── settings.gradle
│   └── gradle/wrapper/
├── backend/                             # Cloudflare Worker backend
│   ├── src/
│   │   └── index.ts
│   ├── schema.sql
│   ├── package.json
│   ├── wrangler.jsonc
│   ├── .dev.vars.example
│   └── node_modules/                   # generated dependencies
├── .gitignore
├── LICENSE
├── README.md
├── project_dump.sh
└── project_dump.txt
```

---

## Security Considerations

This project interacts with telephony infrastructure and operational messaging flows. Before using it:

- verify that the target is explicitly authorized for testing
- run the project only in a controlled lab or on owned equipment
- protect all secrets, tokens, and keys
- review code and deployment configuration before use
- enforce rate limiting and access restrictions for admin APIs
- avoid committing credentials, keystore files, or environment files

The repository intentionally keeps secrets in local files instead of source control.

---

## Configuration

This project follows a 12-factor-style configuration model: runtime values are kept in local files that are not committed to the repository.

### Android configuration

Create the local properties file:

```bash
cp android/local.properties.example android/local.properties
```

Example:

```properties
BACKEND_URL=https://blackout-backend.YOUR_SUBDOMAIN.workers.dev
```

The app reads `BACKEND_URL` from `BuildConfig` at build time. If the value is missing or invalid, the app displays a configuration error instead of silently using a bad URL.

### Backend configuration

Create the local backend environment file:

```bash
cp backend/.dev.vars.example backend/.dev.vars
```

Populate the required values for your local or production environment.

For deployment secrets, use Wrangler:

```bash
npx wrangler secret put NOWPAYMENTS_API_KEY
npx wrangler secret put NOWPAYMENTS_IPN_SECRET
npx wrangler secret put ADMIN_SECRET
```

Update the D1 database ID in `backend/wrangler.jsonc`:

```jsonc
"database_id": "YOUR_DATABASE_ID_HERE"
```

Then regenerate the worker types:

```bash
npx wrangler types
```

---

## Backend Setup

### Requirements

- Node.js 18+
- Cloudflare account
- Wrangler CLI
- D1 database support enabled in your Cloudflare project

### Steps

```bash
cd backend
npm install
npx wrangler login
npx wrangler d1 create blackout-db
```

Copy the returned database ID and update `backend/wrangler.jsonc`.

Initialize the schema:

```bash
npx wrangler d1 execute blackout-db --file=./schema.sql --remote
```

Set required secrets:

```bash
npx wrangler secret put NOWPAYMENTS_API_KEY
npx wrangler secret put NOWPAYMENTS_IPN_SECRET
npx wrangler secret put ADMIN_SECRET
```

Deploy the worker:

```bash
npm run deploy
```

Your deployed backend URL will resemble:

```text
https://YOUR_WORKER_SUBDOMAIN.workers.dev
```

Use this value in the Android app configuration as `BACKEND_URL`.

---

## Android Setup

### Requirements

- Android Studio (latest stable)
- JDK 17+
- Android SDK 35

### Steps

1. Copy the local properties template
2. Fill in the SDK path and backend URL
3. Open the `android/` directory in Android Studio
4. Let Gradle sync complete
5. Run the project on a connected device or emulator

For a signed release APK:

```text
Build > Generate Signed Bundle / APK > APK > Release
```

---

## Typical Usage Flow

1. Launch the app and accept the legal disclaimer.
2. Purchase or restore credits through the configured payment flow.
3. Enter the target phone number or use the relevant operational context.
4. Select the target SIM card.
5. Choose the payload quantity.
6. Execute the operation from the Android client.
7. Review logs, execution history, and backend responses.

For administrative workflows, access the admin flow from settings and authenticate using the configured secret-backed mechanism.

---

## API Reference

The worker exposes the following endpoints:

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/get-plans` | Returns active credit plans |
| POST | `/api/create-payment` | Creates a NOWPayments invoice |
| GET | `/api/get-credits?device_id=X` | Returns license balance and ban status |
| POST | `/api/consume-credits` | Deducts credits after an operation |
| POST | `/api/nowpayments-webhook` | Handles NOWPayments IPN callbacks |
| GET | `/api/admin/stats` | Returns admin-level statistics |
| GET | `/api/admin/users` | Lists all users |
| GET | `/api/admin/transactions` | Lists transaction history |
| POST | `/api/admin/user-action` | Bans, unbans, deletes, or updates user credits |
| GET | `/api/admin/plans` | Lists all plans |
| POST | `/api/admin/plans/update` | Updates pricing and credit values |

Admin routes require a valid authorization header or equivalent protected admin mechanism configured in the backend.

---

## Recommended Production Hardening

- keep all secrets in local environment files or secure secret managers
- never commit `local.properties`, `.dev.vars`, `.env`, keystore files, or private keys
- enforce WAF or firewall rules for admin and webhook routes
- use HTTPS only for public worker endpoints
- validate and reject invalid NOWPayments webhook signatures
- rotate secrets regularly and monitor access logs
- keep the Android signing key private and avoid publishing `.jks` or keystore material

Webhook verification is performed using the NOWPayments signature mechanism and only successful final-state transactions are credited.

---

## License

This project is licensed under the Apache License 2.0.

See [LICENSE](LICENSE) for details.

---

<div align="center">

Developed by Arashxzo

For authorized security research and controlled telephony testing only.

</div>

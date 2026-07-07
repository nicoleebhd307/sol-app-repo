# Sol An Bang — MoMo Payment Backend

A small Node.js/Express backend that creates **MoMo sandbox** payments and syncs their
status to **Firebase Realtime Database** (`/payments/{orderId}`).

The Android app must not hold the MoMo `secretKey` or receive IPN callbacks, so this
service owns the payment: it signs the create request, and MoMo calls it back (IPN) to
report the final result, which we write to RTDB.

## Flow

```
app ──create──▶ backend ──signed create──▶ MoMo sandbox
                   │                              │
                   ├─ write /payments/{id}=pending
                   │                              │
 user pays in MoMo app / QR ◀──── payUrl/deeplink ┘
                   │
MoMo ──IPN (signed)──▶ backend  ── verify sig ──▶ update /payments/{id}=success|failed
```

## Setup

```bash
cd backend/momo
npm install
cp .env.example .env          # adjust PUBLIC_BASE_URL etc.
# Put a Firebase service-account key (RTDB write access) here:
#   backend/momo/serviceAccountKey.json
#   (Firebase console → Project settings → Service accounts → Generate new private key)
npm start
```

Because MoMo must reach the IPN URL, expose the server publicly during testing, e.g.:

```bash
ngrok http 4000
# then set PUBLIC_BASE_URL=https://<id>.ngrok-free.app in .env and restart
```

## Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/payments/create` | Body `{ amount, bookingId?, customerId?, orderInfo?, paymentType? }` → returns `{ orderId, payUrl, deeplink, qrCodeUrl }` |
| `POST` | `/api/payments/ipn` | MoMo server-to-server callback (signature-verified) → updates RTDB status |
| `GET`  | `/api/payments/return` | Browser redirect landing page |
| `GET`  | `/api/payments/:orderId` | Poll current payment status from RTDB |
| `GET`  | `/health` | Liveness check |

## Quick test

```bash
curl -X POST http://localhost:4000/api/payments/create \
  -H 'Content-Type: application/json' \
  -d '{"amount":700000,"bookingId":"demo-booking","orderInfo":"Spa session","paymentType":"spa"}'
```

Open the returned `payUrl` (sandbox), pay with a MoMo test account, then check the record:

```bash
curl http://localhost:4000/api/payments/<orderId>
```

`status` moves `pending → success` once MoMo delivers the IPN.

## Notes
- Default credentials in `.env.example` are MoMo's **public sandbox** test keys.
- Amounts are integer VND.
- The `/payments/{orderId}` shape mirrors the app's payment records and is indexed under
  `/paymentsByBooking/{bookingId}` so existing app queries can pick it up.
- **Never** commit `.env` or `serviceAccountKey.json` (already git-ignored).

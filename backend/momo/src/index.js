'use strict';

const crypto = require('crypto');
const express = require('express');
const config = require('./config');
const momo = require('./momo');
const store = require('./firebase');

const app = express();
app.use(express.json());

app.get('/health', (req, res) => res.json({ ok: true, service: 'sol-momo-backend' }));

/**
 * Create a MoMo sandbox payment.
 * Body: { amount, bookingId?, customerId?, orderInfo?, paymentType? }
 * Returns the gateway response (payUrl / deeplink / qrCodeUrl) plus our orderId.
 */
app.post('/api/payments/create', async (req, res) => {
  try {
    const amount = parseInt(req.body.amount, 10);
    if (!Number.isInteger(amount) || amount <= 0) {
      return res.status(400).json({ error: 'amount must be a positive integer (VND)' });
    }

    const orderId = `SOL${Date.now()}${crypto.randomInt(1000, 9999)}`;
    const requestId = crypto.randomUUID();
    const orderInfo = req.body.orderInfo || 'Sol An Bang payment';

    await store.createPendingPayment({
      orderId,
      bookingId: req.body.bookingId,
      customerId: req.body.customerId,
      amount,
      orderInfo,
      paymentType: req.body.paymentType,
    });

    // channel: 'atm' (Napas card web form), 'card' (intl card page) or 'qr' (wallet, default)
    const gateway = await momo.createPayment({
      orderId, requestId, amount, orderInfo, channel: req.body.channel,
    });

    if (gateway.resultCode !== 0) {
      await store.updatePaymentStatus(orderId, {
        resultCode: gateway.resultCode,
        message: gateway.message,
        transId: null,
      });
      return res.status(502).json({ error: 'MoMo create failed', gateway });
    }

    return res.json({
      orderId,
      amount,
      payUrl: gateway.payUrl,
      deeplink: gateway.deeplink,
      qrCodeUrl: gateway.qrCodeUrl,
      message: gateway.message,
    });
  } catch (error) {
    console.error('create error', error.response ? error.response.data : error.message);
    return res.status(500).json({ error: 'internal error creating payment' });
  }
});

/**
 * Server-to-server IPN callback from MoMo. Verifies the signature, then writes the
 * final status to Realtime Database. Must respond 204 quickly.
 */
app.post('/api/payments/ipn', async (req, res) => {
  const payload = req.body;
  try {
    if (!momo.verifyIpnSignature(payload)) {
      console.warn('IPN signature mismatch for order', payload.orderId);
      return res.status(400).json({ error: 'invalid signature' });
    }
    const status = await store.updatePaymentStatus(payload.orderId, {
      resultCode: payload.resultCode,
      message: payload.message,
      transId: payload.transId,
    });
    console.log(`IPN ${payload.orderId} -> ${status} (resultCode=${payload.resultCode})`);
    return res.status(204).send();
  } catch (error) {
    console.error('ipn error', error.message);
    return res.status(500).json({ error: 'internal error handling ipn' });
  }
});

/**
 * Browser redirect target after the user finishes on the MoMo page. Deeplinks straight
 * back into the app (solanbang://payment); the payment screen is still on the app's back
 * stack — on failure its Pay button is re-enabled so the guest can retry immediately.
 */
app.get('/api/payments/return', async (req, res) => {
  const { orderId, resultCode } = req.query;
  const ok = String(resultCode) === '0';
  const appLink = `solanbang://payment?orderId=${encodeURIComponent(orderId || '')}` +
    `&resultCode=${encodeURIComponent(resultCode || '')}`;
  res.status(200).send(
    `<html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>` +
    `<body style="font-family:sans-serif;text-align:center;padding:40px">` +
    `<h2>${ok ? '✅ Thanh toán thành công' : '❌ Thanh toán chưa hoàn tất'}</h2>` +
    `<p>Mã đơn: ${orderId || '-'}</p>` +
    `<p>${ok ? 'Đang quay lại ứng dụng…' : 'Quay lại ứng dụng để thử thanh toán lại.'}</p>` +
    `<p><a href="${appLink}" style="display:inline-block;padding:14px 28px;background:#a50064;` +
    `color:#fff;border-radius:8px;text-decoration:none;font-weight:bold">Quay lại ứng dụng</a></p>` +
    `<script>setTimeout(function(){ window.location.href = ${JSON.stringify(appLink)}; }, 800);</script>` +
    `</body></html>`
  );
});

/** Poll a payment's current status (useful for the app while waiting for the IPN). */
app.get('/api/payments/:orderId', async (req, res) => {
  try {
    const payment = await store.getPayment(req.params.orderId);
    if (!payment) {
      return res.status(404).json({ error: 'payment not found' });
    }
    return res.json(payment);
  } catch (error) {
    console.error('status error', error.message);
    return res.status(500).json({ error: 'internal error reading payment' });
  }
});

app.listen(config.port, () => {
  console.log(`sol-momo-backend listening on :${config.port}`);
  console.log(`  IPN url:      ${config.resolvedIpnUrl}`);
  console.log(`  redirect url: ${config.resolvedRedirectUrl}`);
});

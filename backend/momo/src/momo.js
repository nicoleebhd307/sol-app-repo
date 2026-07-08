'use strict';

const crypto = require('crypto');
const axios = require('axios');
const config = require('./config');

function hmacSha256(rawSignature, secretKey) {
  return crypto.createHmac('sha256', secretKey).update(rawSignature).digest('hex');
}

/**
 * Maps a payment channel to a MoMo requestType:
 *   'atm'  → payWithATM     (domestic Napas/ATM card web form — testable without the app)
 *   'card' → payWithMethod  (method-selection page incl. international cards)
 *   'qr'   → captureWallet  (MoMo wallet app / QR scan) — default
 */
function requestTypeFor(channel) {
  if (channel === 'atm') {
    return 'payWithATM';
  }
  if (channel === 'card') {
    return 'payWithMethod';
  }
  return config.momo.requestType; // captureWallet by default
}

/**
 * Builds the MoMo AIO v2 "create" request (with HMAC-SHA256 signature) and calls the
 * sandbox gateway. Returns the parsed gateway response ({ payUrl, deeplink, qrCodeUrl, ... }).
 */
async function createPayment({ orderId, requestId, amount, orderInfo, extraData = '', channel }) {
  const { momo, resolvedRedirectUrl, resolvedIpnUrl } = config;
  const requestType = requestTypeFor(channel);

  // Signature fields MUST be in this exact alphabetical order.
  const rawSignature =
    `accessKey=${momo.accessKey}` +
    `&amount=${amount}` +
    `&extraData=${extraData}` +
    `&ipnUrl=${resolvedIpnUrl}` +
    `&orderId=${orderId}` +
    `&orderInfo=${orderInfo}` +
    `&partnerCode=${momo.partnerCode}` +
    `&redirectUrl=${resolvedRedirectUrl}` +
    `&requestId=${requestId}` +
    `&requestType=${requestType}`;

  const signature = hmacSha256(rawSignature, momo.secretKey);

  const body = {
    partnerCode: momo.partnerCode,
    partnerName: momo.partnerName,
    storeId: momo.storeId,
    requestId,
    amount: String(amount),
    orderId,
    orderInfo,
    redirectUrl: resolvedRedirectUrl,
    ipnUrl: resolvedIpnUrl,
    lang: momo.lang,
    requestType,
    autoCapture: true,
    extraData,
    signature,
  };

  const response = await axios.post(momo.endpoint, body, {
    headers: { 'Content-Type': 'application/json' },
    timeout: 15000,
  });
  return response.data;
}

/**
 * Recomputes the IPN signature and compares it to the one MoMo sent, so we never trust
 * an unsigned/forged callback before updating payment status.
 */
function verifyIpnSignature(payload) {
  const { momo } = config;
  const rawSignature =
    `accessKey=${momo.accessKey}` +
    `&amount=${payload.amount}` +
    `&extraData=${payload.extraData}` +
    `&message=${payload.message}` +
    `&orderId=${payload.orderId}` +
    `&orderInfo=${payload.orderInfo}` +
    `&orderType=${payload.orderType}` +
    `&partnerCode=${payload.partnerCode}` +
    `&payType=${payload.payType}` +
    `&requestId=${payload.requestId}` +
    `&responseTime=${payload.responseTime}` +
    `&resultCode=${payload.resultCode}` +
    `&transId=${payload.transId}`;

  const expected = hmacSha256(rawSignature, momo.secretKey);
  return expected === payload.signature;
}

module.exports = { createPayment, verifyIpnSignature };

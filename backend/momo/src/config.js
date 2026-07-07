'use strict';

require('dotenv').config();

/**
 * Central configuration, sourced from environment variables with sane sandbox defaults.
 * MoMo publishes these test credentials openly, so the app runs out-of-the-box against
 * the sandbox. Never commit real production credentials.
 */
const config = {
  port: parseInt(process.env.PORT || '4000', 10),

  momo: {
    endpoint: process.env.MOMO_ENDPOINT
      || 'https://test-payment.momo.vn/v2/gateway/api/create',
    partnerCode: process.env.MOMO_PARTNER_CODE || 'MOMO',
    accessKey: process.env.MOMO_ACCESS_KEY || 'F8BBA842ECF85',
    secretKey: process.env.MOMO_SECRET_KEY || 'K951B6PE1waDMi640xX08PD3vg6EkVlz',
    partnerName: process.env.MOMO_PARTNER_NAME || 'Sol An Bang',
    storeId: process.env.MOMO_STORE_ID || 'SolAnBangStore',
    requestType: process.env.MOMO_REQUEST_TYPE || 'captureWallet',
    lang: process.env.MOMO_LANG || 'vi',
  },

  // Where MoMo sends the user back (redirect) and the server-to-server IPN callback.
  // PUBLIC_BASE_URL must be reachable by MoMo (e.g. an ngrok/hosted URL) for IPN to arrive.
  publicBaseUrl: process.env.PUBLIC_BASE_URL || 'http://localhost:4000',
  redirectUrl: process.env.MOMO_REDIRECT_URL || '',
  ipnUrl: process.env.MOMO_IPN_URL || '',

  firebase: {
    databaseURL: process.env.FIREBASE_DATABASE_URL
      || 'https://sol-app-aaf25-default-rtdb.firebaseio.com',
    // Path to a service-account JSON (falls back to ./serviceAccountKey.json or ADC).
    serviceAccountPath: process.env.GOOGLE_APPLICATION_CREDENTIALS || '',
  },
};

config.resolvedRedirectUrl = config.redirectUrl
  || `${config.publicBaseUrl}/api/payments/return`;
config.resolvedIpnUrl = config.ipnUrl
  || `${config.publicBaseUrl}/api/payments/ipn`;

module.exports = config;

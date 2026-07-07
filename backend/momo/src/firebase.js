'use strict';

const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');
const config = require('./config');

/**
 * Initialises firebase-admin once. Credentials are resolved in this order:
 *   1. GOOGLE_APPLICATION_CREDENTIALS (env path to a service-account JSON)
 *   2. ./serviceAccountKey.json next to the backend
 *   3. Application Default Credentials (e.g. when deployed on GCP / Cloud Functions)
 */
function initFirebase() {
  if (admin.apps.length) {
    return admin.app();
  }

  const localKeyPath = path.join(__dirname, '..', 'serviceAccountKey.json');
  const explicitPath = config.firebase.serviceAccountPath;

  let credential;
  if (explicitPath && fs.existsSync(explicitPath)) {
    credential = admin.credential.cert(require(path.resolve(explicitPath)));
  } else if (fs.existsSync(localKeyPath)) {
    credential = admin.credential.cert(require(localKeyPath));
  } else {
    credential = admin.credential.applicationDefault();
  }

  return admin.initializeApp({
    credential,
    databaseURL: config.firebase.databaseURL,
  });
}

initFirebase();
const db = admin.database();

/**
 * Creates (or overwrites) the payment record at /payments/{orderId} in a "pending" state,
 * and indexes it under the booking so the app's paymentsByBooking queries can see it.
 */
async function createPendingPayment({ orderId, bookingId, customerId, amount, orderInfo, paymentType }) {
  const now = new Date().toISOString();
  const record = {
    orderId,
    bookingId: bookingId || null,
    customerId: customerId || null,
    amount,
    orderInfo: orderInfo || null,
    paymentType: paymentType || 'service',
    paymentMethod: 'momo',
    provider: 'momo_sandbox',
    status: 'pending',
    createdAt: now,
    updatedAt: now,
  };

  const updates = { [`/payments/${orderId}`]: record };
  if (bookingId) {
    updates[`/paymentsByBooking/${bookingId}/${orderId}`] = true;
  }
  await db.ref().update(updates);
  return record;
}

/**
 * Updates payment status from an IPN result. resultCode 0 => success, otherwise failed.
 */
async function updatePaymentStatus(orderId, { resultCode, message, transId }) {
  const status = Number(resultCode) === 0 ? 'success' : 'failed';
  const patch = {
    status,
    resultCode: Number(resultCode),
    momoMessage: message || null,
    transId: transId != null ? String(transId) : null,
    updatedAt: new Date().toISOString(),
  };
  if (status === 'success') {
    patch.paidAt = new Date().toISOString();
  }
  await db.ref(`/payments/${orderId}`).update(patch);
  return status;
}

async function getPayment(orderId) {
  const snapshot = await db.ref(`/payments/${orderId}`).once('value');
  return snapshot.val();
}

module.exports = { createPendingPayment, updatePaymentStatus, getPayment };

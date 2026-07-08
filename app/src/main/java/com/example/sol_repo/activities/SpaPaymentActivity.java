package com.example.sol_repo.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseCallback;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.models.OrderCreationResult;
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.MomoClient;
import com.example.sol_repo.utils.SessionManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Locale;

/** Payment screen for paid (non-Suite) spa bookings, mirroring the room service payment flow. */
public class SpaPaymentActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";
    public static final String EXTRA_DATE_DB = "spa_date_db";
    public static final String EXTRA_DATE_DISPLAY = "spa_date_display";
    public static final String EXTRA_SESSIONS = "spa_sessions";
    public static final String EXTRA_GUESTS = "spa_guests";
    public static final String EXTRA_SLOTS = "spa_slots";
    public static final String EXTRA_PRICE_PER_SLOT = "spa_price_per_slot";
    public static final String EXTRA_TOTAL = "spa_total";

    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;
    private BookingSummary booking;
    private TextView payButton;

    private String bookingId;
    private String dateDb;
    private String dateDisplay;
    private ArrayList<String> sessions;
    private int guests;
    private int slots;
    private double pricePerSlot;
    private double total;

    private String selectedMethod = "bank_card";
    private boolean methodMenuExpanded = false;

    private DatabaseReference paymentStatusRef;
    private ValueEventListener paymentStatusListener;
    private boolean paymentHandled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spa_payment);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        sessionManager = new SessionManager(this);

        bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        dateDb = getIntent().getStringExtra(EXTRA_DATE_DB);
        dateDisplay = getIntent().getStringExtra(EXTRA_DATE_DISPLAY);
        sessions = getIntent().getStringArrayListExtra(EXTRA_SESSIONS);
        guests = getIntent().getIntExtra(EXTRA_GUESTS, 1);
        slots = getIntent().getIntExtra(EXTRA_SLOTS, 0);
        pricePerSlot = getIntent().getDoubleExtra(EXTRA_PRICE_PER_SLOT, SpaTimeActivity.PRICE_PER_SLOT);
        total = getIntent().getDoubleExtra(EXTRA_TOTAL, 0);

        if (bookingId == null || sessions == null || sessions.isEmpty()) {
            finish();
            return;
        }

        payButton = findViewById(R.id.btnSpaPay);
        payButton.setText(getString(R.string.spa_confirm_pay) + "   " + CurrencyFormatter.format(total));

        bindSummary();
        bindPaymentMethods();

        findViewById(R.id.btnSpaPaymentBack).setOnClickListener(view -> finish());
        payButton.setOnClickListener(view -> pay());

        firebaseDatabaseDal.getBookingForCustomer(sessionManager.getCustomerId(), bookingId, resolved -> {
            if (resolved == null) {
                finish();
                return;
            }
            booking = resolved;
            ((TextView) findViewById(R.id.txtSpaPayRoomType)).setText(
                    booking.getRoomTypeName().toUpperCase(Locale.US));
            ((TextView) findViewById(R.id.txtSpaPayBookingCode)).setText(booking.getBookingCode());
        });
    }

    private void bindSummary() {
        ((TextView) findViewById(R.id.txtSpaPayDate)).setText(dateDisplay);
        ((TextView) findViewById(R.id.txtSpaPayGuests)).setText(
                getString(R.string.home_guest_count, guests));
        ((TextView) findViewById(R.id.txtSpaPaySessions)).setText(String.valueOf(sessions.size()));
        ((TextView) findViewById(R.id.txtSpaPaySlot)).setText(TextUtils.join("\n", sessions));
        ((TextView) findViewById(R.id.txtSpaPayTotal)).setText(CurrencyFormatter.format(total));
    }

    private void bindPaymentMethods() {
        findViewById(R.id.paymentMethodSelector).setOnClickListener(view -> {
            methodMenuExpanded = !methodMenuExpanded;
            renderMethodMenu();
        });
        findViewById(R.id.rowMethodBankCard).setOnClickListener(view -> selectMethod("bank_card"));
        findViewById(R.id.rowMethodEwallet).setOnClickListener(view -> selectMethod("e_wallet"));
        renderMethodSelection();
        renderMethodMenu();
    }

    private void renderMethodMenu() {
        findViewById(R.id.paymentMethodOptions).setVisibility(
                methodMenuExpanded ? View.VISIBLE : View.GONE);
        findViewById(R.id.imgMethodChevron).setRotation(methodMenuExpanded ? 180f : 0f);
    }

    private void selectMethod(String method) {
        selectedMethod = method;
        methodMenuExpanded = false;
        renderMethodSelection();
        renderMethodMenu();
    }

    private void renderMethodSelection() {
        boolean ewallet = "e_wallet".equals(selectedMethod);
        renderRadio(findViewById(R.id.radioBankCard), !ewallet);
        renderRadio(findViewById(R.id.radioEwallet), ewallet);

        ((TextView) findViewById(R.id.txtSelectedMethod)).setText(
                ewallet ? R.string.rs_pay_ewallet : R.string.rs_pay_bank_card);
        ((ImageView) findViewById(R.id.imgSelectedMethod)).setImageResource(
                ewallet ? R.drawable.ic_wallet : R.drawable.ic_card);

        // Card details are entered on MoMo's hosted page, not in-app — always hide the mock form.
        findViewById(R.id.cardCardDetails).setVisibility(View.GONE);
    }

    private void renderRadio(View radioFrame, boolean selected) {
        radioFrame.setBackgroundResource(selected
                ? R.drawable.bg_circle_gold : R.drawable.bg_circle_ring);
        ((android.view.ViewGroup) radioFrame).getChildAt(0)
                .setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    private void pay() {
        // Every method settles on MoMo's hosted page (card or wallet). The booking is created
        // only after MoMo's IPN confirms success — no in-app mock that always succeeds.
        startMomoPayment();
    }

    private void startMomoPayment() {
        payButton.setEnabled(false);
        Toast.makeText(this, R.string.momo_starting, Toast.LENGTH_SHORT).show();
        // Bank card → MoMo's ATM/Napas web form (fill card details there, no app needed);
        // e-wallet → MoMo wallet QR page.
        String channel = "e_wallet".equals(selectedMethod)
                ? MomoClient.CHANNEL_WALLET : MomoClient.CHANNEL_ATM;
        MomoClient.createPayment((int) Math.round(total), bookingId, "Spa session booking", "spa",
                channel, new MomoClient.CreateCallback() {
                    @Override
                    public void onCreated(String orderId, String payUrl) {
                        observePayment(orderId);
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(payUrl)));
                        Toast.makeText(SpaPaymentActivity.this, R.string.momo_waiting,
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        payButton.setEnabled(true);
                        Toast.makeText(SpaPaymentActivity.this, R.string.momo_error,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void observePayment(String orderId) {
        detachPaymentListener();
        paymentHandled = false;
        paymentStatusRef = firebaseDatabaseDal.getPaymentStatusRef(orderId);
        paymentStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (paymentHandled) {
                    return;
                }
                String status = snapshot.getValue(String.class);
                if ("success".equals(status)) {
                    paymentHandled = true;
                    detachPaymentListener();
                    finalizeBooking();
                } else if ("failed".equals(status)) {
                    paymentHandled = true;
                    detachPaymentListener();
                    payButton.setEnabled(true);
                    Toast.makeText(SpaPaymentActivity.this, R.string.momo_failed,
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
            }
        };
        paymentStatusRef.addValueEventListener(paymentStatusListener);
    }

    private void detachPaymentListener() {
        if (paymentStatusRef != null && paymentStatusListener != null) {
            paymentStatusRef.removeEventListener(paymentStatusListener);
        }
    }

    private void finalizeBooking() {
        payButton.setEnabled(false);
        firebaseDatabaseDal.createSpaBooking(bookingId, sessionManager.getCustomerId(), dateDb, sessions,
                guests, slots, pricePerSlot, total, false, selectedMethod,
                new FirebaseCallback<OrderCreationResult>() {
                    @Override
                    public void onSuccess(OrderCreationResult result) {
                        payButton.setEnabled(true);
                        if (result == null) {
                            Toast.makeText(SpaPaymentActivity.this, R.string.spa_booking_failed,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        startActivity(SpaConfirmActivity.intentFor(SpaPaymentActivity.this, bookingId,
                                result.getOrderCode(), dateDisplay, guests, sessions, slots, total, false));
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        payButton.setEnabled(true);
                        Toast.makeText(SpaPaymentActivity.this, R.string.spa_booking_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachPaymentListener();
    }

    private boolean validateCardFields() {
        return !isEmpty(R.id.inputCardholder)
                && !isEmpty(R.id.inputCardNumber)
                && !isEmpty(R.id.inputExpiry)
                && !isEmpty(R.id.inputCvv);
    }

    private boolean isEmpty(int inputId) {
        return ((EditText) findViewById(inputId)).getText().toString().trim().isEmpty();
    }
}

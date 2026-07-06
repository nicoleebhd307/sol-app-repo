package com.example.sol_repo.activities;

import android.content.Intent;
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
import com.example.sol_repo.utils.SessionManager;

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

        findViewById(R.id.cardCardDetails).setVisibility(ewallet ? View.GONE : View.VISIBLE);
    }

    private void renderRadio(View radioFrame, boolean selected) {
        radioFrame.setBackgroundResource(selected
                ? R.drawable.bg_circle_gold : R.drawable.bg_circle_ring);
        ((android.view.ViewGroup) radioFrame).getChildAt(0)
                .setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    private void pay() {
        if ("bank_card".equals(selectedMethod) && !validateCardFields()) {
            Toast.makeText(this, R.string.rs_error_card_fields, Toast.LENGTH_SHORT).show();
            return;
        }

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

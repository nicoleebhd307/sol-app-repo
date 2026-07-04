package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.models.OrderCreationResult;
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.RoomServiceCart;
import com.example.sol_repo.utils.SessionManager;

import java.util.Locale;

public class RoomServicePaymentActivity extends AppCompatActivity {
    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;
    private BookingSummary booking;
    private TextView payNowButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_service_payment);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        sessionManager = new SessionManager(this);

        String bookingId = getIntent().getStringExtra(RoomServiceActivity.EXTRA_BOOKING_ID);
        if (bookingId == null || RoomServiceCart.isEmpty()) {
            finish();
            return;
        }

        payNowButton = findViewById(R.id.btnPayNow);

        firebaseDatabaseDal.getBookingForCustomer(sessionManager.getCustomerId(), bookingId, resolvedBooking -> {
            if (resolvedBooking == null) {
                finish();
                return;
            }
            this.booking = resolvedBooking;
            bindBookingCard();
            bindSummary();
            bindPaymentMethods();
        });

        findViewById(R.id.btnPaymentBack).setOnClickListener(view -> finish());
        findViewById(R.id.rowPromoCode).setOnClickListener(view ->
                Toast.makeText(this, R.string.rs_feature_soon, Toast.LENGTH_SHORT).show());
        findViewById(R.id.rowSpecialRequest).setOnClickListener(view ->
                Toast.makeText(this, R.string.rs_feature_soon, Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnPayNow).setOnClickListener(view -> payNow());
    }

    private void bindBookingCard() {
        View card = findViewById(R.id.cardPaymentBooking);
        ((TextView) card.findViewById(R.id.txtRsRoomType)).setText(
                booking.getRoomTypeName().toUpperCase(Locale.US));
        firebaseDatabaseDal.getRoomNumberForBooking(booking.getBookingId(), roomNumber ->
                ((TextView) card.findViewById(R.id.txtRsRoomNumber)).setText(
                        getString(R.string.rs_room_label, roomNumber)));
        ((TextView) card.findViewById(R.id.txtRsBookingCode)).setText(
                getString(R.string.booking_id_label, booking.getBookingCode()));
    }

    private void bindSummary() {
        ((TextView) findViewById(R.id.txtPaySubtotal)).setText(
                CurrencyFormatter.format(RoomServiceCart.getSubtotal()));
        ((TextView) findViewById(R.id.txtPayServiceCharge)).setText(
                CurrencyFormatter.format(RoomServiceCart.getServiceCharge()));
        ((TextView) findViewById(R.id.txtPayTotal)).setText(
                CurrencyFormatter.format(RoomServiceCart.getTotal()));
    }

    private void bindPaymentMethods() {
        findViewById(R.id.rowMethodRoomBill).setOnClickListener(view -> selectMethod("room_bill"));
        findViewById(R.id.rowMethodBankCard).setOnClickListener(view -> selectMethod("bank_card"));
        findViewById(R.id.rowMethodEwallet).setOnClickListener(view -> selectMethod("e_wallet"));
        renderMethodSelection();
    }

    private void selectMethod(String method) {
        RoomServiceCart.setPaymentMethod(method);
        renderMethodSelection();
    }

    private void renderMethodSelection() {
        String method = RoomServiceCart.getPaymentMethod();
        renderRadio(findViewById(R.id.radioRoomBill), "room_bill".equals(method));
        renderRadio(findViewById(R.id.radioBankCard), "bank_card".equals(method));
        renderRadio(findViewById(R.id.radioEwallet), "e_wallet".equals(method));
        findViewById(R.id.cardCardDetails).setVisibility(
                "bank_card".equals(method) ? View.VISIBLE : View.GONE);
    }

    private void renderRadio(View radioFrame, boolean selected) {
        radioFrame.setBackgroundResource(selected
                ? R.drawable.bg_circle_gold
                : R.drawable.bg_circle_ring);
        ((android.view.ViewGroup) radioFrame).getChildAt(0)
                .setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    private void payNow() {
        String method = RoomServiceCart.getPaymentMethod();
        if ("bank_card".equals(method) && !validateCardFields()) {
            Toast.makeText(this, R.string.rs_error_card_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        payNowButton.setEnabled(false);
        firebaseDatabaseDal.createRoomServiceOrder(
                booking.getBookingId(),
                sessionManager.getCustomerId(),
                RoomServiceCart.getEntries(),
                RoomServiceCart.getKitchenNote(),
                RoomServiceCart.getSubtotal(),
                RoomServiceCart.getServiceCharge(),
                Math.round(RoomServiceCart.getTotal() * 100) / 100.0,
                method,
                new com.example.sol_repo.dals.FirebaseCallback<OrderCreationResult>() {
                    @Override
                    public void onSuccess(OrderCreationResult result) {
                        payNowButton.setEnabled(true);
                        if (result == null) {
                            Toast.makeText(RoomServicePaymentActivity.this,
                                    R.string.rs_order_failed, Toast.LENGTH_LONG).show();
                            return;
                        }

                        String bookingId = booking.getBookingId();
                        RoomServiceCart.clear();

                        Intent intent = new Intent(RoomServicePaymentActivity.this,
                                RoomServiceTrackingActivity.class);
                        intent.putExtra(RoomServiceActivity.EXTRA_BOOKING_ID, bookingId);
                        intent.putExtra(RoomServiceTrackingActivity.EXTRA_ORDER_ID, result.getOrderId());
                        intent.putExtra(RoomServiceTrackingActivity.EXTRA_SHOW_SUCCESS, true);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        payNowButton.setEnabled(true);
                        Toast.makeText(RoomServicePaymentActivity.this,
                                R.string.rs_order_failed, Toast.LENGTH_LONG).show();
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

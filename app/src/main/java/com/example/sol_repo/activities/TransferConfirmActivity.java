package com.example.sol_repo.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;

/** Airport transfer confirmation screen (pickup or drop-off). */
public class TransferConfirmActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";
    public static final String EXTRA_BOOKING_CODE = "transfer_booking_code";
    public static final String EXTRA_TRANSFER_TYPE = "transfer_type";
    public static final String EXTRA_FROM = "transfer_from";
    public static final String EXTRA_TO = "transfer_to";
    public static final String EXTRA_DATETIME = "transfer_datetime";
    public static final String EXTRA_FLIGHT = "transfer_flight";
    public static final String EXTRA_GUESTS = "transfer_guests";

    public static Intent intentFor(Context context, String bookingId, String bookingCode,
                                   String transferType, String from, String to,
                                   String dateTimeDisplay, String flightNumber, int guests) {
        Intent intent = new Intent(context, TransferConfirmActivity.class);
        intent.putExtra(EXTRA_BOOKING_ID, bookingId);
        intent.putExtra(EXTRA_BOOKING_CODE, bookingCode);
        intent.putExtra(EXTRA_TRANSFER_TYPE, transferType);
        intent.putExtra(EXTRA_FROM, from);
        intent.putExtra(EXTRA_TO, to);
        intent.putExtra(EXTRA_DATETIME, dateTimeDisplay);
        intent.putExtra(EXTRA_FLIGHT, flightNumber);
        intent.putExtra(EXTRA_GUESTS, guests);
        return intent;
    }

    private String bookingId;
    private CountDownTimer autoHomeTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_confirm);

        bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        String transferType = getIntent().getStringExtra(EXTRA_TRANSFER_TYPE);
        boolean dropoff = "dropoff".equals(transferType);
        int guests = getIntent().getIntExtra(EXTRA_GUESTS, 1);

        ((TextView) findViewById(R.id.txtTransferConfirmTitle)).setText(
                dropoff ? R.string.transfer_dropoff_confirmed : R.string.transfer_pickup_confirmed);
        ((TextView) findViewById(R.id.txtTransferConfirmId)).setText(
                getIntent().getStringExtra(EXTRA_BOOKING_CODE));
        ((TextView) findViewById(R.id.txtTransferConfirmType)).setText(
                dropoff ? R.string.transfer_dropoff : R.string.transfer_pickup);
        ((TextView) findViewById(R.id.txtTransferConfirmFrom)).setText(
                getIntent().getStringExtra(EXTRA_FROM));
        ((TextView) findViewById(R.id.txtTransferConfirmTo)).setText(
                getIntent().getStringExtra(EXTRA_TO));
        ((TextView) findViewById(R.id.txtTransferConfirmDateTime)).setText(
                getIntent().getStringExtra(EXTRA_DATETIME));
        ((TextView) findViewById(R.id.txtTransferConfirmFlight)).setText(
                getIntent().getStringExtra(EXTRA_FLIGHT));
        ((TextView) findViewById(R.id.txtTransferConfirmGuests)).setText(
                getString(R.string.home_guest_count, guests));

        findViewById(R.id.btnTransferConfirmBack).setOnClickListener(view -> goHome());
        TextView btnBackHome = findViewById(R.id.btnTransferBackHome);
        btnBackHome.setOnClickListener(view -> goHome());
        startAutoHomeCountdown(btnBackHome);
    }

    private void startAutoHomeCountdown(TextView button) {
        String label = getString(R.string.store_back_home);
        autoHomeTimer = new CountDownTimer(10_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                button.setText(getString(R.string.back_home_countdown, label, seconds));
            }

            @Override
            public void onFinish() {
                goHome();
            }
        };
        autoHomeTimer.start();
    }

    private void goHome() {
        if (autoHomeTimer != null) {
            autoHomeTimer.cancel();
            autoHomeTimer = null;
        }
        Intent intent = new Intent(this, MainActivity.class);
        if (bookingId != null) {
            intent.putExtra(MainActivity.EXTRA_BOOKING_ID, bookingId);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (autoHomeTimer != null) {
            autoHomeTimer.cancel();
            autoHomeTimer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        goHome();
    }
}

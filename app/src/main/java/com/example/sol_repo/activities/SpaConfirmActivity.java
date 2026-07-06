package com.example.sol_repo.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.utils.CurrencyFormatter;

import java.util.ArrayList;

/** Final spa confirmation screen, reached from either the free flow or after payment. */
public class SpaConfirmActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";
    public static final String EXTRA_BOOKING_CODE = "spa_booking_code";
    public static final String EXTRA_DATE_DISPLAY = "spa_date_display";
    public static final String EXTRA_GUESTS = "spa_guests";
    public static final String EXTRA_SESSIONS = "spa_sessions";
    public static final String EXTRA_SLOTS = "spa_slots";
    public static final String EXTRA_AMOUNT = "spa_amount";
    public static final String EXTRA_FREE = "spa_free";

    public static Intent intentFor(Context context, String bookingId, String bookingCode,
                                   String dateDisplay, int guests, ArrayList<String> sessions,
                                   int slots, double amount, boolean free) {
        Intent intent = new Intent(context, SpaConfirmActivity.class);
        intent.putExtra(EXTRA_BOOKING_ID, bookingId);
        intent.putExtra(EXTRA_BOOKING_CODE, bookingCode);
        intent.putExtra(EXTRA_DATE_DISPLAY, dateDisplay);
        intent.putExtra(EXTRA_GUESTS, guests);
        intent.putStringArrayListExtra(EXTRA_SESSIONS, sessions);
        intent.putExtra(EXTRA_SLOTS, slots);
        intent.putExtra(EXTRA_AMOUNT, amount);
        intent.putExtra(EXTRA_FREE, free);
        return intent;
    }

    private String bookingId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spa_confirm);

        bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        int guests = getIntent().getIntExtra(EXTRA_GUESTS, 1);
        int slots = getIntent().getIntExtra(EXTRA_SLOTS, 0);
        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);
        boolean free = getIntent().getBooleanExtra(EXTRA_FREE, false);
        ArrayList<String> sessions = getIntent().getStringArrayListExtra(EXTRA_SESSIONS);

        ((TextView) findViewById(R.id.txtConfirmBookingId)).setText(
                getIntent().getStringExtra(EXTRA_BOOKING_CODE));
        ((TextView) findViewById(R.id.txtConfirmDate)).setText(
                getIntent().getStringExtra(EXTRA_DATE_DISPLAY));
        ((TextView) findViewById(R.id.txtConfirmGuests)).setText(
                getString(R.string.home_guest_count, guests));
        ((TextView) findViewById(R.id.txtConfirmSession)).setText(sessionSummary(sessions));
        ((TextView) findViewById(R.id.txtConfirmSlots)).setText(String.valueOf(slots));
        ((TextView) findViewById(R.id.txtConfirmAmount)).setText(
                free ? getString(R.string.spa_amount_free) : CurrencyFormatter.format(amount));

        findViewById(R.id.btnSpaConfirmBack).setOnClickListener(view -> goHome());
        findViewById(R.id.btnSpaBackHome).setOnClickListener(view -> goHome());
        findViewById(R.id.btnSpaConcierge).setOnClickListener(view ->
                Toast.makeText(this, R.string.store_concierge_desc, Toast.LENGTH_SHORT).show());
    }

    private String sessionSummary(ArrayList<String> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return "";
        }
        if (sessions.size() == 1) {
            return sessions.get(0);
        }
        return getString(R.string.spa_sessions_plus, sessions.get(0), sessions.size() - 1);
    }

    private void goHome() {
        Intent intent = new Intent(this, MainActivity.class);
        if (bookingId != null) {
            intent.putExtra(MainActivity.EXTRA_BOOKING_ID, bookingId);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        goHome();
    }
}

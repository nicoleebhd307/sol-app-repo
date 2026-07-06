package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.utils.BottomNavHelper;
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Spa intro screen: shows the booking, the spa offer (free for Suite, paid otherwise) and rules. */
public class SpaServiceActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";

    private final SimpleDateFormat databaseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;
    private String bookingId;
    private boolean free = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spa_service);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        sessionManager = new SessionManager(this);

        bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        if (bookingId == null) {
            finish();
            return;
        }

        BottomNavHelper.setup(this, BottomNavHelper.Tab.SERVICES);

        ((TextView) findViewById(R.id.txtSpaSpecialists)).setText(
                getString(R.string.spa_specialists, FirebaseDatabaseDal.SPA_SESSION_CAPACITY));

        findViewById(R.id.btnSpaBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSpaContinue).setOnClickListener(view -> {
            Intent intent = new Intent(this, SpaTimeActivity.class);
            intent.putExtra(SpaTimeActivity.EXTRA_BOOKING_ID, bookingId);
            intent.putExtra(SpaTimeActivity.EXTRA_FREE, free);
            startActivity(intent);
        });

        firebaseDatabaseDal.getBookingForCustomer(sessionManager.getCustomerId(), bookingId, booking -> {
            if (booking == null) {
                Toast.makeText(this, R.string.booking_access_denied, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            bindBookingCard(booking);
            resolvePricing(booking);
        });
    }

    private void bindBookingCard(BookingSummary booking) {
        ((TextView) findViewById(R.id.txtSpaRoomType)).setText(
                booking.getRoomTypeName().toUpperCase(Locale.US));
        ((TextView) findViewById(R.id.txtSpaBookingCode)).setText(booking.getBookingCode());
        ((TextView) findViewById(R.id.txtSpaCheckIn)).setText(formatDate(booking.getCheckInDate()));
        ((TextView) findViewById(R.id.txtSpaCheckOut)).setText(formatDate(booking.getCheckOutDate()));
        ((TextView) findViewById(R.id.txtSpaGuests)).setText(
                getString(R.string.home_guest_count, booking.getNumGuests()));

        TextView roomView = findViewById(R.id.txtSpaRoom);
        String room = booking.getRoomNumber();
        if (!TextUtils.isEmpty(room)) {
            roomView.setText(room);
        } else {
            firebaseDatabaseDal.getRoomNumberForBooking(bookingId, number ->
                    roomView.setText(TextUtils.isEmpty(number)
                            ? getString(R.string.account_unknown_value) : number));
        }
    }

    private void resolvePricing(BookingSummary booking) {
        firebaseDatabaseDal.getRoomCategory(booking.getRoomTypeId(), category -> {
            free = "suite".equalsIgnoreCase(category);
            ((TextView) findViewById(R.id.txtSpaSessionDesc)).setText(
                    free ? R.string.spa_session_desc_free : R.string.spa_session_desc_paid);
            ((TextView) findViewById(R.id.txtSpaInfo)).setText(
                    free ? R.string.spa_info_free : R.string.spa_info_paid);
            if (free) {
                ((TextView) findViewById(R.id.txtSpaPrice)).setText(R.string.spa_price_free);
            } else {
                ((TextView) findViewById(R.id.txtSpaPrice)).setText(getString(R.string.spa_price_per_slot,
                        CurrencyFormatter.format(SpaTimeActivity.PRICE_PER_SLOT)));
            }
        });
    }

    private String formatDate(String rawDate) {
        try {
            return displayDateFormat.format(databaseDateFormat.parse(rawDate));
        } catch (ParseException | NullPointerException exception) {
            return rawDate;
        }
    }
}

package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseCallback;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.models.OrderCreationResult;
import com.example.sol_repo.utils.BottomNavHelper;
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Spa time picker: choose a stay date, guest count and one or more 40-minute sessions. */
public class SpaTimeActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";
    public static final String EXTRA_FREE = "spa_free";

    /** Price per slot (one guest, one session) for non-Suite bookings. */
    public static final double PRICE_PER_SLOT = 350_000d;

    private static final String[] MORNING_SESSIONS = {
            "08:00 – 08:40", "08:40 – 09:20", "09:20 – 10:00", "10:00 – 10:40"};
    private static final String[] AFTERNOON_SESSIONS = {
            "14:00 – 14:40", "14:40 – 15:20", "15:20 – 16:00"};

    private final SimpleDateFormat databaseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    private final SimpleDateFormat chipDayFormat = new SimpleDateFormat("EEE", Locale.US);
    private final SimpleDateFormat chipDateFormat = new SimpleDateFormat("dd MMM", Locale.US);

    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;
    private String bookingId;
    private boolean free;

    private final List<Calendar> stayDates = new ArrayList<>();
    private int selectedDateIndex = 0;
    private int guests = 1;
    private int maxGuests = 1;
    private final Set<String> selectedSessions = new LinkedHashSet<>();
    private Map<String, Integer> bookedBySession = new LinkedHashMap<>();
    private final Map<String, View> sessionTiles = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spa_time);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        sessionManager = new SessionManager(this);

        bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        free = getIntent().getBooleanExtra(EXTRA_FREE, false);
        if (bookingId == null) {
            finish();
            return;
        }

        BottomNavHelper.setup(this, BottomNavHelper.Tab.SERVICES);

        ((TextView) findViewById(R.id.txtSpaTimePrice)).setText(free
                ? getString(R.string.spa_price_free).replace("\n", " ")
                : getString(R.string.spa_price_per_slot, CurrencyFormatter.format(PRICE_PER_SLOT))
                        .replace("\n", " "));
        ((TextView) findViewById(R.id.btnSpaTimeContinue)).setText(
                free ? R.string.spa_confirm_free_action : R.string.spa_continue);

        buildSessionTiles();

        findViewById(R.id.btnSpaTimeBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnGuestMinus).setOnClickListener(view -> changeGuests(-1));
        findViewById(R.id.btnGuestPlus).setOnClickListener(view -> changeGuests(1));
        findViewById(R.id.btnSpaTimeContinue).setOnClickListener(view -> onContinue());

        firebaseDatabaseDal.getBookingForCustomer(sessionManager.getCustomerId(), bookingId, booking -> {
            if (booking == null) {
                Toast.makeText(this, R.string.booking_access_denied, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            maxGuests = Math.max(1, booking.getNumGuests());
            guests = maxGuests;
            buildStayDates(booking);
            renderDateChips();
            renderGuests();
            reloadAvailability();
        });
    }

    private void buildSessionTiles() {
        LayoutInflater inflater = LayoutInflater.from(this);
        GridLayout morning = findViewById(R.id.gridMorning);
        GridLayout afternoon = findViewById(R.id.gridAfternoon);
        for (String session : MORNING_SESSIONS) {
            addSessionTile(inflater, morning, session);
        }
        for (String session : AFTERNOON_SESSIONS) {
            addSessionTile(inflater, afternoon, session);
        }
    }

    private void addSessionTile(LayoutInflater inflater, GridLayout grid, String session) {
        View tile = inflater.inflate(R.layout.item_spa_session, grid, false);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = GridLayout.LayoutParams.WRAP_CONTENT;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED);
        int margin = dpToPx(4);
        params.setMargins(margin, margin, margin, margin);
        tile.setLayoutParams(params);
        tile.setOnClickListener(view -> onSessionClicked(session));
        sessionTiles.put(session, tile);
        grid.addView(tile);
    }

    private void buildStayDates(BookingSummary booking) {
        stayDates.clear();
        Calendar start = parse(booking.getCheckInDate());
        Calendar end = parse(booking.getCheckOutDate());
        if (start == null) {
            start = Calendar.getInstance();
        }
        if (end == null || end.before(start)) {
            end = (Calendar) start.clone();
        }
        Calendar cursor = (Calendar) start.clone();
        while (!cursor.after(end) && stayDates.size() < 60) {
            stayDates.add((Calendar) cursor.clone());
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        selectedDateIndex = 0;
    }

    private void renderDateChips() {
        LinearLayout container = findViewById(R.id.chipDateContainer);
        container.removeAllViews();
        Calendar today = Calendar.getInstance();
        for (int i = 0; i < stayDates.size(); i++) {
            Calendar date = stayDates.get(i);
            boolean selected = i == selectedDateIndex;

            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setBackgroundResource(selected
                    ? R.drawable.bg_spa_session_selected : R.drawable.bg_spa_session_available);
            chip.setMinimumWidth(dpToPx(64));
            chip.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dpToPx(10);
            chip.setLayoutParams(lp);

            TextView line1 = new TextView(this);
            line1.setText(isSameDay(date, today) ? getString(R.string.spa_time_today_short)
                    : chipDayFormat.format(date.getTime()));
            line1.setTextColor(getColor(selected ? R.color.sol_gold_dark : R.color.sol_text_secondary));
            line1.setTextSize(12f);
            line1.setGravity(Gravity.CENTER);

            TextView line2 = new TextView(this);
            line2.setText(chipDateFormat.format(date.getTime()));
            line2.setTextColor(getColor(R.color.sol_text_primary));
            line2.setTextSize(14f);
            line2.setGravity(Gravity.CENTER);

            chip.addView(line1);
            chip.addView(line2);
            final int index = i;
            chip.setOnClickListener(view -> {
                if (selectedDateIndex != index) {
                    selectedDateIndex = index;
                    selectedSessions.clear();
                    renderDateChips();
                    reloadAvailability();
                }
            });
            container.addView(chip);
        }
    }

    private void renderGuests() {
        ((TextView) findViewById(R.id.txtGuestCount)).setText(
                getString(R.string.home_guest_count, guests));
    }

    private void changeGuests(int delta) {
        int next = guests + delta;
        if (next < 1 || next > maxGuests) {
            return;
        }
        guests = next;
        // Drop any selected session that can no longer fit the larger party.
        selectedSessions.removeIf(session -> remainingFor(session) < guests);
        renderGuests();
        renderSessions();
        renderSummary();
    }

    private void reloadAvailability() {
        if (stayDates.isEmpty()) {
            return;
        }
        String date = databaseDateFormat.format(stayDates.get(selectedDateIndex).getTime());
        firebaseDatabaseDal.getBookedSpaSlots(date, new FirebaseCallback<Map<String, Integer>>() {
            @Override
            public void onSuccess(Map<String, Integer> booked) {
                bookedBySession = booked == null ? new LinkedHashMap<>() : booked;
                renderSessions();
                renderSummary();
            }

            @Override
            public void onError(String message) {
                bookedBySession = new LinkedHashMap<>();
                renderSessions();
                renderSummary();
            }
        });
    }

    private int remainingFor(String session) {
        Integer booked = bookedBySession.get(session);
        return FirebaseDatabaseDal.SPA_SESSION_CAPACITY - (booked == null ? 0 : booked);
    }

    private void renderSessions() {
        for (Map.Entry<String, View> entry : sessionTiles.entrySet()) {
            String session = entry.getKey();
            View tile = entry.getValue();
            int remaining = remainingFor(session);
            boolean selected = selectedSessions.contains(session);
            boolean selectable = selected || remaining >= guests;

            TextView time = tile.findViewById(R.id.txtSessionTime);
            TextView left = tile.findViewById(R.id.txtSessionLeft);
            View badge = tile.findViewById(R.id.badgeSelected);

            time.setText(session);
            left.setText(remaining <= 0
                    ? getString(R.string.spa_slots_full)
                    : getString(R.string.spa_slots_left, remaining));

            if (selected) {
                tile.setBackgroundResource(R.drawable.bg_spa_session_selected);
                time.setTextColor(getColor(R.color.sol_text_primary));
                left.setTextColor(getColor(R.color.sol_gold_dark));
                badge.setVisibility(View.VISIBLE);
            } else if (selectable) {
                tile.setBackgroundResource(R.drawable.bg_spa_session_available);
                time.setTextColor(getColor(R.color.sol_text_primary));
                left.setTextColor(getColor(R.color.sol_text_secondary));
                badge.setVisibility(View.GONE);
            } else {
                tile.setBackgroundResource(R.drawable.bg_spa_session_full);
                time.setTextColor(getColor(R.color.sol_text_secondary));
                left.setTextColor(getColor(R.color.sol_text_secondary));
                badge.setVisibility(View.GONE);
            }
        }
    }

    private void onSessionClicked(String session) {
        if (selectedSessions.contains(session)) {
            selectedSessions.remove(session);
        } else if (remainingFor(session) >= guests) {
            selectedSessions.add(session);
        } else {
            Toast.makeText(this, getString(R.string.spa_error_capacity, guests),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        renderSessions();
        renderSummary();
    }

    private void renderSummary() {
        int sessionCount = selectedSessions.size();
        int slots = sessionCount * guests;
        double total = free ? 0 : slots * PRICE_PER_SLOT;

        ((TextView) findViewById(R.id.txtSummarySessions)).setText(
                getString(R.string.spa_summary_sessions, sessionCount));
        ((TextView) findViewById(R.id.txtSummaryGuests)).setText(
                getString(R.string.spa_summary_guests, guests));
        ((TextView) findViewById(R.id.txtSummarySlots)).setText(
                getString(R.string.spa_summary_slots, slots));
        ((TextView) findViewById(R.id.txtEstimatedTotal)).setText(
                free ? getString(R.string.spa_amount_free) : CurrencyFormatter.format(total));
    }

    private void onContinue() {
        if (selectedSessions.isEmpty()) {
            Toast.makeText(this, R.string.spa_error_pick, Toast.LENGTH_SHORT).show();
            return;
        }
        for (String session : selectedSessions) {
            if (remainingFor(session) < guests) {
                Toast.makeText(this, getString(R.string.spa_error_capacity, guests),
                        Toast.LENGTH_LONG).show();
                reloadAvailability();
                return;
            }
        }

        ArrayList<String> sessions = new ArrayList<>(selectedSessions);
        int slots = sessions.size() * guests;
        String dbDate = databaseDateFormat.format(stayDates.get(selectedDateIndex).getTime());
        String displayDate = displayDateFormat.format(stayDates.get(selectedDateIndex).getTime());

        if (free) {
            View button = findViewById(R.id.btnSpaTimeContinue);
            button.setEnabled(false);
            firebaseDatabaseDal.createSpaBooking(bookingId, sessionManager.getCustomerId(), dbDate,
                    sessions, guests, slots, 0, 0, true, null,
                    new FirebaseCallback<OrderCreationResult>() {
                        @Override
                        public void onSuccess(OrderCreationResult result) {
                            button.setEnabled(true);
                            if (result == null) {
                                Toast.makeText(SpaTimeActivity.this, R.string.spa_booking_failed,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            startActivity(SpaConfirmActivity.intentFor(SpaTimeActivity.this, bookingId,
                                    result.getOrderCode(), displayDate, guests, sessions, slots, 0, true));
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            button.setEnabled(true);
                            Toast.makeText(SpaTimeActivity.this, R.string.spa_booking_failed,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
            return;
        }

        Intent intent = new Intent(this, SpaPaymentActivity.class);
        intent.putExtra(SpaPaymentActivity.EXTRA_BOOKING_ID, bookingId);
        intent.putExtra(SpaPaymentActivity.EXTRA_DATE_DB, dbDate);
        intent.putExtra(SpaPaymentActivity.EXTRA_DATE_DISPLAY, displayDate);
        intent.putStringArrayListExtra(SpaPaymentActivity.EXTRA_SESSIONS, sessions);
        intent.putExtra(SpaPaymentActivity.EXTRA_GUESTS, guests);
        intent.putExtra(SpaPaymentActivity.EXTRA_SLOTS, slots);
        intent.putExtra(SpaPaymentActivity.EXTRA_PRICE_PER_SLOT, PRICE_PER_SLOT);
        intent.putExtra(SpaPaymentActivity.EXTRA_TOTAL, slots * PRICE_PER_SLOT);
        startActivity(intent);
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private Calendar parse(String rawDate) {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(databaseDateFormat.parse(rawDate));
            return calendar;
        } catch (ParseException | NullPointerException exception) {
            return null;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

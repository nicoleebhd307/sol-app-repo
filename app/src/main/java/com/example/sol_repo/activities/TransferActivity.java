package com.example.sol_repo.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseCallback;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.models.OrderCreationResult;
import com.example.sol_repo.utils.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Single-screen airport transfer booking (Suite guests only): pick a direction, which reveals
 * the date + time picker, then confirm.
 */
public class TransferActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";

    private final SimpleDateFormat databaseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    private final SimpleDateFormat chipDayFormat = new SimpleDateFormat("EEE", Locale.US);
    private final SimpleDateFormat chipDateFormat = new SimpleDateFormat("dd MMM", Locale.US);
    private final SimpleDateFormat clockDisplayFormat = new SimpleDateFormat("hh:mm a", Locale.US);

    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;
    private String bookingId;
    private String transferType = null;
    private int numGuests = 1;

    private final List<Calendar> stayDates = new ArrayList<>();
    private int selectedDateIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        sessionManager = new SessionManager(this);

        bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        if (bookingId == null) {
            finish();
            return;
        }

        ((TimePicker) findViewById(R.id.timePickerTransfer)).setIs24HourView(false);

        findViewById(R.id.btnTransferBack).setOnClickListener(view -> finish());
        findViewById(R.id.optionPickup).setOnClickListener(view -> selectType("pickup"));
        findViewById(R.id.optionDropoff).setOnClickListener(view -> selectType("dropoff"));
        findViewById(R.id.btnTransferContinue).setOnClickListener(view -> onContinue());
        renderSelection();

        firebaseDatabaseDal.getBookingForCustomer(sessionManager.getCustomerId(), bookingId, booking -> {
            if (booking == null) {
                Toast.makeText(this, R.string.booking_access_denied, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            numGuests = Math.max(1, booking.getNumGuests());
            bindBookingCard(booking);
            buildStayDates(booking);
            renderDateChips();
            // Enforce Suite-only access regardless of how this screen was reached.
            firebaseDatabaseDal.getRoomCategory(booking.getRoomTypeId(), category -> {
                if (!"suite".equalsIgnoreCase(category)) {
                    Toast.makeText(this, R.string.transfer_suite_only, Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        });
    }

    private void bindBookingCard(BookingSummary booking) {
        ((TextView) findViewById(R.id.txtTransferRoomType)).setText(
                booking.getRoomTypeName().toUpperCase(Locale.US));
        ((TextView) findViewById(R.id.txtTransferBookingCode)).setText(booking.getBookingCode());
        ((TextView) findViewById(R.id.txtTransferCheckIn)).setText(formatDate(booking.getCheckInDate()));
        ((TextView) findViewById(R.id.txtTransferCheckOut)).setText(formatDate(booking.getCheckOutDate()));
        ((TextView) findViewById(R.id.txtTransferGuests)).setText(
                getString(R.string.home_guest_count, booking.getNumGuests()));

        TextView roomView = findViewById(R.id.txtTransferRoom);
        String room = booking.getRoomNumber();
        if (!TextUtils.isEmpty(room)) {
            roomView.setText(room);
        } else {
            firebaseDatabaseDal.getRoomNumberForBooking(bookingId, number ->
                    roomView.setText(TextUtils.isEmpty(number)
                            ? getString(R.string.account_unknown_value) : number));
        }
    }

    private void selectType(String type) {
        transferType = type;
        renderSelection();
        // Reveal the date + time picker once a direction is chosen.
        findViewById(R.id.sectionSchedule).setVisibility(View.VISIBLE);
    }

    private void renderSelection() {
        boolean pickup = "pickup".equals(transferType);
        boolean dropoff = "dropoff".equals(transferType);
        findViewById(R.id.optionPickup).setBackgroundResource(
                pickup ? R.drawable.bg_booking_selected : R.drawable.bg_dashboard_card);
        findViewById(R.id.optionDropoff).setBackgroundResource(
                dropoff ? R.drawable.bg_booking_selected : R.drawable.bg_dashboard_card);
        renderRadio(findViewById(R.id.radioPickup), pickup);
        renderRadio(findViewById(R.id.radioDropoff), dropoff);
    }

    private void renderRadio(View radioFrame, boolean selected) {
        radioFrame.setBackgroundResource(selected
                ? R.drawable.bg_circle_gold : R.drawable.bg_circle_ring);
        ((android.view.ViewGroup) radioFrame).getChildAt(0)
                .setVisibility(selected ? View.VISIBLE : View.GONE);
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
            line1.setText(isSameDay(date, today) ? getString(R.string.transfer_time_today_short)
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
                selectedDateIndex = index;
                renderDateChips();
            });
            container.addView(chip);
        }
    }

    private void onContinue() {
        if (transferType == null || stayDates.isEmpty()) {
            return;
        }

        TimePicker timePicker = findViewById(R.id.timePickerTransfer);
        int hour = timePicker.getHour();
        int minute = timePicker.getMinute();

        Calendar date = stayDates.get(selectedDateIndex);
        String dbDate = databaseDateFormat.format(date.getTime());
        String displayDate = displayDateFormat.format(date.getTime());

        Calendar clock = Calendar.getInstance();
        clock.set(Calendar.HOUR_OF_DAY, hour);
        clock.set(Calendar.MINUTE, minute);
        String timeDisplay = clockDisplayFormat.format(clock.getTime());
        String time24 = String.format(Locale.US, "%02d:%02d", hour, minute);
        String dateTimeDisplay = displayDate + " • " + timeDisplay;

        boolean dropoff = "dropoff".equals(transferType);
        String airport = getString(R.string.transfer_airport_name);
        String resort = getString(R.string.transfer_resort_name);
        String from = dropoff ? resort : airport;
        String to = dropoff ? airport : resort;

        View button = findViewById(R.id.btnTransferContinue);
        button.setEnabled(false);
        firebaseDatabaseDal.createTransferBooking(bookingId, sessionManager.getCustomerId(), transferType,
                from, to, dbDate, time24, dateTimeDisplay, numGuests,
                new FirebaseCallback<OrderCreationResult>() {
                    @Override
                    public void onSuccess(OrderCreationResult result) {
                        button.setEnabled(true);
                        if (result == null) {
                            Toast.makeText(TransferActivity.this, R.string.transfer_booking_failed,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        startActivity(TransferConfirmActivity.intentFor(TransferActivity.this, bookingId,
                                result.getOrderCode(), transferType, from, to, dateTimeDisplay, numGuests));
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        button.setEnabled(true);
                        Toast.makeText(TransferActivity.this, R.string.transfer_booking_failed,
                                Toast.LENGTH_LONG).show();
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

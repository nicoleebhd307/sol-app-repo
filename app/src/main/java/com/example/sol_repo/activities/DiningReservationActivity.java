package com.example.sol_repo.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseCallback;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.models.DiningTable;
import com.example.sol_repo.models.OrderCreationResult;
import com.example.sol_repo.utils.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DiningReservationActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";

    private static final String[] SLOTS = {"17:00", "18:00", "19:00", "20:00", "21:00"};
    private static final String DEFAULT_SLOT = "19:00";

    private final SimpleDateFormat databaseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;
    private String bookingId;

    private TextView timeSlotText;
    private GridLayout smallGrid;
    private LinearLayout largeList;

    private final Calendar dateCalendar = Calendar.getInstance();
    private String selectedSlot = DEFAULT_SLOT;
    private List<DiningTable> tables;
    private Set<String> bookedIds;
    private DiningTable selectedTable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dining_reservation);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        sessionManager = new SessionManager(this);

        bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        if (bookingId == null) {
            finish();
            return;
        }

        timeSlotText = findViewById(R.id.txtTimeSlot);
        smallGrid = findViewById(R.id.gridSmallTables);
        largeList = findViewById(R.id.listLargeTables);

        timeSlotText.setText(slotLabel(selectedSlot));

        findViewById(R.id.btnDiningBack).setOnClickListener(view -> finish());
        findViewById(R.id.rowDate).setOnClickListener(view -> pickDate());
        findViewById(R.id.rowTimeSlot).setOnClickListener(this::showSlotMenu);
        findViewById(R.id.btnConfirmTable).setOnClickListener(view -> confirm());

        firebaseDatabaseDal.getBookingForCustomer(sessionManager.getCustomerId(), bookingId, booking -> {
            if (booking == null) {
                Toast.makeText(this, R.string.booking_access_denied, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            initDate(booking);
            firebaseDatabaseDal.getDiningTables(loaded -> {
                tables = loaded;
                reloadAvailability();
            });
        });
    }

    private void initDate(BookingSummary booking) {
        Calendar checkIn = parse(booking.getCheckInDate());
        if (checkIn != null) {
            dateCalendar.setTime(checkIn.getTime());
        }
        renderDate();
    }

    private void showSlotMenu(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        for (int i = 0; i < SLOTS.length; i++) {
            menu.getMenu().add(0, i, i, slotLabel(SLOTS[i]));
        }
        menu.setOnMenuItemClickListener(item -> {
            selectedSlot = SLOTS[item.getItemId()];
            selectedTable = null;
            timeSlotText.setText(slotLabel(selectedSlot));
            renderSelection();
            reloadAvailability();
            return true;
        });
        menu.show();
    }

    private void reloadAvailability() {
        if (tables == null) {
            return;
        }
        String date = databaseDateFormat.format(dateCalendar.getTime());
        firebaseDatabaseDal.getBookedTableIds(date, selectedSlot, booked -> {
            bookedIds = booked;
            renderTables();
            renderSelection();
        });
    }

    private void renderTables() {
        smallGrid.removeAllViews();
        largeList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (DiningTable table : tables) {
            boolean booked = bookedIds != null && bookedIds.contains(table.getTableId());
            boolean selected = selectedTable != null && selectedTable.getTableId().equals(table.getTableId());

            if (table.isLarge()) {
                View tile = inflater.inflate(R.layout.item_dining_table, largeList, false);
                bindTile(tile, table, booked, selected);
                ((LinearLayout.LayoutParams) tile.getLayoutParams()).topMargin = dpToPx(4);
                largeList.addView(tile);
            } else {
                View tile = inflater.inflate(R.layout.item_dining_table, smallGrid, false);
                bindTile(tile, table, booked, selected);
                ((GridLayout.LayoutParams) tile.getLayoutParams())
                        .setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
                smallGrid.addView(tile);
            }
        }
    }

    private void bindTile(View tile, DiningTable table, boolean booked, boolean selected) {
        View shape = tile.findViewById(R.id.tableShape);
        View badgeSelected = tile.findViewById(R.id.badgeSelected);
        View badgeBooked = tile.findViewById(R.id.badgeBooked);
        TextView label = tile.findViewById(R.id.txtTableLabel);

        // Size by capacity/shape — mutate the existing (FrameLayout) params to avoid a
        // LayoutParams type mismatch with the shape's parent.
        int w, h;
        if (table.isRound()) {
            w = h = dpToPx(60);
        } else if (table.isLarge()) {
            w = dpToPx(150);
            h = dpToPx(58);
        } else {
            w = h = dpToPx(66);
        }
        // tableShape's parent is a FrameLayout, so its params MUST be FrameLayout.LayoutParams.
        shape.setLayoutParams(new android.widget.FrameLayout.LayoutParams(w, h));

        int drawable;
        if (booked) {
            drawable = table.isRound() ? R.drawable.bg_table_round_booked : R.drawable.bg_table_rect_booked;
        } else if (selected) {
            drawable = table.isRound() ? R.drawable.bg_table_round_selected : R.drawable.bg_table_rect_selected;
        } else {
            drawable = table.isRound() ? R.drawable.bg_table_round_available : R.drawable.bg_table_rect_available;
        }
        shape.setBackgroundResource(drawable);

        badgeSelected.setVisibility(selected ? View.VISIBLE : View.GONE);
        badgeBooked.setVisibility(booked ? View.VISIBLE : View.GONE);

        label.setText(getString(R.string.dining_table_label, table.getCode(), table.getCapacity()));

        tile.setOnClickListener(view -> {
            if (booked) {
                Toast.makeText(this, R.string.dining_taken, Toast.LENGTH_SHORT).show();
                return;
            }
            selectedTable = table;
            renderTables();
            renderSelection();
        });
    }

    private void renderSelection() {
        ((TextView) findViewById(R.id.txtSelDate)).setText(displayDateFormat.format(dateCalendar.getTime()));
        ((TextView) findViewById(R.id.txtSelTime)).setText(slotLabel(selectedSlot));
        if (selectedTable == null) {
            ((TextView) findViewById(R.id.txtSelTable)).setText(R.string.dining_none);
            ((TextView) findViewById(R.id.txtSelCapacity)).setText(R.string.dining_none);
        } else {
            ((TextView) findViewById(R.id.txtSelTable)).setText(selectedTable.getCode());
            ((TextView) findViewById(R.id.txtSelCapacity)).setText(
                    getString(R.string.dining_guests, selectedTable.getCapacity()));
        }
    }

    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            dateCalendar.set(year, month, day);
            selectedTable = null;
            renderDate();
            renderSelection();
            reloadAvailability();
        }, dateCalendar.get(Calendar.YEAR), dateCalendar.get(Calendar.MONTH),
                dateCalendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void renderDate() {
        ((TextView) findViewById(R.id.txtDiningDate)).setText(displayDateFormat.format(dateCalendar.getTime()));
    }

    private void confirm() {
        if (selectedTable == null) {
            Toast.makeText(this, R.string.dining_error_pick, Toast.LENGTH_SHORT).show();
            return;
        }
        View button = findViewById(R.id.btnConfirmTable);
        button.setEnabled(false);
        String date = databaseDateFormat.format(dateCalendar.getTime());
        DiningTable table = selectedTable;

        firebaseDatabaseDal.createDiningReservation(bookingId, sessionManager.getCustomerId(),
                table.getTableId(), table.getCode(), table.getCapacity(), date, selectedSlot,
                getString(R.string.dining_venue_name), new FirebaseCallback<OrderCreationResult>() {
                    @Override
                    public void onSuccess(OrderCreationResult result) {
                        button.setEnabled(true);
                        if (result == null) {
                            Toast.makeText(DiningReservationActivity.this, R.string.dining_taken,
                                    Toast.LENGTH_LONG).show();
                            selectedTable = null;
                            reloadAvailability();
                            return;
                        }
                        showSuccess(table, result.getOrderCode());
                    }

                    @Override
                    public void onError(String message) {
                        button.setEnabled(true);
                        Toast.makeText(DiningReservationActivity.this, R.string.dining_taken,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showSuccess(DiningTable table, String reservationCode) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dining_success_title)
                .setMessage(getString(R.string.dining_success_message,
                        table.getCode(),
                        getString(R.string.dining_venue_name),
                        displayDateFormat.format(dateCalendar.getTime()),
                        slotLabel(selectedSlot),
                        reservationCode))
                .setCancelable(false)
                .setPositiveButton(R.string.dining_success_action, (dialog, which) -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.putExtra(MainActivity.EXTRA_BOOKING_ID, bookingId);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private String slotLabel(String slot) {
        try {
            int hour = Integer.parseInt(slot.substring(0, 2));
            String suffix = hour >= 12 ? "PM" : "AM";
            int display = hour % 12;
            if (display == 0) {
                display = 12;
            }
            return display + ":00 " + suffix;
        } catch (NumberFormatException exception) {
            return slot;
        }
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

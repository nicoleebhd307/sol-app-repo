package com.example.sol_repo.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.RoomType;
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.ImageLoader;
import com.example.sol_repo.utils.RoomAssets;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class RoomDetailActivity extends AppCompatActivity {
    private final SimpleDateFormat databaseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMM dd", Locale.US);

    private FirebaseDatabaseDal firebaseDatabaseDal;
    private RoomType roomType;

    private final Calendar checkInCalendar = Calendar.getInstance();
    private final Calendar checkOutCalendar = Calendar.getInstance();
    private int numGuests = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_detail);

        firebaseDatabaseDal = new FirebaseDatabaseDal();

        String roomTypeId = getIntent().getStringExtra(RoomBookingActivity.EXTRA_ROOM_TYPE_ID);
        String checkIn = getIntent().getStringExtra(RoomBookingActivity.EXTRA_CHECK_IN);
        String checkOut = getIntent().getStringExtra(RoomBookingActivity.EXTRA_CHECK_OUT);
        numGuests = getIntent().getIntExtra(RoomBookingActivity.EXTRA_GUESTS, 2);

        if (roomTypeId == null || !parseDates(checkIn, checkOut)) {
            finish();
            return;
        }

        firebaseDatabaseDal.getRoomType(roomTypeId,
                databaseDateFormat.format(checkInCalendar.getTime()),
                databaseDateFormat.format(checkOutCalendar.getTime()),
                resolvedRoomType -> {
                    if (resolvedRoomType == null) {
                        Toast.makeText(this, R.string.detail_room_unavailable, Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    this.roomType = resolvedRoomType;
                    numGuests = Math.min(numGuests, roomType.getMaxOccupancy());

                    bindRoom();
                    bindStayPickers();
                });

        findViewById(R.id.btnDetailBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnReserveNow).setOnClickListener(view -> openCheckout());
    }

    private boolean parseDates(String checkIn, String checkOut) {
        try {
            checkInCalendar.setTime(databaseDateFormat.parse(checkIn));
            checkOutCalendar.setTime(databaseDateFormat.parse(checkOut));
            return true;
        } catch (ParseException | NullPointerException exception) {
            return false;
        }
    }

    private void bindRoom() {
        ImageLoader.load(findViewById(R.id.imgDetailRoom), roomType.getImageUrl(), RoomAssets.ROOM_PLACEHOLDER);
        ((TextView) findViewById(R.id.txtDetailName)).setText(roomType.getTypeName());
        ((TextView) findViewById(R.id.txtDetailViewBadge)).setText(
                formatViewBadge(roomType.getViewType()));
        ((TextView) findViewById(R.id.txtDetailPrice)).setText(
                CurrencyFormatter.format(roomType.getBasePrice()));
        ((TextView) findViewById(R.id.txtDetailAdults)).setText(
                getString(R.string.booking_adults_count, roomType.getMaxOccupancy()));
        ((TextView) findViewById(R.id.txtDetailSize)).setText(
                getString(R.string.booking_sqft_value, roomType.getSizeSqft()));
        ((TextView) findViewById(R.id.txtDetailBed)).setText(roomType.getBedType());
        ((TextView) findViewById(R.id.txtDetailDescription)).setText(roomType.getDescription());

        findViewById(R.id.badgeBestSeller).setVisibility(
                roomType.getAvailableRooms() <= 2 ? View.VISIBLE : View.GONE);

        renderAmenities();
    }

    private void renderAmenities() {
        GridLayout amenitiesGrid = findViewById(R.id.gridAmenities);
        amenitiesGrid.removeAllViews();
        if (roomType.getAmenities() == null || roomType.getAmenities().isEmpty()) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        int position = 0;
        int gap = dpToPx(5); // half of the gap that sits between the two columns
        for (String amenity : roomType.getAmenities().split(",")) {
            String amenityName = amenity.trim();
            if (amenityName.isEmpty()) {
                continue;
            }

            View itemView = inflater.inflate(R.layout.item_amenity, amenitiesGrid, false);
            ((ImageView) itemView.findViewById(R.id.imgAmenityIcon))
                    .setImageResource(RoomAssets.amenityIconFor(amenityName));
            ((TextView) itemView.findViewById(R.id.txtAmenityName)).setText(amenityName);

            boolean leftColumn = position % 2 == 0;
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            // Only put a small gap between the two columns — no wasted margin on the outer edges.
            params.setMargins(leftColumn ? 0 : gap, 0, leftColumn ? gap : 0, dpToPx(10));
            amenitiesGrid.addView(itemView, params);
            position++;
        }
    }

    private void bindStayPickers() {
        renderStay();
        findViewById(R.id.btnPickCheckIn).setOnClickListener(view -> pickCheckInDate());
        findViewById(R.id.btnPickCheckOut).setOnClickListener(view -> pickCheckOutDate());
        findViewById(R.id.btnPickGuests).setOnClickListener(view -> pickGuests());
    }

    private void pickCheckInDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            checkInCalendar.set(year, month, day);
            if (!checkOutCalendar.getTime().after(checkInCalendar.getTime())) {
                checkOutCalendar.setTime(checkInCalendar.getTime());
                checkOutCalendar.add(Calendar.DAY_OF_MONTH, 1);
            }
            renderStay();
        }, checkInCalendar.get(Calendar.YEAR), checkInCalendar.get(Calendar.MONTH),
                checkInCalendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
    }

    private void pickCheckOutDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            checkOutCalendar.set(year, month, day);
            renderStay();
        }, checkOutCalendar.get(Calendar.YEAR), checkOutCalendar.get(Calendar.MONTH),
                checkOutCalendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(checkInCalendar.getTimeInMillis() + 24L * 60 * 60 * 1000);
        dialog.show();
    }

    private void pickGuests() {
        String[] options = new String[roomType.getMaxOccupancy()];
        for (int i = 0; i < options.length; i++) {
            options[i] = getString(R.string.booking_adults_count, i + 1);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.detail_select_guests)
                .setSingleChoiceItems(options, numGuests - 1, (dialog, which) -> {
                    numGuests = which + 1;
                    renderStay();
                    dialog.dismiss();
                })
                .show();
    }

    private void renderStay() {
        ((TextView) findViewById(R.id.txtDetailCheckIn)).setText(
                displayDateFormat.format(checkInCalendar.getTime()));
        ((TextView) findViewById(R.id.txtDetailCheckOut)).setText(
                displayDateFormat.format(checkOutCalendar.getTime()));
        ((TextView) findViewById(R.id.txtDetailGuests)).setText(
                getString(R.string.booking_adults_count, numGuests));
    }

    private void openCheckout() {
        Intent intent = new Intent(this, BookingCheckoutActivity.class);
        intent.putExtra(RoomBookingActivity.EXTRA_ROOM_TYPE_ID, roomType.getRoomTypeId());
        intent.putExtra(RoomBookingActivity.EXTRA_CHECK_IN,
                databaseDateFormat.format(checkInCalendar.getTime()));
        intent.putExtra(RoomBookingActivity.EXTRA_CHECK_OUT,
                databaseDateFormat.format(checkOutCalendar.getTime()));
        intent.putExtra(RoomBookingActivity.EXTRA_GUESTS, numGuests);
        startActivity(intent);
    }

    private String formatViewBadge(String viewType) {
        String label = getString(RoomAssets.badgeLabelFor(viewType));
        String[] words = label.toLowerCase(Locale.US).split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

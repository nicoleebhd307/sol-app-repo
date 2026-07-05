package com.example.sol_repo.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.RoomType;
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.ImageLoader;
import com.example.sol_repo.utils.RoomAssets;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RoomBookingActivity extends AppCompatActivity {
    public static final String EXTRA_ROOM_TYPE_ID = "room_type_id";
    public static final String EXTRA_CHECK_IN = "check_in";
    public static final String EXTRA_CHECK_OUT = "check_out";
    public static final String EXTRA_GUESTS = "guests";

    private static final int PRICE_SLIDER_MAX = 3000000;

    private final SimpleDateFormat databaseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMM dd", Locale.US);

    private FirebaseDatabaseDal firebaseDatabaseDal;
    private LinearLayout roomsContainer;
    private TextView dateRangeTextView;
    private TextView noRoomsTextView;

    private final Calendar checkInCalendar = Calendar.getInstance();
    private final Calendar checkOutCalendar = Calendar.getInstance();

    private String filterCategory = null;
    private int filterMinPrice = 0;
    private int filterMaxPrice = PRICE_SLIDER_MAX;
    private final Set<String> filterViews = new HashSet<>();
    private int filterMinGuests = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_booking);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        roomsContainer = findViewById(R.id.listRooms);
        dateRangeTextView = findViewById(R.id.txtDateRange);
        noRoomsTextView = findViewById(R.id.txtNoRooms);

        checkInCalendar.add(Calendar.DAY_OF_MONTH, 7);
        checkOutCalendar.add(Calendar.DAY_OF_MONTH, 13);

        findViewById(R.id.cardDateSearch).setOnClickListener(view -> pickCheckInDate());
        findViewById(R.id.btnSearchRooms).setOnClickListener(view -> loadRooms());
        findViewById(R.id.btnOpenFilter).setOnClickListener(view -> showFilterSheet());
        findViewById(R.id.cardExperienceDining).setOnClickListener(view ->
                Toast.makeText(this, R.string.booking_experience_soon, Toast.LENGTH_SHORT).show());
        findViewById(R.id.cardExperienceSpa).setOnClickListener(view ->
                Toast.makeText(this, R.string.booking_experience_soon, Toast.LENGTH_SHORT).show());

        renderDateRange();
        loadRooms();
    }

    private void pickCheckInDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            checkInCalendar.set(year, month, day);
            if (!checkOutCalendar.getTime().after(checkInCalendar.getTime())) {
                checkOutCalendar.setTime(checkInCalendar.getTime());
                checkOutCalendar.add(Calendar.DAY_OF_MONTH, 1);
            }
            renderDateRange();
            pickCheckOutDate();
        }, checkInCalendar.get(Calendar.YEAR), checkInCalendar.get(Calendar.MONTH),
                checkInCalendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
    }

    private void pickCheckOutDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            checkOutCalendar.set(year, month, day);
            renderDateRange();
            loadRooms();
        }, checkOutCalendar.get(Calendar.YEAR), checkOutCalendar.get(Calendar.MONTH),
                checkOutCalendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(checkInCalendar.getTimeInMillis() + 24L * 60 * 60 * 1000);
        dialog.show();
    }

    private void renderDateRange() {
        dateRangeTextView.setText(String.format(Locale.US, "%s - %s",
                displayDateFormat.format(checkInCalendar.getTime()),
                displayDateFormat.format(checkOutCalendar.getTime())));
    }

    private void loadRooms() {
        firebaseDatabaseDal.getAvailableRoomTypes(
                databaseDateFormat.format(checkInCalendar.getTime()),
                databaseDateFormat.format(checkOutCalendar.getTime()),
                roomTypes -> {
                    List<RoomType> filtered = new ArrayList<>();
                    for (RoomType roomType : roomTypes) {
                        if (matchesFilter(roomType)) {
                            filtered.add(roomType);
                        }
                    }
                    renderRooms(filtered);
                });
    }

    private boolean matchesFilter(RoomType roomType) {
        if (filterCategory != null && !filterCategory.equals(roomType.getCategory())) {
            return false;
        }
        if (roomType.getBasePrice() < filterMinPrice || roomType.getBasePrice() > filterMaxPrice) {
            return false;
        }
        if (!filterViews.isEmpty() && !filterViews.contains(roomType.getViewType())) {
            return false;
        }
        if (filterMinGuests > 0) {
            if (filterMinGuests >= 4) {
                return roomType.getMaxOccupancy() >= 4;
            }
            return roomType.getMaxOccupancy() >= filterMinGuests;
        }
        return true;
    }

    private void renderRooms(List<RoomType> roomTypes) {
        roomsContainer.removeAllViews();
        noRoomsTextView.setVisibility(roomTypes.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (RoomType roomType : roomTypes) {
            View itemView = inflater.inflate(R.layout.item_room_result, roomsContainer, false);

            ImageLoader.load(itemView.findViewById(R.id.imgRoom), roomType.getImageUrl(), RoomAssets.ROOM_PLACEHOLDER);
            ((TextView) itemView.findViewById(R.id.txtViewBadge))
                    .setText(RoomAssets.badgeLabelFor(roomType.getViewType()));
            ((TextView) itemView.findViewById(R.id.txtRoomName)).setText(roomType.getTypeName());
            ((TextView) itemView.findViewById(R.id.txtRoomAdults)).setText(
                    getString(R.string.booking_adults_count, roomType.getMaxOccupancy()));
            ((TextView) itemView.findViewById(R.id.txtRoomSize)).setText(
                    getString(R.string.booking_sqft_value, roomType.getSizeSqft()));
            ((TextView) itemView.findViewById(R.id.txtRoomPrice)).setText(
                    CurrencyFormatter.format(roomType.getBasePrice()));

            itemView.findViewById(R.id.btnBookRoom).setOnClickListener(view ->
                    openCheckout(roomType));
            itemView.setOnClickListener(view -> openDetail(roomType));

            roomsContainer.addView(itemView);
        }
    }

    private void openDetail(RoomType roomType) {
        Intent intent = new Intent(this, RoomDetailActivity.class);
        putStayExtras(intent, roomType);
        startActivity(intent);
    }

    private void openCheckout(RoomType roomType) {
        Intent intent = new Intent(this, BookingCheckoutActivity.class);
        putStayExtras(intent, roomType);
        startActivity(intent);
    }

    private void putStayExtras(Intent intent, RoomType roomType) {
        intent.putExtra(EXTRA_ROOM_TYPE_ID, roomType.getRoomTypeId());
        intent.putExtra(EXTRA_CHECK_IN, databaseDateFormat.format(checkInCalendar.getTime()));
        intent.putExtra(EXTRA_CHECK_OUT, databaseDateFormat.format(checkOutCalendar.getTime()));
        intent.putExtra(EXTRA_GUESTS, Math.min(2, roomType.getMaxOccupancy()));
    }

    private void showFilterSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_filter, null);
        dialog.setContentView(sheetView);

        TextView chipAllRooms = sheetView.findViewById(R.id.chipAllRooms);
        TextView chipSuites = sheetView.findViewById(R.id.chipSuites);
        TextView chipVillas = sheetView.findViewById(R.id.chipVillas);
        TextView chipViewOcean = sheetView.findViewById(R.id.chipViewOcean);
        TextView chipViewGarden = sheetView.findViewById(R.id.chipViewGarden);
        TextView chipViewPool = sheetView.findViewById(R.id.chipViewPool);
        TextView chipGuests2 = sheetView.findViewById(R.id.chipGuests2);
        TextView chipGuests3 = sheetView.findViewById(R.id.chipGuests3);
        TextView chipGuests4 = sheetView.findViewById(R.id.chipGuests4);
        TextView minPriceTextView = sheetView.findViewById(R.id.txtMinPrice);
        TextView maxPriceTextView = sheetView.findViewById(R.id.txtMaxPrice);
        SeekBar minPriceSeekBar = sheetView.findViewById(R.id.seekMinPrice);
        SeekBar maxPriceSeekBar = sheetView.findViewById(R.id.seekMaxPrice);

        final String[] pendingCategory = {filterCategory};
        final Set<String> pendingViews = new HashSet<>(filterViews);
        final int[] pendingGuests = {filterMinGuests};

        Runnable renderCategoryChips = () -> {
            styleChip(chipAllRooms, pendingCategory[0] == null);
            styleChip(chipSuites, "suite".equals(pendingCategory[0]));
            styleChip(chipVillas, "deluxe".equals(pendingCategory[0]));
        };
        Runnable renderViewChips = () -> {
            styleChip(chipViewOcean, pendingViews.contains("sea"));
            styleChip(chipViewGarden, pendingViews.contains("garden"));
            styleChip(chipViewPool, pendingViews.contains("pool"));
        };
        Runnable renderGuestChips = () -> {
            styleChip(chipGuests2, pendingGuests[0] == 2);
            styleChip(chipGuests3, pendingGuests[0] == 3);
            styleChip(chipGuests4, pendingGuests[0] == 4);
        };

        chipAllRooms.setOnClickListener(view -> {
            pendingCategory[0] = null;
            renderCategoryChips.run();
        });
        chipSuites.setOnClickListener(view -> {
            pendingCategory[0] = "suite";
            renderCategoryChips.run();
        });
        chipVillas.setOnClickListener(view -> {
            pendingCategory[0] = "deluxe";
            renderCategoryChips.run();
        });

        View.OnClickListener toggleView = view -> {
            String value = view == chipViewOcean ? "sea" : view == chipViewGarden ? "garden" : "pool";
            if (!pendingViews.remove(value)) {
                pendingViews.add(value);
            }
            renderViewChips.run();
        };
        chipViewOcean.setOnClickListener(toggleView);
        chipViewGarden.setOnClickListener(toggleView);
        chipViewPool.setOnClickListener(toggleView);

        View.OnClickListener pickGuests = view -> {
            int value = view == chipGuests2 ? 2 : view == chipGuests3 ? 3 : 4;
            pendingGuests[0] = pendingGuests[0] == value ? 0 : value;
            renderGuestChips.run();
        };
        chipGuests2.setOnClickListener(pickGuests);
        chipGuests3.setOnClickListener(pickGuests);
        chipGuests4.setOnClickListener(pickGuests);

        minPriceSeekBar.setMax(PRICE_SLIDER_MAX);
        maxPriceSeekBar.setMax(PRICE_SLIDER_MAX);
        minPriceSeekBar.setProgress(filterMinPrice);
        maxPriceSeekBar.setProgress(filterMaxPrice);

        SeekBar.OnSeekBarChangeListener priceListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && minPriceSeekBar.getProgress() > maxPriceSeekBar.getProgress()) {
                    if (seekBar == minPriceSeekBar) {
                        maxPriceSeekBar.setProgress(minPriceSeekBar.getProgress());
                    } else {
                        minPriceSeekBar.setProgress(maxPriceSeekBar.getProgress());
                    }
                }
                minPriceTextView.setText(CurrencyFormatter.format(minPriceSeekBar.getProgress()));
                maxPriceTextView.setText(CurrencyFormatter.format(maxPriceSeekBar.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        minPriceSeekBar.setOnSeekBarChangeListener(priceListener);
        maxPriceSeekBar.setOnSeekBarChangeListener(priceListener);
        priceListener.onProgressChanged(minPriceSeekBar, filterMinPrice, false);

        renderCategoryChips.run();
        renderViewChips.run();
        renderGuestChips.run();

        sheetView.findViewById(R.id.btnCloseFilter).setOnClickListener(view -> dialog.dismiss());
        sheetView.findViewById(R.id.btnApplyFilter).setOnClickListener(view -> {
            filterCategory = pendingCategory[0];
            filterViews.clear();
            filterViews.addAll(pendingViews);
            filterMinGuests = pendingGuests[0];
            filterMinPrice = minPriceSeekBar.getProgress();
            filterMaxPrice = maxPriceSeekBar.getProgress();
            dialog.dismiss();
            loadRooms();
        });

        dialog.show();
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected
                ? R.drawable.bg_chip_selected
                : R.drawable.bg_chip_unselected);
        chip.setTextColor(getColor(selected ? R.color.sol_gold_dark : R.color.sol_text_primary));
        chip.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.plus_jakarta_sans),
                selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }
}

package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.ImageView;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.models.Customer;
import com.example.sol_repo.utils.BottomNavHelper;
import com.example.sol_repo.utils.ImageLoader;
import com.example.sol_repo.utils.RoomAssets;
import com.example.sol_repo.utils.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AccountActivity extends AppCompatActivity {
    private final SimpleDateFormat databaseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    private SessionManager sessionManager;
    private FirebaseDatabaseDal firebaseDatabaseDal;
    private LinearLayout bookingsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        sessionManager = new SessionManager(this);
        firebaseDatabaseDal = new FirebaseDatabaseDal();

        bindViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!sessionManager.hasSession()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        bindProfile();
        bindBookings();
    }

    private void bindViews() {
        bookingsContainer = findViewById(R.id.listBookings);
        BottomNavHelper.setup(this, BottomNavHelper.Tab.PROFILE);
    }

    private void bindProfile() {
        firebaseDatabaseDal.getCustomer(sessionManager.getCustomerId(), customer -> {
            if (customer == null) {
                Toast.makeText(this, R.string.account_session_missing, Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }

            ((TextView) findViewById(R.id.txtAccountName)).setText(customer.getFullName());
            ((TextView) findViewById(R.id.txtAccountTier)).setText(formatMembershipTier(customer.getMembershipTier()));
            ((TextView) findViewById(R.id.txtAccountStatus)).setText(formatCustomerStatus(customer.getStatus()));
            ((TextView) findViewById(R.id.txtAccountMemberId)).setText(
                    getString(R.string.account_member_id_value, extractMemberNumber(customer.getCustomerId())));
            ((TextView) findViewById(R.id.txtAccountPhone)).setText(customer.getPhone());
            ((TextView) findViewById(R.id.txtAccountMemberSince)).setText(formatMemberSince(customer.getCreatedAt()));
            ((TextView) findViewById(R.id.txtAccountBirthday)).setText(formatBirthday(customer.getDob()));
            ((TextView) findViewById(R.id.txtAccountLanguage)).setText(formatLanguage(customer.getLanguage()));

            ImageLoader.loadCircle(findViewById(R.id.imgAccountAvatar), customer.getAvatarUrl(), R.drawable.ic_profile);
        });

        findViewById(R.id.btnEditProfile).setOnClickListener(view ->
                Toast.makeText(this, R.string.account_edit_profile_soon, Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnBookNewStay).setOnClickListener(view ->
                startActivity(new Intent(this, RoomBookingActivity.class)));
    }

    private void bindBookings() {
        firebaseDatabaseDal.getBookingsForCustomer(sessionManager.getCustomerId(), this::renderBookings);
    }

    private int extractMemberNumber(String customerId) {
        if (customerId == null) {
            return 0;
        }
        StringBuilder digits = new StringBuilder();
        for (char character : customerId.toCharArray()) {
            if (Character.isDigit(character)) {
                digits.append(character);
            }
        }
        try {
            return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString()) + 1044;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void renderBookings(List<BookingSummary> bookings) {
        bookingsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        if (bookings.isEmpty()) {
            View emptyView = inflater.inflate(R.layout.item_booking_empty, bookingsContainer, false);
            ((TextView) emptyView.findViewById(R.id.txtEmptyBookingTitle)).setText(R.string.account_no_current_stay);
            emptyView.findViewById(R.id.btnCreateBooking).setOnClickListener(view ->
                    startActivity(new Intent(this, RoomBookingActivity.class)));
            bookingsContainer.addView(emptyView);
            return;
        }

        String selectedId = reconcileSelectedSession(bookings);
        for (BookingSummary booking : bookings) {
            View itemView = inflater.inflate(R.layout.item_account_booking, bookingsContainer, false);
            bindBookingItem(itemView, booking, selectedId);
            bookingsContainer.addView(itemView);
        }
    }

    /**
     * Ensures the persisted session points to an active in-stay booking. If the stored one is no
     * longer active (checked out / not this stay), it falls back to the first active booking.
     * Returns the resolved active session booking id, or null if the guest has no active stay.
     */
    private String reconcileSelectedSession(List<BookingSummary> bookings) {
        String selected = sessionManager.getSelectedBookingId();
        BookingSummary firstActive = null;
        boolean selectedStillActive = false;
        for (BookingSummary booking : bookings) {
            if (isActiveStay(booking)) {
                if (firstActive == null) {
                    firstActive = booking;
                }
                if (booking.getBookingId().equals(selected)) {
                    selectedStillActive = true;
                }
            }
        }
        if (!selectedStillActive) {
            selected = firstActive != null ? firstActive.getBookingId() : null;
            if (selected != null) {
                sessionManager.setSelectedBookingId(selected);
            } else {
                sessionManager.clearSelectedBookingId();
            }
        }
        return selected;
    }

    private void bindBookingItem(View itemView, BookingSummary booking, String selectedId) {
        boolean active = isActiveStay(booking);
        boolean upcoming = isUpcomingStay(booking);
        boolean selectedSession = active && booking.getBookingId().equals(selectedId);
        TextView statusView = itemView.findViewById(R.id.txtAccountBookingStatus);

        itemView.setBackgroundResource(selectedSession
                ? R.drawable.bg_booking_selected
                : R.drawable.bg_dashboard_card);
        itemView.findViewById(R.id.txtSessionBadge)
                .setVisibility(selectedSession ? View.VISIBLE : View.GONE);

        ImageView bookingImage = itemView.findViewById(R.id.imgAccountBookingRoom);
        int bookingPlaceholder = RoomAssets.roomImageForName(booking.getRoomTypeName());
        firebaseDatabaseDal.getRoomTypeImageUrl(booking.getRoomTypeId(), imageUrl ->
                ImageLoader.load(bookingImage, imageUrl, bookingPlaceholder));
        ((TextView) itemView.findViewById(R.id.txtAccountBookingRoom)).setText(booking.getRoomTypeName());
        ((TextView) itemView.findViewById(R.id.txtAccountBookingDates)).setText(formatDateRange(booking));
        ((TextView) itemView.findViewById(R.id.txtAccountBookingGuests)).setText(
                getString(R.string.home_guest_count, booking.getNumGuests()));

        if (active) {
            statusView.setText(R.string.account_status_current);
            statusView.setBackgroundResource(R.drawable.bg_status_success);
            statusView.setTextColor(getColor(R.color.color_icon_success));
        } else if (upcoming) {
            statusView.setText(R.string.account_status_upcoming);
            statusView.setBackgroundResource(R.drawable.bg_status_upcoming);
            statusView.setTextColor(getColor(R.color.sol_gold_dark));
        } else {
            statusView.setText(R.string.account_status_completed);
            statusView.setBackgroundResource(R.drawable.bg_status_completed);
            statusView.setTextColor(getColor(R.color.sol_text_secondary));
        }

        itemView.findViewById(R.id.btnViewBookingDetails).setOnClickListener(view -> openBooking(booking));
        itemView.setOnClickListener(view -> openBooking(booking));
    }

    private void openBooking(BookingSummary booking) {
        if (!isActiveStay(booking)) {
            Toast.makeText(this, R.string.account_booking_locked, Toast.LENGTH_LONG).show();
            return;
        }

        // Switch the active session to this booking so Home/Services follow it.
        sessionManager.setSelectedBookingId(booking.getBookingId());

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_BOOKING_ID, booking.getBookingId());
        startActivity(intent);
    }

    private boolean isActiveStay(BookingSummary booking) {
        String status = booking.getStatus();
        if (!"checked_in".equals(status) && !"confirmed".equals(status)) {
            return false;
        }

        Date today = stripTime(new Date());
        Date checkIn = parseDate(booking.getCheckInDate());
        Date checkOut = parseDate(booking.getCheckOutDate());
        return checkIn != null && checkOut != null && !today.before(checkIn) && today.before(checkOut);
    }

    private boolean isUpcomingStay(BookingSummary booking) {
        Date today = stripTime(new Date());
        Date checkIn = parseDate(booking.getCheckInDate());
        return checkIn != null && today.before(checkIn);
    }

    private Date parseDate(String rawDate) {
        try {
            return stripTime(databaseDateFormat.parse(rawDate));
        } catch (ParseException | NullPointerException exception) {
            return null;
        }
    }

    private Date stripTime(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private String formatDateRange(BookingSummary booking) {
        Date checkIn = parseDate(booking.getCheckInDate());
        Date checkOut = parseDate(booking.getCheckOutDate());
        if (checkIn == null || checkOut == null) {
            return booking.getCheckInDate() + " - " + booking.getCheckOutDate();
        }
        return displayDateFormat.format(checkIn) + " - " + displayDateFormat.format(checkOut);
    }

    private String formatMemberSince(String createdAt) {
        if (createdAt == null || createdAt.length() < 10) {
            return getString(R.string.account_unknown_value);
        }
        Date createdDate = parseDate(createdAt.substring(0, 10));
        if (createdDate == null) {
            return getString(R.string.account_unknown_value);
        }
        return displayDateFormat.format(createdDate);
    }

    private String formatCustomerStatus(String status) {
        if ("in_stay".equals(status)) {
            return getString(R.string.account_status_active);
        }
        if ("pre_stay".equals(status)) {
            return getString(R.string.account_status_pre_stay);
        }
        return getString(R.string.account_status_active);
    }

    private String formatMembershipTier(String tier) {
        if ("vip".equals(tier)) {
            return getString(R.string.account_tier_vip);
        }
        if ("loyal".equals(tier)) {
            return getString(R.string.account_tier_loyal);
        }
        return getString(R.string.account_tier_new);
    }

    private String formatBirthday(String dob) {
        if (dob == null || dob.length() < 10) {
            return getString(R.string.account_unknown_value);
        }
        Date parsed = parseDate(dob.substring(0, 10));
        if (parsed == null) {
            return getString(R.string.account_unknown_value);
        }
        return displayDateFormat.format(parsed);
    }

    private String formatLanguage(String language) {
        if ("vi".equals(language)) {
            return getString(R.string.account_language_vi);
        }
        if ("en".equals(language)) {
            return getString(R.string.account_language_en);
        }
        return getString(R.string.account_language_default);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}

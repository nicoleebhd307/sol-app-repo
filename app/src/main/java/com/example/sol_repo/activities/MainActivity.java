package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.adapters.HomeServiceAdapter;
import com.example.sol_repo.adapters.RecommendationAdapter;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.utils.BottomNavHelper;
import com.example.sol_repo.utils.ImageLoader;
import com.example.sol_repo.utils.RoomAssets;
import com.example.sol_repo.utils.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";

    private SessionManager sessionManager;
    private FirebaseDatabaseDal firebaseDatabaseDal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);
        firebaseDatabaseDal = new FirebaseDatabaseDal();

        if (!sessionManager.hasSession()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        BottomNavHelper.setup(this, BottomNavHelper.Tab.HOME);
        bindHomeData();
    }

    private void bindHomeData() {
        resolveBooking(bookingSummary -> {
            if (bookingSummary == null || !isActiveStay(bookingSummary)) {
                Toast.makeText(this, R.string.booking_access_denied, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            // Remember this active booking as the session so the bottom nav follows it.
            sessionManager.setSelectedBookingId(bookingSummary.getBookingId());
            bindBooking(bookingSummary);
        });
    }

    private void bindBooking(BookingSummary bookingSummary) {
        ((TextView) findViewById(R.id.txtRoomType)).setText(
                bookingSummary.getRoomTypeName().toUpperCase(Locale.US));
        android.widget.ImageView roomImage = findViewById(R.id.imgHomeRoom);
        int roomPlaceholder = RoomAssets.roomImageForName(bookingSummary.getRoomTypeName());
        firebaseDatabaseDal.getRoomTypeImageUrl(bookingSummary.getRoomTypeId(), imageUrl ->
                ImageLoader.load(roomImage, imageUrl, roomPlaceholder));
        ((TextView) findViewById(R.id.txtBookingCode)).setText(bookingSummary.getBookingCode());
        ((TextView) findViewById(R.id.txtCheckIn)).setText(formatDate(bookingSummary.getCheckInDate()));
        ((TextView) findViewById(R.id.txtCheckOut)).setText(formatDate(bookingSummary.getCheckOutDate()));
        ((TextView) findViewById(R.id.txtGuests)).setText(
                getString(R.string.home_guest_count, bookingSummary.getNumGuests()));

        // Status field now shows the assigned room number for this booking.
        TextView roomNumberView = findViewById(R.id.txtBookingStatus);
        firebaseDatabaseDal.getRoomNumberForBooking(bookingSummary.getBookingId(), roomNumber ->
                roomNumberView.setText(roomNumber == null || roomNumber.isEmpty()
                        ? getString(R.string.account_unknown_value)
                        : roomNumber));

        LinearLayout servicesContainer = findViewById(R.id.listHomeServices);
        LinearLayout recommendationsContainer = findViewById(R.id.listRecommendations);

        firebaseDatabaseDal.getHomeServices(bookingSummary.getBookingId(), services ->
                new HomeServiceAdapter(this, services).renderInto(servicesContainer));
        firebaseDatabaseDal.getRecommendations(recommendations ->
                new RecommendationAdapter(this, recommendations).renderInto(recommendationsContainer));

        String activeBookingId = bookingSummary.getBookingId();
        findViewById(R.id.quickRoomService).setOnClickListener(view -> {
            Intent intent = new Intent(this, RoomServiceActivity.class);
            intent.putExtra(RoomServiceActivity.EXTRA_BOOKING_ID, activeBookingId);
            startActivity(intent);
        });

        // Remaining services are not yet available in this build.
        View.OnClickListener comingSoon = view ->
                Toast.makeText(this, R.string.nav_coming_soon, Toast.LENGTH_SHORT).show();
        findViewById(R.id.quickRestaurant).setOnClickListener(comingSoon);
        findViewById(R.id.quickSpa).setOnClickListener(comingSoon);
        findViewById(R.id.quickTransport).setOnClickListener(comingSoon);
        findViewById(R.id.quickSouvenir).setOnClickListener(comingSoon);
        findViewById(R.id.quickMore).setOnClickListener(comingSoon);
    }

    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }
        return fullName.trim().split("\\s+")[0];
    }

    private String formatDate(String rawDate) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
            return outputFormat.format(inputFormat.parse(rawDate));
        } catch (ParseException | NullPointerException exception) {
            return rawDate;
        }
    }

    private void resolveBooking(com.example.sol_repo.dals.FirebaseCallback<BookingSummary> callback) {
        String selectedBookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        if (selectedBookingId == null) {
            selectedBookingId = sessionManager.getSelectedBookingId();
        }
        if (selectedBookingId != null) {
            firebaseDatabaseDal.getBookingForCustomer(sessionManager.getCustomerId(), selectedBookingId, callback);
        } else {
            firebaseDatabaseDal.getCurrentBooking(sessionManager.getCustomerId(), callback);
        }
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

    private Date parseDate(String rawDate) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            return stripTime(inputFormat.parse(rawDate));
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

    private String formatStatus(String status) {
        if ("checked_in".equals(status)) {
            return "Confirmed";
        }
        if (status == null || status.isEmpty()) {
            return "";
        }
        return status.substring(0, 1).toUpperCase(Locale.US) + status.substring(1).replace('_', ' ');
    }
}

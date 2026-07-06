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
        String roomNumber = bookingSummary.getRoomNumber();
        if (roomNumber != null && !roomNumber.isEmpty()) {
            roomNumberView.setText(roomNumber);
        } else {
            firebaseDatabaseDal.getRoomNumberForBooking(bookingSummary.getBookingId(), number ->
                    roomNumberView.setText(number == null || number.isEmpty()
                            ? getString(R.string.account_unknown_value)
                            : number));
        }

        LinearLayout servicesContainer = findViewById(R.id.listHomeServices);
        LinearLayout recommendationsContainer = findViewById(R.id.listRecommendations);

        firebaseDatabaseDal.getHomeServices(bookingSummary.getBookingId(), services ->
                new HomeServiceAdapter(this, services).renderInto(servicesContainer));
        firebaseDatabaseDal.getRecommendations(recommendations ->
                new RecommendationAdapter(this, recommendations).renderInto(recommendationsContainer));

        bindServiceTiles(bookingSummary.getBookingId(), bookingSummary.getRoomTypeId());
    }

    private void bindServiceTiles(String activeBookingId, String roomTypeId) {
        findViewById(R.id.tileRoomService).setOnClickListener(view -> {
            Intent intent = new Intent(this, RoomServiceActivity.class);
            intent.putExtra(RoomServiceActivity.EXTRA_BOOKING_ID, activeBookingId);
            startActivity(intent);
        });
        findViewById(R.id.tileSouvenirs).setOnClickListener(view -> {
            Intent intent = new Intent(this, SouvenirStoreActivity.class);
            intent.putExtra(SouvenirStoreActivity.EXTRA_BOOKING_ID, activeBookingId);
            startActivity(intent);
        });

        findViewById(R.id.tileRestaurant).setOnClickListener(view -> {
            Intent intent = new Intent(this, DiningReservationActivity.class);
            intent.putExtra(DiningReservationActivity.EXTRA_BOOKING_ID, activeBookingId);
            startActivity(intent);
        });

        findViewById(R.id.tileSpa).setOnClickListener(view -> {
            Intent intent = new Intent(this, SpaServiceActivity.class);
            intent.putExtra(SpaServiceActivity.EXTRA_BOOKING_ID, activeBookingId);
            startActivity(intent);
        });

        // Airport transfer is a Suite-only perk: locked by default, unlocked once we confirm the tier.
        configureTransferTile(activeBookingId, roomTypeId);

        View.OnClickListener comingSoon = view ->
                Toast.makeText(this, R.string.nav_coming_soon, Toast.LENGTH_SHORT).show();
        findViewById(R.id.tileMore).setOnClickListener(comingSoon);
    }

    private void configureTransferTile(String activeBookingId, String roomTypeId) {
        View transferTile = findViewById(R.id.tileTransport);
        View lockBadge = findViewById(R.id.imgTransportLock);

        // Default to locked until the room category is confirmed to be Suite.
        transferTile.setAlpha(0.5f);
        lockBadge.setVisibility(View.VISIBLE);
        transferTile.setOnClickListener(view ->
                Toast.makeText(this, R.string.transfer_suite_only, Toast.LENGTH_SHORT).show());

        firebaseDatabaseDal.getRoomCategory(roomTypeId, category -> {
            if (!"suite".equalsIgnoreCase(category)) {
                return;
            }
            transferTile.setAlpha(1f);
            lockBadge.setVisibility(View.GONE);
            transferTile.setOnClickListener(view -> {
                Intent intent = new Intent(this, TransferActivity.class);
                intent.putExtra(TransferActivity.EXTRA_BOOKING_ID, activeBookingId);
                startActivity(intent);
            });
        });
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

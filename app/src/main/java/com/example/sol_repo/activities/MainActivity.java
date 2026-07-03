package com.example.sol_repo.activities;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.adapters.HomeServiceAdapter;
import com.example.sol_repo.adapters.RecommendationAdapter;
import com.example.sol_repo.dals.MockDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.utils.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private SessionManager sessionManager;
    private MockDatabaseDal mockDatabaseDal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);
        mockDatabaseDal = new MockDatabaseDal(this);

        bindHomeData();
    }

    private void bindHomeData() {
        TextView welcomeTextView = findViewById(R.id.txtHomeWelcome);
        TextView roomTypeTextView = findViewById(R.id.txtRoomType);
        TextView bookingCodeTextView = findViewById(R.id.txtBookingCode);
        TextView bookingStatusBadgeTextView = findViewById(R.id.txtBookingStatusBadge);
        TextView checkInTextView = findViewById(R.id.txtCheckIn);
        TextView checkOutTextView = findViewById(R.id.txtCheckOut);
        TextView guestsTextView = findViewById(R.id.txtGuests);
        TextView bookingStatusTextView = findViewById(R.id.txtBookingStatus);
        LinearLayout servicesContainer = findViewById(R.id.listHomeServices);
        LinearLayout recommendationsContainer = findViewById(R.id.listRecommendations);

        String firstName = extractFirstName(sessionManager.getFullName());
        welcomeTextView.setText(getString(R.string.home_welcome_customer, firstName));

        BookingSummary bookingSummary = mockDatabaseDal.getCurrentBooking(sessionManager.getCustomerId());
        if (bookingSummary == null) {
            Toast.makeText(this, R.string.dashboard_intro, Toast.LENGTH_SHORT).show();
            return;
        }

        roomTypeTextView.setText(bookingSummary.getRoomTypeName().toUpperCase(Locale.US));
        bookingCodeTextView.setText(bookingSummary.getBookingCode());
        bookingStatusBadgeTextView.setText(formatStatus(bookingSummary.getStatus()).toUpperCase(Locale.US));
        checkInTextView.setText(formatDate(bookingSummary.getCheckInDate()));
        checkOutTextView.setText(formatDate(bookingSummary.getCheckOutDate()));
        guestsTextView.setText(getString(R.string.home_guest_count, bookingSummary.getNumGuests()));
        bookingStatusTextView.setText(formatStatus(bookingSummary.getStatus()));

        new HomeServiceAdapter(this, mockDatabaseDal.getHomeServices(bookingSummary.getBookingId()))
                .renderInto(servicesContainer);
        new RecommendationAdapter(this, mockDatabaseDal.getRecommendations())
                .renderInto(recommendationsContainer);
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

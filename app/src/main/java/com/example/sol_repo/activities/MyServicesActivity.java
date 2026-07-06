package com.example.sol_repo.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.adapters.HomeServiceAdapter;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.HomeServiceItem;
import com.example.sol_repo.utils.BottomNavHelper;
import com.example.sol_repo.utils.SessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * "My Services" — lists every service booked for the current stay (restaurant, spa, transfer,
 * souvenirs). Food &amp; Drinks (room service) orders are intentionally excluded; those are tracked
 * under the Food &amp; Drinks tab. Tapping a booking shows its details.
 */
public class MyServicesActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";

    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;
    private String bookingId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_services);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        sessionManager = new SessionManager(this);

        bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        if (bookingId == null) {
            bookingId = sessionManager.getSelectedBookingId();
        }
        if (bookingId == null) {
            Toast.makeText(this, R.string.nav_no_active_stay, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        BottomNavHelper.setup(this, BottomNavHelper.Tab.STAY);
        findViewById(R.id.btnMyServicesBack).setOnClickListener(view -> finish());

        loadServices();
    }

    private void loadServices() {
        LinearLayout container = findViewById(R.id.listMyServices);
        TextView emptyView = findViewById(R.id.txtMyServicesEmpty);

        firebaseDatabaseDal.getHomeServices(bookingId, services -> {
            List<HomeServiceItem> filtered = new ArrayList<>();
            for (HomeServiceItem service : services) {
                // Exclude Food & Drinks (room service) orders from My Services.
                if (!"roomservice".equals(service.getIconType())) {
                    filtered.add(service);
                }
            }
            List<HomeServiceItem> ordered = sortByNewest(filtered);

            boolean empty = ordered.isEmpty();
            container.setVisibility(empty ? View.GONE : View.VISIBLE);
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);

            new HomeServiceAdapter(this, ordered)
                    .setOnServiceClickListener(this::showServiceDetail)
                    .renderInto(container);
        });
    }

    private void showServiceDetail(HomeServiceItem service) {
        String message = service.getSubtitle()
                + "\n\n" + getString(R.string.my_services_detail_status, service.getStatus());
        new AlertDialog.Builder(this)
                .setTitle(service.getTitle())
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Sorts services newest-first by their creation timestamp (nulls last). */
    public static List<HomeServiceItem> sortByNewest(List<HomeServiceItem> services) {
        List<HomeServiceItem> copy = new ArrayList<>(services);
        Collections.sort(copy, new Comparator<HomeServiceItem>() {
            @Override
            public int compare(HomeServiceItem a, HomeServiceItem b) {
                String ca = a.getCreatedAt();
                String cb = b.getCreatedAt();
                if (ca == null && cb == null) {
                    return 0;
                }
                if (ca == null) {
                    return 1;
                }
                if (cb == null) {
                    return -1;
                }
                return cb.compareTo(ca);
            }
        });
        return copy;
    }
}

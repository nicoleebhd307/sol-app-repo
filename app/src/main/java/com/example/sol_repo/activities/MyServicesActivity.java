package com.example.sol_repo.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.example.sol_repo.R;
import com.example.sol_repo.adapters.HomeServiceAdapter;
import com.example.sol_repo.dals.FirebaseCallback;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.HomeServiceItem;
import com.example.sol_repo.utils.BottomNavHelper;
import com.example.sol_repo.utils.SessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * "My Services" — lists every service booked for the current stay (restaurant, spa, transfer,
 * souvenirs). Food &amp; Drinks (room service) orders are intentionally excluded; those are tracked
 * under the Food &amp; Drinks tab. Tapping a booking shows its details.
 */
public class MyServicesActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";

    // Filter category keys map to HomeServiceItem.iconType ("all" shows everything).
    private static final String[] CATEGORY_KEYS = {"all", "restaurant", "wellness", "transfer", "souvenir"};
    private static final int[] CATEGORY_LABELS = {
            R.string.svc_filter_all, R.string.svc_filter_restaurant, R.string.svc_filter_spa,
            R.string.svc_filter_transfer, R.string.svc_filter_souvenir};

    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;
    private String bookingId;

    private final List<HomeServiceItem> allServices = new ArrayList<>();
    private String selectedCategory = "all";
    private String searchQuery = "";

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

        buildCategoryChips();
        bindSearch();
        loadServices();
    }

    private void loadServices() {
        firebaseDatabaseDal.getHomeServices(bookingId, services -> {
            allServices.clear();
            for (HomeServiceItem service : services) {
                // Exclude Food & Drinks (room service) orders from My Services.
                if (!"roomservice".equals(service.getIconType())) {
                    allServices.add(service);
                }
            }
            List<HomeServiceItem> ordered = sortByNewest(allServices);
            allServices.clear();
            allServices.addAll(ordered);
            applyFilters();
        });
    }

    /** Renders the services matching the selected category chip and the search query. */
    private void applyFilters() {
        LinearLayout container = findViewById(R.id.listMyServices);
        TextView emptyView = findViewById(R.id.txtMyServicesEmpty);

        List<HomeServiceItem> shown = new ArrayList<>();
        for (HomeServiceItem service : allServices) {
            if (!"all".equals(selectedCategory) && !selectedCategory.equals(service.getIconType())) {
                continue;
            }
            if (!searchQuery.isEmpty() && !matchesQuery(service)) {
                continue;
            }
            shown.add(service);
        }

        boolean empty = shown.isEmpty();
        container.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);

        new HomeServiceAdapter(this, shown)
                .setOnServiceClickListener(this::showServiceDetail)
                .renderInto(container);
    }

    private boolean matchesQuery(HomeServiceItem service) {
        String title = service.getTitle() == null ? "" : service.getTitle().toLowerCase(Locale.US);
        String subtitle = service.getSubtitle() == null ? "" : service.getSubtitle().toLowerCase(Locale.US);
        return title.contains(searchQuery) || subtitle.contains(searchQuery);
    }

    private void buildCategoryChips() {
        LinearLayout row = findViewById(R.id.rowServiceCategories);
        row.removeAllViews();
        for (int i = 0; i < CATEGORY_KEYS.length; i++) {
            String key = CATEGORY_KEYS[i];
            TextView chip = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(38));
            params.setMarginEnd(dpToPx(8));
            chip.setLayoutParams(params);
            chip.setPadding(dpToPx(16), 0, dpToPx(16), 0);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(13);
            chip.setTypeface(ResourcesCompat.getFont(this, R.font.plus_jakarta_sans));
            chip.setText(CATEGORY_LABELS[i]);
            chip.setOnClickListener(view -> {
                selectedCategory = key;
                styleCategoryChips();
                applyFilters();
            });
            row.addView(chip);
        }
        styleCategoryChips();
    }

    private void styleCategoryChips() {
        LinearLayout row = findViewById(R.id.rowServiceCategories);
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView chip = (TextView) row.getChildAt(i);
            boolean selected = CATEGORY_KEYS[i].equals(selectedCategory);
            chip.setBackgroundResource(selected
                    ? R.drawable.bg_chip_gold : R.drawable.bg_chip_unselected);
            chip.setTextColor(getColor(selected ? R.color.white : R.color.sol_text_primary));
        }
    }

    private void bindSearch() {
        EditText searchInput = findViewById(R.id.inputServiceSearch);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                searchQuery = text.toString().trim().toLowerCase(Locale.US);
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showServiceDetail(HomeServiceItem service) {
        String message = service.getSubtitle()
                + "\n\n" + getString(R.string.my_services_detail_status, service.getStatus());
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(service.getTitle())
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null);
        if (isCancellable(service)) {
            builder.setNegativeButton(R.string.cancel_booking, (dialog, which) -> confirmCancel(service));
        }
        builder.show();
    }

    /** A service can be cancelled while it is still pending or confirmed. */
    private boolean isCancellable(HomeServiceItem service) {
        if (service.getServiceType() == null || service.getRefId() == null) {
            return false;
        }
        String status = service.getStatus() == null ? "" : service.getStatus().toLowerCase(Locale.US);
        return status.contains("pending") || status.contains("confirmed");
    }

    private void confirmCancel(HomeServiceItem service) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_confirm_title)
                .setMessage(R.string.cancel_confirm_message)
                .setNegativeButton(R.string.cancel_keep, null)
                .setPositiveButton(R.string.cancel_booking, (dialog, which) ->
                        firebaseDatabaseDal.cancelService(service.getServiceType(), service.getRefId(),
                                new FirebaseCallback<Boolean>() {
                                    @Override
                                    public void onSuccess(Boolean ok) {
                                        Toast.makeText(MyServicesActivity.this,
                                                ok != null && ok ? R.string.cancel_done : R.string.cancel_failed,
                                                Toast.LENGTH_SHORT).show();
                                        if (ok != null && ok) {
                                            loadServices();
                                        }
                                    }

                                    @Override
                                    public void onError(String messageText) {
                                        Toast.makeText(MyServicesActivity.this, R.string.cancel_failed,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }))
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

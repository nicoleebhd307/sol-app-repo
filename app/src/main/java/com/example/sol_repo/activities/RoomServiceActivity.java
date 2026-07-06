package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.models.MenuItem;
import com.example.sol_repo.models.RoomServiceOrder;
import com.example.sol_repo.utils.BottomNavHelper;
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.ImageLoader;
import com.example.sol_repo.utils.RoomAssets;
import com.example.sol_repo.utils.RoomServiceCart;
import com.example.sol_repo.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RoomServiceActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";

    private static final String[] CATEGORY_KEYS = {"all", "breakfast", "main", "drinks", "cocktail", "dessert"};
    private static final int[] CATEGORY_LABELS = {
            R.string.rs_category_all,
            R.string.rs_category_breakfast,
            R.string.rs_category_main,
            R.string.rs_category_drinks,
            R.string.rs_category_cocktail,
            R.string.rs_category_dessert
    };

    private SessionManager sessionManager;
    private FirebaseDatabaseDal firebaseDatabaseDal;
    private BookingSummary booking;
    private List<MenuItem> allMenuItems;

    private LinearLayout menuContainer;
    private LinearLayout categoriesRow;
    private String selectedCategory = "all";
    private String searchQuery = "";

    // An order advances one lifecycle step for every STEP_DURATION_MS elapsed since it was placed;
    // the screen re-checks this every STATUS_UPDATE_INTERVAL_MS while visible.
    private static final long STEP_DURATION_MS = 5 * 60 * 1000L;
    private static final long STATUS_UPDATE_INTERVAL_MS = 60 * 1000L;
    private final SimpleDateFormat orderTimestampFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private Runnable statusTick;
    private boolean showAllOrders = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_service);

        sessionManager = new SessionManager(this);
        firebaseDatabaseDal = new FirebaseDatabaseDal();

        String bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        if (bookingId == null) {
            finish();
            return;
        }

        menuContainer = findViewById(R.id.listMenuItems);
        categoriesRow = findViewById(R.id.rowCategories);

        BottomNavHelper.setup(this, BottomNavHelper.Tab.SERVICES);

        statusTick = () -> {
            bindOrdersList();
            startStatusUpdates();
        };
        findViewById(R.id.btnViewAllOrders).setOnClickListener(view -> {
            showAllOrders = !showAllOrders;
            bindOrdersList();
        });

        firebaseDatabaseDal.getBookingForCustomer(sessionManager.getCustomerId(), bookingId, resolvedBooking -> {
            if (resolvedBooking == null) {
                Toast.makeText(this, R.string.booking_access_denied, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            this.booking = resolvedBooking;
            RoomServiceCart.startFor(booking.getBookingId());

            firebaseDatabaseDal.getMenuItems(items -> {
                allMenuItems = items;
                prefillOrderNote();
                buildCategoryChips();
                bindSearch();
                refreshActiveOrderAndMenu();
                startStatusUpdates();
            });
        });

        findViewById(R.id.btnViewCart).setOnClickListener(view -> confirmOrder());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (booking != null && allMenuItems != null) {
            refreshActiveOrderAndMenu();
            startStatusUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopStatusUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStatusUpdates();
    }

    private void refreshActiveOrderAndMenu() {
        bindOrdersList();
        renderMenu();
        renderOrderPreview();
    }

    private void prefillOrderNote() {
        // Prefill the kitchen note from any note already stored on the cart.
        ((EditText) findViewById(R.id.inputPreviewNote)).setText(RoomServiceCart.getKitchenNote());
    }

    private static final String[] STATUS_FLOW = {"preparing", "on_the_way", "delivered"};

    private static boolean isTerminal(String status) {
        return "delivered".equals(status) || "cancelled".equals(status);
    }

    private static int flowIndex(String status) {
        for (int i = 0; i < STATUS_FLOW.length; i++) {
            if (STATUS_FLOW[i].equals(status)) {
                return i;
            }
        }
        return 0; // "confirmed"/unknown are treated as the "preparing" baseline
    }

    /**
     * Derives an order's status from how long ago it was placed: it advances one step per
     * {@link #STEP_DURATION_MS} (preparing → on_the_way → delivered) and never moves backwards.
     */
    private String expectedStatusFor(RoomServiceOrder order) {
        String current = order.getStatus();
        if ("cancelled".equals(current)) {
            return current;
        }
        long orderedAtMillis;
        try {
            orderedAtMillis = orderTimestampFormat.parse(order.getOrderedAt()).getTime();
        } catch (Exception exception) {
            return current;
        }
        long steps = (System.currentTimeMillis() - orderedAtMillis) / STEP_DURATION_MS;
        int elapsedIndex = (int) Math.max(0, Math.min(steps, STATUS_FLOW.length - 1));
        int targetIndex = Math.max(flowIndex(current), elapsedIndex);
        return STATUS_FLOW[targetIndex];
    }

    /**
     * Lists the active room service orders (delivered ones drop off). Each order's status is
     * derived from elapsed time and persisted, so it keeps advancing across sessions. By default
     * only the latest order is shown; "View all" reveals the rest. No timeline here — that lives on
     * the tracking/detail page.
     */
    private void bindOrdersList() {
        firebaseDatabaseDal.getRoomServiceOrders(booking.getBookingId(), orders -> {
            View section = findViewById(R.id.sectionCurrentOrders);
            LinearLayout container = findViewById(R.id.listCurrentOrders);
            TextView viewAll = findViewById(R.id.btnViewAllOrders);
            container.removeAllViews();

            List<RoomServiceOrder> active = new ArrayList<>();
            List<String> activeStatuses = new ArrayList<>();
            if (orders != null) {
                for (RoomServiceOrder order : orders) {
                    String expected = expectedStatusFor(order);
                    // Persist the advancement so the new status sticks in the database.
                    if (!expected.equals(order.getStatus())) {
                        firebaseDatabaseDal.updateRoomServiceOrderStatus(
                                order.getOrderId(), expected, result -> {
                                });
                    }
                    if (!isTerminal(expected)) {
                        active.add(order);
                        activeStatuses.add(expected);
                    }
                }
            }

            if (active.isEmpty()) {
                section.setVisibility(View.GONE);
                return;
            }

            section.setVisibility(View.VISIBLE);

            // Only show the "View all" toggle when there is more than one active order.
            if (active.size() > 1) {
                viewAll.setVisibility(View.VISIBLE);
                viewAll.setText(showAllOrders ? R.string.rs_show_less : R.string.rs_view_all_orders);
            } else {
                viewAll.setVisibility(View.GONE);
                showAllOrders = false;
            }

            int limit = showAllOrders ? active.size() : 1;
            LayoutInflater inflater = LayoutInflater.from(this);
            for (int i = 0; i < limit; i++) {
                RoomServiceOrder order = active.get(i);
                View row = inflater.inflate(R.layout.item_rs_order, container, false);
                ((TextView) row.findViewById(R.id.txtRsOrderCode)).setText(
                        getString(R.string.rs_order_id_label, order.getOrderCode()));
                ((TextView) row.findViewById(R.id.txtRsOrderMeta)).setText(
                        getString(R.string.rs_items_count, order.getItemCount())
                                + " · " + CurrencyFormatter.format(order.getTotalAmount()));
                ((TextView) row.findViewById(R.id.txtRsOrderStatus)).setText(
                        formatStatus(activeStatuses.get(i)).toUpperCase(Locale.US));

                row.findViewById(R.id.btnRsOrderTrack).setOnClickListener(
                        view -> openTracking(order.getOrderId()));
                row.setOnClickListener(view -> openTracking(order.getOrderId()));
                container.addView(row);
            }
        });
    }

    // ----- Auto status progression (re-checks elapsed time on a timer) -------------------------

    private void startStatusUpdates() {
        stopStatusUpdates();
        statusHandler.postDelayed(statusTick, STATUS_UPDATE_INTERVAL_MS);
    }

    private void stopStatusUpdates() {
        statusHandler.removeCallbacks(statusTick);
    }

    private void buildCategoryChips() {
        categoriesRow.removeAllViews();
        for (int i = 0; i < CATEGORY_KEYS.length; i++) {
            String key = CATEGORY_KEYS[i];
            TextView chip = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(38));
            params.setMarginEnd(dpToPx(8));
            chip.setLayoutParams(params);
            chip.setPadding(dpToPx(16), 0, dpToPx(16), 0);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setTextSize(13);
            chip.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.plus_jakarta_sans));
            chip.setText(CATEGORY_LABELS[i]);
            chip.setOnClickListener(view -> {
                selectedCategory = key;
                styleCategoryChips();
                renderMenu();
            });
            categoriesRow.addView(chip);
        }
        styleCategoryChips();
    }

    private void styleCategoryChips() {
        for (int i = 0; i < categoriesRow.getChildCount(); i++) {
            TextView chip = (TextView) categoriesRow.getChildAt(i);
            boolean selected = CATEGORY_KEYS[i].equals(selectedCategory);
            chip.setBackgroundResource(selected
                    ? R.drawable.bg_chip_gold
                    : R.drawable.bg_chip_unselected);
            chip.setTextColor(getColor(selected ? R.color.white : R.color.sol_text_primary));
        }
    }

    private void bindSearch() {
        EditText searchInput = findViewById(R.id.inputMenuSearch);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                searchQuery = text.toString().trim().toLowerCase(Locale.US);
                renderMenu();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void renderMenu() {
        menuContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int shownCount = 0;

        for (MenuItem item : allMenuItems) {
            if (!"all".equals(selectedCategory) && !selectedCategory.equals(item.getCategory())) {
                continue;
            }
            if (!searchQuery.isEmpty()
                    && !item.getItemName().toLowerCase(Locale.US).contains(searchQuery)) {
                continue;
            }

            View itemView = inflater.inflate(R.layout.item_menu_item, menuContainer, false);
            bindMenuItem(itemView, item);
            menuContainer.addView(itemView);
            shownCount++;
        }

        findViewById(R.id.txtNoMenuItems).setVisibility(shownCount == 0 ? View.VISIBLE : View.GONE);
    }

    private void bindMenuItem(View itemView, MenuItem item) {
        ImageLoader.load(itemView.findViewById(R.id.imgMenuItem), item.getImageUrl(), RoomAssets.MENU_PLACEHOLDER);
        ((TextView) itemView.findViewById(R.id.txtMenuName)).setText(item.getItemName());
        ((TextView) itemView.findViewById(R.id.txtMenuDescription)).setText(item.getDescription());
        ((TextView) itemView.findViewById(R.id.txtMenuPrice)).setText(CurrencyFormatter.format(item.getPrice()));

        View addButton = itemView.findViewById(R.id.btnMenuAdd);
        View stepper = itemView.findViewById(R.id.stepperMenu);
        TextView qtyView = itemView.findViewById(R.id.txtMenuQty);

        Runnable renderQty = () -> {
            int quantity = RoomServiceCart.getQuantity(item.getMenuItemId());
            addButton.setVisibility(quantity == 0 ? View.VISIBLE : View.GONE);
            stepper.setVisibility(quantity == 0 ? View.GONE : View.VISIBLE);
            qtyView.setText(String.valueOf(quantity));
            renderOrderPreview();
        };

        addButton.setOnClickListener(view -> {
            RoomServiceCart.setQuantity(item, 1);
            renderQty.run();
        });
        itemView.findViewById(R.id.btnMenuPlus).setOnClickListener(view -> {
            RoomServiceCart.setQuantity(item, RoomServiceCart.getQuantity(item.getMenuItemId()) + 1);
            renderQty.run();
        });
        itemView.findViewById(R.id.btnMenuMinus).setOnClickListener(view -> {
            RoomServiceCart.setQuantity(item, RoomServiceCart.getQuantity(item.getMenuItemId()) - 1);
            renderQty.run();
        });

        int quantity = RoomServiceCart.getQuantity(item.getMenuItemId());
        addButton.setVisibility(quantity == 0 ? View.VISIBLE : View.GONE);
        stepper.setVisibility(quantity == 0 ? View.GONE : View.VISIBLE);
        qtyView.setText(String.valueOf(quantity));
    }

    private void renderOrderPreview() {
        View previewCard = findViewById(R.id.cardOrderPreview);
        View viewCartButton = findViewById(R.id.btnViewCart);

        if (RoomServiceCart.isEmpty()) {
            previewCard.setVisibility(View.GONE);
            viewCartButton.setVisibility(View.GONE);
            return;
        }

        previewCard.setVisibility(View.VISIBLE);
        viewCartButton.setVisibility(View.VISIBLE);

        ((TextView) findViewById(R.id.txtPreviewTitle)).setText(
                getString(R.string.rs_your_order, RoomServiceCart.getItemCount()));

        LinearLayout linesContainer = findViewById(R.id.listPreviewLines);
        linesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (RoomServiceCart.Entry entry : RoomServiceCart.getEntries()) {
            View lineView = inflater.inflate(R.layout.item_order_line, linesContainer, false);
            ImageLoader.load(lineView.findViewById(R.id.imgOrderLine), entry.item.getImageUrl(),
                    RoomAssets.MENU_PLACEHOLDER);
            ((TextView) lineView.findViewById(R.id.txtOrderLineName)).setText(entry.item.getItemName());
            ((TextView) lineView.findViewById(R.id.txtOrderLineQty)).setText(
                    getString(R.string.rs_quantity_times, entry.quantity));
            ((TextView) lineView.findViewById(R.id.txtOrderLineTotal)).setText(
                    CurrencyFormatter.format(entry.item.getPrice() * entry.quantity));
            linesContainer.addView(lineView);
        }

        ((TextView) findViewById(R.id.txtPreviewSubtotal)).setText(
                CurrencyFormatter.format(RoomServiceCart.getSubtotal()));
        ((TextView) findViewById(R.id.txtPreviewTotal)).setText(
                CurrencyFormatter.format(RoomServiceCart.getTotal()));
    }

    /**
     * The cart page has been removed from the flow: confirming the order here saves the kitchen
     * note captured in the "your order" card and jumps straight to payment.
     */
    private void confirmOrder() {
        if (RoomServiceCart.isEmpty()) {
            Toast.makeText(this, R.string.rs_cart_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        EditText noteInput = findViewById(R.id.inputPreviewNote);
        if (noteInput != null) {
            RoomServiceCart.setKitchenNote(noteInput.getText().toString().trim());
        }
        Intent intent = new Intent(this, RoomServicePaymentActivity.class);
        intent.putExtra(EXTRA_BOOKING_ID, booking.getBookingId());
        startActivity(intent);
    }

    private void openTracking(String orderId) {
        Intent intent = new Intent(this, RoomServiceTrackingActivity.class);
        intent.putExtra(EXTRA_BOOKING_ID, booking.getBookingId());
        intent.putExtra(RoomServiceTrackingActivity.EXTRA_ORDER_ID, orderId);
        startActivity(intent);
    }

    private String formatStatus(String status) {
        if (status == null || status.isEmpty()) {
            return "";
        }
        return status.substring(0, 1).toUpperCase(Locale.US) + status.substring(1).replace('_', ' ');
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

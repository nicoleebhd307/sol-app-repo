package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
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
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.ImageLoader;
import com.example.sol_repo.utils.OrderTimeline;
import com.example.sol_repo.utils.RoomAssets;
import com.example.sol_repo.utils.RoomServiceCart;
import com.example.sol_repo.utils.SessionManager;

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
                bindStayCard();
                buildCategoryChips();
                bindSearch();
                refreshActiveOrderAndMenu();
            });
        });

        findViewById(R.id.btnViewCart).setOnClickListener(view -> openCart());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (booking != null && allMenuItems != null) {
            refreshActiveOrderAndMenu();
        }
    }

    private void refreshActiveOrderAndMenu() {
        bindCurrentOrderCard();
        renderMenu();
        renderOrderPreview();
    }

    private void bindStayCard() {
        ((TextView) findViewById(R.id.txtStayRoomType)).setText(
                booking.getRoomTypeName().toUpperCase(Locale.US));
        firebaseDatabaseDal.getRoomNumberForBooking(booking.getBookingId(), roomNumber -> {
            ((TextView) findViewById(R.id.txtStayRoomNumber)).setText(
                    getString(R.string.rs_room_label, roomNumber));
            ((TextView) findViewById(R.id.txtDeliverTo)).setText(
                    getString(R.string.rs_deliver_to, getString(R.string.rs_room_label, roomNumber)));
        });
        ((TextView) findViewById(R.id.txtStayBookingCode)).setText(
                getString(R.string.booking_id_label, booking.getBookingCode()));
        ((TextView) findViewById(R.id.txtStayStatus)).setText(
                booking.getStatus().replace('_', ' ').toUpperCase(Locale.US));
    }

    private void bindCurrentOrderCard() {
        firebaseDatabaseDal.getActiveRoomServiceOrder(booking.getBookingId(), order -> {
            View card = findViewById(R.id.cardCurrentOrder);
            if (order == null) {
                card.setVisibility(View.GONE);
                return;
            }

            card.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.txtCurrentOrderCode)).setText(
                    getString(R.string.rs_order_id_label, order.getOrderCode()));
            TextView statusView = findViewById(R.id.txtCurrentOrderStatus);
            statusView.setText(formatStatus(order.getStatus()).toUpperCase(Locale.US));
            ((TextView) findViewById(R.id.txtCurrentOrderEta)).setText(
                    getString(R.string.rs_eta_label) + " " + getString(R.string.rs_eta_value));
            ((TextView) findViewById(R.id.txtCurrentOrderItems)).setText(
                    getString(R.string.rs_items_count, order.getItemCount()));
            ((TextView) findViewById(R.id.txtCurrentOrderTotal)).setText(
                    CurrencyFormatter.format(order.getTotalAmount()));

            OrderTimeline.render(this, findViewById(R.id.rowOrderProgress),
                    OrderTimeline.stepIndexFor(order.getStatus()), null);

            findViewById(R.id.btnTrackOrder).setOnClickListener(view -> openTracking(order.getOrderId()));
        });
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
            chip.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.inter));
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

    private void openCart() {
        if (RoomServiceCart.isEmpty()) {
            Toast.makeText(this, R.string.rs_cart_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, RoomServiceCartActivity.class);
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

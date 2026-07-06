package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.StoreProduct;
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.ImageLoader;
import com.example.sol_repo.utils.RoomAssets;
import com.example.sol_repo.utils.SessionManager;
import com.example.sol_repo.utils.StoreCart;

import java.util.List;

public class SouvenirStoreActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID = "booking_id";

    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;
    private String bookingId;

    private GridLayout productsGrid;
    private TextView cartBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_souvenir_store);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        sessionManager = new SessionManager(this);

        bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        if (bookingId == null) {
            finish();
            return;
        }

        productsGrid = findViewById(R.id.gridProducts);
        cartBadge = findViewById(R.id.txtCartBadge);

        firebaseDatabaseDal.getBookingForCustomer(sessionManager.getCustomerId(), bookingId, booking -> {
            if (booking == null) {
                Toast.makeText(this, R.string.booking_access_denied, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            StoreCart.startFor(bookingId);
            loadProducts();
        });

        findViewById(R.id.btnStoreBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnStoreCart).setOnClickListener(view -> openCart());
        findViewById(R.id.cardPromo).setOnClickListener(view ->
                Toast.makeText(this, R.string.store_promo_desc, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
        // Re-render so quantities reflect edits made in the cart screen.
        if (productsGrid != null && productsGrid.getChildCount() > 0) {
            loadProducts();
        }
    }

    private void loadProducts() {
        firebaseDatabaseDal.getStoreProducts(this::renderProducts);
    }

    private void renderProducts(List<StoreProduct> products) {
        productsGrid.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (StoreProduct product : products) {
            View card = inflater.inflate(R.layout.item_souvenir_product, productsGrid, false);
            bindProduct(card, product);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            productsGrid.addView(card, params);
        }
        updateCartBadge();
    }

    private void bindProduct(View card, StoreProduct product) {
        ImageLoader.load(card.findViewById(R.id.imgProduct), product.getImageUrl(),
                RoomAssets.MENU_PLACEHOLDER);
        ((TextView) card.findViewById(R.id.txtProductName)).setText(product.getProductName());
        ((TextView) card.findViewById(R.id.txtProductDescription)).setText(product.getDescription());
        ((TextView) card.findViewById(R.id.txtProductPrice)).setText(
                CurrencyFormatter.format(product.getPrice()));

        TextView qtyView = card.findViewById(R.id.txtProductQty);
        qtyView.setText(String.valueOf(StoreCart.getQuantity(product.getProductId())));

        card.findViewById(R.id.btnProductPlus).setOnClickListener(view -> {
            StoreCart.setQuantity(product, StoreCart.getQuantity(product.getProductId()) + 1);
            qtyView.setText(String.valueOf(StoreCart.getQuantity(product.getProductId())));
            updateCartBadge();
        });
        card.findViewById(R.id.btnProductMinus).setOnClickListener(view -> {
            StoreCart.setQuantity(product, StoreCart.getQuantity(product.getProductId()) - 1);
            qtyView.setText(String.valueOf(StoreCart.getQuantity(product.getProductId())));
            updateCartBadge();
        });
    }

    private void updateCartBadge() {
        int count = StoreCart.getItemCount();
        cartBadge.setText(String.valueOf(count));
        cartBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private void openCart() {
        if (StoreCart.isEmpty()) {
            Toast.makeText(this, R.string.store_cart_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, SouvenirCartActivity.class);
        intent.putExtra(EXTRA_BOOKING_ID, bookingId);
        startActivity(intent);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

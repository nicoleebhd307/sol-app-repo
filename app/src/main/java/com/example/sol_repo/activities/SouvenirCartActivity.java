package com.example.sol_repo.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseCallback;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.models.OrderCreationResult;
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.ImageLoader;
import com.example.sol_repo.utils.MomoClient;
import com.example.sol_repo.utils.RoomAssets;
import com.example.sol_repo.utils.SessionManager;
import com.example.sol_repo.utils.StoreCart;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

public class SouvenirCartActivity extends AppCompatActivity {
    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;
    private String bookingId;
    private String roomNumber = "";

    private LinearLayout itemsContainer;
    private TextView checkoutButton;

    private DatabaseReference paymentStatusRef;
    private ValueEventListener paymentStatusListener;
    private boolean paymentHandled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_souvenir_cart);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        sessionManager = new SessionManager(this);

        bookingId = getIntent().getStringExtra(SouvenirStoreActivity.EXTRA_BOOKING_ID);
        if (bookingId == null || StoreCart.isEmpty()) {
            finish();
            return;
        }

        itemsContainer = findViewById(R.id.listCartItems);
        checkoutButton = findViewById(R.id.btnCheckout);

        firebaseDatabaseDal.getRoomNumberForBooking(bookingId, number -> {
            roomNumber = number;
            ((TextView) findViewById(R.id.txtRoomNumber)).setText(number);
        });

        findViewById(R.id.btnCartBack).setOnClickListener(view -> finish());
        checkoutButton.setOnClickListener(view -> checkout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (StoreCart.isEmpty()) {
            finish();
            return;
        }
        renderCart();
    }

    private void renderCart() {
        itemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        java.util.List<StoreCart.Entry> entries = StoreCart.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            StoreCart.Entry entry = entries.get(i);
            View row = inflater.inflate(R.layout.item_souvenir_cart_line, itemsContainer, false);

            ImageLoader.load(row.findViewById(R.id.imgCartProduct), entry.product.getImageUrl(),
                    RoomAssets.MENU_PLACEHOLDER);
            ((TextView) row.findViewById(R.id.txtCartProductName)).setText(entry.product.getProductName());
            ((TextView) row.findViewById(R.id.txtCartProductQty)).setText(String.valueOf(entry.quantity));
            ((TextView) row.findViewById(R.id.txtCartProductPrice)).setText(
                    CurrencyFormatter.format(entry.product.getPrice() * entry.quantity));

            row.findViewById(R.id.btnCartMinus).setOnClickListener(view -> {
                StoreCart.setQuantity(entry.product, entry.quantity - 1);
                if (StoreCart.isEmpty()) {
                    finish();
                } else {
                    renderCart();
                }
            });

            row.findViewById(R.id.btnCartPlus).setOnClickListener(view -> {
                StoreCart.setQuantity(entry.product, entry.quantity + 1);
                renderCart();
            });

            row.findViewById(R.id.btnCartRemove).setOnClickListener(view -> {
                StoreCart.setQuantity(entry.product, 0);
                if (StoreCart.isEmpty()) {
                    finish();
                } else {
                    renderCart();
                }
            });

            itemsContainer.addView(row);
            if (i < entries.size() - 1) {
                itemsContainer.addView(makeDivider());
            }
        }

        ((TextView) findViewById(R.id.txtSubtotal)).setText(CurrencyFormatter.format(StoreCart.getSubtotal()));
        ((TextView) findViewById(R.id.txtServiceCharge)).setText(CurrencyFormatter.format(StoreCart.getServiceCharge()));
        ((TextView) findViewById(R.id.txtTax)).setText(CurrencyFormatter.format(StoreCart.getTax()));
        ((TextView) findViewById(R.id.txtTotal)).setText(CurrencyFormatter.format(StoreCart.getTotal()));
        checkoutButton.setText(getString(R.string.store_secure_checkout,
                CurrencyFormatter.format(StoreCart.getTotal())));
    }

    private View makeDivider() {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(getColor(R.color.sol_border));
        return divider;
    }

    private void checkout() {
        if (StoreCart.isEmpty()) {
            Toast.makeText(this, R.string.store_cart_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        // Pay via MoMo first; the store order is created only once the IPN confirms success.
        startMomoPayment();
    }

    private void startMomoPayment() {
        checkoutButton.setEnabled(false);
        Toast.makeText(this, R.string.momo_starting, Toast.LENGTH_SHORT).show();
        int amount = (int) Math.round(StoreCart.getTotal());
        MomoClient.createPayment(amount, bookingId, "Souvenir order", "store",
                new MomoClient.CreateCallback() {
                    @Override
                    public void onCreated(String orderId, String payUrl) {
                        observePayment(orderId);
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(payUrl)));
                        Toast.makeText(SouvenirCartActivity.this, R.string.momo_waiting,
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        checkoutButton.setEnabled(true);
                        Toast.makeText(SouvenirCartActivity.this, R.string.momo_error,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void observePayment(String orderId) {
        detachPaymentListener();
        paymentHandled = false;
        paymentStatusRef = firebaseDatabaseDal.getPaymentStatusRef(orderId);
        paymentStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (paymentHandled) {
                    return;
                }
                String status = snapshot.getValue(String.class);
                if ("success".equals(status)) {
                    paymentHandled = true;
                    detachPaymentListener();
                    finalizeOrder();
                } else if ("failed".equals(status)) {
                    paymentHandled = true;
                    detachPaymentListener();
                    checkoutButton.setEnabled(true);
                    Toast.makeText(SouvenirCartActivity.this, R.string.momo_failed,
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
            }
        };
        paymentStatusRef.addValueEventListener(paymentStatusListener);
    }

    private void detachPaymentListener() {
        if (paymentStatusRef != null && paymentStatusListener != null) {
            paymentStatusRef.removeEventListener(paymentStatusListener);
        }
    }

    private void finalizeOrder() {
        double total = Math.round(StoreCart.getTotal() * 100) / 100.0;
        int itemCount = StoreCart.getItemCount();
        checkoutButton.setEnabled(false);

        firebaseDatabaseDal.createStoreOrder(
                bookingId,
                sessionManager.getCustomerId(),
                StoreCart.getEntries(),
                StoreCart.getSubtotal(),
                StoreCart.getServiceCharge(),
                StoreCart.getTax(),
                total,
                roomNumber,
                "e_wallet",
                new FirebaseCallback<OrderCreationResult>() {
                    @Override
                    public void onSuccess(OrderCreationResult result) {
                        checkoutButton.setEnabled(true);
                        if (result == null) {
                            Toast.makeText(SouvenirCartActivity.this,
                                    R.string.store_order_failed, Toast.LENGTH_LONG).show();
                            return;
                        }

                        StoreCart.clear();

                        Intent intent = new Intent(SouvenirCartActivity.this, SouvenirConfirmActivity.class);
                        intent.putExtra(SouvenirConfirmActivity.EXTRA_ORDER_CODE, result.getOrderCode());
                        intent.putExtra(SouvenirConfirmActivity.EXTRA_ITEM_COUNT, itemCount);
                        intent.putExtra(SouvenirConfirmActivity.EXTRA_TOTAL, total);
                        intent.putExtra(SouvenirStoreActivity.EXTRA_BOOKING_ID, bookingId);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        checkoutButton.setEnabled(true);
                        Toast.makeText(SouvenirCartActivity.this,
                                R.string.store_order_failed, Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachPaymentListener();
    }
}

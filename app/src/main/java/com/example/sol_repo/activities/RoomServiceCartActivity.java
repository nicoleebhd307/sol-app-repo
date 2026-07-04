package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
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
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.ImageLoader;
import com.example.sol_repo.utils.RoomAssets;
import com.example.sol_repo.utils.RoomServiceCart;
import com.example.sol_repo.utils.SessionManager;

import java.util.Locale;

public class RoomServiceCartActivity extends AppCompatActivity {
    private FirebaseDatabaseDal firebaseDatabaseDal;
    private BookingSummary booking;
    private EditText noteInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_service_cart);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        SessionManager sessionManager = new SessionManager(this);

        String bookingId = getIntent().getStringExtra(RoomServiceActivity.EXTRA_BOOKING_ID);
        if (bookingId == null) {
            finish();
            return;
        }

        firebaseDatabaseDal.getBookingForCustomer(sessionManager.getCustomerId(), bookingId, resolvedBooking -> {
            if (resolvedBooking == null) {
                finish();
                return;
            }
            this.booking = resolvedBooking;
            bindBookingCard();
            if (RoomServiceCart.isEmpty()) {
                finish();
            } else {
                renderCart();
            }
        });

        noteInput = findViewById(R.id.inputKitchenNote);
        noteInput.setText(RoomServiceCart.getKitchenNote());

        findViewById(R.id.btnCartBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnConfirmOrder).setOnClickListener(view -> confirmOrder());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (RoomServiceCart.isEmpty()) {
            finish();
            return;
        }
        renderCart();
    }

    private void bindBookingCard() {
        View card = findViewById(R.id.cardCartBooking);
        ((TextView) card.findViewById(R.id.txtRsRoomType)).setText(
                booking.getRoomTypeName().toUpperCase(Locale.US));
        firebaseDatabaseDal.getRoomNumberForBooking(booking.getBookingId(), roomNumber ->
                ((TextView) card.findViewById(R.id.txtRsRoomNumber)).setText(
                        getString(R.string.rs_deliver_to, getString(R.string.rs_room_label, roomNumber))
                                .replace("\n", " ")));
        ((TextView) card.findViewById(R.id.txtRsBookingCode)).setText(
                getString(R.string.booking_id_label, booking.getBookingCode()));
    }

    private void renderCart() {
        ((TextView) findViewById(R.id.txtCartItemsTitle)).setText(
                getString(R.string.rs_your_items, RoomServiceCart.getItemCount()));

        LinearLayout itemsContainer = findViewById(R.id.listCartItems);
        itemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (RoomServiceCart.Entry entry : RoomServiceCart.getEntries()) {
            View itemView = inflater.inflate(R.layout.item_cart_line, itemsContainer, false);

            ImageLoader.load(itemView.findViewById(R.id.imgCartItem), entry.item.getImageUrl(),
                    RoomAssets.MENU_PLACEHOLDER);
            ((TextView) itemView.findViewById(R.id.txtCartItemName)).setText(entry.item.getItemName());
            ((TextView) itemView.findViewById(R.id.txtCartItemDescription))
                    .setText(entry.item.getDescription());
            ((TextView) itemView.findViewById(R.id.txtCartItemPrice))
                    .setText(CurrencyFormatter.format(entry.item.getPrice()));
            ((TextView) itemView.findViewById(R.id.txtCartQty)).setText(String.valueOf(entry.quantity));
            ((TextView) itemView.findViewById(R.id.txtCartLineTotal))
                    .setText(CurrencyFormatter.format(entry.item.getPrice() * entry.quantity));

            itemView.findViewById(R.id.btnCartPlus).setOnClickListener(view -> {
                RoomServiceCart.setQuantity(entry.item,
                        RoomServiceCart.getQuantity(entry.item.getMenuItemId()) + 1);
                renderCart();
            });
            itemView.findViewById(R.id.btnCartMinus).setOnClickListener(view -> {
                RoomServiceCart.setQuantity(entry.item,
                        RoomServiceCart.getQuantity(entry.item.getMenuItemId()) - 1);
                if (RoomServiceCart.isEmpty()) {
                    finish();
                } else {
                    renderCart();
                }
            });

            itemsContainer.addView(itemView);
        }

        ((TextView) findViewById(R.id.txtCartSubtotal)).setText(
                CurrencyFormatter.format(RoomServiceCart.getSubtotal()));
        ((TextView) findViewById(R.id.txtCartServiceCharge)).setText(
                CurrencyFormatter.format(RoomServiceCart.getServiceCharge()));
        ((TextView) findViewById(R.id.txtCartTotal)).setText(
                CurrencyFormatter.format(RoomServiceCart.getTotal()));
    }

    private void confirmOrder() {
        if (RoomServiceCart.isEmpty()) {
            Toast.makeText(this, R.string.rs_cart_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        RoomServiceCart.setKitchenNote(noteInput.getText().toString().trim());

        Intent intent = new Intent(this, RoomServicePaymentActivity.class);
        intent.putExtra(RoomServiceActivity.EXTRA_BOOKING_ID, booking.getBookingId());
        startActivity(intent);
    }
}

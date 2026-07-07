package com.example.sol_repo.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.models.OrderLine;
import com.example.sol_repo.models.RoomServiceOrder;
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.ImageLoader;
import com.example.sol_repo.utils.OrderTimeline;
import com.example.sol_repo.utils.RoomAssets;
import com.example.sol_repo.utils.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class RoomServiceTrackingActivity extends AppCompatActivity {
    public static final String EXTRA_ORDER_ID = "order_id";
    public static final String EXTRA_SHOW_SUCCESS = "show_success";

    private final SimpleDateFormat databaseTimestampFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);

    private FirebaseDatabaseDal firebaseDatabaseDal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_service_tracking);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        SessionManager sessionManager = new SessionManager(this);

        String bookingId = getIntent().getStringExtra(RoomServiceActivity.EXTRA_BOOKING_ID);
        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        boolean showSuccess = getIntent().getBooleanExtra(EXTRA_SHOW_SUCCESS, false);

        if (bookingId == null || orderId == null) {
            finish();
            return;
        }

        firebaseDatabaseDal.getBookingForCustomer(sessionManager.getCustomerId(), bookingId, booking -> {
            if (booking == null) {
                finish();
                return;
            }
            firebaseDatabaseDal.getRoomServiceOrder(orderId, order -> {
                if (order == null || !order.getBookingId().equals(bookingId)) {
                    finish();
                    return;
                }
                bindScreen(booking, order, showSuccess);
            });
        });

        // Sub-page: no bottom nav. Back always returns to the main room service page.
        findViewById(R.id.btnTrackingBack).setOnClickListener(view -> backToRoomService(bookingId));
    }

    private void backToRoomService(String bookingId) {
        android.content.Intent intent = new android.content.Intent(this, RoomServiceActivity.class);
        intent.putExtra(RoomServiceActivity.EXTRA_BOOKING_ID, bookingId);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void bindScreen(BookingSummary booking, RoomServiceOrder order, boolean showSuccess) {
        findViewById(R.id.txtPaymentSuccessful).setVisibility(
                showSuccess ? View.VISIBLE : View.GONE);

        bindBookingCard(booking, order);
        bindStatus(order);
        bindOrderLines(order);
        bindPaymentSummary(booking, order);
    }

    private void bindBookingCard(BookingSummary booking, RoomServiceOrder order) {
        ImageView roomImage = findViewById(R.id.imgTrackingRoom);
        int placeholder = RoomAssets.roomImageForName(booking.getRoomTypeName());
        firebaseDatabaseDal.getRoomTypeImageUrl(booking.getRoomTypeId(), imageUrl ->
                ImageLoader.load(roomImage, imageUrl, placeholder));
        ((TextView) findViewById(R.id.txtTrackingRoomType)).setText(
                booking.getRoomTypeName().toUpperCase(Locale.US));
        firebaseDatabaseDal.getRoomNumberForBooking(booking.getBookingId(), roomNumber ->
                ((TextView) findViewById(R.id.txtTrackingRoomNumber)).setText(
                        getString(R.string.rs_room_label, roomNumber)));
        ((TextView) findViewById(R.id.txtTrackingBookingCode)).setText(
                getString(R.string.booking_id_label, booking.getBookingCode()));
        ((TextView) findViewById(R.id.txtTrackingOrderCode)).setText(
                getString(R.string.rs_order_id_label, order.getOrderCode()));
    }

    private void bindStatus(RoomServiceOrder order) {
        int stepIndex = OrderTimeline.stepIndexFor(order.getStatus());
        int[] labels = OrderTimeline.stepLabels();
        ((TextView) findViewById(R.id.txtTrackingStatus)).setText(labels[stepIndex]);
        ((TextView) findViewById(R.id.txtTrackingStatusMessage)).setText(statusMessage(stepIndex));

        String[] times = new String[4];
        Calendar orderedAt = parseTimestamp(order.getOrderedAt());
        if (orderedAt != null) {
            times[0] = timeFormat.format(orderedAt.getTime());
            if (stepIndex >= 1) {
                orderedAt.add(Calendar.MINUTE, 5);
                times[1] = timeFormat.format(orderedAt.getTime());
            }
        }
        for (int i = Math.max(stepIndex + 1, 1); i < 4; i++) {
            times[i] = getString(R.string.rs_status_upcoming);
        }

        OrderTimeline.render(this, findViewById(R.id.rowTrackingTimeline), stepIndex, times);
    }

    private void bindOrderLines(RoomServiceOrder order) {
        bindKitchenNote(order);
        firebaseDatabaseDal.getRoomServiceOrderLines(order.getOrderId(), lines -> {
            ((TextView) findViewById(R.id.txtTrackingItemsTitle)).setText(
                    getString(R.string.rs_order_items, order.getItemCount()));
            ((TextView) findViewById(R.id.txtTrackingTotalLabel)).setText(
                    getString(R.string.rs_total_items, order.getItemCount()));

            LinearLayout linesContainer = findViewById(R.id.listTrackingLines);
            linesContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(this);

            double subtotal = 0;
            for (OrderLine line : lines) {
                View lineView = inflater.inflate(R.layout.item_order_line, linesContainer, false);
                ImageLoader.load(lineView.findViewById(R.id.imgOrderLine), line.getImageUrl(),
                        RoomAssets.MENU_PLACEHOLDER);
                ((TextView) lineView.findViewById(R.id.txtOrderLineName)).setText(line.getItemName());
                ((TextView) lineView.findViewById(R.id.txtOrderLineQty)).setText(
                        getString(R.string.rs_quantity_times, line.getQuantity()));
                ((TextView) lineView.findViewById(R.id.txtOrderLineTotal)).setText(
                        CurrencyFormatter.format(line.getLineTotal()));
                linesContainer.addView(lineView);
                subtotal += line.getLineTotal();
            }

            ((TextView) findViewById(R.id.txtTrackingSubtotal)).setText(CurrencyFormatter.format(subtotal));
        });
    }

    private void bindKitchenNote(RoomServiceOrder order) {
        View noteLayout = findViewById(R.id.layoutTrackingNote);
        String note = order.getKitchenNote();
        if (note == null || note.trim().isEmpty()) {
            noteLayout.setVisibility(View.GONE);
            return;
        }
        noteLayout.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.txtTrackingNote)).setText(note.trim());
    }

    private void bindPaymentSummary(BookingSummary booking, RoomServiceOrder order) {
        firebaseDatabaseDal.getLatestServicePaymentMethod(booking.getBookingId(), method -> {
            int methodLabel;
            if ("bank_card".equals(method)) {
                methodLabel = R.string.rs_charged_card;
            } else if ("e_wallet".equals(method)) {
                methodLabel = R.string.rs_charged_ewallet;
            } else {
                methodLabel = R.string.rs_charged_room;
            }
            ((TextView) findViewById(R.id.txtTrackingPaymentMethod)).setText(methodLabel);
        });
        ((TextView) findViewById(R.id.txtTrackingTotalPaid)).setText(
                CurrencyFormatter.format(order.getTotalAmount()));
    }

    private int statusMessage(int stepIndex) {
        switch (stepIndex) {
            case 1:
                return R.string.rs_status_message_preparing;
            case 2:
                return R.string.rs_status_message_on_the_way;
            case 3:
                return R.string.rs_status_message_delivered;
            default:
                return R.string.rs_status_message_confirmed;
        }
    }

    private Calendar parseTimestamp(String rawTimestamp) {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(databaseTimestampFormat.parse(rawTimestamp));
            return calendar;
        } catch (ParseException | NullPointerException exception) {
            return null;
        }
    }
}

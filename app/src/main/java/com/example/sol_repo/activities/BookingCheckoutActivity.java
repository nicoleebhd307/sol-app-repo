package com.example.sol_repo.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.Customer;
import com.example.sol_repo.models.RoomType;
import com.example.sol_repo.utils.CurrencyFormatter;
import com.example.sol_repo.utils.ImageLoader;
import com.example.sol_repo.utils.MomoClient;
import com.example.sol_repo.utils.RoomAssets;
import com.example.sol_repo.utils.SessionManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class BookingCheckoutActivity extends AppCompatActivity {
    private static final double TAX_RATE = 0.10;
    private static final double DEPOSIT_RATE = 0.30;
    private static final String DEFAULT_COUNTRY_CODE = "+84";

    private final SimpleDateFormat databaseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMM dd", Locale.US);

    private SessionManager sessionManager;
    private FirebaseDatabaseDal firebaseDatabaseDal;
    private RoomType roomType;

    private String checkInDate;
    private String checkOutDate;
    private int numGuests;
    private int nights;
    private double roomTotal;
    private double taxesAndFees;
    private double grandTotal;
    private double dueNow;

    private EditText countryCodeInput;
    private EditText mobileNumberInput;
    private EditText fullNameInput;
    private EditText emailInput;
    private CheckBox acceptPoliciesCheckBox;
    private TextView confirmBookingButton;

    // Guest details captured at confirm time, used after the MoMo deposit succeeds.
    private String pendingFullName;
    private String pendingEmail;
    private String pendingPhone;
    private DatabaseReference paymentStatusRef;
    private ValueEventListener paymentStatusListener;
    private boolean paymentHandled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_checkout);

        sessionManager = new SessionManager(this);
        firebaseDatabaseDal = new FirebaseDatabaseDal();

        String roomTypeId = getIntent().getStringExtra(RoomBookingActivity.EXTRA_ROOM_TYPE_ID);
        checkInDate = getIntent().getStringExtra(RoomBookingActivity.EXTRA_CHECK_IN);
        checkOutDate = getIntent().getStringExtra(RoomBookingActivity.EXTRA_CHECK_OUT);
        numGuests = getIntent().getIntExtra(RoomBookingActivity.EXTRA_GUESTS, 2);

        bindViews();

        if (roomTypeId == null) {
            finish();
            return;
        }

        firebaseDatabaseDal.getRoomType(roomTypeId, checkInDate, checkOutDate, resolvedRoomType -> {
            if (resolvedRoomType == null) {
                Toast.makeText(this, R.string.detail_room_unavailable, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            this.roomType = resolvedRoomType;
            computeTotals();
            bindSummary();
            autoFillGuestInformation();
        });

        findViewById(R.id.btnCheckoutBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnEditSummary).setOnClickListener(view -> finish());
        findViewById(R.id.btnConfirmBooking).setOnClickListener(view -> confirmBooking());
    }

    private void bindViews() {
        countryCodeInput = findViewById(R.id.inputCountryCode);
        mobileNumberInput = findViewById(R.id.inputMobileNumber);
        fullNameInput = findViewById(R.id.inputFullName);
        emailInput = findViewById(R.id.inputEmail);
        acceptPoliciesCheckBox = findViewById(R.id.checkAcceptPolicies);
        confirmBookingButton = findViewById(R.id.btnConfirmBooking);
    }

    private void computeTotals() {
        nights = Math.max(1, (int) TimeUnit.MILLISECONDS.toDays(
                parseTime(checkOutDate) - parseTime(checkInDate)));
        roomTotal = roomType.getBasePrice() * nights;
        taxesAndFees = roomTotal * TAX_RATE;
        grandTotal = roomTotal + taxesAndFees;
        dueNow = Math.round(grandTotal * DEPOSIT_RATE * 100) / 100.0;
    }

    private long parseTime(String rawDate) {
        try {
            Date parsed = databaseDateFormat.parse(rawDate);
            return parsed == null ? 0 : parsed.getTime();
        } catch (ParseException exception) {
            return 0;
        }
    }

    private void bindSummary() {
        ImageLoader.load(findViewById(R.id.imgCheckoutRoom), roomType.getImageUrl(), RoomAssets.ROOM_PLACEHOLDER);
        ImageLoader.load(findViewById(R.id.imgSummaryThumb), roomType.getImageUrl(), RoomAssets.ROOM_PLACEHOLDER);

        String dateRange = String.format(Locale.US, "%s - %s",
                displayDateFormat.format(new Date(parseTime(checkInDate))),
                displayDateFormat.format(new Date(parseTime(checkOutDate))));

        ((TextView) findViewById(R.id.txtCheckoutRoomName)).setText(roomType.getTypeName());
        ((TextView) findViewById(R.id.txtCheckoutDates)).setText(dateRange);
        ((TextView) findViewById(R.id.txtCheckoutGuestsNights)).setText(
                String.format(Locale.US, "%s  |  %s",
                        getString(R.string.booking_adults_count, numGuests),
                        getString(R.string.checkout_nights_count, nights)));
        ((TextView) findViewById(R.id.txtCheckoutPrice)).setText(CurrencyFormatter.format(roomType.getBasePrice()));
        ((TextView) findViewById(R.id.txtSummaryLine)).setText(
                getString(R.string.checkout_summary_line,
                        roomType.getTypeName().toUpperCase(Locale.US),
                        dateRange.toUpperCase(Locale.US)));

        ((TextView) findViewById(R.id.txtRoomTotalLabel)).setText(
                getString(R.string.checkout_room_total, nights));
        ((TextView) findViewById(R.id.txtRoomTotalValue)).setText(CurrencyFormatter.format(roomTotal));
        ((TextView) findViewById(R.id.txtServicesValue)).setText(CurrencyFormatter.format(0));
        ((TextView) findViewById(R.id.txtTaxesValue)).setText(CurrencyFormatter.format(taxesAndFees));
        ((TextView) findViewById(R.id.txtDueNow)).setText(CurrencyFormatter.format(dueNow));
    }

    private void autoFillGuestInformation() {
        firebaseDatabaseDal.getCustomer(sessionManager.getCustomerId(), customer -> {
            if (customer == null) {
                Toast.makeText(this, R.string.account_session_missing, Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }

            fullNameInput.setText(customer.getFullName());
            emailInput.setText(customer.getEmail());

            String phone = customer.getPhone() == null ? "" : customer.getPhone().trim();
            if (phone.startsWith(DEFAULT_COUNTRY_CODE)) {
                phone = phone.substring(DEFAULT_COUNTRY_CODE.length());
            } else if (phone.startsWith("0")) {
                phone = phone.substring(1);
            }
            countryCodeInput.setText(DEFAULT_COUNTRY_CODE);
            mobileNumberInput.setText(phone);
        });
    }

    private void confirmBooking() {
        String fullName = fullNameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String mobileNumber = mobileNumberInput.getText().toString().trim();
        String countryCode = countryCodeInput.getText().toString().trim();

        if (fullName.isEmpty()) {
            fullNameInput.requestFocus();
            Toast.makeText(this, R.string.checkout_error_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.requestFocus();
            Toast.makeText(this, R.string.checkout_error_email, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mobileNumber.isEmpty()) {
            mobileNumberInput.requestFocus();
            Toast.makeText(this, R.string.checkout_error_phone, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!acceptPoliciesCheckBox.isChecked()) {
            Toast.makeText(this, R.string.checkout_error_policies, Toast.LENGTH_SHORT).show();
            return;
        }

        pendingFullName = fullName;
        pendingEmail = email;
        pendingPhone = countryCode.isEmpty() ? mobileNumber : countryCode + mobileNumber;

        // Pay the deposit via MoMo first; the booking is created only after the IPN confirms.
        startDepositPayment();
    }

    private void startDepositPayment() {
        confirmBookingButton.setEnabled(false);
        Toast.makeText(this, R.string.momo_starting, Toast.LENGTH_SHORT).show();
        int amount = (int) Math.round(dueNow);
        // No bookingId yet — the booking is created after payment succeeds.
        MomoClient.createPayment(amount, null, "Room booking deposit", "deposit",
                MomoClient.CHANNEL_ATM, new MomoClient.CreateCallback() {
                    @Override
                    public void onCreated(String orderId, String payUrl) {
                        observePayment(orderId);
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(payUrl)));
                        Toast.makeText(BookingCheckoutActivity.this, R.string.momo_waiting,
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        confirmBookingButton.setEnabled(true);
                        Toast.makeText(BookingCheckoutActivity.this, R.string.momo_error,
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
                    createBookingAfterPayment();
                } else if ("failed".equals(status)) {
                    paymentHandled = true;
                    detachPaymentListener();
                    confirmBookingButton.setEnabled(true);
                    Toast.makeText(BookingCheckoutActivity.this, R.string.momo_failed,
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

    private void createBookingAfterPayment() {
        confirmBookingButton.setEnabled(false);
        firebaseDatabaseDal.createBooking(
                sessionManager.getCustomerId(),
                roomType.getRoomTypeId(),
                checkInDate,
                checkOutDate,
                numGuests,
                grandTotal,
                dueNow,
                "e_wallet",
                pendingFullName,
                pendingEmail,
                pendingPhone,
                true,
                new com.example.sol_repo.dals.FirebaseCallback<com.example.sol_repo.models.BookingCreationResult>() {
                    @Override
                    public void onSuccess(com.example.sol_repo.models.BookingCreationResult result) {
                        confirmBookingButton.setEnabled(true);
                        if (result == null) {
                            Toast.makeText(BookingCheckoutActivity.this,
                                    R.string.checkout_booking_failed, Toast.LENGTH_LONG).show();
                            return;
                        }
                        onBookingCreated(result.getBookingId(), result.getBookingCode());
                    }

                    @Override
                    public void onError(String message) {
                        confirmBookingButton.setEnabled(true);
                        Toast.makeText(BookingCheckoutActivity.this,
                                R.string.checkout_booking_failed, Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachPaymentListener();
    }

    private void onBookingCreated(String bookingId, String bookingCode) {
        firebaseDatabaseDal.getCustomer(sessionManager.getCustomerId(), updatedCustomer -> {
            if (updatedCustomer != null) {
                sessionManager.saveLoginSession(updatedCustomer, sessionManager.isRememberMeEnabled());
            }
            // Suite guests get a complimentary airport transfer — offer it right after booking (optional).
            if (roomType != null && "suite".equalsIgnoreCase(roomType.getCategory())) {
                showSuiteTransferOffer(bookingId, bookingCode);
            } else {
                showBookingConfirmedDialog(bookingCode);
            }
        });
    }

    private void showSuiteTransferOffer(String bookingId, String bookingCode) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.transfer_offer_title)
                .setMessage(getString(R.string.transfer_offer_message))
                .setCancelable(false)
                .setPositiveButton(R.string.transfer_offer_yes, (dialog, which) -> {
                    Intent intent = new Intent(this, TransferActivity.class);
                    intent.putExtra(TransferActivity.EXTRA_BOOKING_ID, bookingId);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.transfer_offer_no, (dialog, which) -> goToAccount())
                .show();
    }

    private void showBookingConfirmedDialog(String bookingCode) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.checkout_success_title)
                .setMessage(getString(R.string.checkout_success_message, bookingCode))
                .setCancelable(false)
                .setPositiveButton(R.string.checkout_success_action, (dialog, which) -> goToAccount())
                .show();
    }

    private void goToAccount() {
        Intent intent = new Intent(this, AccountActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}

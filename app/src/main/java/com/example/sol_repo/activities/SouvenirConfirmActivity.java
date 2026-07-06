package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.utils.CurrencyFormatter;

public class SouvenirConfirmActivity extends AppCompatActivity {
    public static final String EXTRA_ORDER_CODE = "order_code";
    public static final String EXTRA_ITEM_COUNT = "item_count";
    public static final String EXTRA_TOTAL = "total";

    private CountDownTimer autoHomeTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_souvenir_confirm);

        String orderCode = getIntent().getStringExtra(EXTRA_ORDER_CODE);
        int itemCount = getIntent().getIntExtra(EXTRA_ITEM_COUNT, 0);
        double total = getIntent().getDoubleExtra(EXTRA_TOTAL, 0);
        String bookingId = getIntent().getStringExtra(SouvenirStoreActivity.EXTRA_BOOKING_ID);

        ((TextView) findViewById(R.id.txtOrderNumber)).setText(orderCode);
        ((TextView) findViewById(R.id.txtSummaryItems)).setText(String.valueOf(itemCount));
        ((TextView) findViewById(R.id.txtSummaryTotal)).setText(CurrencyFormatter.format(total));

        findViewById(R.id.btnConfirmBack).setOnClickListener(view -> goHome(bookingId));

        TextView btnBackHome = findViewById(R.id.btnBackHome);
        btnBackHome.setOnClickListener(view -> goHome(bookingId));
        startAutoHomeCountdown(btnBackHome, bookingId);
    }

    private void startAutoHomeCountdown(TextView button, String bookingId) {
        String label = getString(R.string.store_back_home);
        autoHomeTimer = new CountDownTimer(10_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                button.setText(getString(R.string.back_home_countdown, label, seconds));
            }

            @Override
            public void onFinish() {
                goHome(bookingId);
            }
        };
        autoHomeTimer.start();
    }

    private void goHome(String bookingId) {
        if (autoHomeTimer != null) {
            autoHomeTimer.cancel();
            autoHomeTimer = null;
        }
        Intent intent = new Intent(this, MainActivity.class);
        if (bookingId != null) {
            intent.putExtra(MainActivity.EXTRA_BOOKING_ID, bookingId);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (autoHomeTimer != null) {
            autoHomeTimer.cancel();
            autoHomeTimer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        goHome(getIntent().getStringExtra(SouvenirStoreActivity.EXTRA_BOOKING_ID));
    }
}

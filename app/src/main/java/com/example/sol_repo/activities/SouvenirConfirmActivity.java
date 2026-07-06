package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.utils.CurrencyFormatter;

public class SouvenirConfirmActivity extends AppCompatActivity {
    public static final String EXTRA_ORDER_CODE = "order_code";
    public static final String EXTRA_ITEM_COUNT = "item_count";
    public static final String EXTRA_TOTAL = "total";

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
        findViewById(R.id.btnBackHome).setOnClickListener(view -> goHome(bookingId));
        findViewById(R.id.btnConcierge).setOnClickListener(view ->
                Toast.makeText(this, R.string.store_concierge_desc, Toast.LENGTH_SHORT).show());
    }

    private void goHome(String bookingId) {
        Intent intent = new Intent(this, MainActivity.class);
        if (bookingId != null) {
            intent.putExtra(MainActivity.EXTRA_BOOKING_ID, bookingId);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        goHome(getIntent().getStringExtra(SouvenirStoreActivity.EXTRA_BOOKING_ID));
    }
}

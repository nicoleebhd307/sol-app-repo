package com.example.sol_repo.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Deeplink trampoline for the MoMo browser return page (solanbang://payment).
 * Opening it brings the app's task back to the foreground; finishing immediately reveals
 * the payment screen that is still on the back stack, where the RTDB status listener has
 * already handled success (navigates on) or failure (re-enables Pay for a retry).
 */
public class PaymentReturnActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}

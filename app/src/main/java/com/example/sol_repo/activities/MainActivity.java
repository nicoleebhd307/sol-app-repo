package com.example.sol_repo.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.utils.SessionManager;

public class MainActivity extends AppCompatActivity {
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);
        TextView welcomeTextView = findViewById(R.id.txtWelcomeCustomer);
        TextView accountTextView = findViewById(R.id.txtAccountSummary);
        TextView signOutButton = findViewById(R.id.btnSignOut);

        String fullName = sessionManager.getFullName();
        String email = sessionManager.getEmail();
        String status = sessionManager.getStatus();

        welcomeTextView.setText(getString(R.string.dashboard_welcome_customer, fullName));
        accountTextView.setText(getString(R.string.dashboard_account_summary, email, status));

        signOutButton.setOnClickListener(view -> {
            sessionManager.clearSession();
            finish();
        });
    }
}

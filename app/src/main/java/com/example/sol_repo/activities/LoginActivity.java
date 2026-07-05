package com.example.sol_repo.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.Customer;
import com.example.sol_repo.utils.SessionManager;

public class LoginActivity extends AppCompatActivity {
    private EditText emailEditText;
    private EditText passwordEditText;
    private CheckBox rememberCheckBox;
    private TextView signInButton;
    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;
    private boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        sessionManager = new SessionManager(this);

        bindViews();
        setupClickListeners();
    }

    private void bindViews() {
        emailEditText = findViewById(R.id.edtEmail);
        passwordEditText = findViewById(R.id.edtPassword);
        rememberCheckBox = findViewById(R.id.chkRememberMe);
        signInButton = findViewById(R.id.btnSignIn);
    }

    private void setupClickListeners() {
        signInButton.setOnClickListener(view -> handleLogin());

        findViewById(R.id.btnTogglePassword).setOnClickListener(view -> togglePasswordVisibility(view));
        findViewById(R.id.rememberAction).setOnClickListener(view ->
                rememberCheckBox.setChecked(!rememberCheckBox.isChecked()));
        findViewById(R.id.txtForgotPassword).setOnClickListener(view ->
                Toast.makeText(this, R.string.forgot_password_message, Toast.LENGTH_SHORT).show());
        findViewById(R.id.txtSignUp).setOnClickListener(view ->
                startActivity(new Intent(this, SignUpActivity.class)));
    }

    private void handleLogin() {
        String email = String.valueOf(emailEditText.getText()).trim();
        String password = String.valueOf(passwordEditText.getText());

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError(getString(R.string.error_email_required));
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError(getString(R.string.error_password_required));
            return;
        }

        signInButton.setEnabled(false);
        firebaseDatabaseDal.loginCustomer(email, password, new com.example.sol_repo.dals.FirebaseCallback<Customer>() {
            @Override
            public void onSuccess(Customer customer) {
                signInButton.setEnabled(true);
                if (customer == null) {
                    Toast.makeText(LoginActivity.this, R.string.login_invalid_message, Toast.LENGTH_SHORT).show();
                    return;
                }

                sessionManager.saveLoginSession(customer, rememberCheckBox.isChecked());
                Intent intent = new Intent(LoginActivity.this, AccountActivity.class);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                signInButton.setEnabled(true);
                Toast.makeText(LoginActivity.this, R.string.login_database_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void togglePasswordVisibility(android.view.View toggleView) {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            if (toggleView instanceof android.widget.ImageView) {
                ((android.widget.ImageView) toggleView).setAlpha(1.0f);
            }
        } else {
            passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (toggleView instanceof android.widget.ImageView) {
                ((android.widget.ImageView) toggleView).setAlpha(0.5f);
            }
        }
        passwordEditText.setSelection(String.valueOf(passwordEditText.getText()).length());
    }

}

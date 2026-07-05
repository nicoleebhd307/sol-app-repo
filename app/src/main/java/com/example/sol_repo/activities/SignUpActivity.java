package com.example.sol_repo.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseCallback;
import com.example.sol_repo.dals.FirebaseDatabaseDal;

public class SignUpActivity extends AppCompatActivity {
    public static final String EXTRA_FULL_NAME = "extra_full_name";
    public static final String EXTRA_EMAIL = "extra_email";
    public static final String EXTRA_PASSWORD = "extra_password";

    private static final int MIN_PASSWORD_LENGTH = 6;

    private FirebaseDatabaseDal firebaseDatabaseDal;

    private EditText fullNameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private ImageView agreeCheck;
    private TextView signUpButton;

    private boolean agreed = true;
    private boolean passwordVisible = false;
    private boolean confirmVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        firebaseDatabaseDal = new FirebaseDatabaseDal();

        fullNameInput = findViewById(R.id.edtFullName);
        emailInput = findViewById(R.id.edtEmail);
        passwordInput = findViewById(R.id.edtPassword);
        confirmPasswordInput = findViewById(R.id.edtConfirmPassword);
        agreeCheck = findViewById(R.id.imgAgreeCheck);
        signUpButton = findViewById(R.id.btnSignUp);

        buildAgreeText();

        findViewById(R.id.btnSignupBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnGoToLogin).setOnClickListener(view -> finish());
        findViewById(R.id.agreeRow).setOnClickListener(view -> toggleAgree());

        findViewById(R.id.btnTogglePassword).setOnClickListener(view -> {
            passwordVisible = togglePassword(passwordInput, passwordVisible, (ImageView) view);
        });
        findViewById(R.id.btnToggleConfirmPassword).setOnClickListener(view -> {
            confirmVisible = togglePassword(confirmPasswordInput, confirmVisible, (ImageView) view);
        });

        signUpButton.setOnClickListener(view -> submit());
    }

    private void buildAgreeText() {
        String prefix = getString(R.string.signup_agree_prefix);
        String terms = getString(R.string.signup_terms);
        String and = getString(R.string.signup_agree_and);
        String privacy = getString(R.string.signup_privacy);

        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(prefix);
        appendGold(builder, terms);
        builder.append(and);
        appendGold(builder, privacy);
        ((TextView) findViewById(R.id.txtAgree)).setText(builder);
    }

    private void appendGold(SpannableStringBuilder builder, String text) {
        int start = builder.length();
        builder.append(text);
        int end = builder.length();
        int gold = ContextCompat.getColor(this, R.color.sol_gold_dark);
        builder.setSpan(new ForegroundColorSpan(gold), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void toggleAgree() {
        agreed = !agreed;
        View box = findViewById(R.id.checkAgreeBox);
        box.setBackgroundResource(agreed
                ? R.drawable.bg_checkbox_checked
                : R.drawable.bg_checkbox_unchecked);
        agreeCheck.setVisibility(agreed ? View.VISIBLE : View.INVISIBLE);
    }

    private boolean togglePassword(EditText input, boolean currentlyVisible, ImageView toggle) {
        boolean nowVisible = !currentlyVisible;
        if (nowVisible) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggle.setColorFilter(ContextCompat.getColor(this, R.color.sol_gold_dark));
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggle.setColorFilter(ContextCompat.getColor(this, R.color.color_icon_inactive));
        }
        input.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.plus_jakarta_sans));
        input.setSelection(input.getText().length());
        return nowVisible;
    }

    private void submit() {
        String fullName = fullNameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        if (fullName.isEmpty()) {
            fullNameInput.requestFocus();
            Toast.makeText(this, R.string.signup_error_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.requestFocus();
            Toast.makeText(this, R.string.signup_error_email, Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            passwordInput.requestFocus();
            Toast.makeText(this, R.string.signup_error_password_short, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.requestFocus();
            Toast.makeText(this, R.string.signup_error_confirm, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!agreed) {
            Toast.makeText(this, R.string.signup_error_agree, Toast.LENGTH_SHORT).show();
            return;
        }

        signUpButton.setEnabled(false);
        firebaseDatabaseDal.emailExists(email, new FirebaseCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean exists) {
                signUpButton.setEnabled(true);
                if (Boolean.TRUE.equals(exists)) {
                    emailInput.requestFocus();
                    Toast.makeText(SignUpActivity.this, R.string.signup_error_email_taken, Toast.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(SignUpActivity.this, SignUpDetailsActivity.class);
                intent.putExtra(EXTRA_FULL_NAME, fullName);
                intent.putExtra(EXTRA_EMAIL, email);
                intent.putExtra(EXTRA_PASSWORD, password);
                startActivity(intent);
            }

            @Override
            public void onError(String message) {
                signUpButton.setEnabled(true);
                Toast.makeText(SignUpActivity.this, R.string.signup_error_generic, Toast.LENGTH_LONG).show();
            }
        });
    }
}

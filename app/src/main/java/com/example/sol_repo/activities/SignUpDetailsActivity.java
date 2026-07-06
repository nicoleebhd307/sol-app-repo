package com.example.sol_repo.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.os.LocaleListCompat;

import com.example.sol_repo.R;
import com.example.sol_repo.dals.FirebaseCallback;
import com.example.sol_repo.dals.FirebaseDatabaseDal;
import com.example.sol_repo.models.Customer;
import com.example.sol_repo.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class SignUpDetailsActivity extends AppCompatActivity {
    private final SimpleDateFormat databaseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    private FirebaseDatabaseDal firebaseDatabaseDal;
    private SessionManager sessionManager;

    private String fullName;
    private String email;
    private String password;

    private EditText countryCodeInput;
    private EditText mobileNumberInput;
    private EditText nationalityInput;
    private EditText idPassportInput;
    private TextView dobText;
    private TextView chipLanguageVi;
    private TextView chipLanguageEn;
    private TextView finishButton;

    private final Calendar dobCalendar = Calendar.getInstance();
    private boolean dobSelected = false;
    private String selectedLanguage = "vi";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup_details);

        firebaseDatabaseDal = new FirebaseDatabaseDal();
        sessionManager = new SessionManager(this);

        fullName = getIntent().getStringExtra(SignUpActivity.EXTRA_FULL_NAME);
        email = getIntent().getStringExtra(SignUpActivity.EXTRA_EMAIL);
        password = getIntent().getStringExtra(SignUpActivity.EXTRA_PASSWORD);
        if (fullName == null || email == null || password == null) {
            finish();
            return;
        }

        countryCodeInput = findViewById(R.id.inputCountryCode);
        mobileNumberInput = findViewById(R.id.inputMobileNumber);
        nationalityInput = findViewById(R.id.inputNationality);
        idPassportInput = findViewById(R.id.inputIdPassport);
        dobText = findViewById(R.id.txtDob);
        chipLanguageVi = findViewById(R.id.chipLanguageVi);
        chipLanguageEn = findViewById(R.id.chipLanguageEn);
        finishButton = findViewById(R.id.btnFinishSignup);

        dobCalendar.add(Calendar.YEAR, -25);
        renderLanguageChips();

        findViewById(R.id.btnDetailsBack).setOnClickListener(view -> finish());
        findViewById(R.id.rowDob).setOnClickListener(view -> pickDob());
        chipLanguageVi.setOnClickListener(view -> selectLanguage("vi"));
        chipLanguageEn.setOnClickListener(view -> selectLanguage("en"));
        finishButton.setOnClickListener(view -> submit());
    }

    private void pickDob() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            dobCalendar.set(year, month, day);
            dobSelected = true;
            dobText.setText(displayDateFormat.format(dobCalendar.getTime()));
            dobText.setTextColor(getColor(R.color.sol_text_primary));
        }, dobCalendar.get(Calendar.YEAR), dobCalendar.get(Calendar.MONTH),
                dobCalendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void selectLanguage(String language) {
        selectedLanguage = language;
        renderLanguageChips();
    }

    private void renderLanguageChips() {
        styleChip(chipLanguageVi, "vi".equals(selectedLanguage));
        styleChip(chipLanguageEn, "en".equals(selectedLanguage));
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected
                ? R.drawable.bg_chip_selected
                : R.drawable.bg_chip_unselected);
        chip.setTextColor(getColor(selected ? R.color.sol_gold_dark : R.color.sol_text_primary));
        chip.setTypeface(ResourcesCompat.getFont(this, R.font.plus_jakarta_sans),
                selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void submit() {
        String countryCode = countryCodeInput.getText().toString().trim();
        String mobileNumber = mobileNumberInput.getText().toString().trim();
        String nationality = nationalityInput.getText().toString().trim();
        String idPassport = idPassportInput.getText().toString().trim();

        if (mobileNumber.isEmpty()) {
            mobileNumberInput.requestFocus();
            Toast.makeText(this, R.string.details_error_phone, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!dobSelected) {
            Toast.makeText(this, R.string.details_error_dob, Toast.LENGTH_SHORT).show();
            return;
        }
        if (nationality.isEmpty()) {
            nationalityInput.requestFocus();
            Toast.makeText(this, R.string.details_error_nationality, Toast.LENGTH_SHORT).show();
            return;
        }

        String phone = countryCode.isEmpty() ? mobileNumber : countryCode + mobileNumber;
        String dob = databaseDateFormat.format(dobCalendar.getTime());

        finishButton.setEnabled(false);
        firebaseDatabaseDal.createCustomerAccount(fullName, email, password, phone, dob,
                nationality, selectedLanguage, idPassport, new FirebaseCallback<Customer>() {
                    @Override
                    public void onSuccess(Customer customer) {
                        finishButton.setEnabled(true);
                        if (customer == null) {
                            Toast.makeText(SignUpDetailsActivity.this,
                                    R.string.signup_error_email_taken, Toast.LENGTH_LONG).show();
                            return;
                        }

                        sessionManager.saveLoginSession(customer, true);
                        // Apply the language the user picked so the app renders in that locale.
                        AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(selectedLanguage));
                        Toast.makeText(SignUpDetailsActivity.this,
                                getString(R.string.details_success, firstName(customer.getFullName())),
                                Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(SignUpDetailsActivity.this, AccountActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        finishButton.setEnabled(true);
                        Toast.makeText(SignUpDetailsActivity.this,
                                R.string.signup_error_generic, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String firstName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }
        return name.trim().split("\\s+")[0];
    }
}

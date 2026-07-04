package com.example.sol_repo.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.sol_repo.models.Customer;

public class SessionManager {
    private static final String PREF_NAME = "sol_an_bang_session";
    private static final String KEY_REMEMBER_ME = "remember_me";
    private static final String KEY_CUSTOMER_ID = "customer_id";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_STATUS = "status";

    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveLoginSession(Customer customer, boolean rememberMe) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_REMEMBER_ME, rememberMe);
        editor.putString(KEY_CUSTOMER_ID, customer.getCustomerId());
        editor.putString(KEY_FULL_NAME, customer.getFullName());
        editor.putString(KEY_EMAIL, customer.getEmail());
        editor.putString(KEY_STATUS, customer.getStatus());
        editor.apply();
    }

    public boolean isRememberMeEnabled() {
        return preferences.getBoolean(KEY_REMEMBER_ME, false);
    }

    public String getFullName() {
        return preferences.getString(KEY_FULL_NAME, "");
    }

    public String getCustomerId() {
        return preferences.getString(KEY_CUSTOMER_ID, null);
    }

    public boolean hasSession() {
        return getCustomerId() != null;
    }

    public String getEmail() {
        return preferences.getString(KEY_EMAIL, "");
    }

    public String getStatus() {
        return preferences.getString(KEY_STATUS, "");
    }

    public void clearSession() {
        preferences.edit().clear().apply();
    }
}

package com.example.sol_repo.dals;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.models.Customer;
import com.example.sol_repo.models.HomeServiceItem;
import com.example.sol_repo.models.RecommendationItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MockDatabaseDal {
    private static final String DATABASE_NAME = "sol_an_bang_mock.db";
    private static final String PREF_NAME = "sol_an_bang_mock_database";
    private static final String KEY_DATABASE_VERSION = "database_version";
    private static final int DATABASE_VERSION = 2;
    private static final int BUFFER_SIZE = 8192;

    private final Context context;
    private final File databaseFile;

    public MockDatabaseDal(Context context) {
        this.context = context.getApplicationContext();
        this.databaseFile = this.context.getDatabasePath(DATABASE_NAME);
    }

    public void prepareDatabase() throws IOException {
        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int installedVersion = preferences.getInt(KEY_DATABASE_VERSION, 0);
        if (databaseFile.exists() && installedVersion == DATABASE_VERSION) {
            return;
        }

        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (InputStream inputStream = context.getAssets().open(DATABASE_NAME);
             OutputStream outputStream = new FileOutputStream(databaseFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
        }
        preferences.edit().putInt(KEY_DATABASE_VERSION, DATABASE_VERSION).apply();
    }

    public Customer loginCustomer(String email, String password) {
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                databaseFile.getPath(),
                null,
                SQLiteDatabase.OPEN_READONLY
        );

        String sql = "SELECT customer_id, full_name, email, phone, status " +
                "FROM Customer WHERE lower(email) = lower(?) AND password_hash = ? LIMIT 1";

        try (Cursor cursor = database.rawQuery(sql, new String[]{email.trim(), password})) {
            if (cursor.moveToFirst()) {
                return new Customer(
                        cursor.getInt(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4)
                );
            }
            return null;
        } finally {
            database.close();
        }
    }

    public BookingSummary getCurrentBooking(int customerId) {
        SQLiteDatabase database = openReadOnlyDatabase();
        String sql = "SELECT b.booking_id, 'BK-2026-' || printf('%04d', b.booking_id + 1044), " +
                "rt.type_name, b.check_in_date, b.check_out_date, b.num_guests, b.status " +
                "FROM Booking b " +
                "JOIN Room r ON b.room_id = r.room_id " +
                "JOIN Room_Type rt ON r.room_type_id = rt.room_type_id " +
                "WHERE b.customer_id = ? AND b.status IN ('confirmed','checked_in') " +
                "ORDER BY b.check_in_date ASC LIMIT 1";

        try (Cursor cursor = database.rawQuery(sql, new String[]{String.valueOf(customerId)})) {
            if (cursor.moveToFirst()) {
                return new BookingSummary(
                        cursor.getInt(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getInt(5),
                        cursor.getString(6)
                );
            }
            return null;
        } finally {
            database.close();
        }
    }

    public List<HomeServiceItem> getHomeServices(int bookingId) {
        SQLiteDatabase database = openReadOnlyDatabase();
        String sql =
                "SELECT 'Airport Pickup', scheduled_datetime, status, 'transfer' FROM Transfer_Booking WHERE booking_id = ? " +
                        "UNION ALL " +
                        "SELECT ws.service_name, wb.scheduled_date || ' ' || wb.scheduled_time, wb.status, 'wellness' " +
                        "FROM Wellness_Booking wb JOIN Wellness_Service ws ON wb.wellness_service_id = ws.wellness_service_id WHERE wb.booking_id = ? " +
                        "UNION ALL " +
                        "SELECT CASE venue_type WHEN 'restaurant' THEN 'Restaurant Table' WHEN 'coffee' THEN 'Coffee Table' ELSE 'Bar Table' END, " +
                        "reservation_date || ' ' || reservation_time, status, 'restaurant' FROM Dining_Reservation WHERE booking_id = ? " +
                        "LIMIT 3";

        List<HomeServiceItem> services = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(sql, new String[]{
                String.valueOf(bookingId),
                String.valueOf(bookingId),
                String.valueOf(bookingId)
        })) {
            while (cursor.moveToNext()) {
                services.add(new HomeServiceItem(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3)
                ));
            }
        } finally {
            database.close();
        }
        return services;
    }

    public List<RecommendationItem> getRecommendations() {
        SQLiteDatabase database = openReadOnlyDatabase();
        List<RecommendationItem> recommendations = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT title, description, type FROM Home_Recommendation ORDER BY recommendation_id LIMIT 2",
                null
        )) {
            while (cursor.moveToNext()) {
                recommendations.add(new RecommendationItem(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2)
                ));
            }
        } finally {
            database.close();
        }
        return recommendations;
    }

    private SQLiteDatabase openReadOnlyDatabase() {
        return SQLiteDatabase.openDatabase(
                databaseFile.getPath(),
                null,
                SQLiteDatabase.OPEN_READONLY
        );
    }
}

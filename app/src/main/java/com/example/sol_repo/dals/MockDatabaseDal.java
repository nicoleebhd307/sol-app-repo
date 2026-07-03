package com.example.sol_repo.dals;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.sol_repo.models.Customer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MockDatabaseDal {
    private static final String DATABASE_NAME = "sol_an_bang_mock.db";
    private static final int BUFFER_SIZE = 8192;

    private final Context context;
    private final File databaseFile;

    public MockDatabaseDal(Context context) {
        this.context = context.getApplicationContext();
        this.databaseFile = this.context.getDatabasePath(DATABASE_NAME);
    }

    public void prepareDatabase() throws IOException {
        if (databaseFile.exists()) {
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
}

package com.example.sol_repo.dals;

import android.util.Log;

public interface FirebaseCallback<T> {
    String TAG = "FirebaseDatabaseDal";

    void onSuccess(T result);

    default void onError(String message) {
        Log.e(TAG, message == null ? "Unknown Firebase error" : message);
    }
}

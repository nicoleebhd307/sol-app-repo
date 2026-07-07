package com.example.sol_repo.utils;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Thin client for the Sol An Bang MoMo payment backend. Runs the HTTP call off the main
 * thread and posts the result back on it.
 */
public final class MomoClient {
    /**
     * Backend base URL — the deployed MoMo payment service on Render.
     * (For local dev against the emulator, use "http://10.0.2.2:4000" instead.)
     */
    public static final String BASE_URL = "https://sol-app-repo.onrender.com";

    private static final Executor IO = Executors.newSingleThreadExecutor();

    private MomoClient() {
    }

    public interface CreateCallback {
        void onCreated(String orderId, String payUrl);

        void onError(String message);
    }

    /**
     * Asks the backend to create a MoMo sandbox payment. On success returns the generated
     * orderId and the payUrl to open. Amount is integer VND.
     */
    public static void createPayment(int amountVnd, String bookingId, String orderInfo,
                                     String paymentType, CreateCallback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject request = new JSONObject();
                request.put("amount", amountVnd);
                if (bookingId != null) {
                    request.put("bookingId", bookingId);
                }
                request.put("orderInfo", orderInfo);
                request.put("paymentType", paymentType);

                URL url = new URL(BASE_URL + "/api/payments/create");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(request.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = connection.getResponseCode();
                boolean ok = code >= 200 && code < 300;
                String body = readAll(ok ? connection.getInputStream() : connection.getErrorStream());
                if (!ok) {
                    postError(main, callback, "HTTP " + code);
                    return;
                }

                JSONObject response = new JSONObject(body);
                String orderId = response.optString("orderId", null);
                String payUrl = response.optString("payUrl", null);
                if (orderId == null || payUrl == null) {
                    postError(main, callback, "Unexpected response");
                    return;
                }
                main.post(() -> callback.onCreated(orderId, payUrl));
            } catch (Exception exception) {
                postError(main, callback, exception.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static void postError(Handler main, CreateCallback callback, String message) {
        main.post(() -> callback.onError(message == null ? "Network error" : message));
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}

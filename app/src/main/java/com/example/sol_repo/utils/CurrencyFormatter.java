package com.example.sol_repo.utils;

import java.util.Locale;

public final class CurrencyFormatter {
    private static final Locale VN = new Locale("vi", "VN");

    private CurrencyFormatter() {
    }

    public static String format(double amountInVnd) {
        return String.format(VN, "%,.0f ₫", amountInVnd);
    }

    public static String formatCompact(double amountInVnd) {
        return String.format(VN, "%,.0f", amountInVnd);
    }
}

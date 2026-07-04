package com.example.sol_repo.models;

public class BookingCreationResult {
    private final String bookingId;
    private final String bookingCode;

    public BookingCreationResult(String bookingId, String bookingCode) {
        this.bookingId = bookingId;
        this.bookingCode = bookingCode;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getBookingCode() {
        return bookingCode;
    }
}

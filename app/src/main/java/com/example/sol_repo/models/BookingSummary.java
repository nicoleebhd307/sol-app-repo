package com.example.sol_repo.models;

public class BookingSummary {
    private String bookingId;
    private String bookingCode;
    private String roomTypeName;
    private String roomTypeId;
    private String checkInDate;
    private String checkOutDate;
    private int numGuests;
    private String status;
    private String roomTypeId;
    private String roomNumber;

    public BookingSummary() {
    }

    public BookingSummary(String bookingId, String bookingCode, String roomTypeName, String checkInDate,
                          String checkOutDate, int numGuests, String status) {
        this.bookingId = bookingId;
        this.bookingCode = bookingCode;
        this.roomTypeName = roomTypeName;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.numGuests = numGuests;
        this.status = status;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public void setBookingCode(String bookingCode) {
        this.bookingCode = bookingCode;
    }

    public String getRoomTypeName() {
        return roomTypeName;
    }

    public void setRoomTypeName(String roomTypeName) {
        this.roomTypeName = roomTypeName;
    }

    public String getRoomTypeId() {
        return roomTypeId;
    }

    public void setRoomTypeId(String roomTypeId) {
        this.roomTypeId = roomTypeId;
    }

    public String getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(String checkInDate) {
        this.checkInDate = checkInDate;
    }

    public String getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(String checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public int getNumGuests() {
        return numGuests;
    }

    public void setNumGuests(int numGuests) {
        this.numGuests = numGuests;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRoomTypeId() {
        return roomTypeId;
    }

    public void setRoomTypeId(String roomTypeId) {
        this.roomTypeId = roomTypeId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    @Override
    public String toString() {
        return "BookingSummary{" +
                "bookingId=" + bookingId +
                ", bookingCode='" + bookingCode + '\'' +
                ", roomTypeName='" + roomTypeName + '\'' +
                ", checkInDate='" + checkInDate + '\'' +
                ", checkOutDate='" + checkOutDate + '\'' +
                ", numGuests=" + numGuests +
                ", status='" + status + '\'' +
                '}';
    }
}

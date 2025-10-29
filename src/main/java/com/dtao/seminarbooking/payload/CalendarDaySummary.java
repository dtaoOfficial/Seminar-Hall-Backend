// File: src/main/java/com/dtao/seminarbooking/payload/CalendarDaySummary.java
package com.dtao.seminarbooking.payload;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * DTO: Represents a single day's booking summary for calendar view.
 * Used by both /api/seminars/calendar and /api/departments/calendar endpoints.
 */
public class CalendarDaySummary {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private String date;        // yyyy-MM-dd
    private boolean free;       // true = no bookings that day
    private int bookingCount;   // number of bookings on that day

    public CalendarDaySummary() {}

    /**
     * Constructs a summary using LocalDate.
     */
    public CalendarDaySummary(LocalDate date, boolean free, int bookingCount) {
        this.date = date.format(DATE_FMT);
        this.free = free;
        this.bookingCount = bookingCount;
    }

    /**
     * Constructs a summary using String date (yyyy-MM-dd).
     */
    public CalendarDaySummary(String date, boolean free, int bookingCount) {
        this.date = date;
        this.free = free;
        this.bookingCount = bookingCount;
    }

    // ---------- Getters & Setters ----------

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public boolean isFree() { return free; }
    public void setFree(boolean free) { this.free = free; }

    public int getBookingCount() { return bookingCount; }
    public void setBookingCount(int bookingCount) { this.bookingCount = bookingCount; }

    @Override
    public String toString() {
        return "CalendarDaySummary{" +
                "date='" + date + '\'' +
                ", free=" + free +
                ", bookingCount=" + bookingCount +
                '}';
    }
}

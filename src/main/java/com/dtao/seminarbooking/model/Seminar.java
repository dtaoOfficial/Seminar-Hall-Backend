package com.dtao.seminarbooking.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * Seminar model.
 *
 * New: supports per-day time map `daySlots` where key = "YYYY-MM-DD" and value = DaySlot (startTime/endTime).
 * A null value in the map indicates the day is a full-day booking.
 */
@Document(collection = "seminars")
public class Seminar {
    @Id
    private String id;

    private String hallName;
    private String slot;        // Morning, Afternoon, Full Day or "Custom"
    private String slotTitle;   // Event Name
    private String bookingName; // Faculty Name
    private String email;       // must end with @newhorizonindia.edu
    private String department;  // e.g. CSE-1, MCA
    private String phone;       // 10-digit phone

    // new: support full-day ranges
    private String startDate;   // YYYY-MM-DD (for day bookings)
    private String endDate;     // YYYY-MM-DD (for day bookings)

    private String date;        // YYYY-MM-DD (for time-based bookings)
    private String startTime;   // HH:mm (optional for slot-based)
    private String endTime;     // HH:mm (optional for slot-based)
    private String status = "APPROVED"; // Admin adds directly by default
    private String remarks;     // free-text remarks from requests/approval
    private String appliedAt;
    private String cancellationReason;
    private String createdBy;

    // New: per-day time map (optional). Key: "YYYY-MM-DD" -> value: DaySlot or null (null -> full-day)
    private Map<String, DaySlot> daySlots;

    public Seminar() {}

    // DaySlot nested class - represents time for a single day
    public static class DaySlot {
        private String startTime;
        private String endTime;

        public DaySlot() {}

        public DaySlot(String startTime, String endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getStartTime() { return startTime; }
        public String getEndTime() { return endTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
    }

    // Getters
    public String getId() { return id; }
    public String getHallName() { return hallName; }
    public String getSlot() { return slot; }
    public String getSlotTitle() { return slotTitle; }
    public String getBookingName() { return bookingName; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public String getPhone() { return phone; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public String getDate() { return date; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public String getStatus() { return status; }
    public String getRemarks() { return remarks; }
    public String getAppliedAt() { return appliedAt; }
    public String getCancellationReason() { return cancellationReason; }
    public String getCreatedBy() { return createdBy; }
    public Map<String, DaySlot> getDaySlots() { return daySlots; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setHallName(String hallName) { this.hallName = hallName; }
    public void setSlot(String slot) { this.slot = slot; }
    public void setSlotTitle(String slotTitle) { this.slotTitle = slotTitle; }
    public void setBookingName(String bookingName) { this.bookingName = bookingName; }
    public void setEmail(String email) { this.email = email; }
    public void setDepartment(String department) { this.department = department; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public void setDate(String date) { this.date = date; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public void setStatus(String status) { this.status = status; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public void setAppliedAt(String appliedAt) { this.appliedAt = appliedAt; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setDaySlots(Map<String, DaySlot> daySlots) { this.daySlots = daySlots; }
}

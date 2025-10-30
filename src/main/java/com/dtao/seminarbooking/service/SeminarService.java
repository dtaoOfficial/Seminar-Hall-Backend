// File: src/main/java/com/dtao/seminarbooking/service/SeminarService.java
package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.Seminar;
import com.dtao.seminarbooking.model.Seminar.DaySlot;
import com.dtao.seminarbooking.payload.CalendarDaySummary;
import com.dtao.seminarbooking.repo.SeminarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SeminarService {

    @Autowired
    private SeminarRepository seminarRepository;

    // Regex for email validation -> only @newhorizonindia.edu allowed
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@newhorizonindia\\.edu$");

    // Regex for Indian phone numbers (start with 6-9, 10 digits)
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[6-9][0-9]{9}$");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    // -------------------------
    // Add seminar
    // (existing code unchanged)
    // -------------------------
    public Seminar addSeminar(Seminar seminar) {
        if (seminar.getCreatedBy() != null &&
                !"ADMIN".equalsIgnoreCase(seminar.getCreatedBy().trim())) {
            throw new RuntimeException("createdBy may only be set to 'ADMIN' by admin endpoints.");
        }

        String status = seminar.getStatus() == null ? "" : seminar.getStatus().toUpperCase();
        if ("APPROVED".equals(status) &&
                (seminar.getCreatedBy() == null || seminar.getCreatedBy().isBlank())) {
            seminar.setCreatedBy("ADMIN");
        }

        // Validate email/phone first
        validateEmailPhoneOrThrow(seminar);

        // Validate payload shape & check conflicts
        validatePayloadShapeOrThrow(seminar);
        checkTimeConflictsForAdd(seminar);

        if (seminar.getAppliedAt() == null) {
            seminar.setAppliedAt(Instant.now().toString());
        }

        return seminarRepository.save(seminar);
    }

    // -------------------------
    // Read operations
    // -------------------------
    public List<Seminar> getAllSeminars() {
        return seminarRepository.findAll();
    }

    public List<Seminar> getSeminarsByDate(String date) {
        return seminarRepository.findByDate(date);
    }

    public List<Seminar> getByHallAndDate(String date, String hallName) {
        return seminarRepository.findByDateAndHallName(date, hallName);
    }

    public List<Seminar> findByDateAndHallName(String date, String hallName) {
        return getByHallAndDate(date, hallName);
    }

    public List<Seminar> getByDepartmentAndEmail(String department, String email) {
        return seminarRepository.findByDepartmentAndEmail(department, email);
    }

    public Optional<Seminar> getById(String id) {
        return seminarRepository.findById(id);
    }

    // -------------------------
    // New: Calendar support methods
    // -------------------------

    /**
     * Returns per-day summary for a given month (uses repo optimized queries when possible).
     *
     * @param hallName optional hall filter (case-insensitive)
     * @param year     e.g. 2025
     * @param month    1..12
     * @return list with one CalendarDaySummary per day of month (sorted ascending)
     */
    public List<CalendarDaySummary> getCalendarMonthSummary(String hallName, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        String startStr = start.format(DATE_FMT);
        String endStr = end.format(DATE_FMT);

        Map<LocalDate, Integer> counts = new HashMap<>();
        String hallNorm = (hallName == null || hallName.isBlank()) ? null : hallName.trim();

        // 1) time bookings (seminar.date)
        List<Seminar> timeBookings;
        if (hallNorm != null) {
            // use optimized repo if available
            try {
                timeBookings = seminarRepository.findByHallNameAndDateBetween(hallNorm, startStr, endStr);
            } catch (Exception ex) {
                // fallback: filter all
                timeBookings = seminarRepository.findAll().stream()
                        .filter(s -> s.getDate() != null && s.getHallName() != null && s.getHallName().equalsIgnoreCase(hallNorm))
                        .collect(Collectors.toList());
            }
        } else {
            // no hall filter -> filter all
            timeBookings = seminarRepository.findAll().stream()
                    .filter(s -> s.getDate() != null)
                    .collect(Collectors.toList());
        }

        for (Seminar s : timeBookings) {
            try {
                LocalDate d = LocalDate.parse(s.getDate(), DATE_FMT);
                if (!d.isBefore(start) && !d.isAfter(end)) {
                    counts.put(d, counts.getOrDefault(d, 0) + 1);
                }
            } catch (DateTimeParseException ignore) {}
        }

        // 2) day-range bookings overlapping month (startDate..endDate)
        List<Seminar> dayRangeBookings;
        if (hallNorm != null) {
            // repository method: findByHallNameAndStartDateLessThanEqualAndEndDateGreaterThanEqual(String hallName, String startDate, String endDate);
            // we want startDate <= monthEnd AND endDate >= monthStart  => pass (endStr, startStr) as params
            try {
                dayRangeBookings = seminarRepository.findByHallNameAndStartDateLessThanEqualAndEndDateGreaterThanEqual(hallNorm, endStr, startStr);
            } catch (Exception ex) {
                dayRangeBookings = seminarRepository.findAll().stream()
                        .filter(s -> s.getStartDate() != null && s.getEndDate() != null
                                && s.getHallName() != null && s.getHallName().equalsIgnoreCase(hallNorm))
                        .collect(Collectors.toList());
            }
        } else {
            // no hall filter -> include any day-range that intersects the month
            dayRangeBookings = seminarRepository.findAll().stream()
                    .filter(s -> s.getStartDate() != null && s.getEndDate() != null)
                    .collect(Collectors.toList());
        }

        for (Seminar s : dayRangeBookings) {
            try {
                LocalDate sd = LocalDate.parse(s.getStartDate(), DATE_FMT);
                LocalDate ed = LocalDate.parse(s.getEndDate(), DATE_FMT);
                LocalDate from = sd.isBefore(start) ? start : sd;
                LocalDate to = ed.isAfter(end) ? end : ed;
                if (!to.isBefore(from)) {
                    LocalDate cur = from;
                    while (!cur.isAfter(to)) {
                        counts.put(cur, counts.getOrDefault(cur, 0) + 1);
                        cur = cur.plusDays(1);
                    }
                }
            } catch (DateTimeParseException ignore) {}
        }

        // 3) daySlots specific dates (map keys)
        // filter seminars that have daySlots and (optional) hall
        List<Seminar> withDaySlots = seminarRepository.findAll().stream()
                .filter(s -> s.getDaySlots() != null && !s.getDaySlots().isEmpty())
                .filter(s -> hallNorm == null || (s.getHallName() != null && s.getHallName().equalsIgnoreCase(hallNorm)))
                .collect(Collectors.toList());

        for (Seminar s : withDaySlots) {
            for (String key : s.getDaySlots().keySet()) {
                try {
                    LocalDate d = LocalDate.parse(key, DATE_FMT);
                    if (!d.isBefore(start) && !d.isAfter(end)) {
                        counts.put(d, counts.getOrDefault(d, 0) + 1);
                    }
                } catch (DateTimeParseException ignore) {}
            }
        }

        // Build final list
        List<CalendarDaySummary> out = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            int c = counts.getOrDefault(cursor, 0);
            out.add(new CalendarDaySummary(cursor, c == 0, c));
            cursor = cursor.plusDays(1);
        }
        return out;
    }

    /**
     * Returns all seminars affecting a given date:
     * - exact date bookings (seminar.date == date)
     * - day-range bookings (startDate <= date <= endDate)
     * - daySlots entries that contain the date
     *
     * @param date     "YYYY-MM-DD"
     * @param hallName optional hall filter
     */
    public List<Seminar> getSeminarsForDay(String date, String hallName) {
        // 1) time bookings exact
        List<Seminar> timeBookings;
        if (hallName != null && !hallName.isBlank()) {
            timeBookings = seminarRepository.findByDateAndHallName(date, hallName);
        } else {
            timeBookings = seminarRepository.findByDate(date);
        }

        // 2) day-range bookings that include date
        List<Seminar> dayRange;
        String hallNorm = (hallName == null || hallName.isBlank()) ? null : hallName.trim();
        if (hallNorm != null) {
            // use repo method expecting startDate <= date && endDate >= date
            try {
                dayRange = seminarRepository.findByHallNameAndStartDateLessThanEqualAndEndDateGreaterThanEqual(hallNorm, date, date);
            } catch (Exception ex) {
                dayRange = seminarRepository.findAll().stream()
                        .filter(s -> s.getStartDate() != null && s.getEndDate() != null
                                && s.getStartDate().compareTo(date) <= 0 && s.getEndDate().compareTo(date) >= 0
                                && s.getHallName() != null && s.getHallName().equalsIgnoreCase(hallNorm))
                        .collect(Collectors.toList());
            }
        } else {
            dayRange = seminarRepository.findAll().stream()
                    .filter(s -> s.getStartDate() != null && s.getEndDate() != null
                            && s.getStartDate().compareTo(date) <= 0 && s.getEndDate().compareTo(date) >= 0)
                    .collect(Collectors.toList());
        }

        // 3) daySlots containing this date
        List<Seminar> daySlotMatches = seminarRepository.findAll().stream()
                .filter(s -> s.getDaySlots() != null && s.getDaySlots().containsKey(date))
                .filter(s -> hallNorm == null || (s.getHallName() != null && s.getHallName().equalsIgnoreCase(hallNorm)))
                .collect(Collectors.toList());

        // Merge without duplicates (use id)
        Map<String, Seminar> merged = new LinkedHashMap<>();
        for (Seminar s : timeBookings) if (s != null && s.getId() != null) merged.put(s.getId(), s);
        for (Seminar s : dayRange) if (s != null && s.getId() != null) merged.put(s.getId(), s);
        for (Seminar s : daySlotMatches) if (s != null && s.getId() != null) merged.put(s.getId(), s);

        return new ArrayList<>(merged.values());
    }

    // -------------------------
    // Update seminar
    // (existing code unchanged)
    // -------------------------
    public Seminar updateSeminar(String id, Seminar updatedSeminar) {
        return seminarRepository.findById(id).map(existing -> {
            if (updatedSeminar.getCreatedBy() != null &&
                    !"ADMIN".equalsIgnoreCase(updatedSeminar.getCreatedBy().trim())) {
                throw new RuntimeException("createdBy may only be set to 'ADMIN' by admin endpoints.");
            }

            if (updatedSeminar.getHallName() != null) existing.setHallName(updatedSeminar.getHallName());
            if (updatedSeminar.getDate() != null) existing.setDate(updatedSeminar.getDate());
            if (updatedSeminar.getStartDate() != null) existing.setStartDate(updatedSeminar.getStartDate());
            if (updatedSeminar.getEndDate() != null) existing.setEndDate(updatedSeminar.getEndDate());
            if (updatedSeminar.getSlot() != null) existing.setSlot(updatedSeminar.getSlot());
            if (updatedSeminar.getSlotTitle() != null) existing.setSlotTitle(updatedSeminar.getSlotTitle());
            if (updatedSeminar.getBookingName() != null) existing.setBookingName(updatedSeminar.getBookingName());
            if (updatedSeminar.getEmail() != null) existing.setEmail(updatedSeminar.getEmail());
            if (updatedSeminar.getDepartment() != null) existing.setDepartment(updatedSeminar.getDepartment());
            if (updatedSeminar.getPhone() != null) existing.setPhone(updatedSeminar.getPhone());
            if (updatedSeminar.getStartTime() != null) existing.setStartTime(updatedSeminar.getStartTime());
            if (updatedSeminar.getEndTime() != null) existing.setEndTime(updatedSeminar.getEndTime());
            if (updatedSeminar.getRemarks() != null) existing.setRemarks(updatedSeminar.getRemarks());
            if (updatedSeminar.getAppliedAt() != null) existing.setAppliedAt(updatedSeminar.getAppliedAt());
            if (updatedSeminar.getStatus() != null) existing.setStatus(updatedSeminar.getStatus());
            if (updatedSeminar.getCancellationReason() != null) {
                existing.setCancellationReason(updatedSeminar.getCancellationReason());
            }
            if (updatedSeminar.getCreatedBy() != null &&
                    !"".equals(updatedSeminar.getCreatedBy().trim())) {
                existing.setCreatedBy(updatedSeminar.getCreatedBy().trim());
            }
            if (updatedSeminar.getDaySlots() != null) {
                existing.setDaySlots(updatedSeminar.getDaySlots());
            }

            // Validate and check conflicts before save
            validateEmailPhoneOrThrow(existing);
            validatePayloadShapeOrThrow(existing);
            checkTimeConflictsForUpdate(existing, id);

            return seminarRepository.save(existing);
        }).orElse(null);
    }

    public void deleteSeminar(String id) {
        seminarRepository.deleteById(id);
    }

    // -------------------------
    // Cancel request
    // -------------------------
    public Seminar requestCancel(String id, String cancellationReason, String remarks) {
        return seminarRepository.findById(id).map(existing -> {
            existing.setStatus("CANCEL_REQUESTED");

            if (cancellationReason != null && !cancellationReason.isBlank()) {
                existing.setCancellationReason(cancellationReason);
            }

            String prev = existing.getRemarks() == null ? "" : existing.getRemarks();
            if (remarks != null && !remarks.isBlank()) {
                if (!prev.isBlank()) prev += " | ";
                prev += remarks;
                existing.setRemarks(prev);
            }

            return seminarRepository.save(existing);
        }).orElse(null);
    }

    // ---------- private helpers ----------
    private void validateEmailPhoneOrThrow(Seminar seminar) {
        if (seminar.getEmail() == null || !EMAIL_PATTERN.matcher(seminar.getEmail()).matches()) {
            throw new RuntimeException("Invalid email! Must end with @newhorizonindia.edu");
        }
        if (seminar.getPhone() == null || !PHONE_PATTERN.matcher(seminar.getPhone()).matches()) {
            throw new RuntimeException("Invalid phone number! Must be 10 digits starting with 6/7/8/9");
        }
    }

    /**
     * Validate payload shape:
     * - Time booking requires date + startTime + endTime
     * - Day booking requires startDate + endDate OR (startDate+endDate + daySlots map)
     * - Otherwise a slot-based booking is allowed if slot is present (Full Day/Morning etc.)
     */
    private void validatePayloadShapeOrThrow(Seminar seminar) {
        boolean hasTimeShape = seminar.getDate() != null && seminar.getStartTime() != null && seminar.getEndTime() != null;
        boolean hasDayShape = seminar.getStartDate() != null && seminar.getEndDate() != null;

        // If daySlots is provided, it must be accompanied by startDate & endDate
        if (seminar.getDaySlots() != null && !hasDayShape) {
            throw new RuntimeException("daySlots provided without startDate/endDate");
        }

        if (!hasTimeShape && !hasDayShape) {
            // allow slot-based bookings (slot string supplied)
            if (seminar.getSlot() == null || seminar.getSlot().isBlank()) {
                throw new RuntimeException("Invalid booking payload. Provide either date+startTime+endTime (time booking) or startDate+endDate (day booking) or a valid slot value.");
            }
        }

        // Validate date formats if present
        try {
            if (seminar.getDate() != null) LocalDate.parse(seminar.getDate(), DATE_FMT);
            if (seminar.getStartDate() != null) LocalDate.parse(seminar.getStartDate(), DATE_FMT);
            if (seminar.getEndDate() != null) LocalDate.parse(seminar.getEndDate(), DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw new RuntimeException("Dates must be in YYYY-MM-DD format");
        }

        // Validate time order for time bookings
        if (hasTimeShape) {
            if (!isTimeOrderValid(seminar.getStartTime(), seminar.getEndTime())) {
                throw new RuntimeException("Invalid time range: endTime must be after startTime");
            }
        }

        if (hasDayShape) {
            LocalDate sd = LocalDate.parse(seminar.getStartDate(), DATE_FMT);
            LocalDate ed = LocalDate.parse(seminar.getEndDate(), DATE_FMT);
            if (ed.isBefore(sd)) throw new RuntimeException("Invalid date range: endDate is before startDate");
            // If daySlots exist, validate each day's time ordering individually
            Map<String, DaySlot> dsMap = seminar.getDaySlots();
            if (dsMap != null && !dsMap.isEmpty()) {
                for (Map.Entry<String, DaySlot> e : dsMap.entrySet()) {
                    String key = e.getKey();
                    DaySlot slot = e.getValue();
                    // key must be parsable and within the provided range
                    LocalDate d;
                    try {
                        d = LocalDate.parse(key, DATE_FMT);
                    } catch (DateTimeParseException ex) {
                        throw new RuntimeException("daySlots key is not a valid date: " + key);
                    }
                    if (d.isBefore(sd) || d.isAfter(ed)) {
                        throw new RuntimeException("daySlots contains a date outside startDate..endDate: " + key);
                    }
                    if (slot != null) {
                        if (!isTimeOrderValid(slot.getStartTime(), slot.getEndTime())) {
                            throw new RuntimeException("Invalid time range in daySlots for " + key);
                        }
                    }
                }
            }
        }
    }

    private boolean isTimeOrderValid(String start, String end) {
        try {
            int s = toMinutes(start);
            int e = toMinutes(end);
            return e > s;
        } catch (Exception ex) {
            return false;
        }
    }

    // Check conflicts for ADD
    private void checkTimeConflictsForAdd(Seminar seminar) {
        // ... (unchanged) existing implementation copied from original file
        // Keep your existing logic as-is (omitted here for brevity in this snippet)
        // The full original implementation remains in your file.
    }

    // Check conflicts for UPDATE (ignore itself)
    private void checkTimeConflictsForUpdate(Seminar seminar, String id) {
        // ... (unchanged) existing implementation copied from original file
        // Keep your existing logic as-is (omitted here for brevity in this snippet)
        // The full original implementation remains in your file.
    }

    // Simple overlap check (time strings HH:mm)
    private boolean isOverlapping(String start1, String end1, String start2, String end2) {
        if (start1 == null || end1 == null || start2 == null || end2 == null) return false;
        try {
            int s1 = toMinutes(start1);
            int e1 = toMinutes(end1);
            int s2 = toMinutes(start2);
            int e2 = toMinutes(end2);
            return s1 < e2 && s2 < e1; // overlap if intervals intersect
        } catch (Exception ex) {
            return false;
        }
    }

    private int toMinutes(String hhmm) {
        String[] parts = hhmm.split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        return h * 60 + m;
    }

    public List<Seminar> getByStatus(String status) {
        return seminarRepository.findByStatusIgnoreCase(status);
    }

}

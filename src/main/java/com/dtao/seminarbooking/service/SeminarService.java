package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.Seminar;
import com.dtao.seminarbooking.model.Seminar.DaySlot;
import com.dtao.seminarbooking.repo.SeminarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

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
    // Update seminar
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
        // If it's a time booking (date + start+end) check:
        if (seminar.getHallName() != null && seminar.getDate() != null && seminar.getStartTime() != null && seminar.getEndTime() != null) {
            // 1) check for existing time bookings on same date (in repo)
            List<Seminar> sameDay = seminarRepository.findByDateAndHallName(seminar.getDate(), seminar.getHallName());
            for (Seminar s : sameDay) {
                if (s.getStartTime() != null && s.getEndTime() != null) {
                    if (isOverlapping(seminar.getStartTime(), seminar.getEndTime(), s.getStartTime(), s.getEndTime())) {
                        throw new RuntimeException("❌ Time conflict: " + seminar.getHallName()
                                + " already booked from " + s.getStartTime() + " to " + s.getEndTime());
                    }
                } else {
                    // existing record without start/end -> treat conservative as conflict
                    throw new RuntimeException("❌ Time conflict: " + seminar.getHallName() + " has an existing booking on " + seminar.getDate());
                }
            }

            // 2) check for any day-booking that covers this date (load all and filter)
            List<Seminar> all = seminarRepository.findAll();
            for (Seminar s : all) {
                if (s.getHallName() == null) continue;
                if (!s.getHallName().equalsIgnoreCase(seminar.getHallName())) continue;

                // if s is day-range (startDate..endDate) then it blocks full day
                if (s.getStartDate() != null && s.getEndDate() != null) {
                    LocalDate sd = LocalDate.parse(s.getStartDate(), DATE_FMT);
                    LocalDate ed = LocalDate.parse(s.getEndDate(), DATE_FMT);
                    LocalDate target = LocalDate.parse(seminar.getDate(), DATE_FMT);
                    if ((target.isEqual(sd) || target.isAfter(sd)) && (target.isEqual(ed) || target.isBefore(ed))) {
                        throw new RuntimeException("❌ Time conflict: " + seminar.getHallName() + " is blocked for full-day booking on " + seminar.getDate());
                    }
                }
            }
        }

        // If it's a day booking (startDate + endDate)
        if (seminar.getHallName() != null && seminar.getStartDate() != null && seminar.getEndDate() != null) {
            LocalDate sd = LocalDate.parse(seminar.getStartDate(), DATE_FMT);
            LocalDate ed = LocalDate.parse(seminar.getEndDate(), DATE_FMT);

            Map<String, DaySlot> dsMap = seminar.getDaySlots();

            // If daySlots provided -> validate conflicts per date
            if (dsMap != null && !dsMap.isEmpty()) {
                // For each date in provided daySlots (only those inside the range)
                for (Map.Entry<String, DaySlot> e : dsMap.entrySet()) {
                    String dateKey = e.getKey();
                    DaySlot daySlot = e.getValue();
                    LocalDate d;
                    try {
                        d = LocalDate.parse(dateKey, DATE_FMT);
                    } catch (DateTimeParseException ex) {
                        throw new RuntimeException("Invalid daySlots date: " + dateKey);
                    }
                    if (d.isBefore(sd) || d.isAfter(ed)) {
                        throw new RuntimeException("daySlots date outside startDate..endDate: " + dateKey);
                    }

                    // If daySlot == null => full day requested for that date -> conflict if any booking exists on that date
                    if (daySlot == null) {
                        // check any time booking on this date or any existing day booking covering it
                        List<Seminar> timeBookings = seminarRepository.findByDate(dateKey);
                        for (Seminar s : timeBookings) {
                            if (s.getHallName() != null && s.getHallName().equalsIgnoreCase(seminar.getHallName())) {
                                throw new RuntimeException("❌ Conflict: existing time booking on " + dateKey + " (full-day requested)");
                            }
                        }
                        // check day bookings that cover this date
                        for (Seminar s : seminarRepository.findAll()) {
                            if (s.getHallName() == null || s.getStartDate() == null || s.getEndDate() == null) continue;
                            if (!s.getHallName().equalsIgnoreCase(seminar.getHallName())) continue;
                            LocalDate os = LocalDate.parse(s.getStartDate(), DATE_FMT);
                            LocalDate oe = LocalDate.parse(s.getEndDate(), DATE_FMT);
                            if (!(oe.isBefore(d) || os.isAfter(d))) {
                                throw new RuntimeException("❌ Conflict: existing full-day booking covers " + dateKey + " (" + s.getStartDate() + " → " + s.getEndDate() + ")");
                            }
                        }
                    } else {
                        // daySlot has start/end -> check time overlaps on that date
                        int sMin = toMinutes(daySlot.getStartTime());
                        int eMin = toMinutes(daySlot.getEndTime());
                        if (eMin <= sMin) throw new RuntimeException("Invalid daySlots times for " + dateKey);

                        // check time bookings on same date
                        List<Seminar> sameDay = seminarRepository.findByDateAndHallName(dateKey, seminar.getHallName());
                        for (Seminar s : sameDay) {
                            if (s.getStartTime() != null && s.getEndTime() != null) {
                                if (isOverlapping(daySlot.getStartTime(), daySlot.getEndTime(), s.getStartTime(), s.getEndTime())) {
                                    throw new RuntimeException("❌ Conflict on " + dateKey + ": existing time booking from " + s.getStartTime() + " to " + s.getEndTime());
                                }
                            } else {
                                // existing day booking without times -> it blocks full day
                                throw new RuntimeException("❌ Conflict on " + dateKey + ": existing full-day booking");
                            }
                        }

                        // check day-range bookings that cover this date (full-day)
                        for (Seminar s : seminarRepository.findAll()) {
                            if (s.getHallName() == null || s.getStartDate() == null || s.getEndDate() == null) continue;
                            if (!s.getHallName().equalsIgnoreCase(seminar.getHallName())) continue;
                            LocalDate os = LocalDate.parse(s.getStartDate(), DATE_FMT);
                            LocalDate oe = LocalDate.parse(s.getEndDate(), DATE_FMT);
                            if (!(oe.isBefore(d) || os.isAfter(d))) {
                                throw new RuntimeException("❌ Conflict: existing full-day booking covers " + dateKey + " (" + s.getStartDate() + " → " + s.getEndDate() + ")");
                            }
                        }
                    }
                }
            } else {
                // No daySlots -> treat entire range as full-day requests -> keep previous checks (existing time bookings inside range and overlapping day bookings)
                // find all time bookings for hall where date between sd..ed
                List<Seminar> timeBookings = seminarRepository.findAll(); // we'll filter in-memory
                for (Seminar s : timeBookings) {
                    if (s.getHallName() == null) continue;
                    if (!s.getHallName().equalsIgnoreCase(seminar.getHallName())) continue;
                    if (s.getDate() == null || s.getStartTime() == null || s.getEndTime() == null) continue;

                    LocalDate d = LocalDate.parse(s.getDate(), DATE_FMT);
                    if ((d.isEqual(sd) || d.isAfter(sd)) && (d.isEqual(ed) || d.isBefore(ed))) {
                        throw new RuntimeException("❌ Conflict: existing time booking on " + s.getDate() + " from " + s.getStartTime() + " to " + s.getEndTime());
                    }
                }

                // also check overlapping day bookings
                for (Seminar s : seminarRepository.findAll()) {
                    if (s.getHallName() == null || s.getStartDate() == null || s.getEndDate() == null) continue;
                    if (!s.getHallName().equalsIgnoreCase(seminar.getHallName())) continue;
                    LocalDate os = LocalDate.parse(s.getStartDate(), DATE_FMT);
                    LocalDate oe = LocalDate.parse(s.getEndDate(), DATE_FMT);
                    // overlap if ranges intersect: !(oe < sd || os > ed)
                    if (!(oe.isBefore(sd) || os.isAfter(ed))) {
                        throw new RuntimeException("❌ Conflict: overlapping full-day booking exists in that range (" + s.getStartDate() + " → " + s.getEndDate() + ")");
                    }
                }
            }
        }
    }

    // Check conflicts for UPDATE (ignore itself)
    private void checkTimeConflictsForUpdate(Seminar seminar, String id) {
        // If it's a time booking (date + start+end) check:
        if (seminar.getHallName() != null && seminar.getDate() != null && seminar.getStartTime() != null && seminar.getEndTime() != null) {
            List<Seminar> sameDay = seminarRepository.findByDateAndHallName(seminar.getDate(), seminar.getHallName());
            for (Seminar s : sameDay) {
                if (s.getId() != null && s.getId().equals(id)) continue;
                if (s.getStartTime() != null && s.getEndTime() != null) {
                    if (isOverlapping(seminar.getStartTime(), seminar.getEndTime(), s.getStartTime(), s.getEndTime())) {
                        throw new RuntimeException("❌ Time conflict: " + seminar.getHallName()
                                + " already booked from " + s.getStartTime() + " to " + s.getEndTime());
                    }
                } else {
                    throw new RuntimeException("❌ Time conflict: " + seminar.getHallName() + " has an existing booking on " + seminar.getDate());
                }
            }

            // check day bookings that include this date
            for (Seminar s : seminarRepository.findAll()) {
                if (s.getId() != null && s.getId().equals(id)) continue;
                if (s.getHallName() == null || s.getStartDate() == null || s.getEndDate() == null) continue;
                if (!s.getHallName().equalsIgnoreCase(seminar.getHallName())) continue;
                LocalDate sd = LocalDate.parse(s.getStartDate(), DATE_FMT);
                LocalDate ed = LocalDate.parse(s.getEndDate(), DATE_FMT);
                LocalDate target = LocalDate.parse(seminar.getDate(), DATE_FMT);
                if ((target.isEqual(sd) || target.isAfter(sd)) && (target.isEqual(ed) || target.isBefore(ed))) {
                    throw new RuntimeException("❌ Time conflict: " + seminar.getHallName() + " is blocked for full-day booking on " + seminar.getDate());
                }
            }
        }

        // If it's a day booking check overlapping time and other day bookings
        if (seminar.getHallName() != null && seminar.getStartDate() != null && seminar.getEndDate() != null) {
            LocalDate sd = LocalDate.parse(seminar.getStartDate(), DATE_FMT);
            LocalDate ed = LocalDate.parse(seminar.getEndDate(), DATE_FMT);

            Map<String, DaySlot> dsMap = seminar.getDaySlots();

            // For daySlots: check per-date conflicts; skip the record itself by id when comparing
            if (dsMap != null && !dsMap.isEmpty()) {
                for (Map.Entry<String, DaySlot> e : dsMap.entrySet()) {
                    String dateKey = e.getKey();
                    DaySlot daySlot = e.getValue();
                    LocalDate d;
                    try {
                        d = LocalDate.parse(dateKey, DATE_FMT);
                    } catch (DateTimeParseException ex) {
                        throw new RuntimeException("Invalid daySlots date: " + dateKey);
                    }
                    if (d.isBefore(sd) || d.isAfter(ed)) {
                        throw new RuntimeException("daySlots date outside startDate..endDate: " + dateKey);
                    }

                    if (daySlot == null) {
                        // full-day requested for this date -> conflict if any other booking (time or day) exists
                        for (Seminar s : seminarRepository.findAll()) {
                            if (s.getId() != null && s.getId().equals(id)) continue;
                            if (s.getHallName() == null) continue;
                            if (!s.getHallName().equalsIgnoreCase(seminar.getHallName())) continue;

                            // existing time booking on this date?
                            if (dateKey.equals(s.getDate()) && s.getStartTime() != null && s.getEndTime() != null) {
                                throw new RuntimeException("❌ Conflict: existing time booking on " + dateKey + " from " + s.getStartTime() + " to " + s.getEndTime());
                            }

                            // existing day-range covering this date?
                            if (s.getStartDate() != null && s.getEndDate() != null) {
                                LocalDate os = LocalDate.parse(s.getStartDate(), DATE_FMT);
                                LocalDate oe = LocalDate.parse(s.getEndDate(), DATE_FMT);
                                if (!(oe.isBefore(d) || os.isAfter(d))) {
                                    throw new RuntimeException("❌ Conflict: existing full-day booking covers " + dateKey + " (" + s.getStartDate() + " → " + s.getEndDate() + ")");
                                }
                            }
                        }
                    } else {
                        // time slot provided for this date -> check time overlaps, ignoring the same id
                        List<Seminar> sameDay = seminarRepository.findByDateAndHallName(dateKey, seminar.getHallName());
                        for (Seminar s : sameDay) {
                            if (s.getId() != null && s.getId().equals(id)) continue;
                            if (s.getStartTime() != null && s.getEndTime() != null) {
                                if (isOverlapping(daySlot.getStartTime(), daySlot.getEndTime(), s.getStartTime(), s.getEndTime())) {
                                    throw new RuntimeException("❌ Conflict on " + dateKey + ": existing time booking from " + s.getStartTime() + " to " + s.getEndTime());
                                }
                            } else {
                                // existing full-day booking without times blocks this date
                                throw new RuntimeException("❌ Conflict on " + dateKey + ": existing full-day booking");
                            }
                        }

                        // check existing day-range bookings covering this date (ignore same id)
                        for (Seminar s : seminarRepository.findAll()) {
                            if (s.getId() != null && s.getId().equals(id)) continue;
                            if (s.getHallName() == null || s.getStartDate() == null || s.getEndDate() == null) continue;
                            if (!s.getHallName().equalsIgnoreCase(seminar.getHallName())) continue;
                            LocalDate os = LocalDate.parse(s.getStartDate(), DATE_FMT);
                            LocalDate oe = LocalDate.parse(s.getEndDate(), DATE_FMT);
                            if (!(oe.isBefore(d) || os.isAfter(d))) {
                                throw new RuntimeException("❌ Conflict: existing full-day booking covers " + dateKey + " (" + s.getStartDate() + " → " + s.getEndDate() + ")");
                            }
                        }
                    }
                }
            } else {
                // If no daySlots: previous behavior - check all time bookings inside range and overlapping day bookings, ignoring self
                for (Seminar s : seminarRepository.findAll()) {
                    if (s.getId() != null && s.getId().equals(id)) continue;
                    if (s.getHallName() == null) continue;
                    if (!s.getHallName().equalsIgnoreCase(seminar.getHallName())) continue;

                    // time bookings inside range
                    if (s.getDate() != null && s.getStartTime() != null && s.getEndTime() != null) {
                        LocalDate d = LocalDate.parse(s.getDate(), DATE_FMT);
                        if ((d.isEqual(sd) || d.isAfter(sd)) && (d.isEqual(ed) || d.isBefore(ed))) {
                            throw new RuntimeException("❌ Conflict: existing time booking on " + s.getDate() + " from " + s.getStartTime() + " to " + s.getEndTime());
                        }
                    }

                    // existing day bookings overlapping
                    if (s.getStartDate() != null && s.getEndDate() != null) {
                        LocalDate os = LocalDate.parse(s.getStartDate(), DATE_FMT);
                        LocalDate oe = LocalDate.parse(s.getEndDate(), DATE_FMT);
                        if (!(oe.isBefore(sd) || os.isAfter(ed))) {
                            throw new RuntimeException("❌ Conflict: overlapping full-day booking exists in that range (" + s.getStartDate() + " → " + s.getEndDate() + ")");
                        }
                    }
                }
            }
        }
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
}

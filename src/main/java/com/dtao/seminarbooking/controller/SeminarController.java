package com.dtao.seminarbooking.controller;

import com.dtao.seminarbooking.model.HallOperator;
import com.dtao.seminarbooking.model.Seminar;
import com.dtao.seminarbooking.service.EmailService;
import com.dtao.seminarbooking.service.HallOperatorService;
import com.dtao.seminarbooking.service.SeminarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/seminars")
public class SeminarController {

    private static final Logger log = LoggerFactory.getLogger(SeminarController.class);

    private final SeminarService seminarService;
    private final EmailService emailService;
    private final HallOperatorService hallOperatorService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public SeminarController(SeminarService seminarService,
                             EmailService emailService,
                             HallOperatorService hallOperatorService) {
        this.seminarService = seminarService;
        this.emailService = emailService;
        this.hallOperatorService = hallOperatorService;
    }

    @PostMapping
    public ResponseEntity<?> createSeminar(@RequestBody Seminar seminar) {
        try {
            Seminar saved = seminarService.addSeminar(seminar);

            // 1) Send booking confirmation to requester (async fire-and-forget)
            try {
                CompletableFuture<Boolean> f = emailService.sendBookingCreatedEmail(saved);
                attachLogging(f, "sendBookingCreatedEmail", saved.getEmail());
            } catch (Exception ex) {
                log.error("[SeminarController] Failed to initiate booking-created email: {}", ex.getMessage(), ex);
            }

            // 2) Notify ALL hall operators for this hall (created event)
            try {
                if (saved.getHallName() != null) {
                    List<HallOperator> heads = hallOperatorService.findByHallName(saved.getHallName());
                    for (HallOperator head : heads) {
                        try {
                            CompletableFuture<Boolean> f = emailService.sendHallHeadBookingCreatedEmail(head, saved);
                            attachLogging(f, "sendHallHeadBookingCreatedEmail", head.getHeadEmail());
                        } catch (Exception ex) {
                            log.error("[SeminarController] Failed to initiate hall-head booking-created email for head={} : {}",
                                    head == null ? "null" : head.getHeadEmail(), ex.getMessage(), ex);
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("[SeminarController] Error while finding hall operators on create: {}", ex.getMessage(), ex);
            }

            // 3) If created already APPROVED (admin-created auto-approve), immediately send APPROVED notifications
            try {
                String status = saved.getStatus() == null ? "" : saved.getStatus().toUpperCase();
                if ("APPROVED".equals(status)) {
                    String adminReason = "Approved & applied by admin";

                    // notify requester
                    try {
                        CompletableFuture<Boolean> f = emailService.sendStatusNotification(saved.getEmail(), saved, "APPROVED", adminReason);
                        attachLogging(f, "sendStatusNotification(APPROVED)", saved.getEmail());
                    } catch (Exception ex) {
                        log.error("[SeminarController] Failed to initiate immediate approved status email to requester: {}", ex.getMessage(), ex);
                    }

                    // notify all hall operators with approved email
                    try {
                        if (saved.getHallName() != null) {
                            List<HallOperator> heads = hallOperatorService.findByHallName(saved.getHallName());
                            for (HallOperator head : heads) {
                                try {
                                    CompletableFuture<Boolean> f = emailService.sendHallHeadBookingApprovedEmail(head, saved, adminReason);
                                    attachLogging(f, "sendHallHeadBookingApprovedEmail", head.getHeadEmail());
                                } catch (Exception ex) {
                                    log.error("[SeminarController] Failed to initiate hall-head immediate-approved email for head={} : {}",
                                            head == null ? "null" : head.getHeadEmail(), ex.getMessage(), ex);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.error("[SeminarController] Error while notifying hall operators for immediate approval: {}", ex.getMessage(), ex);
                    }
                }
            } catch (Exception ex) {
                log.error("[SeminarController] Error sending immediate approved notifications: {}", ex.getMessage(), ex);
            }

            return ResponseEntity.ok(saved);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("[SeminarController] createSeminar unexpected error: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    @GetMapping
    public ResponseEntity<List<Seminar>> getAllSeminars() {
        return ResponseEntity.ok(seminarService.getAllSeminars());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Seminar> getById(@PathVariable String id) {
        return seminarService.getById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<Seminar>> getSeminarsByDate(@PathVariable String date) {
        return ResponseEntity.ok(seminarService.getSeminarsByDate(date));
    }

    @GetMapping("/hall/{hallName}/date/{date}")
    public ResponseEntity<List<Seminar>> getByHallAndDate(
            @PathVariable String hallName,
            @PathVariable String date) {
        return ResponseEntity.ok(seminarService.getByHallAndDate(date, hallName));
    }

    // ----------------- NEW: calendar month summary -----------------
    /**
     * Returns per-day summary for a given month.
     * Query params:
     * - hallName (optional) : filters to specific seminar hall
     * - year (required)
     * - month (required) : 1-12
     *
     * Response: List<CalendarDaySummary>
     */
    @GetMapping("/calendar")
    public ResponseEntity<?> getCalendarMonthSummary(
            @RequestParam(required = false) String hallName,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        try {
            if (year == null || month == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "year and month are required (month: 1-12)"));
            }
            if (month < 1 || month > 12) {
                return ResponseEntity.badRequest().body(Map.of("error", "month must be between 1 and 12"));
            }

            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

            // load all seminars once and filter in-memory (keeps existing service unchanged)
            List<Seminar> all = seminarService.getAllSeminars();

            // if hallName filter provided, normalise
            String hallNameNorm = (hallName == null || hallName.isBlank()) ? null : hallName.trim();

            // Build a map date -> count
            Map<LocalDate, Integer> counts = new HashMap<>();

            // iterate all seminars and mark affected dates
            for (Seminar s : all) {
                // filter by hallName if requested
                if (hallNameNorm != null) {
                    if (s.getHallName() == null || !s.getHallName().equalsIgnoreCase(hallNameNorm)) {
                        continue;
                    }
                }

                // 1) exact date bookings (time bookings)
                if (s.getDate() != null) {
                    try {
                        LocalDate d = LocalDate.parse(s.getDate(), DATE_FMT);
                        if (!d.isBefore(start) && !d.isAfter(end)) {
                            counts.put(d, counts.getOrDefault(d, 0) + 1);
                        }
                    } catch (Exception ex) {
                        // ignore malformed dates
                    }
                }

                // 2) day-range bookings (startDate..endDate)
                if (s.getStartDate() != null && s.getEndDate() != null) {
                    try {
                        LocalDate sd = LocalDate.parse(s.getStartDate(), DATE_FMT);
                        LocalDate ed = LocalDate.parse(s.getEndDate(), DATE_FMT);
                        // compute overlap with requested month
                        LocalDate from = sd.isBefore(start) ? start : sd;
                        LocalDate to = ed.isAfter(end) ? end : ed;
                        if (!to.isBefore(from)) {
                            LocalDate curr = from;
                            while (!curr.isAfter(to)) {
                                counts.put(curr, counts.getOrDefault(curr, 0) + 1);
                                curr = curr.plusDays(1);
                            }
                        }
                    } catch (Exception ex) {
                        // ignore malformed
                    }
                }

                // 3) daySlots map (specific dates inside a day-range)
                if (s.getDaySlots() != null && !s.getDaySlots().isEmpty()) {
                    for (String key : s.getDaySlots().keySet()) {
                        try {
                            LocalDate d = LocalDate.parse(key, DATE_FMT);
                            if (!d.isBefore(start) && !d.isAfter(end)) {
                                counts.put(d, counts.getOrDefault(d, 0) + 1);
                            }
                        } catch (Exception ex) {
                            // ignore malformed keys
                        }
                    }
                }
            }

            // Build result list for all days in month
            List<CalendarDaySummary> result = new ArrayList<>();
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                int c = counts.getOrDefault(cursor, 0);
                boolean free = c == 0;
                result.add(new CalendarDaySummary(cursor, free, c));
                cursor = cursor.plusDays(1);
            }

            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            log.error("[SeminarController] getCalendarMonthSummary error: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    /**
     * Returns detailed list of seminars for a date (optional hallName filter).
     * Example: GET /api/seminars/day/2025-10-15?hallName=Main+Hall
     */
    @GetMapping("/day/{date}")
    public ResponseEntity<?> getSeminarsForDay(
            @PathVariable String date,
            @RequestParam(required = false) String hallName) {
        try {
            if (hallName != null && !hallName.isBlank()) {
                // reuse existing service method
                List<Seminar> res = seminarService.getByHallAndDate(date, hallName);
                return ResponseEntity.ok(res);
            } else {
                List<Seminar> res = seminarService.getSeminarsByDate(date);
                return ResponseEntity.ok(res);
            }
        } catch (Exception ex) {
            log.error("[SeminarController] getSeminarsForDay error: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }
    // ----------------- end calendar endpoints -----------------

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSeminar(
            @PathVariable String id,
            @RequestBody Seminar updatedSeminar) {
        try {
            // fetch before update to detect status change
            Seminar before = seminarService.getById(id).orElse(null);
            String beforeStatus = before == null ? null : (before.getStatus() == null ? null : before.getStatus().toUpperCase());

            Seminar seminar = seminarService.updateSeminar(id, updatedSeminar);
            if (seminar == null) {
                return ResponseEntity.notFound().build();
            }

            String afterStatus = seminar.getStatus() == null ? null : seminar.getStatus().toUpperCase();

            boolean changed = false;
            if (beforeStatus == null && afterStatus != null) changed = true;
            if (beforeStatus != null && afterStatus != null && !beforeStatus.equals(afterStatus)) changed = true;

            if (changed && afterStatus != null) {
                // only send for important statuses
                if (afterStatus.equals("APPROVED") || afterStatus.equals("REJECTED")
                        || afterStatus.equals("CANCELLED") || afterStatus.equals("CANCEL_REQUESTED")) {

                    String reason = updatedSeminar.getRemarks();
                    if ((reason == null || reason.isBlank()) && updatedSeminar.getCancellationReason() != null) {
                        reason = updatedSeminar.getCancellationReason();
                    }

                    // 1) Notify the booking owner (async)
                    try {
                        CompletableFuture<Boolean> f = emailService.sendStatusNotification(seminar.getEmail(), seminar, afterStatus, reason);
                        attachLogging(f, "sendStatusNotification(" + afterStatus + ")", seminar.getEmail());
                    } catch (Exception ex) {
                        log.error("[SeminarController] Failed to initiate status notification to requester: {}", ex.getMessage(), ex);
                    }

                    // 2) Notify ALL hall operators with specialized messages
                    try {
                        if (seminar.getHallName() != null) {
                            List<HallOperator> heads = hallOperatorService.findByHallName(seminar.getHallName());
                            for (HallOperator head : heads) {
                                try {
                                    CompletableFuture<Boolean> f = null;
                                    switch (afterStatus) {
                                        case "APPROVED":
                                            f = emailService.sendHallHeadBookingApprovedEmail(head, seminar, reason);
                                            attachLogging(f, "sendHallHeadBookingApprovedEmail", head.getHeadEmail());
                                            break;
                                        case "REJECTED":
                                            f = emailService.sendHallHeadBookingRejectedEmail(head, seminar, reason);
                                            attachLogging(f, "sendHallHeadBookingRejectedEmail", head.getHeadEmail());
                                            break;
                                        case "CANCEL_REQUESTED":
                                            f = emailService.sendHallHeadBookingCreatedEmail(head, seminar);
                                            attachLogging(f, "sendHallHeadBookingCreatedEmail (cancel-request)", head.getHeadEmail());
                                            break;
                                        case "CANCELLED":
                                            f = emailService.sendHallHeadBookingCancelledEmail(head, seminar, reason);
                                            attachLogging(f, "sendHallHeadBookingCancelledEmail", head.getHeadEmail());
                                            break;
                                        default:
                                            break;
                                    }
                                } catch (Exception ex) {
                                    log.error("[SeminarController] Failed to initiate hall-head status email for head={} : {}",
                                            head == null ? "null" : head.getHeadEmail(), ex.getMessage(), ex);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.error("[SeminarController] Error notifying hall operators on status change: {}", ex.getMessage(), ex);
                    }
                }
            }

            return ResponseEntity.ok(seminar);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("[SeminarController] updateSeminar unexpected error: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSeminar(@PathVariable String id) {
        seminarService.getById(id).ifPresent(seminar -> {
            try {
                CompletableFuture<Boolean> f = emailService.sendSeminarRemovedEmail(seminar);
                attachLogging(f, "sendSeminarRemovedEmail", seminar.getEmail());
            } catch (Exception ex) {
                log.error("[SeminarController] Failed to initiate seminar-removed email: {}", ex.getMessage(), ex);
            }

            // optionally notify hall operator about removal as well
            try {
                if (seminar.getHallName() != null) {
                    List<HallOperator> heads = hallOperatorService.findByHallName(seminar.getHallName());
                    for (HallOperator head : heads) {
                        try {
                            CompletableFuture<Boolean> f = emailService.sendHallHeadBookingCancelledEmail(head, seminar, "Booking removed from portal");
                            attachLogging(f, "sendHallHeadBookingCancelledEmail", head.getHeadEmail());
                        } catch (Exception ex) {
                            log.error("[SeminarController] Failed to initiate hall-head seminar-removed email for head={} : {}",
                                    head == null ? "null" : head.getHeadEmail(), ex.getMessage(), ex);
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("[SeminarController] Error notifying hall operator on deletion: {}", ex.getMessage(), ex);
            }
        });

        seminarService.deleteSeminar(id);
        return ResponseEntity.noContent().build();
    }

    // Dept history (server-side filtered)
    @GetMapping("/history")
    public ResponseEntity<List<Seminar>> getHistory(
            @RequestParam String department,
            @RequestParam String email) {
        return ResponseEntity.ok(seminarService.getByDepartmentAndEmail(department, email));
    }

    // Dedicated cancel-request endpoint (DEPARTMENT + ADMIN allowed in SecurityConfig)
    @PutMapping("/{id}/cancel-request")
    public ResponseEntity<?> requestCancel(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            String remarks = body.getOrDefault("remarks", null);
            String cancellationReason = body.getOrDefault("cancellationReason", null);
            Seminar updated = seminarService.requestCancel(id, cancellationReason, remarks);
            if (updated == null) {
                return ResponseEntity.notFound().build();
            }

            // send a notification to the booking owner that a cancel was requested (async)
            try {
                CompletableFuture<Boolean> f = emailService.sendStatusNotification(updated.getEmail(), updated, "CANCEL_REQUESTED", cancellationReason);
                attachLogging(f, "sendStatusNotification(CANCEL_REQUESTED)", updated.getEmail());
            } catch (Exception ex) {
                log.error("[SeminarController] Failed to initiate cancel-request email to requester: {}", ex.getMessage(), ex);
            }

            // notify hall operator too
            try {
                if (updated.getHallName() != null) {
                    List<HallOperator> heads = hallOperatorService.findByHallName(updated.getHallName());
                    for (HallOperator head : heads) {
                        try {
                            CompletableFuture<Boolean> f = emailService.sendHallHeadBookingCreatedEmail(head, updated);
                            attachLogging(f, "sendHallHeadBookingCreatedEmail (cancel-request)", head.getHeadEmail());
                        } catch (Exception ex) {
                            log.error("[SeminarController] Failed to initiate hall-head cancel-request email for head={} : {}",
                                    head == null ? "null" : head.getHeadEmail(), ex.getMessage(), ex);
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("[SeminarController] Error notifying hall operator on cancel-request: {}", ex.getMessage(), ex);
            }

            return ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("[SeminarController] requestCancel unexpected error: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    // Optional: search
    @GetMapping("/search")
    public ResponseEntity<List<Seminar>> search(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String hall,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String slot
    ) {
        List<Seminar> all = seminarService.getAllSeminars();
        List<Seminar> filtered = all.stream()
                .filter(s -> department == null || department.isBlank() ||
                        (s.getDepartment() != null && s.getDepartment().equalsIgnoreCase(department)))
                .filter(s -> hall == null || hall.isBlank() ||
                        (s.getHallName() != null && s.getHallName().equalsIgnoreCase(hall)))
                .filter(s -> date == null || date.isBlank() ||
                        (s.getDate() != null && s.getDate().equals(date)))
                .filter(s -> slot == null || slot.isBlank() ||
                        (s.getSlot() != null && s.getSlot().toLowerCase().contains(slot.toLowerCase())))
                .toList();
        return ResponseEntity.ok(filtered);
    }

    // -------------------- helper to attach logging to futures --------------------
    private void attachLogging(CompletableFuture<Boolean> future, String operation, String target) {
        if (future == null) return;
        future.whenComplete((ok, ex) -> {
            if (ex != null) {
                log.error("[Email] {} failed for target={} : {}", operation, target, ex.getMessage(), ex);
            } else {
                if (Boolean.TRUE.equals(ok)) {
                    log.info("[Email] {} succeeded for target={}", operation, target);
                } else {
                    log.warn("[Email] {} returned false for target={}", operation, target);
                }
            }
        });
    }

    // -------------------- small DTO used for calendar responses --------------------
    // kept as static inner class to avoid adding extra file; can be moved to payload/ later.
    public static class CalendarDaySummary {
        private String date;
        private boolean free;
        private int bookingCount;

        public CalendarDaySummary() {}

        public CalendarDaySummary(LocalDate date, boolean free, int bookingCount) {
            this.date = date.format(DATE_FMT);
            this.free = free;
            this.bookingCount = bookingCount;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public boolean isFree() {
            return free;
        }

        public void setFree(boolean free) {
            this.free = free;
        }

        public int getBookingCount() {
            return bookingCount;
        }

        public void setBookingCount(int bookingCount) {
            this.bookingCount = bookingCount;
        }
    }
}

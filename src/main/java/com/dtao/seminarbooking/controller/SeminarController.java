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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/seminars")
public class SeminarController {

    private static final Logger log = LoggerFactory.getLogger(SeminarController.class);

    private final SeminarService seminarService;
    private final EmailService emailService;
    private final HallOperatorService hallOperatorService;

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
}

package com.dtao.seminarbooking.controller;

import com.dtao.seminarbooking.model.HallOperator;
import com.dtao.seminarbooking.model.Seminar;
import com.dtao.seminarbooking.service.EmailService;
import com.dtao.seminarbooking.service.HallOperatorService;
import com.dtao.seminarbooking.service.SeminarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seminars")
public class SeminarController {

    @Autowired
    private SeminarService seminarService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private HallOperatorService hallOperatorService;

    @PostMapping
    public ResponseEntity<?> createSeminar(@RequestBody Seminar seminar) {
        try {
            Seminar saved = seminarService.addSeminar(seminar);

            // 1) Send booking confirmation to requester (existing behaviour)
            try {
                emailService.sendBookingCreatedEmail(saved);
            } catch (Exception ex) {
                System.err.println("[SeminarController] Booking confirmation email failed: " + ex.getMessage());
                ex.printStackTrace();
            }

            // 2) Notify ALL hall operators for this hall (created event)
            try {
                if (saved.getHallName() != null) {
                    List<HallOperator> heads = hallOperatorService.findByHallName(saved.getHallName());
                    for (HallOperator head : heads) {
                        try {
                            emailService.sendHallHeadBookingCreatedEmail(head, saved);
                        } catch (Exception ex) {
                            System.err.println("[SeminarController] Hall head booking-create email failed: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("[SeminarController] Error notifying hall operators on create: " + ex.getMessage());
                ex.printStackTrace();
            }

            // 3) If the seminar was created already with status APPROVED (admin-created auto-approve),
            //    immediately send the APPROVED notifications to requester + hall operators.
            try {
                String status = saved.getStatus() == null ? "" : saved.getStatus().toUpperCase();
                if ("APPROVED".equals(status)) {
                    String adminReason = "Approved & applied by admin"; // small message shown in email
                    // notify requester with APPROVED status (so they receive the approved email)
                    try {
                        emailService.sendStatusNotification(saved.getEmail(), saved, "APPROVED", adminReason);
                    } catch (Exception ex) {
                        System.err.println("[SeminarController] Immediate approved status email to requester failed: " + ex.getMessage());
                        ex.printStackTrace();
                    }

                    // notify all hall operators with approved email
                    if (saved.getHallName() != null) {
                        List<HallOperator> heads = hallOperatorService.findByHallName(saved.getHallName());
                        for (HallOperator head : heads) {
                            try {
                                emailService.sendHallHeadBookingApprovedEmail(head, saved, adminReason);
                            } catch (Exception ex) {
                                System.err.println("[SeminarController] Hall head immediate-approved email failed: " + ex.getMessage());
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                // don't fail create if these notification attempts fail
                System.err.println("[SeminarController] Error sending immediate approved notifications: " + ex.getMessage());
                ex.printStackTrace();
            }

            return ResponseEntity.ok(saved);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
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
                // only send for important statuses to avoid your daily limit issues
                if (afterStatus.equals("APPROVED") || afterStatus.equals("REJECTED")
                        || afterStatus.equals("CANCELLED") || afterStatus.equals("CANCEL_REQUESTED")) {

                    String reason = updatedSeminar.getRemarks();
                    if ((reason == null || reason.isBlank()) && updatedSeminar.getCancellationReason() != null) {
                        reason = updatedSeminar.getCancellationReason();
                    }

                    // 1) Notify the booking owner (existing generic notification)
                    try {
                        emailService.sendStatusNotification(seminar.getEmail(), seminar, afterStatus, reason);
                    } catch (Exception ex) {
                        System.err.println("[SeminarController] Status notification to requester failed: " + ex.getMessage());
                        ex.printStackTrace();
                    }

                    // 2) Notify ALL hall operators with specialized messages
                    try {
                        if (seminar.getHallName() != null) {
                            List<HallOperator> heads = hallOperatorService.findByHallName(seminar.getHallName());
                            for (HallOperator head : heads) {
                                try {
                                    switch (afterStatus) {
                                        case "APPROVED":
                                            emailService.sendHallHeadBookingApprovedEmail(head, seminar, reason);
                                            break;
                                        case "REJECTED":
                                            emailService.sendHallHeadBookingRejectedEmail(head, seminar, reason);
                                            break;
                                        case "CANCEL_REQUESTED":
                                            // use the 'created' style to notify head that cancellation was requested
                                            emailService.sendHallHeadBookingCreatedEmail(head, seminar);
                                            break;
                                        case "CANCELLED":
                                            emailService.sendHallHeadBookingCancelledEmail(head, seminar, reason);
                                            break;
                                        default:
                                            break;
                                    }
                                } catch (Exception ex) {
                                    System.err.println("[SeminarController] Hall head status email failed: " + ex.getMessage());
                                    ex.printStackTrace();
                                }
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("[SeminarController] Error notifying hall operators on status change: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
            }

            return ResponseEntity.ok(seminar);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSeminar(@PathVariable String id) {
        seminarService.getById(id).ifPresent(seminar -> {
            try {
                emailService.sendSeminarRemovedEmail(seminar);
            } catch (Exception ex) {
                System.err.println("[SeminarController] Seminar removed email failed: " + ex.getMessage());
                ex.printStackTrace();
            }

            // optionally notify hall operator about removal as well
            try {
                if (seminar.getHallName() != null) {
                    List<HallOperator> heads = hallOperatorService.findByHallName(seminar.getHallName());
                    for (HallOperator head : heads) {
                        try {
                            emailService.sendHallHeadBookingCancelledEmail(head, seminar, "Booking removed from portal");
                        } catch (Exception ex) {
                            System.err.println("[SeminarController] Hall head seminar-removed email failed: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("[SeminarController] Error notifying hall operator on deletion: " + ex.getMessage());
                ex.printStackTrace();
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

            // send a notification to the booking owner that a cancel was requested (optional)
            try {
                emailService.sendStatusNotification(updated.getEmail(), updated, "CANCEL_REQUESTED", cancellationReason);
            } catch (Exception ex) {
                System.err.println("[SeminarController] Cancel-request email failed: " + ex.getMessage());
                ex.printStackTrace();
            }

            // notify hall operator too
            try {
                if (updated.getHallName() != null) {
                    List<HallOperator> heads = hallOperatorService.findByHallName(updated.getHallName());
                    for (HallOperator head : heads) {
                        try {
                            emailService.sendHallHeadBookingCreatedEmail(head, updated);
                        } catch (Exception ex) {
                            System.err.println("[SeminarController] Hall head cancel-request email failed: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("[SeminarController] Error notifying hall operator on cancel-request: " + ex.getMessage());
                ex.printStackTrace();
            }

            return ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
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
}

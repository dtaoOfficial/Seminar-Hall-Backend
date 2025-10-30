package com.dtao.seminarbooking.controller;

import com.dtao.seminarbooking.model.Seminar;
import com.dtao.seminarbooking.service.SeminarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ✅ RequestController
 * Handles endpoints for viewing seminar booking requests
 * (Pending, Approved, Rejected, Cancelled, etc.)
 * Used by /api/requests in frontend.
 */
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    @Autowired
    private SeminarService seminarService;

    /**
     * ✅ Fetch all seminars (optionally filtered by status)
     * Example:
     * - /api/requests              → all seminars
     * - /api/requests?status=PENDING  → only pending
     * - /api/requests?status=APPROVED → only approved
     */
    @GetMapping
    public ResponseEntity<List<Seminar>> getRequests(@RequestParam(required = false) String status) {
        List<Seminar> all = seminarService.getAllSeminars();

        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase();
            List<Seminar> filtered = all.stream()
                    .filter(s -> s.getStatus() != null && s.getStatus().toUpperCase().equals(normalized))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(filtered);
        }

        // Default: return all seminars (all statuses)
        return ResponseEntity.ok(all);
    }

    /**
     * ✅ Get one seminar by ID (works same as /api/seminars/{id})
     */
    @GetMapping("/{id}")
    public ResponseEntity<Seminar> getRequestById(@PathVariable String id) {
        return seminarService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * ✅ (Optional) Quick status summary — for dashboards
     * Example: /api/requests/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> getSummary() {
        List<Seminar> all = seminarService.getAllSeminars();
        Map<String, Long> counts = all.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getStatus() == null ? "UNKNOWN" : s.getStatus().toUpperCase(),
                        Collectors.counting()
                ));
        return ResponseEntity.ok(counts);
    }
}

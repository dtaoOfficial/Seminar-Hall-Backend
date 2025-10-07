package com.dtao.seminarbooking.controller;

import com.dtao.seminarbooking.model.HallOperator;
import com.dtao.seminarbooking.service.HallOperatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hall-operators")
public class HallOperatorController {

    @Autowired
    private HallOperatorService hallOperatorService;

    @GetMapping
    public ResponseEntity<List<HallOperator>> listAll() {
        return ResponseEntity.ok(hallOperatorService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HallOperator> getById(@PathVariable String id) {
        return hallOperatorService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody HallOperator op) {
        try {
            HallOperator saved = hallOperatorService.addOperator(op);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody HallOperator op) {
        try {
            HallOperator updated = hallOperatorService.updateOperator(id, op);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        hallOperatorService.deleteOperator(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-hall")
    public ResponseEntity<?> findByHall(@RequestParam String hallName) {
        return ResponseEntity.ok(hallOperatorService.findByHallName(hallName));
    }
}

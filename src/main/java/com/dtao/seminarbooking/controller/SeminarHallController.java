package com.dtao.seminarbooking.controller;

import com.dtao.seminarbooking.model.SeminarHall;
import com.dtao.seminarbooking.service.SeminarHallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/halls")
public class SeminarHallController {

    @Autowired
    private SeminarHallService service;

    // Add Hall (now accepts capacity too)
    @PostMapping
    public ResponseEntity<SeminarHall> addHall(@RequestBody SeminarHall hall) {
        return ResponseEntity.ok(service.addHall(hall));
    }

    // Get All Halls
    @GetMapping
    public ResponseEntity<List<SeminarHall>> getAll() {
        return ResponseEntity.ok(service.getAllHalls());
    }

    // Get Single Hall
    @GetMapping("/{id}")
    public ResponseEntity<SeminarHall> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getHallById(id));
    }

    // Update Hall (now updates name and capacity)
    @PutMapping("/{id}")
    public ResponseEntity<SeminarHall> updateHall(@PathVariable String id, @RequestBody SeminarHall hall) {
        return ResponseEntity.ok(service.updateHall(id, hall));
    }

    // Delete Hall
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteHall(id);
        return ResponseEntity.noContent().build();
    }
}

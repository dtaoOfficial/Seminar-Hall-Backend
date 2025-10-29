package com.dtao.seminarbooking.controller;

import com.dtao.seminarbooking.model.Department;
import com.dtao.seminarbooking.model.Seminar;
import com.dtao.seminarbooking.payload.CalendarDaySummary;
import com.dtao.seminarbooking.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    @Autowired
    private DepartmentService service;

    // =========================
    // Existing CRUD endpoints
    // =========================
    @PostMapping
    public ResponseEntity<Department> add(@RequestBody Department d) {
        return ResponseEntity.ok(service.addDepartment(d));
    }

    @GetMapping
    public ResponseEntity<List<Department>> getAll() {
        return ResponseEntity.ok(service.getAllDepartments());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Department> update(@PathVariable String id, @RequestBody Department d) {
        return ResponseEntity.ok(service.updateDepartment(id, d));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }

    // =========================
    // ✅ Department Calendar APIs
    // =========================
    @GetMapping("/calendar")
    public ResponseEntity<List<CalendarDaySummary>> getDepartmentCalendar(
            @RequestParam String department,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(service.getDepartmentCalendar(department, year, month));
    }

    @GetMapping("/day")
    public ResponseEntity<List<Seminar>> getDepartmentSeminarsByDate(
            @RequestParam String department,
            @RequestParam String date
    ) {
        return ResponseEntity.ok(service.getDepartmentSeminarsByDate(department, date));
    }
}

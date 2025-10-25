package com.dtao.seminarbooking.controller;

import com.dtao.seminarbooking.model.Department;
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
}

package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.Department;
import com.dtao.seminarbooking.repo.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Service
public class DepartmentService {

    @Autowired
    private DepartmentRepository repository;

    public Department addDepartment(Department d) {
        if (d.getName() == null || d.getName().trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Department name cannot be empty");
        }
        if (repository.existsByNameIgnoreCase(d.getName())) {
            throw new ResponseStatusException(CONFLICT, "Department already exists");
        }
        return repository.save(d);
    }

    public List<Department> getAllDepartments() {
        return repository.findAll();
    }

    public Department updateDepartment(String id, Department updated) {
        if (updated.getName() == null || updated.getName().trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Department name cannot be empty");
        }
        // check conflict: if another doc exists with same name (case-insensitive)
        boolean nameExists = repository.existsByNameIgnoreCase(updated.getName());
        return repository.findById(id).map(d -> {
            // If name exists and it's not the same document, throw conflict
            if (nameExists && !d.getName().equalsIgnoreCase(updated.getName())) {
                throw new ResponseStatusException(CONFLICT, "Department already exists");
            }
            d.setName(updated.getName());
            return repository.save(d);
        }).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Department not found"));
    }

    public void deleteDepartment(String id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(NOT_FOUND, "Department not found");
        }
        repository.deleteById(id);
    }
}

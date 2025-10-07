package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.SeminarHall;
import com.dtao.seminarbooking.repo.SeminarHallRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

@Service
public class SeminarHallService {

    @Autowired
    private SeminarHallRepository repository;

    // Add Hall
    public SeminarHall addHall(SeminarHall hall) {
        if (hall == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Hall data is required");
        }
        if (hall.getName() == null || hall.getName().trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Hall name cannot be empty");
        }
        if (hall.getCapacity() == null || hall.getCapacity() <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Capacity must be a positive integer");
        }
        if (repository.existsByNameIgnoreCase(hall.getName())) {
            throw new ResponseStatusException(CONFLICT, "Hall already exists");
        }
        return repository.save(hall);
    }

    // Get all
    public List<SeminarHall> getAllHalls() {
        return repository.findAll();
    }

    // Get by id
    public SeminarHall getHallById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Hall not found"));
    }

    // Update name and capacity
    public SeminarHall updateHall(String id, SeminarHall updated) {
        if (updated == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Updated data is required");
        }

        String newName = updated.getName() == null ? "" : updated.getName().trim();
        Integer newCapacity = updated.getCapacity();

        if (newName.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Hall name cannot be empty");
        }
        if (newCapacity == null || newCapacity <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Capacity must be a positive integer");
        }

        SeminarHall existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Hall not found"));

        // Check duplicate name (case-insensitive)
        Optional<SeminarHall> maybe = repository.findFirstByNameIgnoreCase(newName);
        if (maybe.isPresent() && !maybe.get().getId().equals(existing.getId())) {
            throw new ResponseStatusException(CONFLICT, "Another hall with the same name exists");
        }

        existing.setName(newName);
        existing.setCapacity(newCapacity);

        return repository.save(existing);
    }

    // Delete
    public void deleteHall(String id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(NOT_FOUND, "Hall not found");
        }
        repository.deleteById(id);
    }
}

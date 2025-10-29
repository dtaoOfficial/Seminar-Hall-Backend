package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.Department;
import com.dtao.seminarbooking.model.Seminar;
import com.dtao.seminarbooking.payload.CalendarDaySummary;
import com.dtao.seminarbooking.repo.DepartmentRepository;
import com.dtao.seminarbooking.repo.SeminarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@Service
public class DepartmentService {

    @Autowired
    private DepartmentRepository repository;

    @Autowired
    private SeminarRepository seminarRepository;

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

    // ============================
    // Department calendar (month view)
    // ============================
    /**
     * Returns a list of CalendarDaySummary objects for the given department and month.
     * month should be 1..12
     */
    public List<CalendarDaySummary> getDepartmentCalendar(String departmentName, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        String start = ym.atDay(1).toString();
        String end = ym.atEndOfMonth().toString();

        List<Seminar> seminars = seminarRepository.findByDepartmentAndDateBetween(departmentName, start, end);

        Map<String, Long> dayCounts = seminars.stream()
                .collect(Collectors.groupingBy(Seminar::getDate, Collectors.counting()));

        List<CalendarDaySummary> summary = new ArrayList<>();
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            String date = ym.atDay(day).toString();
            boolean free = !dayCounts.containsKey(date);
            long count = dayCounts.getOrDefault(date, 0L);
            summary.add(new CalendarDaySummary(date, free, (int) count));
        }
        return summary;
    }

    // ============================
    // Department calendar (day details)
    // ============================
    /**
     * Returns list of seminars for a department on a specific date (date format: yyyy-MM-dd).
     */
    public List<Seminar> getDepartmentSeminarsByDate(String departmentName, String date) {
        return seminarRepository.findByDepartmentAndDateBetween(departmentName, date, date);
    }
}

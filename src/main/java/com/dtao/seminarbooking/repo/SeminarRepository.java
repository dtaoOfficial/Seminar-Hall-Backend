// File: src/main/java/com/dtao/seminarbooking/repo/SeminarRepository.java
package com.dtao.seminarbooking.repo;

import com.dtao.seminarbooking.model.Seminar;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeminarRepository extends MongoRepository<Seminar, String> {

    // Existing basic queries
    List<Seminar> findByHallNameAndDateAndSlot(String hallName, String date, String slot);
    List<Seminar> findByDate(String date);
    List<Seminar> findByDateAndHallName(String date, String hallName);
    List<Seminar> findByDepartmentAndEmail(String department, String email);

    // --- For calendar feature support ---

    // ✅ Find all day-range bookings for a hall that overlap a given date
    // Used for both single day and month-wide calendar lookups
    List<Seminar> findByHallNameAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            String hallName,
            String startDate,
            String endDate
    );

    // ✅ Find all time-based bookings (seminar.date) for a hall between two dates (inclusive)
    List<Seminar> findByHallNameAndDateBetween(
            String hallName,
            String startDate,
            String endDate
    );

    // ✅ (NEW) For global calendar lookup — when hallName not specified
    List<Seminar> findByDateBetween(String startDate, String endDate);

    // ✅ (NEW) For global day-range lookup — when hallName not specified
    List<Seminar> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
            String startDate,
            String endDate
    );

    // ✅ Department-based calendar lookups
    List<Seminar> findByDepartmentAndDateBetween(
            String department,
            String startDate,
            String endDate
    );

    List<Seminar> findByDepartmentAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            String department,
            String startDate,
            String endDate
    );
    List<Seminar> findByStatusIgnoreCase(String status);

}

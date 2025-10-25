package com.dtao.seminarbooking.repo;

import com.dtao.seminarbooking.model.Seminar;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeminarRepository extends MongoRepository<Seminar, String> {
    List<Seminar> findByHallNameAndDateAndSlot(String hallName, String date, String slot);
    List<Seminar> findByDate(String date);
    List<Seminar> findByDateAndHallName(String date, String hallName);
    List<Seminar> findByDepartmentAndEmail(String department, String email);

    // NEW: find day bookings that include a given date (startDate <= date <= endDate)
    List<Seminar> findByHallNameAndStartDateLessThanEqualAndEndDateGreaterThanEqual(String hallName, String startDate, String endDate);

    // NEW: find any time bookings for a date range (seminar.date between start and end) for a hall
    List<Seminar> findByHallNameAndDateBetween(String hallName, String startDate, String endDate);

    // NEW: find any seminars for a hall where date equals OR day-range overlaps (helper queries can be combined in service)
}


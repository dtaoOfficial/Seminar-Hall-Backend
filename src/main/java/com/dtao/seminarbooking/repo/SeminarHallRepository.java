package com.dtao.seminarbooking.repo;

import com.dtao.seminarbooking.model.SeminarHall;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeminarHallRepository extends MongoRepository<SeminarHall, String> {
    boolean existsByNameIgnoreCase(String name);
    Optional<SeminarHall> findFirstByNameIgnoreCase(String name);
}

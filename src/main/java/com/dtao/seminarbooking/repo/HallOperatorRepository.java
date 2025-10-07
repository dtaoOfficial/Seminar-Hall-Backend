package com.dtao.seminarbooking.repo;

import com.dtao.seminarbooking.model.HallOperator;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface HallOperatorRepository extends MongoRepository<HallOperator, String> {
    List<HallOperator> findByHallNameIgnoreCase(String hallName);
    List<HallOperator> findByHallId(String hallId);
    Optional<HallOperator> findFirstByHallNameIgnoreCase(String hallName);
}

package com.dtao.seminarbooking.repo;

import com.dtao.seminarbooking.model.HallOperator;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface HallOperatorRepository extends MongoRepository<HallOperator, String> {

    // Find operators managing a specific hall by name
    List<HallOperator> findByHallNamesIgnoreCase(String hallName);

    // Find operators managing a specific hall by hall ID
    List<HallOperator> findByHallIdsContains(String hallId);

    // For checking if email already exists
    Optional<HallOperator> findByHeadEmailIgnoreCase(String headEmail);

    // Keep your previous finder for compatibility
    Optional<HallOperator> findFirstByHallNamesIgnoreCase(String hallName);
}

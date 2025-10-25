package com.dtao.seminarbooking.repo;

import com.dtao.seminarbooking.model.Department;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartmentRepository extends MongoRepository<Department, String> {
    boolean existsByNameIgnoreCase(String name);
}

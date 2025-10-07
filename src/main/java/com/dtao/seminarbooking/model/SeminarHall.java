package com.dtao.seminarbooking.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "seminar_halls")
public class SeminarHall {

    @Id
    private String id;

    private String name;

    // NEW: capacity
    private Integer capacity;

    public SeminarHall() {}

    public SeminarHall(String name, Integer capacity) {
        this.name = name;
        this.capacity = capacity;
    }

    // --- Getters & Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
}

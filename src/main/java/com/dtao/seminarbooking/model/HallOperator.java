package com.dtao.seminarbooking.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "hall_operators")
public class HallOperator {
    @Id
    private String id;

    private String hallId;
    private String hallName;
    private String headName;
    private String headEmail;
    private String phone;

    public HallOperator() {}

    public HallOperator(String hallId, String hallName, String headName, String headEmail, String phone) {
        this.hallId = hallId;
        this.hallName = hallName;
        this.headName = headName;
        this.headEmail = headEmail;
        this.phone = phone;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHallId() { return hallId; }
    public void setHallId(String hallId) { this.hallId = hallId; }

    public String getHallName() { return hallName; }
    public void setHallName(String hallName) { this.hallName = hallName; }

    public String getHeadName() { return headName; }
    public void setHeadName(String headName) { this.headName = headName; }

    public String getHeadEmail() { return headEmail; }
    public void setHeadEmail(String headEmail) { this.headEmail = headEmail; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}

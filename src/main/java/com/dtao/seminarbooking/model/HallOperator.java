package com.dtao.seminarbooking.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "hall_operators")
public class HallOperator {
    @Id
    private String id;

    // Each operator can manage multiple halls
    private List<String> hallIds = new ArrayList<>();
    private List<String> hallNames = new ArrayList<>();

    private String headName;

    @Indexed(unique = true) // ensures one operator per unique email
    private String headEmail;

    private String phone;

    public HallOperator() {}

    public HallOperator(List<String> hallIds, List<String> hallNames, String headName, String headEmail, String phone) {
        this.hallIds = hallIds;
        this.hallNames = hallNames;
        this.headName = headName;
        this.headEmail = headEmail;
        this.phone = phone;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<String> getHallIds() { return hallIds; }
    public void setHallIds(List<String> hallIds) { this.hallIds = hallIds; }

    public List<String> getHallNames() { return hallNames; }
    public void setHallNames(List<String> hallNames) { this.hallNames = hallNames; }

    public String getHeadName() { return headName; }
    public void setHeadName(String headName) { this.headName = headName; }

    public String getHeadEmail() { return headEmail; }
    public void setHeadEmail(String headEmail) { this.headEmail = headEmail; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}

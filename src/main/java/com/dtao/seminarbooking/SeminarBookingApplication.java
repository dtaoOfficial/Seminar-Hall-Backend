package com.dtao.seminarbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;  // <-- import this

@SpringBootApplication
@EnableAsync  // <-- add this annotation
public class SeminarBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeminarBookingApplication.class, args);
    }

}

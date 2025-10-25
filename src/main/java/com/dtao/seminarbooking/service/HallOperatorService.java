// src/main/java/com/dtao/seminarbooking/service/HallOperatorService.java
package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.HallOperator;
import com.dtao.seminarbooking.model.SeminarHall;
import com.dtao.seminarbooking.repo.HallOperatorRepository;
import com.dtao.seminarbooking.repo.SeminarHallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.*;

@Service
public class HallOperatorService {

    private static final Logger logger = LoggerFactory.getLogger(HallOperatorService.class);

    @Autowired
    private HallOperatorRepository hallOperatorRepository;

    @Autowired
    private SeminarHallRepository hallRepository;

    // Inject email service to send welcome email AFTER save
    @Autowired
    private EmailService emailService;

    // phone must start with 6-9 and be 10 digits
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[6-9][0-9]{9}$");

    // allowed domains: newhorizonindia.edu and gmail.com
    private static final String[] ALLOWED_DOMAINS = {"@newhorizonindia.edu", "@gmail.com"};

    private void validateEmailDomain(String email) {
        if (email == null)
            throw new ResponseStatusException(BAD_REQUEST, "Head email required");
        String lower = email.trim().toLowerCase();
        for (String d : ALLOWED_DOMAINS) {
            if (lower.endsWith(d)) return;
        }
        throw new ResponseStatusException(BAD_REQUEST, "Head email must be @newhorizonindia.edu or @gmail.com");
    }

    private void validatePhoneOptional(String phone) {
        if (phone == null || phone.isBlank()) return;
        if (!PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "Phone must be 10 digits starting with 6/7/8/9");
        }
    }

    /**
     * Add operator.
     * Checks for existing email and adds support for multiple hall IDs/names.
     * Sends welcome email AFTER successful save using the saved instance (so hallNames are present).
     */
    public HallOperator addOperator(HallOperator op) {
        if (op == null)
            throw new ResponseStatusException(BAD_REQUEST, "Operator body required");

        // normalize email
        if (op.getHeadEmail() != null)
            op.setHeadEmail(op.getHeadEmail().trim().toLowerCase());

        validateEmailDomain(op.getHeadEmail());
        validatePhoneOptional(op.getPhone());

        // --- duplicate email check ---
        Optional<HallOperator> existing = hallOperatorRepository.findByHeadEmailIgnoreCase(op.getHeadEmail());
        if (existing.isPresent()) {
            throw new ResponseStatusException(BAD_REQUEST, "Email already exists");
        }

        // --- resolve hall list ---
        List<String> hallIds = new ArrayList<>();
        List<String> hallNames = new ArrayList<>();

        if (op.getHallIds() != null && !op.getHallIds().isEmpty()) {
            for (String hallId : op.getHallIds()) {
                SeminarHall hall = hallRepository.findById(hallId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Hall not found"));
                hallIds.add(hall.getId());
                hallNames.add(hall.getName());
            }
        } else if (op.getHallNames() != null && !op.getHallNames().isEmpty()) {
            for (String hallName : op.getHallNames()) {
                Optional<SeminarHall> maybe = hallRepository.findFirstByNameIgnoreCase(hallName.trim());
                if (maybe.isEmpty()) {
                    throw new ResponseStatusException(NOT_FOUND, "Hall not found by name: " + hallName);
                }
                SeminarHall hall = maybe.get();
                hallIds.add(hall.getId());
                hallNames.add(hall.getName());
            }
        } else {
            throw new ResponseStatusException(BAD_REQUEST, "At least one hall required");
        }

        op.setHallIds(hallIds);
        op.setHallNames(hallNames);

        // Persist operator
        HallOperator saved = hallOperatorRepository.save(op);

        // Send welcome email using the saved instance (ensures hallNames are present).
        // Do not let email failure break the creation: log and continue.
        try {
            if (emailService != null) {
                emailService.sendWelcomeEmailForOperator(saved);
            } else {
                logger.warn("EmailService not available - cannot send welcome email for operator {}", saved.getHeadEmail());
            }
        } catch (Exception ex) {
            logger.error("Failed to send welcome email to operator {}: {}", saved.getHeadEmail(), ex.getMessage(), ex);
        }

        return saved;
    }

    public HallOperator updateOperator(String id, HallOperator op) {
        if (op == null)
            throw new ResponseStatusException(BAD_REQUEST, "Operator body required");

        if (op.getHeadEmail() != null) {
            op.setHeadEmail(op.getHeadEmail().trim().toLowerCase());
            validateEmailDomain(op.getHeadEmail());
        }
        if (op.getPhone() != null)
            validatePhoneOptional(op.getPhone());

        return hallOperatorRepository.findById(id).map(existing -> {
            if (op.getHeadName() != null) existing.setHeadName(op.getHeadName());
            if (op.getHeadEmail() != null) existing.setHeadEmail(op.getHeadEmail());
            if (op.getPhone() != null) existing.setPhone(op.getPhone());
            // update hall list if provided
            if (op.getHallIds() != null && !op.getHallIds().isEmpty())
                existing.setHallIds(op.getHallIds());
            if (op.getHallNames() != null && !op.getHallNames().isEmpty())
                existing.setHallNames(op.getHallNames());
            return hallOperatorRepository.save(existing);
        }).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Operator not found"));
    }

    public List<HallOperator> getAll() {
        return hallOperatorRepository.findAll();
    }

    public List<HallOperator> getByHallId(String hallId) {
        return hallOperatorRepository.findByHallIdsContains(hallId);
    }

    public Optional<HallOperator> getById(String id) {
        return hallOperatorRepository.findById(id);
    }

    public void deleteOperator(String id) {
        if (!hallOperatorRepository.existsById(id)) {
            throw new ResponseStatusException(NOT_FOUND, "Operator not found");
        }
        hallOperatorRepository.deleteById(id);
    }

    public List<HallOperator> findByHallName(String hallName) {
        return hallOperatorRepository.findByHallNamesIgnoreCase(hallName);
    }

    public Optional<HallOperator> findFirstByHallName(String hallName) {
        return hallOperatorRepository.findFirstByHallNamesIgnoreCase(hallName);
    }

    public boolean emailExists(String email) {
        return hallOperatorRepository.findByHeadEmailIgnoreCase(email.trim().toLowerCase()).isPresent();
    }
}

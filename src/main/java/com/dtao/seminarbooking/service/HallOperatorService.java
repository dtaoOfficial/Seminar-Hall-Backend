package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.HallOperator;
import com.dtao.seminarbooking.model.SeminarHall;
import com.dtao.seminarbooking.repo.HallOperatorRepository;
import com.dtao.seminarbooking.repo.SeminarHallRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.*;

@Service
public class HallOperatorService {

    @Autowired
    private HallOperatorRepository hallOperatorRepository;

    @Autowired
    private SeminarHallRepository hallRepository;

    // phone must start with 6-9 and be 10 digits
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[6-9][0-9]{9}$");

    // allowed domains: newhorizonindia.edu and gmail.com
    private static final String[] ALLOWED_DOMAINS = { "@newhorizonindia.edu", "@gmail.com" };

    private void validateEmailDomain(String email) {
        if (email == null) throw new ResponseStatusException(BAD_REQUEST, "Head email required");
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
     * Add operator. Accepts either hallId OR hallName (if hallId not provided we try to resolve by hallName).
     */
    public HallOperator addOperator(HallOperator op) {
        if (op == null) throw new ResponseStatusException(BAD_REQUEST, "Operator body required");

        // normalize email
        if (op.getHeadEmail() != null) op.setHeadEmail(op.getHeadEmail().trim().toLowerCase());

        validateEmailDomain(op.getHeadEmail());
        validatePhoneOptional(op.getPhone());

        // resolve hallId: if not provided, try by hallName
        if ((op.getHallId() == null || op.getHallId().isBlank()) && (op.getHallName() != null && !op.getHallName().isBlank())) {
            Optional<SeminarHall> maybe = hallRepository.findFirstByNameIgnoreCase(op.getHallName().trim());
            if (maybe.isEmpty()) {
                throw new ResponseStatusException(NOT_FOUND, "Hall not found by name: " + op.getHallName());
            }
            SeminarHall hall = maybe.get();
            op.setHallId(hall.getId());
            op.setHallName(hall.getName());
        } else if (op.getHallId() != null && !op.getHallId().isBlank()) {
            SeminarHall hall = hallRepository.findById(op.getHallId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Hall not found"));
            op.setHallName(hall.getName());
        } else {
            throw new ResponseStatusException(BAD_REQUEST, "hallId or hallName required");
        }

        return hallOperatorRepository.save(op);
    }

    public HallOperator updateOperator(String id, HallOperator op) {
        if (op == null) throw new ResponseStatusException(BAD_REQUEST, "Operator body required");

        // validate email/phone if present
        if (op.getHeadEmail() != null) {
            op.setHeadEmail(op.getHeadEmail().trim().toLowerCase());
            validateEmailDomain(op.getHeadEmail());
        }
        if (op.getPhone() != null) validatePhoneOptional(op.getPhone());

        return hallOperatorRepository.findById(id).map(existing -> {
            if (op.getHeadName() != null) existing.setHeadName(op.getHeadName());
            if (op.getHeadEmail() != null) existing.setHeadEmail(op.getHeadEmail());
            if (op.getPhone() != null) existing.setPhone(op.getPhone());
            // we do NOT change hallId via update here
            return hallOperatorRepository.save(existing);
        }).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Operator not found"));
    }

    public List<HallOperator> getAll() {
        return hallOperatorRepository.findAll();
    }

    public List<HallOperator> getByHallId(String hallId) {
        return hallOperatorRepository.findByHallId(hallId);
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
        return hallOperatorRepository.findByHallNameIgnoreCase(hallName);
    }

    public Optional<HallOperator> findFirstByHallName(String hallName) {
        return hallOperatorRepository.findFirstByHallNameIgnoreCase(hallName);
    }
}

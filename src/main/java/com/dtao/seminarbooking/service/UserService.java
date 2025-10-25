package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.User;
import com.dtao.seminarbooking.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@newhorizonindia\\.edu$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[6-9][0-9]{9}$");

    public User addUser(User user) {
        if (user.getName() == null || user.getName().trim().isEmpty()) {
            throw new RuntimeException("Name is required!");
        }

        if (!EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
            throw new RuntimeException("Invalid email! Must end with @newhorizonindia.edu");
        }

        if (!PHONE_PATTERN.matcher(user.getPhone()).matches()) {
            throw new RuntimeException("Invalid phone number! Must be 10 digits starting with 6/7/8/9");
        }

        boolean emailExists = userRepository.findByEmail(user.getEmail()).isPresent();
        boolean phoneExists = userRepository.findByPhone(user.getPhone()).isPresent();

        if (emailExists && phoneExists) {
            throw new RuntimeException("Email and Phone already registered!");
        } else if (emailExists) {
            throw new RuntimeException("Email already registered!");
        } else if (phoneExists) {
            throw new RuntimeException("Phone already registered!");
        }

        // Hash the password before saving
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new RuntimeException("Password required");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        if (user.getCreatedAt() == null) user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    public Optional<User> authenticateUser(String email, String rawPassword) {
        if (email == null || rawPassword == null) return Optional.empty();
        Optional<User> maybe = userRepository.findByEmail(email);
        if (maybe.isEmpty()) return Optional.empty();
        User user = maybe.get();
        if (passwordEncoder.matches(rawPassword, user.getPassword())) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    // -------------------------
    // Update user (includes password)
    // -------------------------
    public Optional<User> updateUser(String id, User newData) {
        return userRepository.findById(id).map(existing -> {
            if (newData.getName() != null) existing.setName(newData.getName());
            if (newData.getEmail() != null) existing.setEmail(newData.getEmail());
            if (newData.getPhone() != null) existing.setPhone(newData.getPhone());
            if (newData.getRole() != null) existing.setRole(newData.getRole());
            if (newData.getDepartment() != null) existing.setDepartment(newData.getDepartment());
            if (newData.getPassword() != null) {
                // Hash password on update
                existing.setPassword(passwordEncoder.encode(newData.getPassword()));
            }
            return userRepository.save(existing);
        });
    }
}

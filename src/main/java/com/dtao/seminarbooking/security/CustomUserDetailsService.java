package com.dtao.seminarbooking.security;

import com.dtao.seminarbooking.model.User;
import com.dtao.seminarbooking.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Loads users from MongoDB using UserRepository.
 */
@Service
public class CustomUserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null) throw new UsernameNotFoundException("Email is null");

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> maybe = userRepository.findByEmail(normalized);

        User user = maybe.orElseThrow(() -> new UsernameNotFoundException("User not found: " + normalized));

        return new CustomUserDetails(user, user.getEmail());
    }
}

package com.dtao.seminarbooking.config;

import com.dtao.seminarbooking.security.CustomUserDetailsService;
import com.dtao.seminarbooking.security.JwtAuthenticationEntryPoint;
import com.dtao.seminarbooking.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String corsAllowedOrigins;

    // ✅ BCrypt for strong password hashing
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ✅ Authentication provider (DAO pattern)
    @Bean
    public DaoAuthenticationProvider authProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ✅ CORS config with controlled origins
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // cache pre-flight requests

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // ✅ MAIN SECURITY CHAIN
    @Bean
    public SecurityFilterChain securityFilterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // ✅ Public endpoints
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/users/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ✅ Seminar endpoints
                        .requestMatchers(HttpMethod.GET, "/api/seminars/**").hasAnyRole("ADMIN", "DEPARTMENT")
                        .requestMatchers(HttpMethod.POST, "/api/seminars/**").hasAnyRole("ADMIN", "DEPARTMENT")

                        // ✅ Only Admin can modify or delete seminars
                        .requestMatchers(HttpMethod.PUT, "/api/seminars/*/confirm-cancel").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/seminars/*/reject-cancel").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/seminars/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/seminars/**").hasRole("ADMIN")

                        // ✅ Cancel request allowed by both
                        .requestMatchers(HttpMethod.PUT, "/api/seminars/*/cancel-request").hasAnyRole("DEPARTMENT","ADMIN")

                        // ✅ Requests
                        .requestMatchers(HttpMethod.POST, "/api/requests/**").hasRole("DEPARTMENT")
                        .requestMatchers(HttpMethod.GET, "/api/requests/**").hasAnyRole("ADMIN", "DEPARTMENT")
                        .requestMatchers(HttpMethod.PUT, "/api/requests/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/requests/**").hasRole("ADMIN")

                        // ✅ Departments
                        .requestMatchers(HttpMethod.GET, "/api/departments/**").hasAnyRole("ADMIN","DEPARTMENT")
                        .requestMatchers(HttpMethod.POST, "/api/departments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/departments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/departments/**").hasRole("ADMIN")

                        // ✅ Halls
                        .requestMatchers(HttpMethod.GET, "/api/halls/**").hasAnyRole("ADMIN","DEPARTMENT")
                        .requestMatchers(HttpMethod.POST, "/api/halls/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/halls/*/media").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/halls/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/halls/**").hasRole("ADMIN")

                        // ✅ Any other request requires authentication
                        .anyRequest().authenticated()
                );

        // ✅ Add JWT filter before username/password filter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // ✅ Use custom auth provider
        http.authenticationProvider(authProvider());

        return http.build();
    }
}

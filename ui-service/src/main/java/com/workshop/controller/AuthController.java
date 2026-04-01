package com.workshop.controller;

import com.workshop.model.User;
import com.workshop.model.UserRepository;
import com.workshop.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @GetMapping("/users")
    public ResponseEntity<?> listCustomers() {
        List<Map<String, String>> customers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.CUSTOMER)
                .map(u -> Map.of("username", u.getUsername(), "fullName", u.getFullName()))
                .toList();
        return ResponseEntity.ok(customers);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No valid token provided"));
        }
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        String fullName = userRepository.findByUsername(auth.getName())
                .map(User::getFullName).orElse(auth.getName());
        return ResponseEntity.ok(Map.of(
                "token", jwtUtil.generate(auth.getName(), role),
                "username", auth.getName(),
                "fullName", fullName,
                "role", role));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        return userRepository.findByUsername(username)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .map(u -> ResponseEntity.ok(Map.of(
                        "token", jwtUtil.generate(u.getUsername(), u.getRole().name()),
                        "username", u.getUsername(),
                        "fullName", u.getFullName(),
                        "role", u.getRole().name())))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid username or password")));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String fullName = body.get("fullName");
        String role     = body.getOrDefault("role", "CUSTOMER").toUpperCase();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
        }
        if (fullName == null || fullName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Full name is required"));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already taken"));
        }
        User.Role userRole;
        try {
            userRole = User.Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Role must be ADMIN or CUSTOMER"));
        }

        User user = User.builder()
                .username(username)
                .fullName(fullName)
                .password(passwordEncoder.encode(password))
                .role(userRole)
                .build();
        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "token", jwtUtil.generate(user.getUsername(), user.getRole().name()),
                "username", user.getUsername(),
                "fullName", user.getFullName(),
                "role", user.getRole().name()));
    }
}

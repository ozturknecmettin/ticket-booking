package com.workshop.config;

import com.workshop.model.User;
import com.workshop.model.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seed("admin",     "admin123",    "Admin User",      User.Role.ADMIN);
        seed("customer",  "customer123", "Alice Johnson",   User.Role.CUSTOMER);
        seed("customer2", "customer123", "Bob Smith",       User.Role.CUSTOMER);
        log.info("[UserSeeder] Ready — admin/admin123 (ADMIN), customer/customer123, customer2/customer123 (CUSTOMER)");
    }

    private void seed(String username, String password, String fullName, User.Role role) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(User.builder()
                    .username(username)
                    .fullName(fullName)
                    .password(passwordEncoder.encode(password))
                    .role(role)
                    .build());
        }
    }
}

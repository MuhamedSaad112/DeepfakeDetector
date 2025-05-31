package com.deepfakedetector.configuration;

import com.deepfakedetector.model.entity.Authority;
import com.deepfakedetector.model.entity.User;
import com.deepfakedetector.repository.AuthorityRepository;
import com.deepfakedetector.repository.UserRepository;
import com.deepfakedetector.security.AuthoritiesConstants;
import com.deepfakedetector.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            initializeAuthorities();
            initializeDefaultUsers();
        } catch (Exception e) {
            log.error("Failed to initialize default users and authorities", e);
        }
    }

    private void initializeAuthorities() {
        log.info("Initializing authorities...");
        if (!authorityRepository.existsById(AuthoritiesConstants.ADMIN)) {
            Authority adminAuthority = new Authority();
            adminAuthority.setName(AuthoritiesConstants.ADMIN);
            authorityRepository.save(adminAuthority);
            log.info("Created authority: {}", AuthoritiesConstants.ADMIN);
        }
        if (!authorityRepository.existsById(AuthoritiesConstants.USER)) {
            Authority userAuthority = new Authority();
            userAuthority.setName(AuthoritiesConstants.USER);
            authorityRepository.save(userAuthority);
            log.info("Created authority: {}", AuthoritiesConstants.USER);
        }
    }

    private void initializeDefaultUsers() {
        long userCount = userRepository.count();
        log.info("Current user count: {}", userCount);
        if (userCount == 0) {
            log.info("No users found. Creating default users...");
            createDefaultUsers();
        } else {
            log.info("Users already exist. Skipping default user creation.");
        }
    }

    private void createDefaultUsers() {
        createAdminUser();
        createRegularUsers();
        log.info("Default users created successfully");
    }

    private void createAdminUser() {
        String adminUsername = "admin";
        String adminPassword = "P@ssw0rd12345Secure";
        if (userRepository.findOneByUserName(adminUsername).isEmpty()) {
            User adminUser = createUser(
                    adminUsername,
                    adminPassword,
                    "Admin",
                    "User",
                    "m.saad112003@gmail.com",
                    true,
                    AuthoritiesConstants.ADMIN
            );
            userRepository.save(adminUser);
            log.info("Created admin user: {}", adminUsername);
        }
    }

    private void createRegularUsers() {
        String[] usernames = {"Mohamed01", "Mohamed02", "Mohamed03", "Mohamed04"};
        String defaultPassword = "password123";
        for (String username : usernames) {
            if (userRepository.findOneByUserName(username).isEmpty()) {
                User user = createUser(
                        username,
                        defaultPassword,
                        "Mohamed",
                        "User",
                        username.toLowerCase() + "@example.com",
                        true,
                        AuthoritiesConstants.USER
                );
                userRepository.save(user);
                log.info("Created user: {}", username);
            }
        }
    }

    private User createUser(String username, String password, String firstName,
                            String lastName, String email, boolean activated, String authorityName) {
        User user = new User();
        user.setUserName(username.toLowerCase());
        user.setPassword(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email.toLowerCase());
        user.setActivated(activated);
        user.setLangKey("en");
        user.setCreatedBy("system");
        if (!activated) {
            user.setActivationKey(RandomUtil.generateActivationKey().substring(0, 20));
        }
        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(authorityName).ifPresent(authorities::add);
        if (!AuthoritiesConstants.USER.equals(authorityName)) {
            authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
        }
        user.setAuthorities(authorities);
        return user;
    }
}

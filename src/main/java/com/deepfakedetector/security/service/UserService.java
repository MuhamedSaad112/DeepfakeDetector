package com.deepfakedetector.security.service;

import com.deepfakedetector.configuration.Validation;
import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.mapper.UserMapper;
import com.deepfakedetector.model.dto.AdminUserDTO;
import com.deepfakedetector.model.dto.ResetKeyRequest;
import com.deepfakedetector.model.dto.UserResponseDto;
import com.deepfakedetector.model.entity.Authority;
import com.deepfakedetector.model.entity.User;
import com.deepfakedetector.repository.AuthorityRepository;
import com.deepfakedetector.repository.UserRepository;
import com.deepfakedetector.security.AuthoritiesConstants;
import com.deepfakedetector.security.SecurityUtils;
import com.deepfakedetector.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Log4j2
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorityRepository authorityRepository;
    private final CacheManager cacheManager;
    private final UserMapper userMapper;

    public Optional<User> activateRegistration(String key) {
        log.debug("Activating user for activation key {}", key);

        if (key == null || key.trim().isEmpty()) {
            throw new DeepfakeSilentException(DetectionErrorCode.ACTIVATION_KEY_NOT_FOUND);
        }

        return userRepository.findOneByActivationKey(key).map(user -> {
            user.setActivated(true);
            user.setActivationKey(null);
            this.clearUserCaches(user);
            log.debug("Activated User :{}", user);
            return user;
        });
    }

    public Optional<User> completePasswordReset(String newPassword, String key) {
        log.debug("Reset user password for reset key {}", key);

        if (key == null || key.trim().isEmpty()) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_RESET_KEY);
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_PASSWORD);
        }

        return userRepository.findOneByResetKey(key)
                .filter(user -> user.getResetDate() != null &&
                        user.getResetDate().isAfter(Instant.now().minus(1, ChronoUnit.DAYS)))
                .map(user -> {
                    user.setPassword(passwordEncoder.encode(newPassword));
                    user.setResetDate(null);
                    user.setResetKey(null);
                    this.clearUserCaches(user);
                    return user;
                });
    }

    public Optional<User> requestPasswordReset(String mail) {
        log.debug("Searching for email: {}", mail);

        if (mail == null || mail.trim().isEmpty()) {
            throw new DeepfakeSilentException(DetectionErrorCode.USER_NOT_FOUND);
        }

        return userRepository.findOneByEmailIgnoreCase(mail)
                .filter(User::isActivated)
                .map(user -> {
                    log.debug("User found and activated: {}", user.getEmail());
                    user.setResetKey(RandomUtil.generateResetKey().substring(0, 20));
                    user.setResetDate(Instant.now());
                    this.clearUserCaches(user);
                    return user;
                });
    }

    public User registerUser(AdminUserDTO userDTO, String password) {
        if (userDTO == null || userDTO.getUserName() == null || userDTO.getUserName().trim().isEmpty()) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_INPUT);
        }

        if (password == null || password.trim().isEmpty()) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_PASSWORD);
        }

        userRepository.findOneByUserName(userDTO.getUserName().toLowerCase()).ifPresent(existingUser -> {
            boolean removed = removeNonActivatedUser(existingUser);
            if (!removed) {
                throw new DeepfakeSilentException(DetectionErrorCode.USERNAME_ALREADY_USED);
            }
        });

        if (userDTO.getEmail() != null && !userDTO.getEmail().trim().isEmpty()) {
            userRepository.findOneByEmailIgnoreCase(userDTO.getEmail()).ifPresent(existingUser -> {
                boolean removed = removeNonActivatedUser(existingUser);
                if (!removed) {
                    throw new DeepfakeSilentException(DetectionErrorCode.EMAIL_ALREADY_USED);
                }
            });
        }

        User newUser = new User();
        String encryptedPassword = passwordEncoder.encode(password);

        newUser.setUserName(userDTO.getUserName().toLowerCase());
        newUser.setPassword(encryptedPassword);
        newUser.setFirstName(userDTO.getFirstName());
        newUser.setLastName(userDTO.getLastName());

        if (userDTO.getEmail() != null && !userDTO.getEmail().trim().isEmpty()) {
            newUser.setEmail(userDTO.getEmail().toLowerCase());
        }

        newUser.setLangKey(userDTO.getLangKey() != null ? userDTO.getLangKey() : Validation.DEFAULT_LANGUAGE);
        newUser.setImageUrl(userDTO.getImageUrl());
        newUser.setActivated(false);
        newUser.setActivationKey(RandomUtil.generateActivationKey().substring(0, 20));

        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
        newUser.setAuthorities(authorities);

        try {
            userRepository.save(newUser);
            this.clearUserCaches(newUser);
            log.debug("Created Information for User: {}", newUser);
            return newUser;
        } catch (Exception e) {
            log.error("Error saving user: {}", e.getMessage());
            throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
        }
    }

    private Boolean removeNonActivatedUser(User existingUser) {
        if (existingUser.isActivated()) {
            return false;
        }
        userRepository.delete(existingUser);
        userRepository.flush();
        this.clearUserCaches(existingUser);
        return true;
    }

    public User createUser(AdminUserDTO userDTO) {
        if (userDTO == null || userDTO.getUserName() == null || userDTO.getUserName().trim().isEmpty()) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_INPUT);
        }

        User user = userMapper.toEntity(userDTO);
        user.setUserName(userDTO.getUserName().toLowerCase());

        if (userDTO.getEmail() != null && !userDTO.getEmail().trim().isEmpty()) {
            user.setEmail(userDTO.getEmail().toLowerCase());
        }

        user.setLangKey(userDTO.getLangKey() != null ? userDTO.getLangKey() : Validation.DEFAULT_LANGUAGE);

        String encryptedPassword = passwordEncoder.encode(RandomUtil.generatePassword());
        user.setPassword(encryptedPassword);
        user.setResetKey(RandomUtil.generateResetKey().substring(0, 20));
        user.setResetDate(Instant.now());
        user.setActivated(true);

        Set<Authority> authorities = new HashSet<>();
        if (userDTO.getAuthorities() != null && !userDTO.getAuthorities().isEmpty()) {
            for (String authorityName : userDTO.getAuthorities()) {
                authorityRepository.findById(authorityName).ifPresent(authorities::add);
            }
        } else {
            authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
        }
        user.setAuthorities(authorities);

        try {
            userRepository.save(user);
            this.clearUserCaches(user);
            log.debug("Created Information for User: {}", user);
            return user;
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage());
            throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
        }
    }

    public Optional<AdminUserDTO> updateUser(AdminUserDTO userDTO) {
        if (userDTO == null || userDTO.getId() == null) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_INPUT);
        }

        return userRepository.findByIdWithAuthorities(userDTO.getId())
                .map(user -> {
                    userMapper.updateUserFromDto(userDTO, user);

                    if (userDTO.getEmail() != null && !userDTO.getEmail().trim().isEmpty()) {
                        user.setEmail(userDTO.getEmail().toLowerCase());
                    }

                    Set<Authority> authorities = user.getAuthorities();
                    if (authorities == null) {
                        authorities = new HashSet<>();
                        user.setAuthorities(authorities);
                    } else {
                        authorities.clear();
                    }

                    if (userDTO.getAuthorities() != null && !userDTO.getAuthorities().isEmpty()) {
                        for (String authorityName : userDTO.getAuthorities()) {
                            authorityRepository.findById(authorityName).ifPresent(authorities::add);
                        }
                    } else {
                        authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
                    }

                    try {
                        this.clearUserCaches(user);
                        log.debug("Changed Information for User: {}", user);
                        return user;
                    } catch (Exception e) {
                        log.error("Error updating user: {}", e.getMessage());
                        throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
                    }
                }).map(userMapper::toDto);
    }

    public void deleteUser(String userName) {
        if (userName == null || userName.trim().isEmpty()) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_INPUT);
        }

        userRepository.findOneByUserName(userName).ifPresentOrElse(user -> {
            userRepository.delete(user);
            this.clearUserCaches(user);
            log.debug("Delete User: {}", user);
        }, () -> {
            throw new DeepfakeSilentException(DetectionErrorCode.USER_NOT_FOUND);
        });
    }

    @Transactional
    public AdminUserDTO updateUserAndReturnDTO(String firstName, String lastName, String email, String langKey, String imageUrl) {
        return SecurityUtils.getCurrentUserUserName()
                .flatMap(userRepository::findOneWithAuthorityByUserName)
                .map(user -> {
                    user.setFirstName(firstName);
                    user.setLastName(lastName);
                    if (email != null) {
                        user.setEmail(email);
                    }
                    user.setLangKey(langKey);
                    user.setImageUrl(imageUrl);
                    this.clearUserCaches(user);
                    log.debug("Changed Information for User: {}", user);
                    return userMapper.toDto(user);
                })
                .orElseThrow(() -> new DeepfakeSilentException(DetectionErrorCode.CURRENT_USER_NOT_FOUND));
    }

    @Transactional
    public void updateUser(String firstName, String lastName, String email, String langKey, String imageUrl) {
        SecurityUtils.getCurrentUserUserName()
                .flatMap(userRepository::findOneWithAuthorityByUserName)
                .ifPresentOrElse(user -> {
                    user.setFirstName(firstName);
                    user.setLastName(lastName);
                    if (email != null) {
                        user.setEmail(email);
                    }
                    user.setLangKey(langKey);
                    user.setImageUrl(imageUrl);
                    this.clearUserCaches(user);
                    log.debug("Changed Information for User: {}", user);
                }, () -> {
                    throw new DeepfakeSilentException(DetectionErrorCode.CURRENT_USER_NOT_FOUND);
                });
    }

    @Transactional
    public void changePassword(String currentClearTextPassword, String newPassword) {
        if (currentClearTextPassword == null || currentClearTextPassword.trim().isEmpty() ||
                newPassword == null || newPassword.trim().isEmpty()) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_PASSWORD);
        }

        SecurityUtils.getCurrentUserUserName()
                .flatMap(userRepository::findOneWithAuthorityByUserName)
                .ifPresentOrElse(user -> {
                    String currentEncryptedPassword = user.getPassword();

                    if (!passwordEncoder.matches(currentClearTextPassword, currentEncryptedPassword)) {
                        throw new DeepfakeSilentException(DetectionErrorCode.INVALID_PASSWORD);
                    }

                    String encryptedPassword = passwordEncoder.encode(newPassword);
                    user.setPassword(encryptedPassword);
                    this.clearUserCaches(user);
                    log.debug("Changed password for User: {}", user);
                }, () -> {
                    throw new DeepfakeSilentException(DetectionErrorCode.CURRENT_USER_NOT_FOUND);
                });
    }

    @Transactional(readOnly = true)
    public Page<AdminUserDTO> getAllManagedUsers(Pageable pageable) {
        return userRepository.findAllWithAuthorities(pageable).map(userMapper::toDto);
    }

    public void assertValidResetKey(ResetKeyRequest request) throws DeepfakeException {
        if (request == null || request.getEmail() == null || request.getKey() == null) {
            throw new DeepfakeException(DetectionErrorCode.INVALID_RESET_KEY);
        }

        boolean isValid = userRepository.findOneByEmailIgnoreCase(request.getEmail())
                .filter(user -> request.getKey().equals(user.getResetKey()))
                .filter(user -> user.getResetDate() != null &&
                        user.getResetDate().isAfter(Instant.now().minus(1, ChronoUnit.DAYS)))
                .isPresent();

        if (!isValid) {
            throw new DeepfakeException(DetectionErrorCode.INVALID_RESET_KEY);
        }
    }


    @Transactional
    public void deleteCurrentUserAccount() {
        SecurityUtils.getCurrentUserUserName()
                .flatMap(userRepository::findOneWithAuthorityByUserName)
                .ifPresentOrElse(user -> {
                    log.debug("Deleting current user account: {}", user.getUserName());
                    userRepository.delete(user);
                    this.clearUserCaches(user);
                }, () -> {
                    throw new DeepfakeSilentException(DetectionErrorCode.CURRENT_USER_NOT_FOUND);
                });
    }


    @Transactional(readOnly = true)
    public Page<UserResponseDto> getAllPublicUsers(Pageable pageable) {
        return userRepository.findAllByIdNotNullAndActivatedIsTrue(pageable).map(UserResponseDto::new);
    }

    @Transactional(readOnly = true)
    public Optional<AdminUserDTO> getUserDTOWithAuthorities(String userName) {
        return userRepository.findOneWithAuthorityByUserName(userName)
                .map(userMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<User> getAllWithAuthoritiesByUserName(String userName) {
        return userRepository.findOneWithAuthorityByUserName(userName);
    }

    @Transactional(readOnly = true)
    public Optional<AdminUserDTO> getCurrentUserWithAuthorities() {
        return SecurityUtils.getCurrentUserUserName()
                .flatMap(userRepository::findOneWithAuthorityByUserName)
                .map(userMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserWithAuthorities() {
        return SecurityUtils.getCurrentUserUserName()
                .flatMap(userRepository::findOneWithAuthorityByUserName);
    }

    @Scheduled(cron = "0 0 1 * * ?")
    public void removeNotActivatedUsers() {
        userRepository.findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(
                Instant.now().minus(30, ChronoUnit.DAYS)).forEach(user -> {
            log.debug("Deleting not activated user {}", user.getUserName());
            userRepository.delete(user);
            this.clearUserCaches(user);
        });
    }

    @Transactional(readOnly = true)
    public List<String> getAuthorities() {
        return authorityRepository.findAll().stream().map(Authority::getName).collect(Collectors.toList());
    }

    public void clearUserCaches(User user) {
        Objects.requireNonNull(cacheManager.getCache(UserRepository.USERS_BY_USER_NAME_CACHE)).evict(user.getUserName());
        if (user.getEmail() != null) {
            Objects.requireNonNull(cacheManager.getCache(UserRepository.USERS_BY_EMAIL_CACHE)).evict(user.getEmail());
        }
    }
}
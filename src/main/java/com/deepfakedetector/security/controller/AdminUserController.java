package com.deepfakedetector.security.controller;

import com.deepfakedetector.configuration.Validation;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.exception.GenericApiResponse;
import com.deepfakedetector.model.dto.AdminUserDTO;
import com.deepfakedetector.model.entity.User;
import com.deepfakedetector.repository.UserRepository;
import com.deepfakedetector.security.AuthoritiesConstants;
import com.deepfakedetector.security.service.UserService;
import com.deepfakedetector.service.mail.MailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Admin User Management", description = "Endpoints for admin users to manage application users")
public class AdminUserController {

    @Value("${spring.application.name}")
    private String applicationName;

    private final UserRepository userRepository;
    private final UserService userService;
    private final MailService mailService;

    private static final List<String> ALLOWED_ORDERED_PROPERTIES = Collections.unmodifiableList(
            Arrays.asList("id", "userName", "firstName", "lastName", "email", "activated",
                    "langKey", "createdBy", "createdDate", "lastModifiedBy", "lastModifiedDate")
    );

    @Operation(summary = "Create a new user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or username/email already in use")
    })
    @PostMapping("/users")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<GenericApiResponse<Map<String, String>>> createUser(
            @Valid @RequestBody AdminUserDTO userDTO
    ) {
        log.debug("REST Request to save user: {}", userDTO);

        if (userDTO.getId() != null) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_INPUT);
        }
        if (userRepository.findOneByUserName(userDTO.getUserName().toLowerCase()).isPresent()) {
            throw new DeepfakeSilentException(DetectionErrorCode.USERNAME_ALREADY_USED);
        }
        if (userRepository.findOneByEmailIgnoreCase(userDTO.getEmail()).isPresent()) {
            throw new DeepfakeSilentException(DetectionErrorCode.EMAIL_ALREADY_USED);
        }

        User newUser = userService.createUser(userDTO);
        mailService.sendPasswordResetEmail(newUser);

        Map<String, String> data = new HashMap<>();
        data.put("username", newUser.getUserName());
        data.put("email", newUser.getEmail());
        data.put("id", String.valueOf(newUser.getId()));
        data.put("status", "created");

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(GenericApiResponse.created(data));
    }

    @Operation(summary = "Update an existing user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "400", description = "Email or username already in use"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/users")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public GenericApiResponse<AdminUserDTO> updateUser(
            @Valid @RequestBody AdminUserDTO userDTO
    ) {
        log.debug("REST request to update User : {}", userDTO);

        Optional<User> existingByEmail = userRepository.findOneByEmailIgnoreCase(userDTO.getEmail());
        if (existingByEmail.isPresent() && !existingByEmail.get().getId().equals(userDTO.getId())) {
            throw new DeepfakeSilentException(DetectionErrorCode.EMAIL_ALREADY_USED);
        }
        Optional<User> existingByUsername = userRepository.findOneByUserName(userDTO.getUserName().toLowerCase());
        if (existingByUsername.isPresent() && !existingByUsername.get().getId().equals(userDTO.getId())) {
            throw new DeepfakeSilentException(DetectionErrorCode.USERNAME_ALREADY_USED);
        }

        AdminUserDTO updatedUser = userService.updateUser(userDTO)
                .orElseThrow(() -> new DeepfakeSilentException(DetectionErrorCode.USER_NOT_FOUND));

        return GenericApiResponse.ok(updatedUser);
    }

    @Operation(summary = "Get all users with pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid sort property")
    })
    @GetMapping("/users")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public GenericApiResponse<Map<String, Object>> getAllUsers(Pageable pageable) {
        log.debug("REST request to get all User for an admin");

        if (!onlyContainsAllowedProperties(pageable)) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_INPUT);
        }

        Page<AdminUserDTO> page = userService.getAllManagedUsers(pageable);

        Map<String, Object> data = new HashMap<>();
        data.put("users", page.getContent());
        data.put("totalElements", page.getTotalElements());
        data.put("totalPages", page.getTotalPages());
        data.put("currentPage", page.getNumber());
        data.put("hasNext", page.hasNext());
        data.put("hasPrevious", page.hasPrevious());

        return GenericApiResponse.ok(data);
    }

    @Operation(summary = "Get a specific user by username")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/users/{userName}")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public GenericApiResponse<AdminUserDTO> getUser(
            @PathVariable @Pattern(regexp = Validation.USER_NAME_PATTERN) String userName
    ) {
        log.debug("REST request to get User : {}", userName);

        AdminUserDTO userDTO = userService.getUserDTOWithAuthorities(userName)
                .orElseThrow(() -> new DeepfakeSilentException(DetectionErrorCode.USER_NOT_FOUND));

        return GenericApiResponse.ok(userDTO);
    }

    @Operation(summary = "Delete a specific user by username")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deleted successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping("/users/{userName}")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public GenericApiResponse<Map<String, String>> deleteUser(
            @PathVariable @Pattern(regexp = Validation.USER_NAME_PATTERN) String userName
    ) {
        log.debug("REST request to delete User: {}", userName);

        userService.getAllWithAuthoritiesByUserName(userName)
                .orElseThrow(() -> new DeepfakeSilentException(DetectionErrorCode.USER_NOT_FOUND));
        userService.deleteUser(userName);

        Map<String, String> data = new HashMap<>();
        data.put("username", userName);
        data.put("status", "deleted");
        data.put("message", "User " + userName + " deleted successfully");

        return GenericApiResponse.ok(data);
    }

    private boolean onlyContainsAllowedProperties(Pageable pageable) {
        return pageable.getSort().stream()
                .map(Sort.Order::getProperty)
                .allMatch(ALLOWED_ORDERED_PROPERTIES::contains);
    }
}

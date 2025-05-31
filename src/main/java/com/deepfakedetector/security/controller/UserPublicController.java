package com.deepfakedetector.security.controller;

import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.exception.GenericApiResponse;
import com.deepfakedetector.model.dto.UserResponseDto;
import com.deepfakedetector.security.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Public Users", description = "Endpoints for retrieving public user information and authorities")
public class UserPublicController {

    private final UserService userService;

    private static final List<String> ALLOWED_ORDERED_PROPERTIES = Collections.unmodifiableList(
            Arrays.asList("id", "userName", "firstName", "lastName", "email", "activated", "langKey")
    );

    @Operation(summary = "Get all public users", description = "Retrieve all active users with pagination and sorting. Allowed sort properties: id, userName, firstName, lastName, email, activated, langKey.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid sort property specified")
    })
    @GetMapping("/users")
    public GenericApiResponse<Map<String, Object>> getAllPublicUsers(@NotNull Pageable pageable) {
        log.debug("REST request to get all public users");
        if (!onlyContainsAllowedProperties(pageable)) {
            log.warn("Invalid sort property provided: {}", pageable.getSort());
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_SORT_PROPERTY);
        }
        Page<UserResponseDto> page = userService.getAllPublicUsers(pageable);
        Map<String, Object> data = new HashMap<>();
        data.put("content", page.getContent());
        data.put("page", page.getNumber());
        data.put("size", page.getSize());
        data.put("totalElements", page.getTotalElements());
        data.put("totalPages", page.getTotalPages());
        return GenericApiResponse.ok(data);
    }

    @Operation(summary = "Get all authorities", description = "Retrieve a list of all available user authorities/roles.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authorities retrieved successfully")
    })
    @GetMapping("/authorities")
    public GenericApiResponse<List<String>> getAuthorities() {
        log.debug("REST request to get all authorities");
        List<String> authorities = userService.getAuthorities();
        return GenericApiResponse.ok(authorities);
    }

    private boolean onlyContainsAllowedProperties(Pageable pageable) {
        return pageable.getSort().stream()
                .map(Sort.Order::getProperty)
                .allMatch(ALLOWED_ORDERED_PROPERTIES::contains);
    }
}

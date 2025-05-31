package com.deepfakedetector.security.controller;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.exception.GenericApiResponse;
import com.deepfakedetector.model.dto.*;
import com.deepfakedetector.model.entity.User;
import com.deepfakedetector.model.request.EmailRequest;
import com.deepfakedetector.repository.UserRepository;
import com.deepfakedetector.security.SecurityUtils;
import com.deepfakedetector.security.service.UserService;
import com.deepfakedetector.service.mail.MailService;
import com.deepfakedetector.service.profile.ProfileImageUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Account", description = "Authentication and user account management endpoints")
public class AccountController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final MailService mailService;
    private final ProfileImageUpdateService profileImageUpdateService;

    @Operation(summary = "Register a new user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or password length"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/register")
    public ResponseEntity<GenericApiResponse<Map<String, String>>> registerAccount(
            @Valid @RequestBody RegisterUserRequestDto dto) {

        if (isPasswordLengthInvalid(dto.getPassword())) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_PASSWORD);
        }

        User user = userService.registerUser(dto, dto.getPassword());
        mailService.sendActivationEmail(user);

        Map<String, String> data = new HashMap<>();
        data.put("username", user.getUserName());
        data.put("email", user.getEmail());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(GenericApiResponse.created(data));
    }

    @Operation(summary = "Activate a registered user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account activated successfully"),
            @ApiResponse(responseCode = "400", description = "Activation key not found or invalid")
    })
    @GetMapping("/activate")
    public GenericApiResponse<Map<String, String>> activateAccount(
            @RequestParam("key") String key) {

        Optional<User> user = userService.activateRegistration(key);
        if (!user.isPresent()) {
            throw new DeepfakeSilentException(DetectionErrorCode.ACTIVATION_KEY_NOT_FOUND);
        }

        mailService.sendCreationEmail(user.get());
        Map<String, String> data = new HashMap<>();
        data.put("email", user.get().getEmail());
        data.put("activated", "true");

        return GenericApiResponse.ok(data);
    }

    @Operation(summary = "Check if current request is authenticated")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authenticated status retrieved")
    })
    @GetMapping("/authenticate")
    public GenericApiResponse<Map<String, Object>> isAuthenticated(HttpServletRequest request) {
        String username = request.getRemoteUser();
        Map<String, Object> data = new HashMap<>();
        data.put("authenticated", username != null);
        data.put("username", username);
        return GenericApiResponse.ok(data);
    }

    @Operation(summary = "Get current authenticated user's account details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/account")
    public GenericApiResponse<AdminUserDTO> getAccount() {
        AdminUserDTO dto = userService.getCurrentUserWithAuthorities()
                .orElseThrow(() -> new DeepfakeSilentException(DetectionErrorCode.USER_NOT_FOUND));
        return GenericApiResponse.ok(dto);
    }

    @Operation(summary = "Update current user's account details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account updated successfully"),
            @ApiResponse(responseCode = "400", description = "Email already used or invalid input"),
            @ApiResponse(responseCode = "404", description = "Current user not found")
    })
    @PutMapping("/account")
    public GenericApiResponse<AdminUserDTO> updateAccount(
            @Valid @RequestBody AdminUserDTO userDTO) {

        String current = SecurityUtils.getCurrentUserUserName()
                .orElseThrow(() -> new DeepfakeSilentException(DetectionErrorCode.CURRENT_USER_NOT_FOUND));

        userRepository.findOneByEmailIgnoreCase(userDTO.getEmail())
                .filter(u -> !u.getUserName().equalsIgnoreCase(current))
                .ifPresent(u -> {
                    throw new DeepfakeSilentException(DetectionErrorCode.EMAIL_ALREADY_USED);
                });

        AdminUserDTO updated = userService.updateUserAndReturnDTO(
                userDTO.getFirstName(),
                userDTO.getLastName(),
                userDTO.getEmail(),
                userDTO.getLangKey(),
                userDTO.getImageUrl()
        );

        return GenericApiResponse.ok(updated);
    }

    @Operation(summary = "Change current user's password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password length or current password mismatch"),
            @ApiResponse(responseCode = "404", description = "Current user not found")
    })
    @PostMapping("/account/change-password")
    public GenericApiResponse<Map<String, String>> changePassword(
            @Valid @RequestBody PasswordChangeRequestDto pwd) {

        if (isPasswordLengthInvalid(pwd.getNewPassword())) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_PASSWORD);
        }
        userService.changePassword(pwd.getCurrentPassword(), pwd.getNewPassword());

        Map<String, String> data = new HashMap<>();
        data.put("passwordChanged", "true");
        return GenericApiResponse.ok(data);
    }

    @Operation(summary = "Request password reset link to be sent to email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset email sent"),
            @ApiResponse(responseCode = "404", description = "User email not found")
    })
    @PostMapping("/account/reset-password/init")
    public GenericApiResponse<Map<String, String>> requestPasswordReset(
            @RequestBody EmailRequest email) {
        userService.requestPasswordReset(email.getEmail())
                .ifPresent(u -> mailService.sendPasswordResetEmail(u));

        Map<String, String> data = new HashMap<>();
        data.put("resetEmailSent", "true");
        return GenericApiResponse.ok(data);
    }

    @Operation(summary = "Validate password reset key")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reset key is valid"),
            @ApiResponse(responseCode = "400", description = "Invalid reset key")
    })
    @PostMapping("/account/reset-password/check")
    public GenericApiResponse<Map<String, String>> checkResetKey(
            @Valid @RequestBody ResetKeyRequest req) throws DeepfakeException {

        userService.assertValidResetKey(req);
        Map<String, String> data = new HashMap<>();
        data.put("resetKeyValid", "true");
        data.put("key", req.getKey());
        return GenericApiResponse.ok(data);
    }

    @Operation(summary = "Complete password reset using reset key and new password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password length or reset key not found")
    })
    @PostMapping("/account/reset-password/finish")
    public GenericApiResponse<Map<String, String>> finishPasswordReset(
            @Valid @RequestBody ResetKeyAndPasswordRequestDto keyAndPwd) {

        if (isPasswordLengthInvalid(keyAndPwd.getNewPassword())) {
            throw new DeepfakeSilentException(DetectionErrorCode.INVALID_PASSWORD);
        }

        User user = userService.completePasswordReset(keyAndPwd.getNewPassword(), keyAndPwd.getKey())
                .orElseThrow(() -> new DeepfakeSilentException(DetectionErrorCode.RESET_KEY_NOT_FOUND));

        Map<String, String> data = new HashMap<>();
        data.put("passwordReset", "true");
        data.put("email", user.getEmail());
        return GenericApiResponse.ok(data);
    }

    @Operation(summary = "Update profile image for current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile image updated successfully"),
            @ApiResponse(responseCode = "400", description = "Empty or missing file"),
            @ApiResponse(responseCode = "404", description = "Current user not found")
    })
    @PostMapping("/profile-image")
    public GenericApiResponse<Map<String, String>> updateProfileImage(
            @RequestParam("image") MultipartFile file) throws DeepfakeException {

        if (file.isEmpty() || file == null) {
            throw new DeepfakeSilentException(DetectionErrorCode.EMPTY_OR_MISSING_FILE);
        }
        String userName = SecurityUtils.getCurrentUserUserName()
                .orElseThrow(() -> new DeepfakeSilentException(DetectionErrorCode.CURRENT_USER_NOT_FOUND));

        String filename = profileImageUpdateService.updateUserProfileImage(userName, file);
        Map<String, String> data = new HashMap<>();
        data.put("filename", filename);
        data.put("uploaded", "true");
        return GenericApiResponse.ok(data);
    }

    @Operation(summary = "Retrieve profile image for current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile image retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Profile image not found or user not found")
    })
    @GetMapping("/profile-image")
    public ResponseEntity<byte[]> getProfileImage() throws IOException, DeepfakeException {
        String userName = SecurityUtils.getCurrentUserUserName()
                .orElseThrow(() -> new DeepfakeSilentException(DetectionErrorCode.CURRENT_USER_NOT_FOUND));

        byte[] image = profileImageUpdateService.loadImage(userName);
        if (image == null || image.length == 0) {
            throw new DeepfakeSilentException(DetectionErrorCode.PROFILE_IMAGE_NOT_FOUND);
        }
        return ResponseEntity.ok()
                .header("Content-Type", "image/jpeg")
                .header("Content-Length", String.valueOf(image.length))
                .body(image);
    }

    @Operation(summary = "Check if email or username already exists")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Existence check completed")
    })
    @GetMapping("/account/exists")
    public GenericApiResponse<Map<String, Boolean>> checkAccountExists(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String username) {

        Map<String, Boolean> data = new HashMap<>();
        if (!StringUtils.isEmpty(email)) {
            data.put("emailExists", userRepository.findOneByEmailIgnoreCase(email).isPresent());
        }
        if (!StringUtils.isEmpty(username)) {
            data.put("usernameExists", userRepository.findOneByUserName(username).isPresent());
        }
        return GenericApiResponse.ok(data);
    }

    @DeleteMapping("/account/delete")
    @Operation(summary = "Delete current user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User account deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Current user not found")
    })
    public GenericApiResponse<Void> deleteCurrentUserAccount() {
        userService.deleteCurrentUserAccount();
        return GenericApiResponse.ok("Account deleted successfully.", "تم حذف الحساب بنجاح.", null);
    }

    private static boolean isPasswordLengthInvalid(String pwd) {
        return StringUtils.isEmpty(pwd)
                || pwd.length() < RegisterUserRequestDto.PASSWORD_MIN_LENGTH
                || pwd.length() > RegisterUserRequestDto.PASSWORD_MAX_LENGTH;
    }
}

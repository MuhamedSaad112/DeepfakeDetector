package com.deepfakedetector.security.controller;

import com.deepfakedetector.exception.GenericApiResponse;
import com.deepfakedetector.model.dto.LoginRequestDto;
import com.deepfakedetector.security.jwt.JWTFilter;
import com.deepfakedetector.security.jwt.TokenProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication")
public class AuthController {

    private final TokenProvider tokenProvider;
    private final AuthenticationManagerBuilder managerBuilder;

    @Operation(summary = "Authenticate user and return JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/authenticate")
    public ResponseEntity<GenericApiResponse<JWTToken>> authorize(
            @Valid @RequestBody LoginRequestDto loginDto) {

        try {
            Authentication authentication = managerBuilder.getObject()
                    .authenticate(new UsernamePasswordAuthenticationToken(
                            loginDto.getUserName(),
                            loginDto.getPassword()
                    ));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = tokenProvider.createToken(authentication, loginDto.isRememberMe());

            HttpHeaders headers = new HttpHeaders();
            headers.add(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwt);

            GenericApiResponse<JWTToken> response = GenericApiResponse.ok(
                    "Login successful",
                    "تم تسجيل الدخول بنجاح",
                    new JWTToken(jwt)
            );

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(response);

        } catch (Exception e) {
            GenericApiResponse<JWTToken> errorResponse = GenericApiResponse.unauthorized(
                    "Invalid credentials",
                    "بيانات اعتماد غير صحيحة"
            );

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse);
        }
    }

    public static class JWTToken {
        @JsonProperty("id_token")
        private final String idToken;

        public JWTToken(String idToken) {
            this.idToken = idToken;
        }

        public String getIdToken() {
            return idToken;
        }

        @Override
        public String toString() {
            return "JWTToken{" +
                    "idToken='" + (idToken != null ? "[PROTECTED]" : "null") + '\'' +
                    '}';
        }
    }
}

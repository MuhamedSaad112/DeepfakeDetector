package com.deepfakedetector.exception;

import com.deepfakedetector.util.DetectionMessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String arabicMessage = DetectionMessageResolver.getArabicMessage(DetectionErrorCode.AUTHENTICATION_FAILED.getLabel());
        String englishMessage = DetectionMessageResolver.getEnglishMessage(DetectionErrorCode.AUTHENTICATION_FAILED.getLabel());

        DetectionResponse<Object> detectionResponse = DetectionResponse.<Object>builder()
                .status(HttpStatus.UNAUTHORIZED)
                .errorCode(DetectionErrorCode.AUTHENTICATION_FAILED.getLabel())
                .errorMessages(DetectionErrorCode.AUTHENTICATION_FAILED)
                .timeStamp(new Date())
                .messageAr(arabicMessage)
                .messageEn(englishMessage)
                .data(null)
                .count(0L)
                .build();

        String jsonResponse = objectMapper.writeValueAsString(detectionResponse);
        response.getWriter().write(jsonResponse);
    }
}

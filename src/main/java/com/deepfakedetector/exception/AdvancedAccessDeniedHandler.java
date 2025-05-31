package com.deepfakedetector.exception;

import com.deepfakedetector.util.DetectionMessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;

@Slf4j
@Component
public class AdvancedAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {

        log.warn("Access Denied: User attempted to access a restricted URL: {} at {}",
                request.getRequestURI(), new Date());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");

        String arabicMessage = DetectionMessageResolver.getArabicMessage(DetectionErrorCode.ACCESS_DENIED.getLabel());
        String englishMessage = DetectionMessageResolver.getEnglishMessage(DetectionErrorCode.ACCESS_DENIED.getLabel());

        DetectionResponse<Object> detectionResponse = DetectionResponse.<Object>builder()
                .status(HttpStatus.FORBIDDEN)
                .errorCode(DetectionErrorCode.ACCESS_DENIED.getLabel())
                .errorMessages(DetectionErrorCode.ACCESS_DENIED)
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

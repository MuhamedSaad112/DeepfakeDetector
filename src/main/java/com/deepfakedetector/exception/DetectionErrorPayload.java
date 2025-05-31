package com.deepfakedetector.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DetectionErrorPayload {

    private String messageEn;
    private String messageAr;
    private String errorCode;
    private String type;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private String details;
    private List<DetectionError> errors;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DetectionError {
        private String errorMessage;
    }
}

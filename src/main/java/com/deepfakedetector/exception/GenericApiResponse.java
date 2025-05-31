package com.deepfakedetector.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericApiResponse<T> {

    private boolean success;
    private String messageEn;
    private String messageAr;
    private String errorCode;
    private T data;
    private HttpStatus status;
    private LocalDateTime timestamp;

    public static <T> GenericApiResponse<T> ok(T data) {
        return GenericApiResponse.<T>builder()
                .success(true)
                .data(data)
                .status(HttpStatus.OK)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> GenericApiResponse<T> ok(String messageEn, String messageAr, T data) {
        return GenericApiResponse.<T>builder()
                .success(true)
                .messageEn(messageEn)
                .messageAr(messageAr)
                .data(data)
                .status(HttpStatus.OK)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> GenericApiResponse<T> created(T data) {
        return GenericApiResponse.<T>builder()
                .success(true)
                .data(data)
                .status(HttpStatus.CREATED)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> GenericApiResponse<T> created(String messageEn, String messageAr, T data) {
        return GenericApiResponse.<T>builder()
                .success(true)
                .messageEn(messageEn)
                .messageAr(messageAr)
                .data(data)
                .status(HttpStatus.CREATED)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> GenericApiResponse<T> error(String messageEn, String messageAr, String errorCode, HttpStatus status) {
        return GenericApiResponse.<T>builder()
                .success(false)
                .messageEn(messageEn)
                .messageAr(messageAr)
                .errorCode(errorCode)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> GenericApiResponse<T> error(DetectionErrorPayload payload, HttpStatus status) {
        return GenericApiResponse.<T>builder()
                .success(false)
                .messageEn(payload.getMessageEn())
                .messageAr(payload.getMessageAr())
                .errorCode(payload.getErrorCode())
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> GenericApiResponse<T> badRequest(String messageEn, String messageAr) {
        return GenericApiResponse.<T>builder()
                .success(false)
                .messageEn(messageEn)
                .messageAr(messageAr)
                .status(HttpStatus.BAD_REQUEST)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> GenericApiResponse<T> notFound(String messageEn, String messageAr) {
        return GenericApiResponse.<T>builder()
                .success(false)
                .messageEn(messageEn)
                .messageAr(messageAr)
                .status(HttpStatus.NOT_FOUND)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> GenericApiResponse<T> unauthorized(String messageEn, String messageAr) {
        return GenericApiResponse.<T>builder()
                .success(false)
                .messageEn(messageEn)
                .messageAr(messageAr)
                .status(HttpStatus.UNAUTHORIZED)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> GenericApiResponse<T> forbidden(String messageEn, String messageAr) {
        return GenericApiResponse.<T>builder()
                .success(false)
                .messageEn(messageEn)
                .messageAr(messageAr)
                .status(HttpStatus.FORBIDDEN)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> GenericApiResponse<T> conflict(String messageEn, String messageAr) {
        return GenericApiResponse.<T>builder()
                .success(false)
                .messageEn(messageEn)
                .messageAr(messageAr)
                .status(HttpStatus.CONFLICT)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> GenericApiResponse<T> internalServerError(String messageEn, String messageAr) {
        return GenericApiResponse.<T>builder()
                .success(false)
                .messageEn(messageEn)
                .messageAr(messageAr)
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
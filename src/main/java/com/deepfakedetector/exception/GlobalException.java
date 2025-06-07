package com.deepfakedetector.exception;

import com.deepfakedetector.util.DetectionMessageResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MultipartException;

import java.time.LocalDateTime;
import java.util.List;

@ControllerAdvice
@Slf4j
public class GlobalException {

    @Value("${detector.error.code:DEEPFAKE_ERROR}")
    private String defaultErrorCode;

    @ExceptionHandler({DeepfakeException.class, DeepfakeSilentException.class})
    public ResponseEntity<GenericApiResponse<Object>> handleBusinessErrors(IDeepfakeException ex) {
        String ar = DetectionMessageResolver.getArabicMessage(ex.getMessage());
        String en = DetectionMessageResolver.getEnglishMessage(ex.getMessage());
        return ResponseEntity
                .ok(GenericApiResponse.error(en, ar, ex.getMessage(), HttpStatus.OK));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<GenericApiResponse<Object>> handleNotFound(EntityNotFoundException ex) {
        String ar = DetectionMessageResolver.getArabicMessage(ex.getMessage());
        String en = DetectionMessageResolver.getEnglishMessage(ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(GenericApiResponse.error(en, ar, defaultErrorCode, HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<GenericApiResponse<Object>> handleBadRequest(IllegalStateException ex) {
        String ar = DetectionMessageResolver.getArabicMessage(ex.getMessage());
        String en = DetectionMessageResolver.getEnglishMessage(ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(GenericApiResponse.error(en, ar, defaultErrorCode, HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<GenericApiResponse<Void>> handleAuthFailure(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(GenericApiResponse.error(
                        "Invalid username or password",
                        "اسم المستخدم أو كلمة المرور غير صحيحة",
                        defaultErrorCode,
                        HttpStatus.UNAUTHORIZED
                ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<GenericApiResponse<Object>> handleRuntime(RuntimeException ex, WebRequest request) {
        Throwable cause = ex.getCause();
        if (cause instanceof DeepfakeException) {
            return handleBusinessErrors((DeepfakeException) cause);
        }
        if (cause instanceof DeepfakeSilentException) {
            return handleBusinessErrors((DeepfakeSilentException) cause);
        }
        return handleAllUncaught(ex, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericApiResponse<Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<DetectionErrorPayload.DetectionError> errorList = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new DetectionErrorPayload.DetectionError("[" + error.getField() + "] " + error.getDefaultMessage()))
                .toList();

        DetectionErrorPayload payload = DetectionErrorPayload.builder()
                .messageEn("Validation failed. Please check the input fields.")
                .messageAr("فشل التحقق من صحة البيانات. يرجى مراجعة الحقول المُدخلة.")
                .errorCode("VALIDATION_ERROR")
                .errors(errorList)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(GenericApiResponse.error(payload, HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<GenericApiResponse<Object>> handleMultipartException(MultipartException ex) {
        DetectionErrorPayload payload = DetectionErrorPayload.builder()
                .messageEn("Request must be multipart/form-data. Please upload a file.")
                .messageAr("يجب أن يكون نوع الطلب multipart/form-data ويتضمن ملفًا.")
                .errorCode("MULTIPART_ERROR")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(GenericApiResponse.error(payload, HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<GenericApiResponse<Object>> handleTimeout(AsyncRequestTimeoutException ex) {
        DetectionErrorPayload payload = DetectionErrorPayload.builder()
                .messageEn("Request timed out. The server took too long to respond.")
                .messageAr("انتهى وقت الانتظار. استغرق الخادم وقتًا طويلاً للرد.")
                .errorCode("REQUEST_TIMEOUT")
                .timestamp(LocalDateTime.now())
                .details("AsyncRequestTimeoutException: " + ex.getMessage())
                .build();
        return ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .body(GenericApiResponse.error(payload, HttpStatus.GATEWAY_TIMEOUT));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericApiResponse<Object>> handleAllUncaught(Exception ex, WebRequest request) {
        log.error("Unhandled exception caught:", ex);
        DetectionErrorPayload payload = DetectionErrorPayload.builder()
                .messageEn("An unexpected error occurred. Please contact the administrator.")
                .messageAr("حدث خطأ غير متوقع، يرجى التواصل مع المسؤول.")
                .errorCode(defaultErrorCode)
                .details(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                .build();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(GenericApiResponse.error(payload, HttpStatus.INTERNAL_SERVER_ERROR));
    }


}

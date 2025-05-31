package com.deepfakedetector.exception;

import com.deepfakedetector.util.DetectionMessageResolver;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

import java.util.Date;
import java.util.List;

import static com.deepfakedetector.exception.DetectionErrorCode.SUCCESS_MESSAGE_AR;
import static com.deepfakedetector.exception.DetectionErrorCode.SUCCESS_MESSAGE_EN;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DetectionResponse<T> {

    private HttpStatus status;
    private String errorCode;
    private DetectionErrorCode errorMessages;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date timeStamp;

    private String messageAr;
    private String messageEn;

    private T data;
    private long count;

    // Success constructor
    public DetectionResponse(T data) {
        this.status = HttpStatus.OK;
        this.messageAr = DetectionMessageResolver.getArabicMessage(SUCCESS_MESSAGE_AR.getLabel());
        this.messageEn = DetectionMessageResolver.getEnglishMessage(SUCCESS_MESSAGE_EN.getLabel());
        this.timeStamp = new Date();
        this.data = data;
        this.count = data == null ? 0 : (data instanceof List) ? ((List<?>) data).size() : 1;
    }

    // Error constructor
    public DetectionResponse(HttpStatus status, String messageAr, String messageEn, String errorCode) {
        this.status = status;
        this.messageAr = messageAr;
        this.messageEn = messageEn;
        this.timeStamp = new Date();
        this.errorCode = errorCode;
    }

    // Paging constructor
    public DetectionResponse(Page<T> page) {
        this.status = HttpStatus.OK;
        this.messageAr = DetectionMessageResolver.getArabicMessage(SUCCESS_MESSAGE_AR.getLabel());
        this.messageEn = DetectionMessageResolver.getEnglishMessage(SUCCESS_MESSAGE_EN.getLabel());
        this.timeStamp = new Date();
        this.data = (T) page.getContent();
        this.count = page.getTotalElements();
    }
}

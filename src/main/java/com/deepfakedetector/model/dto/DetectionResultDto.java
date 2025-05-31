package com.deepfakedetector.model.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Builder
@Getter
@Setter
public class DetectionResultDto {
    private UUID id;
    private String fileName;
    private String fileUrl;
    private String predictionLabel;
    private double confidenceScore;
    private String fakeRatio;
    private double processingTime;
    private String modelVersion;
    private String resolution;
    private String format;
    private String uploadTime;
    private String videoDuration;
    private boolean isDeepfake;
}
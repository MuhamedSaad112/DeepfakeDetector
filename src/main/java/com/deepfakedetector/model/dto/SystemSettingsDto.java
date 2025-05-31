package com.deepfakedetector.model.dto;

import lombok.Data;

@Data
public class SystemSettingsDto {
    private int maxUploadSizeMB = 100;
    private boolean enableAnalysisNotifications = true;
    private String defaultModelVersion = "v1.0";
}

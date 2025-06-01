package com.deepfakedetector.mapper;

import com.deepfakedetector.model.dto.DetectionResultDto;
import com.deepfakedetector.model.entity.DetectionResultEntity;
import com.deepfakedetector.model.entity.MediaFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DetectionResultMapper {

    @Mapping(target = "id", source = "media.id")
    @Mapping(target = "fileName", expression = "java(getFileName(media))")
    @Mapping(target = "predictionLabel", expression = "java(getPredictionLabel(result))")
    @Mapping(target = "confidenceScore", source = "result.confidenceScore")
    @Mapping(target = "processingTime", expression = "java(getProcessingTime(result))")
    @Mapping(target = "modelVersion", expression = "java(getModelVersion(result))")
    @Mapping(target = "resolution", expression = "java(getResolution(media))")
    @Mapping(target = "format", expression = "java(getFormat(media))")
    @Mapping(target = "isDeepfake", expression = "java(isDeepfake(media))")
    @Mapping(target = "fakeRatio", expression = "java(getFakeRatio(result))")
    @Mapping(target = "uploadTime", expression = "java(getUploadTime(media))")
    @Mapping(target = "videoDuration", expression = "java(formatDuration(media.getDuration()))")
    DetectionResultDto toDto(MediaFile media, DetectionResultEntity result);

    default String getFileName(MediaFile media) {
        return media != null && media.getFileName() != null ? media.getFileName() : "Unknown File";
    }

    default String getPredictionLabel(DetectionResultEntity result) {
        if (result == null || result.getPredictionLabel() == null || result.getPredictionLabel().isBlank()) {
            return "Processing...";
        }
        return result.getPredictionLabel();
    }

    default double getProcessingTime(DetectionResultEntity result) {
        return result != null && result.getDetectionTime() != null ? result.getDetectionTime() : 0.0;
    }

    default String getModelVersion(DetectionResultEntity result) {
        if (result == null || result.getModelVersion() == null || result.getModelVersion().isBlank()) {
            return "v2.0.0";
        }
        return result.getModelVersion();
    }

    default String getResolution(MediaFile media) {
        if (media == null || media.getResolution() == null || media.getResolution().isBlank()) {
            return "1920x1080";
        }
        return media.getResolution();
    }

    default String getFormat(MediaFile media) {
        if (media == null || media.getFormat() == null || media.getFormat().isBlank()) {
            return "MP4";
        }
        return media.getFormat().toUpperCase();
    }

    default boolean isDeepfake(MediaFile media) {
        return media != null && media.getIsDeepfake() != null && media.getIsDeepfake();
    }

    default String getFakeRatio(DetectionResultEntity result) {
        if (result == null) {
            return "N/A";
        }

        if (result.getFakeRatio() != null && !result.getFakeRatio().isBlank()) {
            String fakeRatio = result.getFakeRatio().trim();
            if (!fakeRatio.equals("-")) {
                return fakeRatio.endsWith("%") ? fakeRatio : fakeRatio + "%";
            }
        }

        if (result.getProcessingDetails() != null) {
            String extracted = extractFakeRatioFromDetails(result.getProcessingDetails());
            if (!extracted.equals("N/A")) {
                return extracted;
            }
        }

        if (result.getConfidenceScore() != null) {
            double confidence = result.getConfidenceScore() * 100;
            return String.format("%.1f%%", confidence);
        }

        return "N/A";
    }

    default String getUploadTime(MediaFile media) {
        if (media == null || media.getUploadedAt() == null) {
            return java.time.LocalDateTime.now().toString();
        }
        return media.getUploadedAt().toString();
    }

    default String formatDuration(Double durationInSeconds) {
        if (durationInSeconds == null || durationInSeconds <= 0) {
            return "0 seconds";
        }
        if (durationInSeconds >= 3600) {
            long hours = Math.round(durationInSeconds / 3600.0);
            long minutes = Math.round((durationInSeconds % 3600) / 60.0);
            return String.format("%d hour%s %d minute%s",
                    hours, hours == 1 ? "" : "s",
                    minutes, minutes == 1 ? "" : "s");
        } else if (durationInSeconds >= 60) {
            long minutes = Math.round(durationInSeconds / 60.0);
            long seconds = Math.round(durationInSeconds % 60);
            return String.format("%d minute%s %d second%s",
                    minutes, minutes == 1 ? "" : "s",
                    seconds, seconds == 1 ? "" : "s");
        } else {
            long seconds = Math.round(durationInSeconds);
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
    }

    default String extractFakeRatioFromDetails(String processingDetails) {
        if (processingDetails == null || processingDetails.isBlank()) {
            return "N/A";
        }
        try {
            String[] patterns = {
                    "fakeRatio\":\\s*\"?([0-9.]+)%?\"?",
                    "fake_ratio\":\\s*\"?([0-9.]+)%?\"?",
                    "confidence\":\\s*\"?([0-9.]+)%?\"?",
                    "score\":\\s*\"?([0-9.]+)%?\"?"
            };
            for (String pattern : patterns) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = p.matcher(processingDetails);
                if (m.find()) {
                    double value = Double.parseDouble(m.group(1));
                    if (value > 1) {
                        return String.format("%.1f%%", value);
                    } else {
                        return String.format("%.1f%%", value * 100);
                    }
                }
            }
            if (processingDetails.contains("fakeRatio")) {
                int start = processingDetails.indexOf("fakeRatio");
                String substring = processingDetails.substring(start, Math.min(start + 50, processingDetails.length()));
                java.util.regex.Pattern numberPattern = java.util.regex.Pattern.compile("([0-9]+\\.?[0-9]*)");
                java.util.regex.Matcher numberMatcher = numberPattern.matcher(substring);
                if (numberMatcher.find()) {
                    double value = Double.parseDouble(numberMatcher.group(1));
                    return value > 1 ? String.format("%.1f%%", value) : String.format("%.1f%%", value * 100);
                }
            }
        } catch (Exception e) {
            return "N/A";
        }
        return "N/A";
    }
}

package com.deepfakedetector.model.dto;

import com.deepfakedetector.model.enums.MediaFileType;
import com.deepfakedetector.model.enums.ProcessingStatus;
import com.deepfakedetector.model.enums.UploadSource;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaFileDto {
    private UUID id;
    private String fileName;
    private MediaFileType fileType;
    private String fileSize;
    private String duration;
    private String resolution;
    private String format;
    private Boolean isDeepfake;
    private ProcessingStatus processingStatus;
    private UploadSource uploadSource;
    private String comments;
    private LocalDateTime uploadedAt;

    private String username;
}

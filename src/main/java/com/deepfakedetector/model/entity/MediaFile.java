package com.deepfakedetector.model.entity;

import com.deepfakedetector.model.enums.MediaFileType;
import com.deepfakedetector.model.enums.ProcessingStatus;
import com.deepfakedetector.model.enums.UploadSource;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "media_files")
public class MediaFile extends AbstractAuditingEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "File name is required")
    @Size(max = 255)
    @Column(name = "file_name", nullable = false)
    private String fileName;

    @NotBlank(message = "File path is required")
    @Column(name = "file_path", nullable = false, unique = true)
    private String filePath;

    @NotNull(message = "File type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private MediaFileType fileType;

    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @PositiveOrZero(message = "Duration must be non-negative")
    @Column(name = "duration")
    private Double duration;

    @Pattern(regexp = "^\\d+x\\d+$", message = "Resolution must be in format WxH (e.g., 1920x1080)")
    @Column(name = "resolution")
    private String resolution;

    @Column(name = "format")
    private String format;

    @Column(name = "is_deepfake")
    private Boolean isDeepfake = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_source", nullable = false)
    private UploadSource uploadSource;

    @Size(max = 1000, message = "Comments cannot exceed 1000 characters")
    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @NotNull(message = "Upload timestamp is required")
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @OneToMany(mappedBy = "mediaFile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DetectionResultEntity> detectionResults;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "User reference is required")
    private User user;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
        if (uploadSource == null) {
            uploadSource = UploadSource.UNKNOWN;
        }
    }

    public MediaFile updateStatus(ProcessingStatus newStatus) {
        this.processingStatus = newStatus;
        return this;
    }

    public MediaFile markAsDeepfake() {
        this.isDeepfake = true;
        return this;
    }


    public boolean isVideo() {
        return fileType != null && fileType.getMimeType().startsWith("video/");
    }


    public boolean isImage() {
        return fileType != null && fileType.getMimeType().startsWith("image/");
    }


    public boolean isProcessingComplete() {
        return processingStatus == ProcessingStatus.COMPLETED ||
                processingStatus == ProcessingStatus.FAILED;
    }


    public double getFileSizeInMB() {
        return fileSize / (1024.0 * 1024.0);
    }


    public int[] getResolutionDimensions() {
        if (resolution == null || !resolution.matches("^\\d+x\\d+$")) {
            return new int[]{0, 0};
        }
        String[] dimensions = resolution.split("x");
        return new int[]{
                Integer.parseInt(dimensions[0]),
                Integer.parseInt(dimensions[1])
        };
    }

    public static MediaFileBuilder defaultBuilder() {
        return builder()
                .processingStatus(ProcessingStatus.PENDING)
                .isDeepfake(false)
                .uploadedAt(LocalDateTime.now());
    }
}

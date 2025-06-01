package com.deepfakedetector.model.entity;

import com.deepfakedetector.model.enums.DetectionMethod;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "detection_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectionResultEntity extends AbstractAuditingEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mediaFile_id", nullable = false)
    @NotNull(message = "Associated media file is required")
    private MediaFile mediaFile;

    @NotBlank(message = "Prediction label is required")
    @Column(name = "prediction_label", nullable = false)
    private String predictionLabel;

    @NotNull(message = "Confidence score is required")
    @DecimalMin(value = "0.0", message = "Confidence score must be at least 0")
    @DecimalMax(value = "1.0", message = "Confidence score must not exceed 1")
    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;

    @PositiveOrZero(message = "Detection time must be non-negative")
    @Column(name = "detection_time")
    private Double detectionTime;

    @NotBlank(message = "Model version is required")
    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "detection_method", nullable = false)
    private DetectionMethod detectionMethod;

    @Column(name = "confidence_distribution", columnDefinition = "TEXT")
    private String confidenceDistribution;

    @Lob
    @Column(name = "processing_details", columnDefinition = "TEXT")
    private String processingDetails;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @NotNull(message = "FakeRatio score is required")
    @DecimalMin(value = "0.0", message = "FakeRatio score must be at least 0")
    @DecimalMax(value = "1.0", message = "FakeRatio score must not exceed 1")
    @Column(name = "fake_ratio", nullable = false)
    private String fakeRatio;

    @DecimalMin(value = "0.0", message = "Detection accuracy must be at least 0")
    @DecimalMax(value = "1.0", message = "Detection accuracy must not exceed 1")
    @Column(name = "detection_accuracy")
    private Double detectionAccuracy;

    @NotNull(message = "Prediction timestamp is required")
    @Column(name = "predicted_at", nullable = false)
    private LocalDateTime predictedAt;

    @PrePersist
    protected void onCreate() {
        if (predictedAt == null) {
            predictedAt = LocalDateTime.now();
        }
    }


    public DetectionResultEntity verify(String reviewerName) {
        this.isVerified = true;
        this.reviewedBy = reviewerName;
        return this;
    }


    public boolean meetsConfidenceThreshold(double threshold) {
        return this.confidenceScore >= threshold;
    }

    public DetectionResultEntity updateMetrics(Double accuracy, Double confidence) {
        if (accuracy != null) {
            this.detectionAccuracy = accuracy;
        }
        if (confidence != null) {
            this.confidenceScore = confidence;
        }
        return this;
    }


    public boolean needsReview(double threshold) {
        return !isVerified && confidenceScore < threshold;
    }


    public static DetectionResultEntityBuilder defaultBuilder() {
        return builder()
                .isVerified(false)
                .predictedAt(LocalDateTime.now())
                .detectionMethod(DetectionMethod.DEEP_LEARNING);
    }
}

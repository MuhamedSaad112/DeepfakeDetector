package com.deepfakedetector.repository;

import com.deepfakedetector.model.entity.DetectionResultEntity;
import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.model.enums.DetectionMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface DetectionResultRepository extends JpaRepository<DetectionResultEntity, UUID> {

    Optional<DetectionResultEntity> findByMediaFile_Id(UUID mediaFileId);


    List<DetectionResultEntity> findByIsVerified(Boolean isVerified);

    Page<DetectionResultEntity> findByIsVerified(Boolean isVerified, Pageable pageable);


    List<DetectionResultEntity> findByDetectionMethod(DetectionMethod detectionMethod);


    List<DetectionResultEntity> findByModelVersion(String modelVersion);


    @Query("SELECT p FROM DetectionResultEntity p WHERE p.confidenceScore < :threshold " +
            "AND p.isVerified = false ORDER BY p.predictedAt DESC")
    List<DetectionResultEntity> findPredictionsNeedingReview(@Param("threshold") Double threshold);


    List<DetectionResultEntity> findByReviewedBy(String reviewedBy);


    List<DetectionResultEntity> findByPredictedAtBetween(LocalDateTime start, LocalDateTime end);


    @Query("SELECT p.modelVersion, AVG(p.detectionAccuracy), COUNT(p) " +
            "FROM DetectionResultEntity p GROUP BY p.modelVersion")
    List<Object[]> getAccuracyStatsByModel();


    @Query("SELECT p.detectionMethod, AVG(p.confidenceScore) " +
            "FROM DetectionResultEntity p GROUP BY p.detectionMethod")
    List<Object[]> getAverageConfidenceByMethod();


    List<DetectionResultEntity> findByPredictionLabel(String label);


    long countByIsVerified(Boolean isVerified);


    @Query("SELECT p FROM DetectionResultEntity p WHERE p.modelVersion = :modelVersion " +
            "AND p.predictedAt >= :since ORDER BY p.predictedAt DESC")
    List<DetectionResultEntity> findRecentPredictionsByModel(
            @Param("modelVersion") String modelVersion,
            @Param("since") LocalDateTime since
    );


    @Query("SELECT p.detectionMethod, " +
            "AVG(p.detectionAccuracy) as avgAccuracy, " +
            "AVG(p.confidenceScore) as avgConfidence, " +
            "AVG(p.detectionTime) as avgTime " +
            "FROM DetectionResultEntity p " +
            "WHERE p.predictedAt BETWEEN :startDate AND :endDate " +
            "GROUP BY p.detectionMethod")
    List<Object[]> getPerformanceMetrics(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


    @Query("SELECT p FROM DetectionResultEntity p WHERE p.isVerified = false " +
            "AND p.predictedAt < :date ORDER BY p.predictedAt")
    List<DetectionResultEntity> findOldUnverifiedPredictions(@Param("date") LocalDateTime date);


    @Query("SELECT p1 FROM DetectionResultEntity p1, DetectionResultEntity p2 " +
           "WHERE p1.mediaFile = p2.mediaFile " +
           "AND p1.id < p2.id " +
           "AND p1.predictionLabel <> p2.predictionLabel")
    List<DetectionResultEntity> findConflictingPredictions();

    Optional<DetectionResultEntity> findByMediaFile(MediaFile mediaFile);
}

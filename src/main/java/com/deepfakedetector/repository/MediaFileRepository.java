package com.deepfakedetector.repository;

import com.deepfakedetector.model.entity.DetectionResultEntity;
import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.model.enums.MediaFileType;
import com.deepfakedetector.model.enums.ProcessingStatus;
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
public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    List<MediaFile> findAllByUser_UserNameOrderByUploadedAtDesc(String userName);

    Optional<MediaFile> findByIdAndUser_UserName(UUID id, String userName);

    List<MediaFile> findByUserUserName(String UserName);


    Page<MediaFile> findByUserIdOrderByUploadedAtDesc(UUID userId, Pageable pageable);


    Optional<MediaFile> findByFileName(String fileName);


    List<MediaFile> findByProcessingStatus(ProcessingStatus status);


    Page<MediaFile> findByProcessingStatus(ProcessingStatus status, Pageable pageable);


    List<MediaFile> findByIsDeepfakeTrue();


    List<MediaFile> findByUserId(UUID userId);


    Page<MediaFile> findByUserId(Long userId, Pageable pageable);


    List<MediaFile> findByFileType(MediaFileType fileType);


    List<MediaFile> findByUploadedAtBetween(LocalDateTime start, LocalDateTime end);


    long countByProcessingStatus(ProcessingStatus status);


    long countByIsDeepfakeTrue();


    @Query("SELECT m FROM MediaFile m WHERE m.processingStatus IN :statuses")
    List<MediaFile> findUnprocessedMedia(@Param("statuses") List<ProcessingStatus> statuses);


    @Query("SELECT m FROM MediaFile m WHERE m.processingStatus = 'FAILED' ORDER BY m.uploadedAt DESC")
    List<MediaFile> findFailedProcessingMedia();


}

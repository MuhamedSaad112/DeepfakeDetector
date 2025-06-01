package com.deepfakedetector.repository;

import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.model.enums.MediaFileType;
import com.deepfakedetector.model.enums.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = "user")
    List<MediaFile> findAllByUser_UserNameOrderByUploadedAtDesc(String userName, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    List<MediaFile> findAllByUser_UserNameOrderByUploadedAtDesc(String username);

    @Query("SELECT mf FROM MediaFile mf JOIN FETCH mf.user WHERE mf.user.userName = :username ORDER BY mf.uploadedAt DESC")
    List<MediaFile> findAllByUserUserNameWithUser(@Param("username") String username);

    @EntityGraph(attributePaths = "user")
    Optional<MediaFile> findByIdAndUser_UserName(UUID id, String userName);

    @Query("SELECT mf FROM MediaFile mf LEFT JOIN FETCH mf.detectionResults WHERE mf.id = :videoId AND mf.user.userName = :username")
    Optional<MediaFile> findByIdAndUsernameWithDetectionResults(@Param("videoId") UUID videoId, @Param("username") String username);

    @EntityGraph(attributePaths = {"user"})
    Page<MediaFile> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    List<MediaFile> findByUserUserName(String UserName);

    @EntityGraph(attributePaths = {"user", "detectionResults"})
    List<MediaFile> findAllByOrderByUploadedAtDesc();

    @Query("SELECT m FROM MediaFile m ORDER BY m.uploadedAt DESC")
    Page<MediaFile> findAllByOrderByUploadedAtDesc(Pageable pageable);

    @Query("SELECT DISTINCT m FROM MediaFile m " +
            "LEFT JOIN FETCH m.detectionResults " +
            "LEFT JOIN FETCH m.user " +
            "WHERE m.id = :videoId AND m.user.userName = :username")
    Optional<MediaFile> findByIdAndUsernameWithUserAndDetectionResults(@Param("videoId") UUID videoId,
                                                                       @Param("username") String username);

    @Query("SELECT m FROM MediaFile m LEFT JOIN FETCH m.detectionResults WHERE m.id = :uuid")
    Optional<MediaFile> findWithResultsByUuid(UUID uuid);


    long countByUser_UserName(String username);

    boolean existsByIdAndUser_UserName(UUID id, String username);

    Page<MediaFile> findByUserIdOrderByUploadedAtDesc(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);

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

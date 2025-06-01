package com.deepfakedetector.service.video;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.mapper.DetectionResultMapper;
import com.deepfakedetector.mapper.MediaFileMapper;
import com.deepfakedetector.model.dto.DetectionResultDto;
import com.deepfakedetector.model.dto.MediaFileDto;
import com.deepfakedetector.model.entity.DetectionResultEntity;
import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.repository.DetectionResultRepository;
import com.deepfakedetector.repository.MediaFileRepository;
import com.deepfakedetector.util.ReportGenerator;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileVideoService {

    private final MediaFileRepository mediaFileRepository;
    private final DetectionResultRepository detectionResultRepository;
    private final ReportGenerator reportGenerator;
    private final DetectionResultMapper detectionResultMapper;
    private final MediaFileMapper mediaFileMapper;

    private final ExecutorService profileProcessingExecutor = Executors.newFixedThreadPool(
            Math.min(4, Runtime.getRuntime().availableProcessors())
    );

    @Value("${deepfake.profile.max-videos-per-page:50}")
    private int maxVideosPerPage;

    @Value("${deepfake.profile.default-page-size:20}")
    private int defaultPageSize;

    public Mono<List<MediaFileDto>> getAllVideosByUser(String username) {
        log.info("Fetching all videos for user: {}", username);
        log.debug("Available memory before processing: {} MB",
                Runtime.getRuntime().freeMemory() / 1024 / 1024);

        return Mono.fromCallable(() -> validateAndFetchUserVideos(username))
                .subscribeOn(Schedulers.fromExecutor(profileProcessingExecutor))
                .doOnSuccess(videos -> {
                    if (videos != null) {
                        log.info("Successfully retrieved {} videos for user: {}", videos.size(), username);
                    } else {
                        log.warn("Video retrieval returned null for user: {}", username);
                    }
                })
                .doOnError(err -> {
                    log.error("Failed to fetch videos for user {}: {}",
                            username, err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    public Mono<List<MediaFileDto>> getVideosByUserWithPagination(String username, int page, int size) {
        log.info("Fetching videos for user: {} with pagination - page: {}, size: {}", username, page, size);

        return Mono.fromCallable(() -> validateAndFetchUserVideosWithPagination(username, page, size))
                .subscribeOn(Schedulers.fromExecutor(profileProcessingExecutor))
                .doOnSuccess(videos -> {
                    if (videos != null) {
                        log.info("Successfully retrieved {} videos for user: {} (page: {})",
                                videos.size(), username, page);
                    } else {
                        log.warn("Paginated video retrieval returned null for user: {}", username);
                    }
                })
                .doOnError(err -> {
                    log.error("Failed to fetch paginated videos for user {}: {}",
                            username, err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    public Mono<DetectionResultDto> getVideoAnalysisDetails(UUID videoId, String username) {
        log.info("Fetching analysis details for video: {} by user: {}", videoId, username);

        return Mono.fromCallable(() -> validateAndFetchAnalysisDetails(videoId, username))
                .subscribeOn(Schedulers.fromExecutor(profileProcessingExecutor))
                .doOnSuccess(result -> {
                    if (result != null) {
                        log.info("Successfully retrieved analysis details for video: {} - Result: {}",
                                videoId, result.getPredictionLabel());
                    } else {
                        log.warn("Analysis details retrieval returned null for video: {}", videoId);
                    }
                })
                .doOnError(err -> {
                    log.error("Failed to fetch analysis details for video {} by user {}: {}",
                            videoId, username, err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    public Mono<byte[]> getReportForVideo(UUID videoId, String username) {
        return Mono.fromCallable(() -> validateAndGenerateReport(videoId, username))
                .subscribeOn(Schedulers.fromExecutor(profileProcessingExecutor))
                .flatMap(mediaFile -> {
                    DetectionResultEntity result = mediaFile.getDetectionResults().stream()
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                    if (result != null && result.getDetectionAccuracy() == null) {
                        result.setDetectionAccuracy(0.0);
                    }
                    return reportGenerator.generatePdf(mediaFile);
                })
                .doOnSuccess(reportBytes -> {
                    if (reportBytes != null && reportBytes.length > 0) {
                        log.info("Successfully generated report for video: {} - Size: {} KB",
                                videoId, reportBytes.length / 1024);
                    } else {
                        log.warn("Report generation returned empty result for video: {}", videoId);
                    }
                })
                .doOnError(err -> {
                    log.error("Failed to generate report for video {} by user {}: {}",
                            videoId, username, err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }


    @Transactional
    public Mono<Void> deleteVideo(UUID videoId, String username) {
        log.info("Deleting video: {} by user: {}", videoId, username);

        return Mono.fromCallable(() -> validateAndDeleteVideo(videoId, username))
                .subscribeOn(Schedulers.fromExecutor(profileProcessingExecutor))
                .doOnSuccess(fileName -> {
                    log.info("Successfully deleted video: {} (ID: {}) by user: {}", fileName, videoId, username);
                })
                .doOnError(err -> {
                    log.error("Failed to delete video {} by user {}: {}",
                            videoId, username, err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException)
                .then();
    }

    public Mono<Long> getUserVideoCount(String username) {
        log.debug("Counting videos for user: {}", username);

        return Mono.fromCallable(() -> validateAndCountUserVideos(username))
                .subscribeOn(Schedulers.fromExecutor(profileProcessingExecutor))
                .doOnSuccess(count -> {
                    log.debug("User {} has {} videos", username, count);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    public Mono<Boolean> hasUserAccessToVideo(UUID videoId, String username) {
        log.debug("Checking user access - video: {} by user: {}", videoId, username);

        return Mono.fromCallable(() -> validateUserAccessToVideo(videoId, username))
                .subscribeOn(Schedulers.fromExecutor(profileProcessingExecutor))
                .doOnSuccess(hasAccess -> {
                    log.debug("User {} access to video {}: {}", username, videoId, hasAccess);
                })
                .onErrorReturn(false);
    }

    public Mono<MediaFileDto> getVideoById(UUID videoId, String username) {
        log.info("Fetching video by ID: {} for user: {}", videoId, username);

        return Mono.fromCallable(() -> validateAndFetchVideoById(videoId, username))
                .subscribeOn(Schedulers.fromExecutor(profileProcessingExecutor))
                .doOnSuccess(video -> {
                    if (video != null) {
                        log.info("Successfully retrieved video: {} for user: {}", video.getFileName(), username);
                    } else {
                        log.warn("Video retrieval returned null for ID: {}", videoId);
                    }
                })
                .doOnError(err -> {
                    log.error("Failed to fetch video {} for user {}: {}",
                            videoId, username, err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    private List<MediaFileDto> validateAndFetchUserVideos(String username) throws DeepfakeException {
        if (username == null || username.isBlank()) {
            throw new DeepfakeException(DetectionErrorCode.INVALID_INPUT);
        }

        List<MediaFile> mediaFiles = mediaFileRepository.findAllByUserUserNameWithUser(username);

        if (mediaFiles.isEmpty()) {
            return List.of();
        }

        return mediaFileMapper.toDtoList(mediaFiles);
    }


    private List<MediaFileDto> validateAndFetchUserVideosWithPagination(String username, int page, int size)
            throws DeepfakeException {
        validateUsername(username);
        validatePaginationParams(page, size);
        int zeroBasedPage = Math.max(page - 1, 0);
        int validatedSize = Math.min(size, maxVideosPerPage);
        Pageable pageable = PageRequest.of(zeroBasedPage, validatedSize);

        List<MediaFile> mediaFiles = mediaFileRepository.findAllByUser_UserNameOrderByUploadedAtDesc(username, pageable);
        return mediaFiles.stream()
                .map(mediaFileMapper::toDto)
                .collect(Collectors.toList());
    }


    private DetectionResultDto validateAndFetchAnalysisDetails(UUID videoId, String username)
            throws DeepfakeException {
        validateVideoId(videoId);
        validateUsername(username);

        MediaFile file = findMediaFileByIdAndUser(videoId, username);
        DetectionResultEntity result = findDetectionResultByMediaFile(file);
        return detectionResultMapper.toDto(file, result);
    }

    private MediaFile validateAndGenerateReport(UUID videoId, String username) throws DeepfakeException {
        validateVideoId(videoId);
        validateUsername(username);

        return mediaFileRepository.findByIdAndUsernameWithUserAndDetectionResults(videoId, username)
                .orElseThrow(() -> {
                    log.error("Video with user and detection results not found - ID: {} for user: {}", videoId, username);
                    return new DeepfakeException(DetectionErrorCode.VIDEO_NOT_FOUND);
                });
    }


    private String validateAndDeleteVideo(UUID videoId, String username) throws DeepfakeException {
        validateVideoId(videoId);
        validateUsername(username);

        MediaFile file = findMediaFileByIdAndUser(videoId, username);
        String fileName = file.getFileName();

        try {
            mediaFileRepository.delete(file);
            log.debug("Successfully deleted media file from database: {}", fileName);
        } catch (Exception e) {
            log.error("Failed to delete media file from database: {}", fileName, e);
            throw new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_DELETE_VIDEO);
        }

        return fileName;
    }

    private Long validateAndCountUserVideos(String username) throws DeepfakeException {
        validateUsername(username);
        List<MediaFile> userVideos = mediaFileRepository.findAllByUser_UserNameOrderByUploadedAtDesc(username);
        return (long) userVideos.size();
    }

    private Boolean validateUserAccessToVideo(UUID videoId, String username) throws DeepfakeException {
        validateVideoId(videoId);
        validateUsername(username);

        return mediaFileRepository.findByIdAndUser_UserName(videoId, username).isPresent();
    }

    private MediaFileDto validateAndFetchVideoById(UUID videoId, String username) throws DeepfakeException {
        validateVideoId(videoId);
        validateUsername(username);

        MediaFile file = findMediaFileByIdAndUser(videoId, username);
        return mediaFileMapper.toDto(file);
    }

    private MediaFile findMediaFileByIdAndUser(UUID videoId, String username) throws DeepfakeException {
        return mediaFileRepository.findByIdAndUser_UserName(videoId, username)
                .orElseThrow(() -> {
                    log.error("Video not found - ID: {} for user: {}", videoId, username);
                    return new DeepfakeException(DetectionErrorCode.VIDEO_NOT_FOUND);
                });
    }


    private DetectionResultEntity findDetectionResultByMediaFile(MediaFile file) throws DeepfakeException {
        return detectionResultRepository.findByMediaFile_Id(file.getId())
                .orElseThrow(() -> {
                    log.error("Detection result not found for video: {} (ID: {})",
                            file.getFileName(), file.getId());
                    return new DeepfakeException(DetectionErrorCode.DETECTION_RESULT_NOT_FOUND);
                });
    }


    private void validateVideoId(UUID videoId) throws DeepfakeException {
        if (videoId == null) {
            log.error("Video ID is null");
            throw new DeepfakeException(DetectionErrorCode.INVALID_VIDEO_ID);
        }
        log.debug("Video ID validation passed: {}", videoId);
    }

    private void validateUsername(String username) throws DeepfakeException {
        if (username == null || username.trim().isEmpty()) {
            log.error("Username is null or empty");
            throw new DeepfakeException(DetectionErrorCode.INVALID_USERNAME);
        }
        log.debug("Username validation passed: {}", username);
    }

    private void validatePaginationParams(int page, int size) throws DeepfakeException {
        if (page < 0) {
            log.error("Invalid page number: {} (must be >= 0)", page);
            throw new DeepfakeException(DetectionErrorCode.INVALID_PAGE_NUMBER);
        }
        if (size <= 0) {
            log.error("Invalid page size: {} (must be > 0)", size);
            throw new DeepfakeException(DetectionErrorCode.INVALID_PAGE_SIZE);
        }
        if (size > maxVideosPerPage) {
            log.warn("Requested page size {} exceeds maximum {}, will be capped", size, maxVideosPerPage);
        }
        log.debug("Pagination validation passed - page: {}, size: {}", page, size);
    }

    private DetectionResultDto convertToDetectionResultDto(MediaFile mediaFile, DetectionResultEntity result) {
        return detectionResultMapper.toDto(mediaFile, result);
    }

    private Throwable mapToAppropriateException(Throwable error) {
        log.error("Mapping profile service error to appropriate exception: {} - {}",
                error.getClass().getSimpleName(), error.getMessage());

        if (error instanceof DeepfakeException || error instanceof DeepfakeSilentException) {
            return error;
        }

        if (error.getMessage() != null && error.getMessage().contains("database")) {
            return new DeepfakeSilentException(DetectionErrorCode.DATABASE_ERROR);
        }

        if (error instanceof RuntimeException && error.getCause() instanceof DeepfakeException) {
            return error.getCause();
        }

        return new DeepfakeSilentException(DetectionErrorCode.PROFILE_SERVICE_ERROR);
    }

    @PreDestroy
    public void cleanup() {
        log.info("Starting cleanup of ProfileVideoService resources");
        profileProcessingExecutor.shutdown();
        try {
            if (!profileProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Profile executor did not terminate gracefully, forcing shutdown");
                profileProcessingExecutor.shutdownNow();
            } else {
                log.info("Profile executor terminated gracefully");
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for profile executor termination", e);
            profileProcessingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("ProfileVideoService cleanup completed");
    }
}
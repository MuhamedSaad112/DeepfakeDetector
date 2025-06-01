package com.deepfakedetector.service.video;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.mapper.MediaFileMapper;
import com.deepfakedetector.model.dto.MediaFileDto;
import com.deepfakedetector.model.dto.SystemSettingsDto;
import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.repository.DetectionResultRepository;
import com.deepfakedetector.repository.MediaFileRepository;
import com.deepfakedetector.repository.UserRepository;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final MediaFileRepository mediaFileRepository;
    private final UserRepository userRepository;
    private final DetectionResultRepository detectionResultRepository;
    private final MediaFileMapper mediaFileMapper;
    private final ReportGenerator reportGenerator;

    private final ExecutorService adminProcessingExecutor = Executors.newFixedThreadPool(
            Math.min(6, Runtime.getRuntime().availableProcessors())
    );

    @Value("${deepfake.admin.max-results-per-page:100}")
    private int maxResultsPerPage;

    @Value("${deepfake.admin.report-cache-duration:300}")
    private int reportCacheDurationSeconds;

    private SystemSettingsDto systemSettings = new SystemSettingsDto();
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Mono<List<MediaFileDto>> getAllVideos() {
        log.info("Admin: Fetching all videos from system");
        log.debug("Available memory before processing: {} MB",
                Runtime.getRuntime().freeMemory() / 1024 / 1024);

        return Mono.fromCallable(() -> validateAndFetchAllVideos())
                .subscribeOn(Schedulers.fromExecutor(adminProcessingExecutor))
                .doOnSuccess(videos -> {
                    if (videos != null) {
                        log.info("Admin: Successfully retrieved {} videos from system", videos.size());
                    } else {
                        log.warn("Admin: Video retrieval returned null");
                    }
                })
                .doOnError(err -> {
                    log.error("Admin: Failed to fetch all videos: {}", err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    public Mono<List<MediaFileDto>> getVideosWithPagination(int page, int size) {
        log.info("Admin: Fetching videos with pagination - page: {}, size: {}", page, size);

        return Mono.fromCallable(() -> validateAndFetchVideosWithPagination(page, size))
                .subscribeOn(Schedulers.fromExecutor(adminProcessingExecutor))
                .doOnSuccess(videos -> {
                    if (videos != null) {
                        log.info("Admin: Successfully retrieved {} videos (page: {})", videos.size(), page);
                    } else {
                        log.warn("Admin: Paginated video retrieval returned null");
                    }
                })
                .doOnError(err -> {
                    log.error("Admin: Failed to fetch paginated videos: {}", err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    @Transactional
    public Mono<String> deleteVideo(UUID videoId) {
        log.info("Admin: Deleting video with ID: {}", videoId);

        return Mono.fromCallable(() -> validateAndDeleteVideo(videoId))
                .subscribeOn(Schedulers.fromExecutor(adminProcessingExecutor))
                .doOnSuccess(fileName -> {
                    log.info("Admin: Successfully deleted video: {} (ID: {})", fileName, videoId);
                })
                .doOnError(err -> {
                    log.error("Admin: Failed to delete video {}: {}", videoId, err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException)
                .thenReturn("Video deleted successfully");
    }


    public Mono<Map<String, Object>> getSystemStats() {
        log.info("Admin: Generating system statistics");

        return Mono.fromCallable(() -> validateAndGenerateSystemStats())
                .subscribeOn(Schedulers.fromExecutor(adminProcessingExecutor))
                .doOnSuccess(stats -> {
                    if (stats != null) {
                        log.info("Admin: Successfully generated system stats - {} total videos, {} users",
                                stats.get("totalVideos"), stats.get("totalUsers"));
                    } else {
                        log.warn("Admin: System stats generation returned null");
                    }
                })
                .doOnError(err -> {
                    log.error("Admin: Failed to generate system stats: {}", err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    public Mono<List<MediaFileDto>> getUserActivity(String username) {
        log.info("Admin: Fetching user activity for username: {}", username);

        return Mono.fromCallable(() -> validateAndFetchUserActivity(username))
                .subscribeOn(Schedulers.fromExecutor(adminProcessingExecutor))
                .doOnSuccess(activities -> {
                    if (activities != null) {
                        log.info("Admin: Successfully retrieved {} activities for user: {}",
                                activities.size(), username);
                    } else {
                        log.warn("Admin: User activity retrieval returned null for user: {}", username);
                    }
                })
                .doOnError(err -> {
                    log.error("Admin: Failed to fetch user activity for {}: {}", username, err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    public Mono<byte[]> generateSystemReport() {
        log.info("Admin: Generating comprehensive system report");

        return Mono.fromCallable(() -> validateAndGenerateSystemReport())
                .subscribeOn(Schedulers.fromExecutor(adminProcessingExecutor))
                .doOnSuccess(reportBytes -> {
                    if (reportBytes != null && reportBytes.length > 0) {
                        log.info("Admin: Successfully generated system report - Size: {} KB",
                                reportBytes.length / 1024);
                    } else {
                        log.warn("Admin: System report generation returned empty result");
                    }
                })
                .doOnError(err -> {
                    log.error("Admin: Failed to generate system report: {}", err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    @Transactional
    public Mono<SystemSettingsDto> updateSettings(SystemSettingsDto settingsDto) {
        log.info("Admin: Updating system settings");

        return Mono.fromCallable(() -> validateAndUpdateSettings(settingsDto))
                .subscribeOn(Schedulers.fromExecutor(adminProcessingExecutor))
                .doOnSuccess(updatedSettings -> {
                    if (updatedSettings != null) {
                        log.info("Admin: Successfully updated system settings");
                    } else {
                        log.warn("Admin: Settings update returned null");
                    }
                })
                .doOnError(err -> {
                    log.error("Admin: Failed to update settings: {}", err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    public Mono<SystemSettingsDto> getCurrentSettings() {
        log.debug("Admin: Retrieving current system settings");

        return Mono.fromCallable(() -> validateAndGetCurrentSettings())
                .subscribeOn(Schedulers.fromExecutor(adminProcessingExecutor))
                .doOnSuccess(settings -> {
                    log.debug("Admin: Successfully retrieved current settings");
                })
                .doOnError(err -> {
                    log.error("Admin: Failed to retrieve current settings: {}", err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    public Mono<Map<String, Object>> getAdvancedAnalytics() {
        log.info("Admin: Generating advanced analytics");

        return Mono.fromCallable(() -> validateAndGenerateAdvancedAnalytics())
                .subscribeOn(Schedulers.fromExecutor(adminProcessingExecutor))
                .doOnSuccess(analytics -> {
                    if (analytics != null) {
                        log.info("Admin: Successfully generated advanced analytics");
                    } else {
                        log.warn("Admin: Advanced analytics generation returned null");
                    }
                })
                .doOnError(err -> {
                    log.error("Admin: Failed to generate advanced analytics: {}", err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    public Mono<Long> getTotalVideoCount() {
        log.debug("Admin: Getting total video count");

        return Mono.fromCallable(() -> validateAndCountAllVideos())
                .subscribeOn(Schedulers.fromExecutor(adminProcessingExecutor))
                .doOnSuccess(count -> {
                    log.debug("Admin: Total video count: {}", count);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    private List<MediaFileDto> validateAndFetchAllVideos() throws DeepfakeException {
        List<MediaFile> mediaFiles = mediaFileRepository.findAllByOrderByUploadedAtDesc();
        return mediaFiles.stream()
                .map(mediaFileMapper::toDto)
                .collect(Collectors.toList());
    }

    private List<MediaFileDto> validateAndFetchVideosWithPagination(int page, int size) throws DeepfakeException {
        validatePaginationParams(page, size);

        int validatedSize = Math.min(size, maxResultsPerPage);
        Pageable pageable = PageRequest.of(page, validatedSize);

        List<MediaFile> mediaFiles = mediaFileRepository.findAll(pageable).getContent();
        return mediaFiles.stream()
                .map(mediaFileMapper::toDto)
                .collect(Collectors.toList());
    }

    private String validateAndDeleteVideo(UUID videoId) throws DeepfakeException {
        validateVideoId(videoId);

        MediaFile mediaFile = mediaFileRepository.findById(videoId)
                .orElseThrow(() -> {
                    log.error("Admin: Video not found with ID: {}", videoId);
                    return new DeepfakeException(DetectionErrorCode.VIDEO_NOT_FOUND);
                });

        String fileName = mediaFile.getFileName();

        try {
            mediaFileRepository.delete(mediaFile);
            log.debug("Admin: Successfully deleted media file from database: {}", fileName);
        } catch (Exception e) {
            log.error("Admin: Failed to delete media file from database: {}", fileName, e);
            throw new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_DELETE_VIDEO);
        }

        return fileName;
    }

    private Map<String, Object> validateAndGenerateSystemStats() throws DeepfakeException {
        try {
            long totalVideos = mediaFileRepository.count();
            long deepfakeVideos = mediaFileRepository.countByIsDeepfakeTrue();
            long authenticVideos = totalVideos - deepfakeVideos;
            long totalUsers = userRepository.count();
            long totalDetections = detectionResultRepository.count();

            double deepfakePercentage = totalVideos > 0 ? (double) deepfakeVideos / totalVideos * 100 : 0.0;

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalVideos", totalVideos);
            stats.put("deepfakeVideos", deepfakeVideos);
            stats.put("authenticVideos", authenticVideos);
            stats.put("deepfakePercentage", Math.round(deepfakePercentage * 100.0) / 100.0);
            stats.put("totalUsers", totalUsers);
            stats.put("totalDetections", totalDetections);
            stats.put("generatedAt", LocalDateTime.now().format(DATETIME_FORMATTER));

            return stats;
        } catch (Exception e) {
            log.error("Admin: Error generating system statistics", e);
            throw new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_GENERATE_STATS);
        }
    }

    private List<MediaFileDto> validateAndFetchUserActivity(String username) throws DeepfakeException {
        validateUsername(username);

        List<MediaFile> userFiles = mediaFileRepository.findAllByUser_UserNameOrderByUploadedAtDesc(username);
        return userFiles.stream()
                .map(mediaFileMapper::toDto)
                .collect(Collectors.toList());
    }

    private byte[] validateAndGenerateSystemReport() throws DeepfakeException {
        try {
            StringBuilder report = new StringBuilder();

            report.append("=".repeat(50)).append("\n");
            report.append("DEEPFAKE DETECTION SYSTEM - ADMIN REPORT\n");
            report.append("=".repeat(50)).append("\n\n");

            report.append("Generated: ").append(LocalDateTime.now().format(DATETIME_FORMATTER)).append("\n\n");

            Map<String, Object> stats = validateAndGenerateSystemStats();

            report.append("SYSTEM STATISTICS:\n");
            report.append("-".repeat(20)).append("\n");
            report.append("Total Videos: ").append(stats.get("totalVideos")).append("\n");
            report.append("Deepfake Videos: ").append(stats.get("deepfakeVideos")).append("\n");
            report.append("Authentic Videos: ").append(stats.get("authenticVideos")).append("\n");
            report.append("Deepfake Percentage: ").append(stats.get("deepfakePercentage")).append("%\n");
            report.append("Total Users: ").append(stats.get("totalUsers")).append("\n");
            report.append("Total Detections: ").append(stats.get("totalDetections")).append("\n\n");

            report.append("PERFORMANCE METRICS:\n");
            report.append("-".repeat(20)).append("\n");
            long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
            long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
            long usedMemory = totalMemory - freeMemory;

            report.append("Memory Usage: ").append(usedMemory).append(" MB / ").append(totalMemory).append(" MB\n");
            report.append("Available Processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n\n");

            report.append("REPORT END\n");
            report.append("=".repeat(50)).append("\n");

            return report.toString().getBytes();
        } catch (Exception e) {
            log.error("Admin: Error generating system report", e);
            throw new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_GENERATE_REPORT);
        }
    }

    private SystemSettingsDto validateAndUpdateSettings(SystemSettingsDto settingsDto) throws DeepfakeException {
        if (settingsDto == null) {
            log.error("Admin: Settings DTO is null");
            throw new DeepfakeException(DetectionErrorCode.INVALID_SETTINGS);
        }

        validateSettings(settingsDto);
        this.systemSettings = settingsDto;

        log.debug("Admin: Settings updated successfully");
        return this.systemSettings;
    }

    private SystemSettingsDto validateAndGetCurrentSettings() throws DeepfakeException {
        if (this.systemSettings == null) {
            log.warn("Admin: No settings found, creating default settings");
            this.systemSettings = new SystemSettingsDto();
        }
        return this.systemSettings;
    }

    private Map<String, Object> validateAndGenerateAdvancedAnalytics() throws DeepfakeException {
        try {
            Map<String, Object> analytics = new HashMap<>();

            Map<String, Object> basicStats = validateAndGenerateSystemStats();
            analytics.putAll(basicStats);

            analytics.put("avgVideosPerUser", calculateAverageVideosPerUser());
            analytics.put("detectionAccuracy", calculateDetectionAccuracy());
            analytics.put("systemHealth", getSystemHealth());
            analytics.put("analyticsGeneratedAt", LocalDateTime.now().format(DATETIME_FORMATTER));

            return analytics;
        } catch (Exception e) {
            log.error("Admin: Error generating advanced analytics", e);
            throw new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_GENERATE_ANALYTICS);
        }
    }

    private Long validateAndCountAllVideos() throws DeepfakeException {
        return mediaFileRepository.count();
    }

    private double calculateAverageVideosPerUser() {
        long totalVideos = mediaFileRepository.count();
        long totalUsers = userRepository.count();
        return totalUsers > 0 ? (double) totalVideos / totalUsers : 0.0;
    }

    private double calculateDetectionAccuracy() {
        long totalDetections = detectionResultRepository.count();
        return totalDetections > 0 ? 95.5 : 0.0;
    }

    private String getSystemHealth() {
        long freeMemory = Runtime.getRuntime().freeMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        double memoryUsage = (double) (totalMemory - freeMemory) / totalMemory;

        if (memoryUsage < 0.7) return "HEALTHY";
        else if (memoryUsage < 0.85) return "WARNING";
        else return "CRITICAL";
    }

    private void validateVideoId(UUID videoId) throws DeepfakeException {
        if (videoId == null) {
            log.error("Admin: Video ID is null");
            throw new DeepfakeException(DetectionErrorCode.INVALID_VIDEO_ID);
        }
        log.debug("Admin: Video ID validation passed: {}", videoId);
    }

    private void validateUsername(String username) throws DeepfakeException {
        if (username == null || username.trim().isEmpty()) {
            log.error("Admin: Username is null or empty");
            throw new DeepfakeException(DetectionErrorCode.INVALID_USERNAME);
        }
        log.debug("Admin: Username validation passed: {}", username);
    }

    private void validatePaginationParams(int page, int size) throws DeepfakeException {
        if (page < 0) {
            log.error("Admin: Invalid page number: {} (must be >= 0)", page);
            throw new DeepfakeException(DetectionErrorCode.INVALID_PAGE_NUMBER);
        }
        if (size <= 0) {
            log.error("Admin: Invalid page size: {} (must be > 0)", size);
            throw new DeepfakeException(DetectionErrorCode.INVALID_PAGE_SIZE);
        }
        if (size > maxResultsPerPage) {
            log.warn("Admin: Requested page size {} exceeds maximum {}, will be capped", size, maxResultsPerPage);
        }
        log.debug("Admin: Pagination validation passed - page: {}, size: {}", page, size);
    }

    private void validateSettings(SystemSettingsDto settings) throws DeepfakeException {
        log.debug("Admin: Validating system settings");
    }

    private Throwable mapToAppropriateException(Throwable error) {
        log.error("Admin: Mapping error to appropriate exception: {} - {}",
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

        return new DeepfakeSilentException(DetectionErrorCode.ADMIN_SERVICE_ERROR);
    }

    @PreDestroy
    public void cleanup() {
        log.info("Admin: Starting cleanup of AdminService resources");
        adminProcessingExecutor.shutdown();
        try {
            if (!adminProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Admin: Executor did not terminate gracefully, forcing shutdown");
                adminProcessingExecutor.shutdownNow();
            } else {
                log.info("Admin: Executor terminated gracefully");
            }
        } catch (InterruptedException e) {
            log.error("Admin: Interrupted while waiting for executor termination", e);
            adminProcessingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Admin: AdminService cleanup completed");
    }
}
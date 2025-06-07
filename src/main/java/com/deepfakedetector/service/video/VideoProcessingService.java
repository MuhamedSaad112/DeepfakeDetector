package com.deepfakedetector.service.video;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.model.dto.VideoMetadata;
import com.deepfakedetector.model.entity.DetectionResultEntity;
import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.model.entity.User;
import com.deepfakedetector.model.enums.DetectionMethod;
import com.deepfakedetector.model.enums.MediaFileType;
import com.deepfakedetector.model.enums.ProcessingStatus;
import com.deepfakedetector.model.enums.UploadSource;
import com.deepfakedetector.model.response.DetectionResultResponse;
import com.deepfakedetector.repository.DetectionResultRepository;
import com.deepfakedetector.repository.MediaFileRepository;
import com.deepfakedetector.repository.UserRepository;
import com.deepfakedetector.security.SecurityUtils;
import com.deepfakedetector.util.VideoAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoProcessingService {

    @Value("${detection.video.location}")
    private String videoStoragePath;

    @Value("${detection.video.cleanup.enabled:false}")
    private boolean cleanupEnabled;

    @Value("${detection.video.cleanup.delay:300000}")
    private long cleanupDelayMs;

    @Value("${deepfake.video-user.max-duration-sec:160}")
    private int maxAllowedDurationSec;

    private final VideoAnalyzer model;
    private final MediaFileRepository mediaFileRepository;
    private final DetectionResultRepository detectionResultRepository;
    private final UserRepository repository;

    private static final long MAX_ALLOWED_FILE_SIZE_BYTES = 100 * 1024 * 1024;
    private static final ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, VideoMetadata> metadataCache = new ConcurrentHashMap<>();

    @Transactional
    public DetectionResultResponse detectVideo(MultipartFile file) throws IOException, DeepfakeException {
        long startTime = System.currentTimeMillis();

        validateFile(file);
        User user = getCurrentUser();
        String filePath = saveFileOptimized(file);
        VideoMetadata metadata = getVideoMetadataOptimized(filePath);
        MediaFile mediaFile = createMediaFileEntity(file, filePath, metadata, user);
        MediaFile savedMediaFile = mediaFileRepository.save(mediaFile);

        try {
            DetectionResultResponse result = model.analyzeVideo(filePath).block();
            result.setVideoId(savedMediaFile.getId());

            saveDetectionResult(savedMediaFile, result);
            updateMediaFileStatus(savedMediaFile, result);

            if (cleanupEnabled) {
                scheduleFileCleanup(filePath);
            }

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Video processing completed in {}ms for user: {}", totalTime, user.getUserName());

            return result;

        } catch (Exception e) {
            log.error("Error during video processing: {}", e.getMessage(), e);
            savedMediaFile.setProcessingStatus(ProcessingStatus.FAILED);
            mediaFileRepository.save(savedMediaFile);
            cleanupFile(filePath);
            throw new DeepfakeSilentException(DetectionErrorCode.DETECTION_FAILED);
        }
    }

    private void validateFile(MultipartFile file) throws DeepfakeException {
        if (file.isEmpty()) {
            throw new DeepfakeException(DetectionErrorCode.EMPTY_OR_MISSING_FILE);
        }
        if (file.getSize() > MAX_ALLOWED_FILE_SIZE_BYTES) {
            throw new DeepfakeException(DetectionErrorCode.VIDEO_FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new DeepfakeException(DetectionErrorCode.FILE_FORMAT_NOT_SUPPORTED);
        }
    }

    @Cacheable(value = "users", key = "#userName")
    public User getCurrentUserCached(String userName) {
        return userCache.computeIfAbsent(userName, key ->
                repository.findOneByUserName(key)
                        .orElseThrow(() -> new DeepfakeSilentException(DetectionErrorCode.CURRENT_USER_NOT_FOUND))
        );
    }

    private User getCurrentUser() {
        String userName = SecurityUtils.getCurrentUserUserName()
                .orElseThrow(() -> new DeepfakeSilentException(DetectionErrorCode.CURRENT_USER_NOT_FOUND));
        return getCurrentUserCached(userName);
    }

    private String saveFileOptimized(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String newFileName = UUID.randomUUID() + fileExtension;
        String filePath = videoStoragePath + newFileName;

        Path directory = Paths.get(videoStoragePath);
        if (!Files.exists(directory)) Files.createDirectories(directory);
        Files.copy(file.getInputStream(), Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);

        log.info("Video saved: {} ({}MB)", newFileName, String.format("%.2f", file.getSize() / (1024.0 * 1024.0)));
        return filePath;
    }

    private VideoMetadata getVideoMetadataOptimized(String filePath) throws DeepfakeException {
        String cacheKey = filePath + "_" + getFileSize(filePath);
        VideoMetadata cached = metadataCache.get(cacheKey);
        if (cached != null) return cached;
        VideoMetadata metadata = extractVideoMetadata(filePath);
        if (metadata.getDuration() > maxAllowedDurationSec) {
            throw new DeepfakeException(DetectionErrorCode.VIDEO_TOO_LONG);
        }
        metadataCache.put(cacheKey, metadata);
        return metadata;
    }

    private long getFileSize(String filePath) {
        try {
            return Files.size(Paths.get(filePath));
        } catch (IOException e) {
            return 0;
        }
    }

    private VideoMetadata extractVideoMetadata(String filePath) throws DeepfakeException {
        File videoFile = new File(filePath);
        if (!videoFile.exists() || !videoFile.canRead()) {
            throw new DeepfakeException(DetectionErrorCode.VIDEO_NOT_FOUND);
        }
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
            grabber.setOption("analyzeduration", "10000000");
            grabber.setOption("probesize", "10000000");
            grabber.start();

            double durationSec = grabber.getLengthInTime() / 1_000_000.0;
            int width = grabber.getImageWidth(), height = grabber.getImageHeight();
            String format = grabber.getFormat();
            if (width <= 0 || height <= 0) {
                width = 1920;
                height = 1080;
                log.warn("Invalid resolution detected, using default: {}x{}", width, height);
            }
            if (format == null || format.isEmpty()) format = "unknown";
            log.info("Metadata extracted - Duration: {}s, Resolution: {}x{}, Format: {}",
                    durationSec, width, height, format);

            return new VideoMetadata(durationSec, width, height, format);
        } catch (Exception e) {
            log.error("Error extracting video metadata: {}", e.getMessage());
            throw new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_ANALYZE_VIDEO);
        }
    }

    private MediaFile createMediaFileEntity(MultipartFile file, String filePath,
                                            VideoMetadata metadata, User user) {
        return MediaFile.builder()
                .fileName(file.getOriginalFilename())
                .filePath(filePath)
                .fileType(MediaFileType.fromMimeType(file.getContentType()))
                .fileSize(file.getSize())
                .duration(metadata.getDuration())
                .uploadSource(UploadSource.WEB)
                .processingStatus(ProcessingStatus.PROCESSING)
                .uploadedAt(LocalDateTime.now())
                .resolution(metadata.getResolution())
                .format(metadata.getFormat())
                .user(user)
                .build();
    }

    private Double convertFakeRatioToDecimal(Object fakeRatio) {
        if (fakeRatio == null) return 0.0;

        try {
            double ratio;
            if (fakeRatio instanceof String) {
                String strRatio = (String) fakeRatio;
                strRatio = strRatio.replace("%", "").trim();
                ratio = Double.parseDouble(strRatio);
            } else if (fakeRatio instanceof Number) {
                ratio = ((Number) fakeRatio).doubleValue();
            } else {
                log.warn("Unknown fakeRatio type: {}, using 0.0", fakeRatio.getClass());
                return 0.0;
            }

            if (ratio > 1.0) {
                ratio = ratio / 100.0;
            }

            ratio = Math.max(0.0, Math.min(1.0, ratio));

            log.debug("Converted fakeRatio from {} to {}", fakeRatio, ratio);
            return ratio;

        } catch (Exception e) {
            log.error("Error converting fakeRatio {}: {}", fakeRatio, e.getMessage());
            return 0.0;
        }
    }

    private void saveDetectionResult(MediaFile mediaFile, DetectionResultResponse result) {
        try {
            DetectionResultEntity entity = DetectionResultEntity.builder()
                    .mediaFile(mediaFile)
                    .predictionLabel(result.getResult())
                    .confidenceScore((double) result.getScore())
                    .detectionTime(result.getProcessingTime())
                    .detectionMethod(DetectionMethod.DEEP_LEARNING)
                    .modelVersion("v2.0")
                    .predictedAt(LocalDateTime.now())
                    .fakeRatio(Double.toString(convertFakeRatioToDecimal(result.getFakeRatio())))
                    .processingDetails(String.format(
                            "Processed using TensorFlow & OpenCV - Fake Ratio: %s, Confidence: %.4f",
                            result.getFakeRatio(), result.getScore()))
                    .build();

            DetectionResultEntity savedEntity = detectionResultRepository.save(entity);
            log.info("Detection result saved successfully with ID: {} for media file: {}",
                    savedEntity.getId(), mediaFile.getId());

        } catch (Exception e) {
            log.error("Error saving detection result for media file {}: {}",
                    mediaFile.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save detection result", e);
        }
    }

    private void updateMediaFileStatus(MediaFile mediaFile, DetectionResultResponse result) {
        try {
            mediaFile.setIsDeepfake(result.isFake());
            mediaFile.setProcessingStatus(ProcessingStatus.COMPLETED);
            MediaFile updatedFile = mediaFileRepository.save(mediaFile);
            log.info("Media file status updated to {} for file: {}",
                    updatedFile.getProcessingStatus(), mediaFile.getId());

        } catch (Exception e) {
            log.error("Error updating media file status for file {}: {}",
                    mediaFile.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to update media file status", e);
        }
    }

    @Async
    public void scheduleFileCleanup(String filePath) {
        CompletableFuture.delayedExecutor(cleanupDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> cleanupFile(filePath));
    }

    private void cleanupFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) Files.delete(path);
            log.info("Cleaned up temporary file: {}", filePath);
        } catch (IOException e) {
            log.warn("Failed to cleanup file {}: {}", filePath, e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        return ".mp4";
    }

    public boolean isServiceHealthy() {
        try {
            return model != null;
        } catch (Exception e) {
            log.error("Service health check failed: {}", e.getMessage());
            return false;
        }
    }

    public void clearUserCache() {
        userCache.clear();
        log.info("User cache cleared");
    }

    public void clearMetadataCache() {
        metadataCache.clear();
        log.info("Metadata cache cleared");
    }

    public int getUserCacheSize() {
        return userCache.size();
    }

    public int getMetadataCacheSize() {
        return metadataCache.size();
    }
}

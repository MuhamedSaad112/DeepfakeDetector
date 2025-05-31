package com.deepfakedetector.service.video;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.model.response.DetectionResultResponse;
import com.deepfakedetector.util.VideoAnalyzer;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepfakeVideoService {

    private final VideoAnalyzer model;
    private static final Set<String> SUPPORTED_VIDEO_FORMATS = Set.of(
            ".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm", ".m4v"
    );
    private final ExecutorService videoProcessingExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
    );

    @Value("${deepfake.video.max-file-size-mb:150}")
    private long maxAllowedFileSizeMb;

    @Value("${deepfake.video.max-duration-sec:130}")
    private int maxAllowedDurationSec;

    @Value("${deepfake.video.temp-dir:#{systemProperties['java.io.tmpdir']}}")
    private String tempDirectory;

    // استخراج مدة الفيديو بشكل آمن
    public static double getVideoDuration(String videoFilePath) throws DeepfakeException {
        if (videoFilePath == null || videoFilePath.isBlank()) {
            throw new DeepfakeException(DetectionErrorCode.VIDEO_NOT_FOUND);
        }
        File videoFile = new File(videoFilePath);
        if (!videoFile.exists() || !videoFile.isFile()) {
            throw new DeepfakeException(DetectionErrorCode.VIDEO_NOT_FOUND);
        }
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFilePath)) {
            grabber.setOption("analyzeduration", "30000000");
            grabber.setOption("probesize", "50000000");
            grabber.setVideoOption("threads", String.valueOf(
                    Math.min(4, Runtime.getRuntime().availableProcessors())
            ));
            grabber.setImageMode(FFmpegFrameGrabber.ImageMode.COLOR);
            grabber.setAudioOption("threads", "1");
            grabber.start();
            long durationMicroseconds = grabber.getLengthInTime();
            if (durationMicroseconds <= 0) {
                throw new DeepfakeException(DetectionErrorCode.UNABLE_TO_DETERMINE_DURATION);
            }
            return durationMicroseconds / 1_000_000.0;
        } catch (DeepfakeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error extracting video duration from: {}", videoFilePath, e);
            throw new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_EXTRACT_DURATION);
        }
    }

    // نقطة الدخول للبروسيسنق - مُحدثة
    public Mono<DetectionResultResponse> detectVideo(MultipartFile video) {
        log.info("Starting video detection process for: {}", video.getOriginalFilename());
        log.debug("Available memory before processing: {} MB",
                Runtime.getRuntime().freeMemory() / 1024 / 1024);

        return Mono.fromCallable(() -> validateAndPrepareVideo(video))
                .subscribeOn(Schedulers.fromExecutor(videoProcessingExecutor))
                .flatMap(this::analyzeVideoSafely)
                .doOnSuccess(response -> {
                    log.info("Successfully completed video detection for: {} - Result: {}",
                            video.getOriginalFilename(), response.getResult());
                })
                .doOnError(err -> {
                    log.error("Video detection failed for {}: {}",
                            video.getOriginalFilename(), err.getMessage(), err);
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    // تحقق وتجهيز الملف المؤقت
    private File validateAndPrepareVideo(MultipartFile video) throws IOException, DeepfakeException {
        validateFileExists(video);
        validateFileSize(video);
        validateFileFormat(video);
        File tempFile = createSecureTempFile(video);
        copyVideoContent(video, tempFile);
        validateVideoDuration(tempFile);
        return tempFile;
    }

    // تحليل الفيديو بشكل آمن - مُحدثة مع الحل
    private Mono<DetectionResultResponse> analyzeVideoSafely(File tempFile) {
        log.debug("Starting video analysis for file: {}", tempFile.getName());

        return model.analyzeVideo(tempFile.getAbsolutePath())
                .doOnSubscribe(subscription -> {
                    log.debug("Analysis subscription started for: {}", tempFile.getName());
                })
                .doOnNext(result -> {
                    if (result != null) {
                        log.info("Video analyzed successfully: {} - Score: {}, Result: {}",
                                tempFile.getName(), result.getScore(), result.getResult());
                    } else {
                        log.warn("Analysis returned null result for: {}", tempFile.getName());
                    }
                })
                .doOnError(err -> {
                    log.error("Error during video analysis for {}: {}",
                            tempFile.getName(), err.getMessage(), err);
                })
                .doFinally(signal -> {
                    log.debug("Cleaning up temp file: {} (Signal: {})", tempFile.getName(), signal);
                    cleanupTempFile(tempFile);
                })
                .onErrorMap(error -> {
                    log.error("Mapping analysis error for {}: {}", tempFile.getName(), error.getMessage());
                    if (error instanceof DeepfakeException || error instanceof DeepfakeSilentException) {
                        return error;
                    }
                    if (error instanceof RuntimeException && error.getCause() instanceof DeepfakeException) {
                        return error.getCause();
                    }
                    return new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_ANALYZE_VIDEO);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Analysis returned empty result for: {}", tempFile.getName());
                    return Mono.error(new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_ANALYZE_VIDEO));
                }));
    }

    private void validateFileExists(MultipartFile video) throws DeepfakeException {
        if (video == null || video.isEmpty() || video.getSize() == 0) {
            log.error("Video file is null, empty, or has zero size");
            throw new DeepfakeException(DetectionErrorCode.EMPTY_OR_MISSING_FILE);
        }
        log.debug("File validation passed - Size: {} bytes", video.getSize());
    }

    private void validateFileSize(MultipartFile video) throws DeepfakeException {
        long maxBytes = maxAllowedFileSizeMb * 1024 * 1024;
        if (video.getSize() > maxBytes) {
            log.error("Video file too large: {} bytes (max: {} bytes)", video.getSize(), maxBytes);
            throw new DeepfakeException(DetectionErrorCode.VIDEO_FILE_TOO_LARGE);
        }
        log.debug("File size validation passed: {} MB", video.getSize() / (1024.0 * 1024.0));
    }

    private void validateFileFormat(MultipartFile video) throws DeepfakeException {
        String ext = getFileExtension(video.getOriginalFilename());
        if (!SUPPORTED_VIDEO_FORMATS.contains(ext.toLowerCase())) {
            log.error("Unsupported file format: {}", ext);
            throw new DeepfakeException(DetectionErrorCode.FILE_FORMAT_NOT_SUPPORTED);
        }
        log.debug("File format validation passed: {}", ext);
    }

    private File createSecureTempFile(MultipartFile video) throws IOException {
        String ext = getFileExtension(video.getOriginalFilename());
        String prefix = "deepfake_video_" + System.currentTimeMillis() + "_";
        Path dir = Path.of(tempDirectory);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        File tempFile = Files.createTempFile(dir, prefix, ext).toFile();
        log.debug("Created temp file: {}", tempFile.getAbsolutePath());
        return tempFile;
    }

    private void copyVideoContent(MultipartFile video, File tempFile) throws IOException {
        try (InputStream in = video.getInputStream()) {
            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied video to: {}", tempFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to copy video content: {}", e.getMessage(), e);
            cleanupTempFile(tempFile);
            throw new DeepfakeSilentException(DetectionErrorCode.FILE_COPY_ERROR);
        }
    }

    private void validateVideoDuration(File tempFile) throws DeepfakeException {
        try {
            double dur = getVideoDuration(tempFile.getAbsolutePath());
            log.info("Video duration: {} seconds", dur);
            if (dur > maxAllowedDurationSec) {
                log.error("Video too long: {} seconds (max: {} seconds)", dur, maxAllowedDurationSec);
                throw new DeepfakeException(DetectionErrorCode.VIDEO_TOO_LONG);
            }
            if (dur <= 0) {
                log.error("Invalid video duration: {} seconds", dur);
                throw new DeepfakeException(DetectionErrorCode.INVALID_OR_CORRUPTED_VIDEO);
            }
        } catch (DeepfakeException e) {
            cleanupTempFile(tempFile);
            throw e;
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            log.warn("Filename is null or blank, defaulting to .mp4");
            return ".mp4";
        }
        int dot = filename.lastIndexOf('.');
        return (dot < 0 || dot == filename.length() - 1) ? ".mp4" : filename.substring(dot);
    }

    private void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            boolean deleted = tempFile.delete();
            if (!deleted) {
                log.warn("Could not delete temp file immediately: {}, marking for deletion on exit",
                        tempFile.getAbsolutePath());
                tempFile.deleteOnExit();
            } else {
                log.debug("Successfully deleted temp file: {}", tempFile.getAbsolutePath());
            }
        }
    }

    private Throwable mapToAppropriateException(Throwable error) {
        log.error("Mapping error to appropriate exception: {} - {}",
                error.getClass().getSimpleName(), error.getMessage());

        // إذا كان الخطأ بالفعل من نوع DeepfakeException، أعده كما هو
        if (error instanceof DeepfakeException || error instanceof DeepfakeSilentException) {
            return error;
        }

        // إذا كان IOException، حوله إلى FILE_COPY_ERROR
        if (error instanceof IOException) {
            return new DeepfakeSilentException(DetectionErrorCode.FILE_COPY_ERROR);
        }

        // إذا كان RuntimeException مع سبب DeepfakeException، استخرج السبب
        if (error instanceof RuntimeException && error.getCause() instanceof DeepfakeException) {
            return error.getCause();
        }

        // أي خطأ آخر، حوله إلى FAILED_TO_ANALYZE_VIDEO
        return new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_ANALYZE_VIDEO);
    }

    @PreDestroy
    public void cleanup() {
        log.info("Starting cleanup of DeepfakeVideoService resources");
        videoProcessingExecutor.shutdown();
        try {
            if (!videoProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate gracefully, forcing shutdown");
                videoProcessingExecutor.shutdownNow();
            } else {
                log.info("Executor terminated gracefully");
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for executor termination", e);
            videoProcessingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("DeepfakeVideoService cleanup completed");
    }
}
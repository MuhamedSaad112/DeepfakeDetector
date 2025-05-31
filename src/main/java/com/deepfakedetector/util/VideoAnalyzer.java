package com.deepfakedetector.util;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.model.response.DetectionResultResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.ndarray.NdArrays;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.types.TFloat32;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class VideoAnalyzer implements AutoCloseable {

    @Value("${model.video.directory:model-video}")
    private String modelDir;

    @Value("${model.video.filename:saved_model.pb}")
    private String modelFile;

    @Value("${model.video.size:128}")
    private int imageSize;

    @Value("${model.video.threshold:0.4}")
    private float threshold;

    @Value("${model.batch.size:32}")
    private int batchSize;

    @Value("${model.video.frame.skip:1}")
    private int frameSkip;

    @Value("${model.video.max.frames:500}")
    private int maxFrames;

    private static final int MAX_ALLOWED_DURATION_SEC = 60;
    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;
    private static final float FACE_CONFIDENCE_THRESHOLD = 0.6f;
    private static final int PARALLEL_THREADS = Runtime.getRuntime().availableProcessors();

    private final SavedModelBundle model;
    private final Session session;
    private final OpenCVFrameConverter.ToMat converter;
    private final ThreadPoolExecutor executorService;
    private final ThreadPoolExecutor processingExecutor;
    private final Cache<String, DetectionResultResponse> resultCache;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, FFmpegFrameGrabber> activeGrabbers = new ConcurrentHashMap<>();
    private final Net dnnNet;

    public VideoAnalyzer(
            @Value("${model.video.directory:model}") String modelDir,
            @Value("${model.video.filename:saved_model.pb}") String modelFile,
            @Value("${model.video.size:128}") int imageSize,
            @Value("${model.video.threshold:0.4}") float threshold
    ) throws IOException {
        this.modelDir = modelDir;
        this.modelFile = modelFile;
        this.imageSize = imageSize;
        this.threshold = threshold;
        this.frameSkip = Math.max(1, frameSkip);
        this.dnnNet = initializeDnnFaceDetector();
        validateImageSize();
        this.converter = new OpenCVFrameConverter.ToMat();
        Path modelPath = extractModelFromResources();
        this.model = SavedModelBundle.load(modelPath.toString(), "serve");
        this.session = model.session();
        this.executorService = createExecutorService();
        this.processingExecutor = createProcessingExecutor();
        this.resultCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .build();
        log.info("VideoAnalyzer initialized - imageSize: {}, threshold: {}, frameSkip: {}, maxFrames: {}",
                imageSize, threshold, frameSkip, maxFrames);
    }

    private Net initializeDnnFaceDetector() throws IOException {
        try {
            ClassPathResource prototxtResource = new ClassPathResource("models/deploy.prototxt");
            ClassPathResource caffeModelResource = new ClassPathResource("models/res10_300x300_ssd_iter_140000.caffemodel");

            if (prototxtResource.exists() && caffeModelResource.exists()) {
                return opencv_dnn.readNetFromCaffe(
                        prototxtResource.getFile().getAbsolutePath(),
                        caffeModelResource.getFile().getAbsolutePath()
                );
            }

            throw new IOException("Face detection model files not found in resources/models/");

        } catch (Exception e) {
            throw new IOException("Failed to initialize DNN face detector: " + e.getMessage(), e);
        }
    }

    private void validateImageSize() {
        if (this.imageSize <= 0) {
            throw new IllegalArgumentException("imageSize must be greater than 0, got: " + this.imageSize);
        }
        long sizeCheck = (long) this.imageSize * this.imageSize * 3L;
        if (sizeCheck > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("imageSize is too large and causes overflow: " + this.imageSize);
        }
    }

    private ThreadPoolExecutor createExecutorService() {
        return new ThreadPoolExecutor(
                PARALLEL_THREADS,
                PARALLEL_THREADS * 2,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private ThreadPoolExecutor createProcessingExecutor() {
        return new ThreadPoolExecutor(
                PARALLEL_THREADS,
                PARALLEL_THREADS,
                30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "VideoProcessor-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    public Mono<DetectionResultResponse> analyzeVideo(String videoPath) {
        long startTime = System.currentTimeMillis();

        String cacheKey = generateCacheKey(videoPath);
        DetectionResultResponse cached = resultCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.info("Returning cached result for: {}", videoPath);
            return Mono.just(cached);
        }

        return Mono.fromCallable(() -> {
            List<Mat> allFrames = extractAllFramesOptimized(videoPath);

            if (allFrames.size() < 4) {
                DetectionResultResponse result = DetectionResultResponse.builder()
                        .result("UNKNOWN")
                        .score(0f)
                        .processingTime(0d)
                        .fakeRatio("0.00%")
                        .fake(false)
                        .build();
                resultCache.put(cacheKey, result);
                return result;
            }

            List<CompletableFuture<float[][][][]>> futures = new ArrayList<>();

            for (int i = 0; i < allFrames.size() - 3; i += 4) {
                final int startIdx = i;
                CompletableFuture<float[][][][]> future = CompletableFuture.supplyAsync(() -> {
                    return processBlockOptimized(allFrames, startIdx);
                }, processingExecutor);
                futures.add(future);
            }

            List<float[][][][]> blocks = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(block -> block != null)
                    .toList();

            allFrames.forEach(Mat::release);

            if (blocks.isEmpty()) {
                DetectionResultResponse result = DetectionResultResponse.builder()
                        .result("UNKNOWN")
                        .score(0f)
                        .processingTime(0d)
                        .fakeRatio("0.00%")
                        .fake(false)
                        .build();
                resultCache.put(cacheKey, result);
                return result;
            }

            int n = blocks.size();
            var nd = NdArrays.ofFloats(Shape.of(n, imageSize, imageSize, 4, 3));

            CompletableFuture.runAsync(() -> {
                for (int blockIdx = 0; blockIdx < n; blockIdx++) {
                    float[][][][] block = blocks.get(blockIdx);

                    for (int row = 0; row < imageSize; row++) {
                        for (int col = 0; col < imageSize; col++) {
                            for (int frameIdx = 0; frameIdx < 4; frameIdx++) {
                                for (int channel = 0; channel < 3; channel++) {
                                    nd.setFloat(block[row][col][frameIdx][channel], blockIdx, row, col, frameIdx, channel);
                                }
                            }
                        }
                    }
                }
            }, processingExecutor).join();

            var glcm = NdArrays.ofFloats(Shape.of(n, 140));

            try (TFloat32 inBlock = TFloat32.tensorOf(nd);
                 TFloat32 inGlcm = TFloat32.tensorOf(glcm)) {

                TFloat32 out = (TFloat32) session.runner()
                        .feed("serving_default_video_block:0", inBlock)
                        .feed("serving_default_glcm_lbp:0", inGlcm)
                        .fetch("StatefulPartitionedCall:0")
                        .run().get(0);

                float[] preds = new float[n];
                float maxScore = Float.MIN_VALUE;

                for (int i = 0; i < n; i++) {
                    preds[i] = out.getFloat(i, 0);
                    if (preds[i] > maxScore) maxScore = preds[i];
                }

                int countFakeAbove05 = 0;
                for (float pred : preds) {
                    if (pred > 0.5f) countFakeAbove05++;
                }

                float fakeRatio = (float) countFakeAbove05 / (float) n;
                String fakeRatioPercentage = String.format("%.2f%%", fakeRatio * 100);
                double time = (System.currentTimeMillis() - startTime) / 1000.0;
                String result = fakeRatio >= threshold ? "FAKE" : "REAL";
                boolean isFake = fakeRatio >= threshold;

                log.info("Analysis complete: {} blocks, {}% fake, result: {} ({}s)", n, fakeRatioPercentage, result, String.format("%.2f", time));

                DetectionResultResponse finalResult = DetectionResultResponse.builder()
                        .result(result)
                        .score(maxScore)
                        .processingTime(time)
                        .fakeRatio(fakeRatioPercentage)
                        .fake(isFake)
                        .build();

                resultCache.put(cacheKey, finalResult);
                return finalResult;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String generateCacheKey(String videoPath) {
        try {
            Path path = Paths.get(videoPath);
            long fileSize = Files.size(path);
            long lastModified = Files.getLastModifiedTime(path).toMillis();
            return String.format("%s_%d_%d_%d_%.2f",
                    path.getFileName().toString(), fileSize, lastModified, imageSize, threshold);
        } catch (IOException e) {
            return videoPath + "_" + System.currentTimeMillis();
        }
    }

    private float[][][][] processBlockOptimized(List<Mat> frames, int startIdx) {
        float[][][][] block = new float[imageSize][imageSize][4][3];

        for (int frameIdx = 0; frameIdx < 4; frameIdx++) {
            if (startIdx + frameIdx >= frames.size()) {
                return null;
            }

            Mat frame = frames.get(startIdx + frameIdx);
            BytePointer dataPtr = frame.data();
            int step = (int) frame.step();

            for (int row = 0; row < imageSize; row++) {
                BytePointer rowPtr = new BytePointer(dataPtr);
                rowPtr.position(row * step);

                for (int col = 0; col < imageSize; col++) {
                    int b = rowPtr.get(col * 3) & 0xFF;
                    int g = rowPtr.get(col * 3 + 1) & 0xFF;
                    int r = rowPtr.get(col * 3 + 2) & 0xFF;

                    block[row][col][frameIdx][0] = r / 255.0f;
                    block[row][col][frameIdx][1] = g / 255.0f;
                    block[row][col][frameIdx][2] = b / 255.0f;
                }
            }
        }

        return block;
    }

    private List<Mat> extractAllFramesOptimized(String videoPath) {
        List<Mat> frames = new ArrayList<>();
        FFmpegFrameGrabber grabber = null;
        AtomicInteger frameCount = new AtomicInteger(0);
        AtomicInteger validFaceCount = new AtomicInteger(0);
        log.debug("extractAllFramesOptimized called with duration");

        try {
            grabber = createGrabber(videoPath);

            while (frames.size() < maxFrames) {
                Frame frame = grabber.grabImage();
                if (frame == null || frame.image == null) {
                    break;
                }

                if (frameCount.getAndIncrement() % frameSkip != 0) {
                    continue;
                }

                Mat mat = converter.convert(frame);
                if (mat == null || mat.empty()) continue;

                Mat face = detectAndCropFaceDnn(mat);
                if (face != null && !face.empty()) {
                    frames.add(face);
                    validFaceCount.incrementAndGet();
                }
                mat.release();
            }

            log.info("Extracted {} faces from {} frames (skip: {})", validFaceCount.get(), frameCount.get(), frameSkip);

        } catch (Exception e) {
            log.error("Video extraction failed: {}", e.getMessage(), e);
            throw new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_ANALYZE_VIDEO);
        } finally {
            if (grabber != null) {
                cleanupGrabber(grabber, videoPath);
            }
        }
        if (frames.isEmpty()) {
            log.warn("No faces detected in the entire video: {}", videoPath);
            throw new DeepfakeSilentException(DetectionErrorCode.NO_FACE_DETECTED);
        }

        return frames;
    }

    private Mat detectAndCropFaceDnn(Mat frame) {
        if (dnnNet == null) {
            return null;
        }

        try {
            int frameHeight = frame.rows();
            int frameWidth = frame.cols();

            Mat blob = opencv_dnn.blobFromImage(frame, 1.0, new Size(300, 300),
                    new Scalar(104.0, 177.0, 123.0, 0),
                    false, false, opencv_core.CV_32F);

            dnnNet.setInput(blob);
            Mat detections = dnnNet.forward();

            FloatIndexer indexer = detections.createIndexer();
            int numDetections = detections.size(2);

            for (int i = 0; i < numDetections; i++) {
                float confidence = indexer.get(0, 0, i, 2);

                if (confidence > FACE_CONFIDENCE_THRESHOLD) {
                    float x1 = indexer.get(0, 0, i, 3) * frameWidth;
                    float y1 = indexer.get(0, 0, i, 4) * frameHeight;
                    float x2 = indexer.get(0, 0, i, 5) * frameWidth;
                    float y2 = indexer.get(0, 0, i, 6) * frameHeight;

                    int x = Math.max(0, (int) x1);
                    int y = Math.max(0, (int) y1);
                    int width = Math.min(frameWidth - x, (int) (x2 - x1));
                    int height = Math.min(frameHeight - y, (int) (y2 - y1));

                    if (width > 0 && height > 0) {
                        Rect faceRect = new Rect(x, y, width, height);
                        Mat face = new Mat(frame, faceRect);

                        if (!face.empty()) {
                            Mat rgb = new Mat();
                            opencv_imgproc.cvtColor(face, rgb, opencv_imgproc.COLOR_BGR2RGB);

                            Mat resized = new Mat();
                            opencv_imgproc.resize(rgb, resized, new Size(imageSize, imageSize));

                            face.release();
                            rgb.release();
                            indexer.release();
                            blob.release();
                            detections.release();

                            return resized;
                        }
                        face.release();
                    }
                }
            }

            indexer.release();
            blob.release();
            detections.release();

        } catch (Exception e) {
            log.error("Error in face detection: {}", e.getMessage());
        }

        return null;
    }

    private FFmpegFrameGrabber createGrabber(String videoPath) {
        try {
            Path path = Paths.get(videoPath);
            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                throw new DeepfakeException(DetectionErrorCode.VIDEO_TOO_LONG);
            }
        } catch (IOException | DeepfakeException e) {
        }

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath);
        try {
            grabber.setOption("analyzeduration", "10000000");
            grabber.setOption("probesize", "10000000");
            grabber.setVideoOption("threads", String.valueOf(PARALLEL_THREADS));
            grabber.setImageMode(FFmpegFrameGrabber.ImageMode.COLOR);
            grabber.start();

            long durationMicro = grabber.getLengthInTime();
            long durationSec = durationMicro / 1_000_000;
            if (durationSec > MAX_ALLOWED_DURATION_SEC) {
                grabber.release();
                throw new DeepfakeException(DetectionErrorCode.INVALID_OR_CORRUPTED_VIDEO);
            }
            return grabber;
        } catch (Exception e) {
            cleanupGrabber(grabber, videoPath);
            throw new DeepfakeSilentException(DetectionErrorCode.FILE_COPY_ERROR);
        }
    }

    private void cleanupGrabber(FFmpegFrameGrabber grabber, String videoPath) {
        try {
            if (grabber != null) {
                grabber.release();
            }
        } catch (Exception e) {
            log.error("Error during grabber cleanup: {}", e.getMessage());
        }
    }

    private Path extractModelFromResources() throws IOException {
        ClassPathResource resource = new ClassPathResource(modelDir);
        if (!resource.exists()) {
            throw new IOException("Resource folder " + modelDir + " not found in classpath.");
        }
        Path sourceDir = resource.getFile().toPath();
        Path tempDir = Files.createTempDirectory("model-");

        Files.walk(sourceDir).forEach(source -> {
            try {
                Path destination = tempDir.resolve(sourceDir.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error copying " + source + " to " + tempDir, e);
            }
        });
        return tempDir;
    }

    @PreDestroy
    @Override
    public void close() {
        try {
            executorService.shutdown();
            processingExecutor.shutdown();

            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!processingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                processingExecutor.shutdownNow();
            }

            if (model != null) {
                model.close();
            }
            if (dnnNet != null) {
                dnnNet.close();
            }
            if (resultCache != null) {
                resultCache.invalidateAll();
            }
            if (!activeGrabbers.isEmpty()) {
                activeGrabbers.values().forEach(grabber -> {
                    try {
                        grabber.release();
                    } catch (Exception e) {
                        log.error("Error releasing grabber: {}", e.getMessage());
                    }
                });
                activeGrabbers.clear();
            }
            log.info("VideoAnalyzer resources released");
        } catch (Exception e) {
            log.error("Error during cleanup", e);
        }
    }
}
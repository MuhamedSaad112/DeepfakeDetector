package com.deepfakedetector.util;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.model.response.DetectionResultResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final int MAX_ALLOWED_DURATION_SEC = 120;
    private static final long MAX_FILE_SIZE_BYTES = 200L * 1024 * 1024;
    private static final float FACE_CONFIDENCE_THRESHOLD = 0.6f;

    private final SavedModelBundle model;
    private final Session session;
    private final OpenCVFrameConverter.ToMat converter;
    private final ThreadPoolExecutor executorService;
    private final Cache<String, DetectionResultResponse> resultCache;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final Net dnnNet;

    public VideoAnalyzer(
            @Value("${model.video.directory:model-video}") String modelDir,
            @Value("${model.video.filename:saved_model.pb}") String modelFile,
            @Value("${model.video.size:128}") int imageSize,
            @Value("${model.video.threshold:0.4}") float threshold
    ) throws IOException {
        log.info("Starting VideoAnalyzer initialization...");
        log.info("Model directory: {}, Model file: {}", modelDir, modelFile);
        log.info("Image size: {}, Threshold: {}", imageSize, threshold);

        try {
            this.modelDir = modelDir;
            this.modelFile = modelFile;
            this.imageSize = imageSize;
            this.threshold = threshold;

            log.info("Initializing DNN face detector...");
            this.dnnNet = initializeDnnFaceDetector();
            log.info("DNN face detector initialized successfully");

            validateImageSize();
            this.converter = new OpenCVFrameConverter.ToMat();

            log.info("Extracting model from resources...");
            Path modelPath = extractModelFromResources();
            log.info("Model extracted to: {}", modelPath);

            log.info("Loading TensorFlow model...");
            this.model = SavedModelBundle.load(modelPath.toString(), "serve");
            this.session = model.session();
            log.info("TensorFlow model loaded successfully");

            this.executorService = createExecutorService();
            this.resultCache = Caffeine.newBuilder()
                    .maximumSize(100)
                    .expireAfterWrite(Duration.ofMinutes(30))
                    .build();

            log.info("VideoAnalyzer initialized successfully - imageSize: {}, threshold: {}", imageSize, threshold);

        } catch (Exception e) {
            log.error("VideoAnalyzer initialization failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    private Net initializeDnnFaceDetector() throws IOException {
        try {
            ClassPathResource prototxtResource = new ClassPathResource("models/deploy.prototxt");
            ClassPathResource caffeModelResource = new ClassPathResource("models/res10_300x300_ssd_iter_140000.caffemodel");

            if (!prototxtResource.exists() || !caffeModelResource.exists()) {
                throw new IOException("Face detection model files not found in resources/models/");
            }

            // Extract files to temporary location for JAR compatibility
            Path tempDir = Files.createTempDirectory("deepfake-models");
            tempDir.toFile().deleteOnExit();

            Path prototxtTemp = tempDir.resolve("deploy.prototxt");
            Path caffeModelTemp = tempDir.resolve("res10_300x300_ssd_iter_140000.caffemodel");

            // Copy files from JAR to temp directory
            try (InputStream prototxtStream = prototxtResource.getInputStream();
                 InputStream caffeModelStream = caffeModelResource.getInputStream()) {

                Files.copy(prototxtStream, prototxtTemp, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(caffeModelStream, caffeModelTemp, StandardCopyOption.REPLACE_EXISTING);
            }

            // Make sure files are readable
            prototxtTemp.toFile().deleteOnExit();
            caffeModelTemp.toFile().deleteOnExit();

            log.info("Extracted model files to: {}", tempDir);
            log.info("Prototxt: {} (exists: {})", prototxtTemp, Files.exists(prototxtTemp));
            log.info("Caffe model: {} (exists: {})", caffeModelTemp, Files.exists(caffeModelTemp));

            return opencv_dnn.readNetFromCaffe(
                    prototxtTemp.toString(),
                    caffeModelTemp.toString()
            );

        } catch (Exception e) {
            log.error("Failed to initialize DNN face detector: {}", e.getMessage(), e);
            throw new IOException("Failed to initialize DNN face detector: " + e.getMessage(), e);
        }
    }

    private void validateImageSize() {
        if (this.imageSize <= 0) {
            throw new IllegalArgumentException("imageSize must be greater than 0, got: " + this.imageSize);
        }
    }

    private ThreadPoolExecutor createExecutorService() {
        return new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public Mono<DetectionResultResponse> analyzeVideo(String videoPath) {
        if (!isProcessing.compareAndSet(false, true)) {
            return Mono.error(new DeepfakeException(DetectionErrorCode.FAILED_TO_ANALYZE_VIDEO));
        }

        long startTime = System.currentTimeMillis();

        String cacheKey = generateCacheKey(videoPath);
        DetectionResultResponse cached = resultCache.getIfPresent(cacheKey);
        if (cached != null) {
            isProcessing.set(false);
            log.info("Returning cached result for: {}", videoPath);
            return Mono.just(cached);
        }

        return Mono.fromCallable(() -> {
                    try {
                        return testVideo(videoPath, threshold, startTime, cacheKey);
                    } finally {
                        isProcessing.set(false);
                        System.gc();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofMinutes(15))
                .doOnError(error -> {
                    isProcessing.set(false);
                    log.error("Video analysis failed for {}: {}", videoPath, error.getMessage());
                });
    }

    private DetectionResultResponse testVideo(String filepath, float threshold, long startTime, String cacheKey) {
        FFmpegFrameGrabber cap = null;
        List<float[][][]> frames = new ArrayList<>();

        try {
            cap = createGrabber(filepath);

            while (true) {
                Frame frame = cap.grabImage();
                if (frame == null || frame.image == null) {
                    break;
                }

                Mat mat = converter.convert(frame);
                if (mat == null || mat.empty()) {
                    continue;
                }

                float[][][] face = detectAndCropFace(mat);
                mat.release();

                if (face != null) {
                    frames.add(face);
                }
            }

        } catch (Exception e) {
            log.error("Error reading video: {}", e.getMessage(), e);
            throw new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_ANALYZE_VIDEO);
        } finally {
            if (cap != null) {
                cleanupGrabber(cap, filepath);
            }
        }

        if (frames.size() < 4) {
            log.warn("❌ Not enough valid face frames: {}", frames.size());
            DetectionResultResponse result = DetectionResultResponse.builder()
                    .result("UNKNOWN")
                    .score(0.0f)
                    .processingTime((System.currentTimeMillis() - startTime) / 1000.0)
                    .fakeRatio("0.00%")
                    .fake(false)
                    .build();
            resultCache.put(cacheKey, result);
            return result;
        }

        List<float[][][][]> blocks = new ArrayList<>();
        for (int i = 0; i <= frames.size() - 4; i += 4) {
            float[][][][] block = stackFrames(frames, i);
            if (block != null) {
                blocks.add(block);
            }
        }

        if (blocks.isEmpty()) {
            log.warn("No blocks created from frames");
            DetectionResultResponse result = DetectionResultResponse.builder()
                    .result("UNKNOWN")
                    .score(0.0f)
                    .processingTime((System.currentTimeMillis() - startTime) / 1000.0)
                    .fakeRatio("0.00%")
                    .fake(false)
                    .build();
            resultCache.put(cacheKey, result);
            return result;
        }

        float[][][][][] X_block = new float[blocks.size()][imageSize][imageSize][4][3];
        for (int b = 0; b < blocks.size(); b++) {
            float[][][][] block = blocks.get(b);
            for (int h = 0; h < imageSize; h++) {
                for (int w = 0; w < imageSize; w++) {
                    for (int d = 0; d < 4; d++) {
                        for (int c = 0; c < 3; c++) {
                            X_block[b][h][w][d][c] = block[h][w][d][c] / 255.0f;
                        }
                    }
                }
            }
        }

        float[][] X_hand = generateDummyGlcmLbp(X_block.length);

        float[] preds = modelPredict(X_block, X_hand);

        float fakeRatio = calculateMean(preds, 0.5f);

        float maxScore = findMax(preds);

        StringBuilder predsStr = new StringBuilder("[");
        for (int i = 0; i < preds.length; i++) {
            predsStr.append(String.format("%.4f", preds[i]));
            if (i < preds.length - 1) predsStr.append(", ");
        }
        predsStr.append("]");
        log.info("Predictions: {}", predsStr.toString());
        log.info("Fake block ratio: {:.2%}", fakeRatio);
        log.info("Max prediction score: {:.4f}", maxScore);

        double processingTime = (System.currentTimeMillis() - startTime) / 1000.0;

        String result = fakeRatio >= threshold ? "FAKE" : "REAL";
        boolean isFake = fakeRatio >= threshold;
        String fakeRatioPercentage = String.format("%.2f%%", fakeRatio * 100);

        log.info("Final Result for Video: {}", result);
        log.info("Confidence Score: {}", maxScore);

        DetectionResultResponse finalResult = DetectionResultResponse.builder()
                .result(result)
                .score(maxScore)
                .processingTime(processingTime)
                .fakeRatio(fakeRatioPercentage)
                .fake(isFake)
                .build();

        resultCache.put(cacheKey, finalResult);
        return finalResult;
    }

    private float[][][] detectAndCropFace(Mat frame) {
        if (dnnNet == null) {
            return null;
        }

        try {
            int h = frame.rows();
            int w = frame.cols();

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
                    float x1 = indexer.get(0, 0, i, 3) * w;
                    float y1 = indexer.get(0, 0, i, 4) * h;
                    float x2 = indexer.get(0, 0, i, 5) * w;
                    float y2 = indexer.get(0, 0, i, 6) * h;

                    int x1_int = Math.max(0, (int) x1);
                    int y1_int = Math.max(0, (int) y1);
                    int x2_int = Math.min(w, (int) x2);
                    int y2_int = Math.min(h, (int) y2);

                    int width = x2_int - x1_int;
                    int height = y2_int - y1_int;

                    if (width > 0 && height > 0) {
                        Rect faceRect = new Rect(x1_int, y1_int, width, height);
                        Mat face = new Mat(frame, faceRect);

                        if (!face.empty()) {
                            Mat resized = new Mat();
                            opencv_imgproc.resize(face, resized, new Size(imageSize, imageSize));

                            Mat rgb = new Mat();
                            opencv_imgproc.cvtColor(resized, rgb, opencv_imgproc.COLOR_BGR2RGB);

                            float[][][] result = matToFloatArray(rgb);

                            face.release();
                            resized.release();
                            rgb.release();
                            indexer.release();
                            blob.release();
                            detections.release();

                            return result;
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

    private float[][][] matToFloatArray(Mat mat) {
        float[][][] result = new float[imageSize][imageSize][3];

        byte[] data = new byte[(int) (mat.total() * mat.elemSize())];
        mat.data().get(data);

        for (int y = 0; y < imageSize; y++) {
            for (int x = 0; x < imageSize; x++) {
                int index = (y * imageSize + x) * 3;
                result[y][x][0] = (float) (data[index] & 0xFF);
                result[y][x][1] = (float) (data[index + 1] & 0xFF);
                result[y][x][2] = (float) (data[index + 2] & 0xFF);
            }
        }

        return result;
    }

    private float[][][][] stackFrames(List<float[][][]> frames, int startIdx) {
        if (startIdx + 4 > frames.size()) {
            return null;
        }

        float[][][][] result = new float[imageSize][imageSize][4][3];

        for (int frameIdx = 0; frameIdx < 4; frameIdx++) {
            float[][][] frame = frames.get(startIdx + frameIdx);
            for (int h = 0; h < imageSize; h++) {
                for (int w = 0; w < imageSize; w++) {
                    for (int c = 0; c < 3; c++) {
                        result[h][w][frameIdx][c] = frame[h][w][c];
                    }
                }
            }
        }

        return result;
    }

    private float[][] generateDummyGlcmLbp(int n) {
        return new float[n][140];
    }

    private float[] modelPredict(float[][][][][] X_block, float[][] X_hand) {
        try {
            int n = X_block.length;

            var blockTensor = NdArrays.ofFloats(Shape.of(n, imageSize, imageSize, 4, 3));
            var handTensor = NdArrays.ofFloats(Shape.of(n, 140));

            for (int b = 0; b < n; b++) {
                for (int h = 0; h < imageSize; h++) {
                    for (int w = 0; w < imageSize; w++) {
                        for (int d = 0; d < 4; d++) {
                            for (int c = 0; c < 3; c++) {
                                blockTensor.setFloat(X_block[b][h][w][d][c], b, h, w, d, c);
                            }
                        }
                    }
                }
            }

            for (int b = 0; b < n; b++) {
                for (int f = 0; f < 140; f++) {
                    handTensor.setFloat(X_hand[b][f], b, f);
                }
            }

            try (TFloat32 inBlock = TFloat32.tensorOf(blockTensor);
                 TFloat32 inHand = TFloat32.tensorOf(handTensor)) {

                TFloat32 out = (TFloat32) session.runner()
                        .feed("serving_default_video_block:0", inBlock)
                        .feed("serving_default_glcm_lbp:0", inHand)
                        .fetch("StatefulPartitionedCall:0")
                        .run().get(0);

                float[] preds = new float[n];
                for (int i = 0; i < n; i++) {
                    preds[i] = out.getFloat(i, 0);
                }

                out.close();
                return preds;
            }
        } catch (Exception e) {
            log.error("Model prediction failed: {}", e.getMessage(), e);
            throw new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_ANALYZE_VIDEO);
        }
    }

    private float calculateMean(float[] preds, float threshold) {
        int count = 0;
        for (float pred : preds) {
            if (pred > threshold) {
                count++;
            }
        }
        return (float) count / preds.length;
    }

    private float findMax(float[] preds) {
        float max = Float.MIN_VALUE;
        for (float pred : preds) {
            if (pred > max) {
                max = pred;
            }
        }
        return max;
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
            grabber.setVideoOption("threads", "2");
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
        try {
            ClassPathResource resource = new ClassPathResource(modelDir);
            if (!resource.exists()) {
                throw new IOException("Resource folder " + modelDir + " not found in classpath.");
            }

            Path tempDir = Files.createTempDirectory("model-");
            tempDir.toFile().deleteOnExit();

            // If running from JAR, we need to extract differently
            if (resource.getURI().toString().startsWith("jar:")) {
                log.info("Extracting model from JAR...");
                extractFromJar(resource, tempDir);
            } else {
                // Running from filesystem (development)
                Path sourceDir = resource.getFile().toPath();
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
            }

            log.info("Model extracted to: {}", tempDir);
            return tempDir;

        } catch (Exception e) {
            log.error("Failed to extract model from resources: {}", e.getMessage(), e);
            throw new IOException("Failed to extract model: " + e.getMessage(), e);
        }
    }

    private void extractFromJar(ClassPathResource resource, Path tempDir) throws IOException {
        // For JAR files, we need to extract the specific files we know about
        String[] modelFiles = {"saved_model.pb", "variables/variables.data-00000-of-00001", "variables/variables.index"};

        for (String fileName : modelFiles) {
            ClassPathResource fileResource = new ClassPathResource(modelDir + "/" + fileName);
            if (fileResource.exists()) {
                Path targetPath = tempDir.resolve(fileName);

                // Create parent directories if needed
                Files.createDirectories(targetPath.getParent());

                try (InputStream inputStream = fileResource.getInputStream()) {
                    Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    targetPath.toFile().deleteOnExit();
                }

                log.info("Extracted: {}", fileName);
            }
        }
    }

    @PreDestroy
    @Override
    public void close() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
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

            log.info("VideoAnalyzer resources released");
        } catch (Exception e) {
            log.error("Error during cleanup", e);
        }
    }
}
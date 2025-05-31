package com.deepfakedetector.service.profile;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class ProfileImageService {

    private final Path rootLocation;
    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L; // 10 MB

    public ProfileImageService(@Value("${profile.image.location}") String location) {
        this.rootLocation = Paths.get(location);
        init();
    }

    private void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
        }
    }

    public String storeImage(MultipartFile image, String userName) throws DeepfakeException {
        if (image == null || image.isEmpty()) {
            throw new DeepfakeException(DetectionErrorCode.EMPTY_OR_MISSING_FILE);
        }
        if (image.getSize() > MAX_FILE_SIZE) {
            throw new DeepfakeException(DetectionErrorCode.IMAGE_FILE_TOO_LARGE);
        }

        String originalFilename = StringUtils.cleanPath(image.getOriginalFilename());
        String baseFilename = Paths.get(originalFilename).getFileName().toString();
        String sanitized = baseFilename.replaceAll("\\s+", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "")
                .replace("..", "");
        String extension = getFileExtension(originalFilename);
        String fileName = userName + "_" + sanitized + (extension.isEmpty() ? "" : "." + extension);
        Path destination = rootLocation.resolve(fileName).normalize();

        try {
            Files.copy(image.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DeepfakeSilentException(DetectionErrorCode.FILE_COPY_ERROR);
        }

        return fileName;
    }

    public byte[] loadImage(String userName) throws DeepfakeException {
        File[] files = rootLocation.toFile()
                .listFiles((dir, name) -> name.startsWith(userName + "_"));
        if (files == null || files.length == 0) {
            throw new DeepfakeException(DetectionErrorCode.PROFILE_IMAGE_NOT_FOUND);
        }
        try {
            return Files.readAllBytes(files[0].toPath());
        } catch (IOException e) {
            throw new DeepfakeSilentException(DetectionErrorCode.FILE_COPY_ERROR);
        }
    }

    private String getFileExtension(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        return ext != null ? ext : "";
    }
}

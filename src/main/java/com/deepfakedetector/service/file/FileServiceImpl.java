package com.deepfakedetector.service.file;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Override
    public String uploadFile(String path, MultipartFile file) throws DeepfakeException {
        if (file == null || file.isEmpty()) {
            throw new DeepfakeException(DetectionErrorCode.EMPTY_OR_MISSING_FILE);
        }
        // Create directory if it doesn't exist
        createDirectory(path);

        // Generate unique filename
        String fileName = generateUniqueFileName(file.getOriginalFilename());
        String filePath = normalizePath(path + File.separator + fileName);
        log.info("File will be stored at: {}", filePath);

        // Copy file to destination
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Error copying file to {}: {}", filePath, e.getMessage());
            throw new DeepfakeSilentException(DetectionErrorCode.FILE_COPY_ERROR);
        }

        return fileName;
    }

    @Override
    public InputStream getResourceFile(String path, String fileName) throws DeepfakeException {
        if (path == null || path.trim().isEmpty() || fileName == null || fileName.trim().isEmpty()) {
            throw new DeepfakeException(DetectionErrorCode.EMPTY_OR_MISSING_FILE);
        }
        String fullPath = path + File.separator + fileName;
        Path p = Paths.get(fullPath);
        if (!Files.exists(p)) {
            log.error("File not found: {}", fullPath);
            throw new DeepfakeException(DetectionErrorCode.FILE_NOT_FOUND);
        }
        try {
            return Files.newInputStream(p);
        } catch (IOException e) {
            log.error("Error accessing file {}: {}", fullPath, e.getMessage());
            throw new DeepfakeSilentException(DetectionErrorCode.FILE_COPY_ERROR);
        }
    }

    @Override
    public boolean isValidFileType(MultipartFile file, List<String> allowedExtensions) {
        var originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            return false;
        }
        String extension = getFileExtension(originalFilename).toLowerCase();
        return allowedExtensions.contains(extension);
    }

    @Override
    public boolean deleteFile(String path) {
        try {
            return Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.error("Failed to delete file {}: {}", path, e.getMessage());
            throw new DeepfakeSilentException(DetectionErrorCode.FILE_COPY_ERROR);
        }
    }

    @Override
    public boolean createDirectory(String path) {
        Path dirPath = Paths.get(path);
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
                return true;
            } catch (IOException e) {
                log.error("Failed to create directory {}: {}", path, e.getMessage());
                throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
            }
        }
        return false;
    }

    @Override
    public String generateUniqueFileName(String originalFilename) {
        return sanitizeFileName(originalFilename);
    }

    @Override
    public String getReadableFileSize(MultipartFile file) {
        long size = file.getSize();
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        DecimalFormat df = new DecimalFormat("#,##0.#");
        return df.format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    @Override
    public boolean fileExists(String path) {
        return Files.exists(Paths.get(path));
    }

    @Override
    public List<String> listFiles(String directoryPath) {
        try (var paths = Files.walk(Paths.get(directoryPath), 1)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list files in {}: {}", directoryPath, e.getMessage());
            throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
        }
    }

    @Override
    public String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty() || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String normalizePath(String path) {
        return Paths.get(path).normalize().toString();
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}

package com.deepfakedetector.controller;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.exception.GenericApiResponse;
import com.deepfakedetector.service.file.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/file")
@RequiredArgsConstructor
@Tag(name = "File", description = "Endpoints for file upload, download, deletion, and listing")
public class FileController {

    private final FileService fileService;

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.allowed.extensions}")
    private String allowedExtensions;

    @Value("${file.max.size:524288000}")
    private long maxFileSize;

    @Operation(summary = "Upload a file")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Empty file or invalid file type or file too large"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/upload")
    public GenericApiResponse<Map<String, String>> uploadFile(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new DeepfakeSilentException(DetectionErrorCode.EMPTY_OR_MISSING_FILE);
        }
        if (file.getSize() > maxFileSize) {
            throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
        }
        List<String> allowedExtList = Arrays.asList(allowedExtensions.split(","));
        if (!fileService.isValidFileType(file, allowedExtList)) {
            throw new DeepfakeSilentException(DetectionErrorCode.FILE_FORMAT_NOT_SUPPORTED);
        }
        try {
            String fileName = fileService.uploadFile(uploadPath, file);
            Map<String, String> data = new HashMap<>();
            data.put("fileName", fileName);
            return GenericApiResponse.ok(data);
        } catch (IOException | DeepfakeException e) {
            throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
        }
    }

    @Operation(summary = "Download a file by name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File downloaded successfully"),
            @ApiResponse(responseCode = "404", description = "File not found")
    })
    @GetMapping("/download/{fileName}")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable String fileName) {
        try {
            InputStream resource = fileService.getResourceFile(uploadPath, fileName);
            String contentType = determineContentType(fileName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileName)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new InputStreamResource(resource));
        } catch (IOException | DeepfakeSilentException | DeepfakeException e) {
            throw new DeepfakeSilentException(DetectionErrorCode.FILE_NOT_FOUND);
        }
    }

    @Operation(summary = "Delete a file by name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File deleted successfully"),
            @ApiResponse(responseCode = "404", description = "File not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{fileName}")
    public GenericApiResponse<Map<String, String>> deleteFile(@PathVariable String fileName) {
        String filePath = uploadPath + File.separator + fileName;
        try {
            if (!fileService.fileExists(filePath)) {
                throw new DeepfakeSilentException(DetectionErrorCode.FILE_NOT_FOUND);
            }
            boolean deleted = fileService.deleteFile(filePath);
            if (!deleted) {
                throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
            }
            Map<String, String> data = new HashMap<>();
            data.put("message", "File deleted successfully");
            return GenericApiResponse.ok(data);
        } catch (IOException e) {
            throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
        }
    }

    @Operation(summary = "List all files")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File list retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/list")
    public GenericApiResponse<List<String>> listFiles() {
        try {
            List<String> files = fileService.listFiles(uploadPath);
            return GenericApiResponse.ok(files);
        } catch (IOException e) {
            throw new DeepfakeSilentException(DetectionErrorCode.GENERAL_ERROR);
        }
    }

    @Operation(summary = "Check if a file exists by name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Existence status retrieved")
    })
    @GetMapping("/exists/{fileName}")
    public GenericApiResponse<Map<String, Boolean>> checkFileExists(@PathVariable String fileName) {
        String filePath = uploadPath + File.separator + fileName;
        boolean exists = fileService.fileExists(filePath);
        Map<String, Boolean> data = new HashMap<>();
        data.put("exists", exists);
        return GenericApiResponse.ok(data);
    }

    private String determineContentType(String fileName) {
        String ext = fileService.getFileExtension(fileName).toLowerCase();
        switch (ext) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "wmv":
                return "video/x-ms-wmv";
            case "mov":
                return "video/quicktime";
            case "mkv":
                return "video/x-matroska";
            case "flv":
                return "video/x-flv";
            case "webm":
                return "video/webm";
            case "3gp":
                return "video/3gpp";
            default:
                return "application/octet-stream";
        }
    }
}

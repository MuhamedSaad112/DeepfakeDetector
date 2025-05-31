package com.deepfakedetector.controller;

import com.deepfakedetector.model.dto.SystemSettingsDto;
import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.service.video.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/videos")
    public ResponseEntity<List<MediaFile>> getAllVideos() {
        return ResponseEntity.ok(adminService.getAllVideos());
    }

    @DeleteMapping("/videos/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable UUID id) {
        adminService.deleteVideo(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/videos/stats")
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        return ResponseEntity.ok(adminService.getSystemStats());
    }

    @GetMapping("/users/{username}/activity")
    public ResponseEntity<List<MediaFile>> getUserActivity(@PathVariable String username) {
        return ResponseEntity.ok(adminService.getUserActivity(username));
    }

    @GetMapping("/reports/system")
    public ResponseEntity<byte[]> downloadReport() {
        byte[] report = adminService.generateSystemReport();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=system-report.txt")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(report);
    }

    @PutMapping("/settings")
    public ResponseEntity<Void> updateSettings(@RequestBody SystemSettingsDto dto) {
        adminService.updateSettings(dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/settings")
    public ResponseEntity<SystemSettingsDto> getSettings() {
        return ResponseEntity.ok(adminService.getCurrentSettings());
    }
}



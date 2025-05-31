package com.deepfakedetector.service.video;

import com.deepfakedetector.model.dto.SystemSettingsDto;
import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.repository.MediaFileRepository;
import com.deepfakedetector.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final MediaFileRepository mediaFileRepository;
    private final UserRepository userRepository;

    private SystemSettingsDto systemSettings = new SystemSettingsDto();

    public List<MediaFile> getAllVideos() {
        return mediaFileRepository.findAll();
    }

    public void deleteVideo(UUID id) {
        mediaFileRepository.deleteById(id);
    }

    public Map<String, Object> getSystemStats() {
        long totalVideos = mediaFileRepository.count();
        long deepfakeVideos = mediaFileRepository.countByIsDeepfakeTrue();
        long totalUsers = userRepository.count();
        return Map.of(
                "totalVideos", totalVideos,
                "deepfakeVideos", deepfakeVideos,
                "totalUsers", totalUsers
        );
    }

    public List<MediaFile> getUserActivity(String username) {
        return mediaFileRepository.findByUserUserName(username);
    }

    public byte[] generateSystemReport() {
        StringBuilder report = new StringBuilder("System Report\n==============\n");
        report.append("Total Videos: ").append(mediaFileRepository.count()).append("\n");
        report.append("Deepfake Videos: ").append(mediaFileRepository.countByIsDeepfakeTrue()).append("\n");
        report.append("Total Users: ").append(userRepository.count()).append("\n");
        return report.toString().getBytes();
    }

    public void updateSettings(SystemSettingsDto dto) {
        this.systemSettings = dto;
    }

    public SystemSettingsDto getCurrentSettings() {
        return this.systemSettings;
    }
}


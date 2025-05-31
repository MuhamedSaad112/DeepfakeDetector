package com.deepfakedetector.service.video;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.model.entity.DetectionResultEntity;
import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.repository.DetectionResultRepository;
import com.deepfakedetector.repository.MediaFileRepository;
import com.deepfakedetector.util.ReportGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileVideoService {

    private final MediaFileRepository mediaFileRepository;
    private final DetectionResultRepository detectionResultRepository;

    public List<MediaFile> getAllVideosByUser(String username) {
        return mediaFileRepository.findAllByUser_UserNameOrderByUploadedAtDesc(username);
    }

    public DetectionResultEntity getVideoAnalysisDetails(UUID videoId, String username) throws DeepfakeException {
        MediaFile file = mediaFileRepository.findByIdAndUser_UserName(videoId, username)
                .orElseThrow(() -> new DeepfakeException(DetectionErrorCode.VIDEO_NOT_FOUND));

        return detectionResultRepository.findByMediaFile(file)
                .orElseThrow(() -> new DeepfakeException(DetectionErrorCode.DETECTION_FAILED));
    }

    public byte[] getReportForVideo(UUID videoId, String username) throws DeepfakeException {
        MediaFile file = mediaFileRepository.findByIdAndUser_UserName(videoId, username)
                .orElseThrow(() -> new DeepfakeException(DetectionErrorCode.VIDEO_NOT_FOUND));

        return ReportGenerator.generatePdf(file);
    }

    public void deleteVideo(UUID videoId, String username) throws DeepfakeException {
        MediaFile file = mediaFileRepository.findByIdAndUser_UserName(videoId, username)
                .orElseThrow(() -> new DeepfakeException(DetectionErrorCode.VIDEO_NOT_FOUND));
        mediaFileRepository.delete(file);
    }
}

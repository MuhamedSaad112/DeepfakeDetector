package com.deepfakedetector.service.video;

import com.deepfakedetector.mapper.DetectionResultMapper;
import com.deepfakedetector.model.dto.DetectionResultDto;
import com.deepfakedetector.model.entity.DetectionResultEntity;
import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.repository.DetectionResultRepository;
import com.deepfakedetector.repository.MediaFileRepository;
import com.deepfakedetector.repository.UserRepository;
import javassist.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisplayDataService {

    private final MediaFileRepository mediaFileRepository;
    private final DetectionResultRepository detectionResultRepository;
    private final UserRepository repository;
    private final DetectionResultMapper detectionResultMapper;


    public DetectionResultDto getVideoDetails(UUID mediaFileId) throws NotFoundException {
        MediaFile media = mediaFileRepository.findById(mediaFileId)
                .orElseThrow(() -> new NotFoundException("Media file not found"));

        DetectionResultEntity result = detectionResultRepository.findByMediaFile(media)
                .orElseThrow(() -> new NotFoundException("Detection result not found"));

        return detectionResultMapper.toDto(media, result);
    }

    public List<DetectionResultDto> getLatestResultsForUser(UUID userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return mediaFileRepository.findByUserIdOrderByUploadedAtDesc(userId, pageable)
                .stream()
                .map(media -> detectionResultRepository.findByMediaFile(media)
                        .map(result -> detectionResultMapper.toDto(media, result))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }


    public void deleteVideo(UUID mediaFileId) throws NotFoundException {
        MediaFile media = mediaFileRepository.findById(mediaFileId)
                .orElseThrow(() -> new NotFoundException("Media file not found"));
        mediaFileRepository.delete(media);
        log.info("Deleted video file: {}", media.getFileName());
    }


}

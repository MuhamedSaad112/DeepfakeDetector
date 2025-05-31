package com.deepfakedetector.mapper;

import com.deepfakedetector.model.dto.DetectionResultDto;
import com.deepfakedetector.model.entity.DetectionResultEntity;
import com.deepfakedetector.model.entity.MediaFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface DetectionResultMapper {

    DetectionResultMapper INSTANCE = Mappers.getMapper(DetectionResultMapper.class);

    @Mapping(target = "id", source = "media.id")
    @Mapping(target = "fileName", source = "media.fileName")
    @Mapping(target = "fileUrl", expression = "java(\"/api/videos/file/\" + media.getId())")
    @Mapping(target = "predictionLabel", source = "result.predictionLabel")
    @Mapping(target = "confidenceScore", source = "result.confidenceScore")
    @Mapping(target = "fakeRatio", expression = "java(getFakeRatioFromDetails(result.getProcessingDetails()))")
    @Mapping(target = "processingTime", source = "result.detectionTime")
    @Mapping(target = "modelVersion", source = "result.modelVersion")
    @Mapping(target = "resolution", source = "media.resolution")
    @Mapping(target = "format", source = "media.format")
    @Mapping(target = "uploadTime", expression = "java(media.getUploadedAt().toString())")
    @Mapping(target = "videoDuration", expression = "java(media.getDuration() + \" ثانية\")")
    @Mapping(target = "isDeepfake", source = "media.isDeepfake")
    DetectionResultDto toDto(MediaFile media, DetectionResultEntity result);

    default String getFakeRatioFromDetails(String processingDetails) {
        if (processingDetails == null || !processingDetails.contains("fakeRatio")) return "N/A";
        try {
            int start = processingDetails.indexOf("fakeRatio") + 10;
            int end = processingDetails.indexOf("%", start);
            return processingDetails.substring(start, end + 1);
        } catch (Exception e) {
            return "N/A";
        }
    }
}

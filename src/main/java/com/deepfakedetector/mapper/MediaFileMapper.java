package com.deepfakedetector.mapper;

import com.deepfakedetector.model.dto.MediaFileDto;
import com.deepfakedetector.model.entity.MediaFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MediaFileMapper {

    @Mapping(source = "user.userName", target = "username")
    @Mapping(target = "fileSize", expression = "java(formatFileSize(mediaFile.getFileSize()))")
    @Mapping(target = "duration", expression = "java(formatDuration(mediaFile.getDuration()))")
    MediaFileDto toDto(MediaFile mediaFile);

    List<MediaFileDto> toDtoList(List<MediaFile> mediaFiles);

    default String formatFileSize(Long sizeInBytes) {
        if (sizeInBytes == null) {
            return "0 MB";
        }
        long mb = Math.round(sizeInBytes / (1024.0 * 1024.0));
        return mb + " MB";
    }

    default String formatDuration(Double durationInSeconds) {
        if (durationInSeconds == null) {
            return "0 seconds";
        }
        if (durationInSeconds >= 60) {
            long minutes = Math.round(durationInSeconds / 60.0);
            return minutes + (minutes == 1 ? " minute" : " minutes");
        } else {
            long seconds = Math.round(durationInSeconds);
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
    }
}

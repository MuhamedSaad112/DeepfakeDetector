package com.deepfakedetector.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VideoMetadata {
    private double duration;
    private int width;
    private int height;
    private String format;

    public String getResolution() {
        return width + "x" + height;
    }
}

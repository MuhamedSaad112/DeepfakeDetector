package com.deepfakedetector.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectionResultResponse {
    private UUID videoId;
    private String result;
    private float score;
    private double processingTime;
    private String fakeRatio;
    private boolean fake;
}

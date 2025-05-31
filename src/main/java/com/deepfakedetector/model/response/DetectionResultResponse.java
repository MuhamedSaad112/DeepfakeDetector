package com.deepfakedetector.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectionResultResponse {

    private String result;
    private float score;
    private double processingTime;
    private String fakeRatio;
    private boolean fake;
}

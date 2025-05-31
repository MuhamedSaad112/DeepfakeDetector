package com.deepfakedetector.controller;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DetectionResponse;
import com.deepfakedetector.model.response.DetectionResultResponse;
import com.deepfakedetector.service.video.VideoProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/profile/videos")
@Log4j2
@RequiredArgsConstructor
@Tag(name = "Deepfake Detection (Profile)", description = "Endpoints for authenticated users to analyze videos")
public class DeepfakeDetectionProfileController {

    private final VideoProcessingService videoService;

    @PostMapping(
            value = "/detect",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Detect deepfake videos (authenticated)",
            description = "Analyze a video file using AI to determine if it's fake or real for an authenticated user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Video analyzed successfully",
                    content = @Content(schema = @Schema(implementation = DetectionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input or file",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema())
            )
    })
    public ResponseEntity<DetectionResponse<DetectionResultResponse>> detectVideo(
            @Parameter(description = "Video file to analyze", required = true)
            @RequestPart("video")
            @NotNull(message = "Video file is required")
            MultipartFile video
    ) throws IOException, InterruptedException, DeepfakeException {

        log.info("Received video file: {}", video.getOriginalFilename());
        DetectionResultResponse result = videoService.detectVideo(video);
        DetectionResponse<DetectionResultResponse> response = new DetectionResponse<>(result);
        return ResponseEntity.ok(response);
    }
}

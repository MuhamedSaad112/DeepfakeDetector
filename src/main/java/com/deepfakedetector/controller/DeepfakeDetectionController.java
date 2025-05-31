package com.deepfakedetector.controller;

import com.deepfakedetector.exception.DetectionResponse;
import com.deepfakedetector.model.response.DetectionResultResponse;
import com.deepfakedetector.service.video.DeepfakeVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/public/videos")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Deepfake Detection", description = "Endpoints for analyzing videos to detect deepfakes")
public class DeepfakeDetectionController {

    private final DeepfakeVideoService videoService;

    @PostMapping(
            value = "/detect",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Detect deepfake videos",
            description = "Analyze a video file to determine if it's fake or authentic using advanced AI algorithms."
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
    public Mono<DetectionResponse<DetectionResultResponse>> detectVideo(
            @Parameter(
                    description = "Video file to analyze (MP4, AVI, MKV, MOV, WMV, FLV, WEBM, M4V)",
                    required = true,
                    schema = @Schema(type = "string", format = "binary")
            )
            @RequestPart("video")
            @NotNull(message = "Video file is required")
            MultipartFile video
    ) {
        log.info("Received video detection request - File: {}, Size: {} bytes",
                video.getOriginalFilename(), video.getSize());

        return videoService.detectVideo(video)
                .map(DetectionResponse::new);
    }
}

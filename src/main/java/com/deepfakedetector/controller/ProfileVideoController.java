package com.deepfakedetector.controller;

import com.deepfakedetector.exception.GenericApiResponse;
import com.deepfakedetector.model.dto.DetectionResultDto;
import com.deepfakedetector.model.dto.MediaFileDto;
import com.deepfakedetector.service.video.ProfileVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile/videos")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Profile Video Management", description = "Endpoints for authenticated users to manage their videos and view analysis results")
public class ProfileVideoController {

    private final ProfileVideoService profileVideoService;

    @GetMapping
    @Operation(
            summary = "Get all user videos",
            description = "Retrieve all videos uploaded by the authenticated user, ordered by upload date (newest first)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Videos retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<List<MediaFileDto>>> getAllUserVideos() {
        String username = getCurrentUsername();
        log.info("Fetching all videos for user: {}", username);

        return profileVideoService.getAllVideosByUser(username)
                .map(videos -> GenericApiResponse.ok(
                        "Videos retrieved successfully",
                        "تم استرجاع الفيديوهات بنجاح",
                        videos
                ))
                .doOnSuccess(response -> log.info("Successfully retrieved {} videos for user: {}",
                        response.getData().size(), username))
                .doOnError(error -> log.error("Failed to retrieve videos for user: {}", username, error));
    }

    @GetMapping("/paginated")
    @Operation(
            summary = "Get user videos with pagination",
            description = "Retrieve user videos with pagination support for better performance"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Videos retrieved successfully with pagination",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination parameters",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<List<MediaFileDto>>> getUserVideosWithPagination(
            @Parameter(description = "Page number (1-based)", example = "1")
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "Page number must be >= 1")
            int page,

            @Parameter(description = "Page size (max 50)", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be > 0")
            int size
    ) {
        String username = getCurrentUsername();
        log.info("Fetching videos for user: {} with pagination - page: {}, size: {}", username, page, size);

        return profileVideoService.getVideosByUserWithPagination(username, page, size)
                .map(videos -> GenericApiResponse.ok(
                        "Videos retrieved successfully with pagination",
                        "تم استرجاع الفيديوهات بنجاح مع التصفح",
                        videos
                ))
                .doOnSuccess(response -> log.info("Successfully retrieved {} videos for user: {} (page: {})",
                        response.getData().size(), username, page))
                .doOnError(error -> log.error("Failed to retrieve paginated videos for user: {}", username, error));
    }


    @GetMapping("/{videoId}")
    @Operation(
            summary = "Get specific video details",
            description = "Retrieve detailed information about a specific video by ID"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Video details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Video not found or access denied",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid video ID format",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<MediaFileDto>> getVideoById(
            @Parameter(description = "Video ID", required = true)
            @PathVariable
            @NotNull(message = "Video ID is required")
            UUID videoId
    ) {
        String username = getCurrentUsername();
        log.info("Fetching video details for ID: {} by user: {}", videoId, username);

        return profileVideoService.getVideoById(videoId, username)
                .map(video -> GenericApiResponse.ok(
                        "Video details retrieved successfully",
                        "تم استرجاع تفاصيل الفيديو بنجاح",
                        video
                ))
                .doOnSuccess(response -> log.info("Successfully retrieved video: {} for user: {}",
                        response.getData().getFileName(), username))
                .doOnError(error -> log.error("Failed to retrieve video {} for user: {}", videoId, username, error));
    }

    @GetMapping("/{videoId}/analysis")
    @Operation(
            summary = "Get video analysis results",
            description = "Retrieve detailed AI analysis results for a specific video including confidence scores and detection details"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Analysis results retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Video or analysis results not found",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid video ID format",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<DetectionResultDto>> getVideoAnalysis(
            @Parameter(description = "Video ID", required = true)
            @PathVariable
            @NotNull(message = "Video ID is required")
            UUID videoId
    ) {
        String username = getCurrentUsername();
        log.info("Fetching analysis results for video: {} by user: {}", videoId, username);

        return profileVideoService.getVideoAnalysisDetails(videoId, username)
                .map(analysis -> GenericApiResponse.ok(
                        "Analysis results retrieved successfully",
                        "تم استرجاع نتائج التحليل بنجاح",
                        analysis
                ))
                .doOnSuccess(response -> log.info("Successfully retrieved analysis for video: {} - Result: {}",
                        videoId, response.getData().getPredictionLabel()))
                .doOnError(error -> log.error("Failed to retrieve analysis for video {} by user: {}",
                        videoId, username, error));
    }

    @GetMapping("/{videoId}/report")
    @Operation(
            summary = "Download video analysis report",
            description = "Generate and download a comprehensive PDF report containing video analysis results"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Report generated and downloaded successfully",
                    content = @Content(mediaType = "application/pdf")
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Video not found or access denied",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Report generation failed",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<ResponseEntity<ByteArrayResource>> downloadVideoReport(
            @Parameter(description = "Video ID", required = true)
            @PathVariable
            @NotNull(message = "Video ID is required")
            UUID videoId
    ) {
        String username = getCurrentUsername();
        log.info("Generating report for video: {} by user: {}", videoId, username);

        return profileVideoService.getReportForVideo(videoId, username)
                .map(reportBytes -> {
                    ByteArrayResource resource = new ByteArrayResource(reportBytes);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDispositionFormData("attachment",
                            String.format("deepfake-report-%s.pdf", videoId));
                    headers.setContentLength(reportBytes.length);

                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(resource);
                })
                .doOnSuccess(response -> log.info("Successfully generated report for video: {} (Size: {} KB)",
                        videoId, response.getBody().contentLength() / 1024))
                .doOnError(error -> log.error("Failed to generate report for video {} by user: {}",
                        videoId, username, error));
    }

    @DeleteMapping("/{videoId}")
    @Operation(
            summary = "Delete video",
            description = "Permanently delete a video and all its associated analysis data"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Video deleted successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Video not found or access denied",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Failed to delete video",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<String>> deleteVideo(
            @Parameter(description = "Video ID", required = true)
            @PathVariable
            @NotNull(message = "Video ID is required")
            UUID videoId
    ) {
        String username = getCurrentUsername();
        log.info("Deleting video: {} by user: {}", videoId, username);

        return profileVideoService.deleteVideo(videoId, username)
                .map(result -> GenericApiResponse.ok(
                        "Video deleted successfully",
                        "تم حذف الفيديو بنجاح",
                        "Video deleted successfully"
                ))
                .doOnSuccess(response -> log.info("Successfully deleted video: {} by user: {}", videoId, username))
                .doOnError(error -> log.error("Failed to delete video {} by user: {}", videoId, username, error));
    }

    @GetMapping("/count")
    @Operation(
            summary = "Get user video count",
            description = "Get the total number of videos uploaded by the authenticated user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Video count retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<Long>> getUserVideoCount() {
        String username = getCurrentUsername();
        log.debug("Getting video count for user: {}", username);

        return profileVideoService.getUserVideoCount(username)
                .map(count -> GenericApiResponse.ok(
                        "Video count retrieved successfully",
                        "تم استرجاع عدد الفيديوهات بنجاح",
                        count
                ))
                .doOnSuccess(response -> log.debug("User {} has {} videos", username, response.getData()))
                .doOnError(error -> log.error("Failed to get video count for user: {}", username, error));
    }

    @GetMapping("/{videoId}/access")
    @Operation(
            summary = "Check video access",
            description = "Verify if the authenticated user has access to a specific video"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Access check completed",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid video ID format",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<Boolean>> checkVideoAccess(
            @Parameter(description = "Video ID", required = true)
            @PathVariable
            @NotNull(message = "Video ID is required")
            UUID videoId
    ) {
        String username = getCurrentUsername();
        log.debug("Checking access for video: {} by user: {}", videoId, username);

        return profileVideoService.hasUserAccessToVideo(videoId, username)
                .map(hasAccess -> GenericApiResponse.ok(
                        "Access check completed successfully",
                        "تم فحص الوصول بنجاح",
                        hasAccess
                ))
                .doOnSuccess(response -> log.debug("User {} access to video {}: {}",
                        username, videoId, response.getData()))
                .doOnError(error -> log.error("Failed to check access for video {} by user: {}",
                        videoId, username, error));
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("No authenticated user found");
            throw new RuntimeException("User not authenticated");
        }
        return authentication.getName();
    }
}
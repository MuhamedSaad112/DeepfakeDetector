package com.deepfakedetector.controller;

import com.deepfakedetector.exception.GenericApiResponse;
import com.deepfakedetector.model.dto.MediaFileDto;
import com.deepfakedetector.model.dto.SystemSettingsDto;
import com.deepfakedetector.service.video.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Management", description = "Administrative endpoints for system management and monitoring")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/videos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get all videos in system",
            description = "Retrieve all videos from the system for administrative purposes"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Videos retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<List<MediaFileDto>>> getAllVideos() {
        log.info("Admin: Request to get all videos");

        return adminService.getAllVideos()
                .map(videos -> GenericApiResponse.ok(
                        "All videos retrieved successfully",
                        "تم استرجاع جميع الفيديوهات بنجاح",
                        videos
                ))
                .doOnSuccess(response -> log.info("Admin: Successfully retrieved {} videos",
                        response.getData().size()))
                .doOnError(error -> log.error("Admin: Failed to retrieve all videos", error));
    }

    @GetMapping("/videos/paginated")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get videos with pagination",
            description = "Retrieve videos with pagination support for better performance"
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
                    responseCode = "403",
                    description = "Access denied - Admin role required",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<List<MediaFileDto>>> getVideosWithPagination(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be >= 0")
            int page,

            @Parameter(description = "Page size (max 100)", example = "50")
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "Page size must be > 0")
            int size
    ) {
        log.info("Admin: Request to get videos with pagination - page: {}, size: {}", page, size);

        return adminService.getVideosWithPagination(page, size)
                .map(videos -> GenericApiResponse.ok(
                        "Videos retrieved successfully with pagination",
                        "تم استرجاع الفيديوهات بنجاح مع التصفح",
                        videos
                ))
                .doOnSuccess(response -> log.info("Admin: Successfully retrieved {} videos (page: {})",
                        response.getData().size(), page))
                .doOnError(error -> log.error("Admin: Failed to retrieve paginated videos", error));
    }

    @DeleteMapping("/videos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete video",
            description = "Permanently delete a video and all its associated data from the system"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Video deleted successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Video not found",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<String>> deleteVideo(
            @Parameter(description = "Video ID", required = true)
            @PathVariable
            @NotNull(message = "Video ID is required")
            UUID id
    ) {
        log.info("Admin: Request to delete video with ID: {}", id);

        return adminService.deleteVideo(id)
                .map(result -> GenericApiResponse.ok(
                        "Video deleted successfully",
                        "تم حذف الفيديو بنجاح",
                        "Video deleted successfully"
                ))
                .doOnSuccess(response -> log.info("Admin: Successfully deleted video: {}", id))
                .doOnError(error -> log.error("Admin: Failed to delete video: {}", id, error));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get system statistics",
            description = "Retrieve comprehensive system statistics including video counts, user metrics, and system health"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Statistics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<Map<String, Object>>> getSystemStats() {
        log.info("Admin: Request to get system statistics");

        return adminService.getSystemStats()
                .map(stats -> GenericApiResponse.ok(
                        "System statistics retrieved successfully",
                        "تم استرجاع إحصائيات النظام بنجاح",
                        stats
                ))
                .doOnSuccess(response -> log.info("Admin: Successfully retrieved system statistics"))
                .doOnError(error -> log.error("Admin: Failed to retrieve system statistics", error));
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get advanced analytics",
            description = "Retrieve advanced analytics including performance metrics and detailed system insights"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Analytics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<Map<String, Object>>> getAdvancedAnalytics() {
        log.info("Admin: Request to get advanced analytics");

        return adminService.getAdvancedAnalytics()
                .map(analytics -> GenericApiResponse.ok(
                        "Advanced analytics retrieved successfully",
                        "تم استرجاع التحليلات المتقدمة بنجاح",
                        analytics
                ))
                .doOnSuccess(response -> log.info("Admin: Successfully retrieved advanced analytics"))
                .doOnError(error -> log.error("Admin: Failed to retrieve advanced analytics", error));
    }

    @GetMapping("/users/{username}/activity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get user activity",
            description = "Retrieve detailed activity information for a specific user"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User activity retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<List<MediaFileDto>>> getUserActivity(
            @Parameter(description = "Username", required = true)
            @PathVariable
            @NotBlank(message = "Username is required")
            String username
    ) {
        log.info("Admin: Request to get user activity for: {}", username);

        return adminService.getUserActivity(username)
                .map(activities -> GenericApiResponse.ok(
                        "User activity retrieved successfully",
                        "تم استرجاع نشاط المستخدم بنجاح",
                        activities
                ))
                .doOnSuccess(response -> log.info("Admin: Successfully retrieved {} activities for user: {}",
                        response.getData().size(), username))
                .doOnError(error -> log.error("Admin: Failed to retrieve user activity for: {}", username, error));
    }

    @GetMapping("/reports/system")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Download system report",
            description = "Generate and download a comprehensive system report in text format"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Report generated and downloaded successfully",
                    content = @Content(mediaType = "application/octet-stream")
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Report generation failed",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<ResponseEntity<ByteArrayResource>> downloadSystemReport() {
        log.info("Admin: Request to generate and download system report");

        return adminService.generateSystemReport()
                .map(reportBytes -> {
                    ByteArrayResource resource = new ByteArrayResource(reportBytes);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    headers.setContentDispositionFormData("attachment",
                            String.format("system-report-%s.txt",
                                    java.time.LocalDateTime.now().format(
                                            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));
                    headers.setContentLength(reportBytes.length);

                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(resource);
                })
                .doOnSuccess(response -> log.info("Admin: Successfully generated system report (Size: {} KB)",
                        response.getBody().contentLength() / 1024))
                .doOnError(error -> log.error("Admin: Failed to generate system report", error));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update system settings",
            description = "Update global system settings and configuration"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Settings updated successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid settings data",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<SystemSettingsDto>> updateSettings(
            @Parameter(description = "System settings", required = true)
            @RequestBody
            @NotNull(message = "Settings data is required")
            SystemSettingsDto settingsDto
    ) {
        log.info("Admin: Request to update system settings");

        return adminService.updateSettings(settingsDto)
                .map(updatedSettings -> GenericApiResponse.ok(
                        "System settings updated successfully",
                        "تم تحديث إعدادات النظام بنجاح",
                        updatedSettings
                ))
                .doOnSuccess(response -> log.info("Admin: Successfully updated system settings"))
                .doOnError(error -> log.error("Admin: Failed to update system settings", error));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get current settings",
            description = "Retrieve current system settings and configuration"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Settings retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<SystemSettingsDto>> getCurrentSettings() {
        log.info("Admin: Request to get current system settings");

        return adminService.getCurrentSettings()
                .map(settings -> GenericApiResponse.ok(
                        "Current settings retrieved successfully",
                        "تم استرجاع الإعدادات الحالية بنجاح",
                        settings
                ))
                .doOnSuccess(response -> log.info("Admin: Successfully retrieved current settings"))
                .doOnError(error -> log.error("Admin: Failed to retrieve current settings", error));
    }

    @GetMapping("/videos/count")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get total video count",
            description = "Get the total number of videos in the system"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Video count retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GenericApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required",
                    content = @Content(schema = @Schema())
            )
    })
    public Mono<GenericApiResponse<Long>> getTotalVideoCount() {
        log.debug("Admin: Request to get total video count");

        return adminService.getTotalVideoCount()
                .map(count -> GenericApiResponse.ok(
                        "Total video count retrieved successfully",
                        "تم استرجاع إجمالي عدد الفيديوهات بنجاح",
                        count
                ))
                .doOnSuccess(response -> log.debug("Admin: Total videos in system: {}", response.getData()))
                .doOnError(error -> log.error("Admin: Failed to get total video count", error));
    }
}
package com.biel.lifecamp.system.controller;

import com.biel.lifecamp.starter.security.IdentityContext;
import com.biel.lifecamp.starter.web.ApiResponse;
import com.biel.lifecamp.system.common.exception.ProfileException;
import com.biel.lifecamp.system.config.SystemOpenApiConfiguration;
import com.biel.lifecamp.system.model.dto.request.UpdateProfileReq;
import com.biel.lifecamp.system.model.dto.response.AvatarUpdateResp;
import com.biel.lifecamp.system.model.dto.response.ProfileUpdateResp;
import com.biel.lifecamp.system.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Current authenticated user's editable profile endpoints.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
@RestController
@RequestMapping("/api/system/v1/me/profile")
@Tag(name = "User profile")
@SecurityRequirement(name = SystemOpenApiConfiguration.EXTERNAL_BEARER)
public final class ProfileController {
    private final ProfileService profileService;

    ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Updates or clears the current user's nickname.
     *
     * @param request profile update
     * @param servletRequest trusted identity request
     * @return normalized nickname and update time
     */
    @Operation(
            operationId = "updateCurrentProfile",
            summary = "Update current user profile",
            description = "A null nickname clears it. A non-null nickname is stripped and must"
                    + " contain 1 to 32 Unicode characters without control characters.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Profile updated",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Verified identity missing",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Active WeChat identity missing",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "Nickname rejected",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping
    ApiResponse<ProfileUpdateResp> update(
            @RequestBody UpdateProfileReq request, HttpServletRequest servletRequest) {
        if (request == null) {
            throw ProfileException.invalid(
                    "PROFILE_NICKNAME_INVALID", "nickname member is required");
        }
        long employeeId = currentEmployeeId(servletRequest);
        return ApiResponse.success(profileService.updateNickname(
                employeeId, request.isNicknamePresent(), request.getNickname()));
    }

    /**
     * Uploads a validated avatar to private object storage.
     *
     * @param avatar JPEG, PNG, or WebP image up to 2 MB
     * @param servletRequest trusted identity request
     * @return signed avatar URL and update time
     */
    @Operation(
            operationId = "uploadCurrentProfileAvatar",
            summary = "Upload current user avatar",
            description = "Accepts a decodable JPEG, PNG, or WebP image up to 2 MB."
                    + " The returned private-object URL is valid for one hour.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Avatar uploaded",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Verified identity missing",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413", description = "Avatar exceeds 2 MB",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "Avatar is malformed or unsupported",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "Private profile storage unavailable",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<AvatarUpdateResp> uploadAvatar(
            @RequestPart("avatar") MultipartFile avatar,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(profileService.uploadAvatar(
                currentEmployeeId(servletRequest), avatar));
    }

    private long currentEmployeeId(HttpServletRequest request) {
        return Long.parseLong(IdentityContext.require(request).employeeId());
    }
}

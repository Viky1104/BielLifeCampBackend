package com.biel.lifecamp.system.common.exception;

import com.biel.lifecamp.starter.web.ApiResponse;
import com.biel.lifecamp.system.controller.ProfileController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Converts profile failures to the platform response contract.
 *
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = ProfileController.class)
final class ProfileExceptionHandler {
    @ExceptionHandler(ProfileException.class)
    ResponseEntity<ApiResponse<Void>> profile(ProfileException exception) {
        return response(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> avatarTooLarge() {
        return response(HttpStatus.PAYLOAD_TOO_LARGE,
                "PROFILE_AVATAR_TOO_LARGE", "Avatar exceeds the 2 MB limit");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiResponse<Void>> avatarMissing() {
        return response(HttpStatus.UNPROCESSABLE_CONTENT,
                "PROFILE_AVATAR_INVALID", "Avatar part is required");
    }

    private ResponseEntity<ApiResponse<Void>> response(
            HttpStatus status, String code, String errorMsg) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, errorMsg));
    }
}

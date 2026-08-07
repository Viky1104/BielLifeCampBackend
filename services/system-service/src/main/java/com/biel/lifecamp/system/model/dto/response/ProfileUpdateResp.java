package com.biel.lifecamp.system.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Nickname update response.
 *
 * @param nickname normalized nickname, or {@code null} after clearing
 * @param updatedAt update time
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
@Schema(name = "ProfileUpdateResult")
public record ProfileUpdateResp(String nickname, Instant updatedAt) {
}

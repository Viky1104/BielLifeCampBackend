package com.biel.lifecamp.system.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Avatar upload response.
 *
 * @param avatarUrl one-hour signed avatar URL
 * @param updatedAt update time
 * @author Biel Life Camp Team
 * @since 2026-08-05
 */
@Schema(name = "AvatarUpdateResult")
public record AvatarUpdateResp(String avatarUrl, Instant updatedAt) {
}
